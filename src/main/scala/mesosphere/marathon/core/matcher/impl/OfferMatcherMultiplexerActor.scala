package mesosphere.marathon.core.matcher.impl

import akka.actor._
import akka.event.LoggingReceive
import mesosphere.marathon.core.tasks.TaskStatusObservable.TaskStatusUpdate
import mesosphere.marathon.core.tasks.{ MarathonTaskStatus, TaskStatusEmitter }
import mesosphere.marathon.state.Timestamp
import mesosphere.marathon.tasks.ResourceUtil
import org.apache.mesos.Protos.{ Offer, OfferID, Resource, TaskInfo }
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.collection.immutable.Queue
import scala.concurrent.duration.Deadline
import scala.util.Random
import scala.util.control.NonFatal

/**
  * The OfferMatcherMultiplexer actor offers one interface to a
  * dynamic collection of matchers.
  */
private[impl] object OfferMatcherMultiplexerActor {
  def props(random: Random, taskStatusEmitter: TaskStatusEmitter): Props = {
    Props(new OfferMatcherMultiplexerActor(random, taskStatusEmitter))
  }

  /**
    * Send to an offer matcher to request a match.
    *
    * This should always be replied to with a LaunchTasks message.
    */
  case class MatchOffer(matchingDeadline: Deadline, remainingOffer: Offer)

  /**
    * Reply from an offer matcher to a MatchOffer. If the offer match
    * could not match the offer in any way it should simply leave the tasks
    * collection empty.
    *
    * To increase fairness between matchers, each normal matcher should only launch as
    * few tasks as possible per offer -- usually one. Multiple tasks could be used
    * if the tasks need to be colocated. The OfferMultiplexer tries to summarize suitable
    * matches from multiple offer matches into one response.
    *
    * Sending a LaunchTasks reply does not guarantee that these tasks can actually be launched.
    * The launcher of message should setup some kind of timeout mechanism.
    *
    * The receiver of this message should send a DeclineLaunch message to the launcher though if they
    * are any obvious reasons to deny launching these tasks.
    */
  case class LaunchTasks(offerId: OfferID, tasks: Seq[TaskInfo])

  sealed trait ChangeMatchersRequest
  case class AddMatcher(consumer: ActorRef) extends ChangeMatchersRequest
  case class RemoveMatcher(consumer: ActorRef) extends ChangeMatchersRequest

  sealed trait ChangeConsumersResponse
  case class MatcherAdded(consumer: ActorRef) extends ChangeConsumersResponse
  case class MatcherRemoved(consumer: ActorRef) extends ChangeConsumersResponse

  case class SetTaskLaunchTokens(tokens: Int)

  private val log = LoggerFactory.getLogger(getClass)
  private case class OfferData(
      offer: Offer,
      deadline: Deadline,
      sender: ActorRef,
      consumerQueue: Queue[ActorRef],
      tasks: Seq[TaskInfo]) {
    def addConsumer(consumer: ActorRef): OfferData = copy(consumerQueue = consumerQueue.enqueue(consumer))
    def nextConsumerOpt: Option[(ActorRef, OfferData)] = {
      consumerQueue.dequeueOption map {
        case (nextConsumer, newQueue) => nextConsumer -> copy(consumerQueue = newQueue)
      }
    }

    def addTasks(added: Seq[TaskInfo]): OfferData = {
      val offerResources: Seq[Resource] = offer.getResourcesList.asScala
      val taskResources: Seq[Resource] = added.flatMap(_.getResourcesList.asScala)
      val leftOverResources = ResourceUtil.consumeResources(offerResources, taskResources)
      val leftOverOffer = offer.toBuilder.clearResources().addAllResources(leftOverResources.asJava).build()
      copy(
        offer = leftOverOffer,
        tasks = added ++ tasks
      )
    }
  }
}

private class OfferMatcherMultiplexerActor private (random: Random, taskStatusEmitter: TaskStatusEmitter)
    extends Actor with ActorLogging {
  private[this] var launchTokens: Int = 0

  private[this] var livingMatchers: Set[ActorRef] = Set.empty
  private[this] var offerQueues: Map[OfferID, OfferMatcherMultiplexerActor.OfferData] = Map.empty

  override def receive: Receive = {
    Seq[Receive](
      setLaunchTokens,
      processTerminated,
      changingConsumers,
      receiveProcessOffer,
      processLaunchTasks
    ).reduceLeft(_.orElse[Any, Unit](_))
  }

  private[this] def setLaunchTokens: Receive = LoggingReceive.withLabel("setLaunchTokens") {
    case OfferMatcherMultiplexerActor.SetTaskLaunchTokens(tokens) => launchTokens = tokens
  }

  private[this] def processTerminated: Receive = LoggingReceive.withLabel("processTerminated") {
    case Terminated(consumer) =>
      livingMatchers -= consumer
  }

  private[this] def changingConsumers: Receive = LoggingReceive.withLabel("changingConsumers") {
    case OfferMatcherMultiplexerActor.AddMatcher(consumer) =>
      livingMatchers += consumer
      offerQueues.mapValues(_.addConsumer(consumer))
      sender() ! OfferMatcherMultiplexerActor.MatcherAdded(consumer)

    case OfferMatcherMultiplexerActor.RemoveMatcher(consumer) =>
      livingMatchers -= consumer
      sender() ! OfferMatcherMultiplexerActor.MatcherRemoved(consumer)
  }

  private[this] def receiveProcessOffer: Receive = LoggingReceive.withLabel("receiveProcessOffer") {
    case OfferMatcherMultiplexerActor.MatchOffer(deadline, offer) if deadline.isOverdue() || launchTokens <= 0 =>
      sender() ! OfferMatcherMultiplexerActor.LaunchTasks(offer.getId, tasks = Seq.empty)

    case processOffer @ OfferMatcherMultiplexerActor.MatchOffer(deadline, offer: Offer) =>
      log.info(s"Start processing offer ${offer.getId}")

      // setup initial offer data
      val randomizedConsumers = random.shuffle(livingMatchers).to[Queue]
      val data = OfferMatcherMultiplexerActor.OfferData(offer, deadline, sender(), randomizedConsumers, Seq.empty)
      offerQueues += offer.getId -> data

      // deal with the timeout
      import context.dispatcher
      context.system.scheduler.scheduleOnce(
        deadline.timeLeft,
        self,
        OfferMatcherMultiplexerActor.LaunchTasks(offer.getId, Seq.empty))

      // process offer for the first time
      continueWithOffer(offer.getId, Seq.empty)
  }

  private[this] def processLaunchTasks: Receive = {
    case OfferMatcherMultiplexerActor.LaunchTasks(offerId, tasks) => continueWithOffer(offerId, tasks)
  }

  private[this] def continueWithOffer(offerId: OfferID, addedTasks: Seq[TaskInfo]): Unit = {
    offerQueues.get(offerId).foreach { data =>
      val dataWithTasks = try {
        val (launchTasks, declineTasks) = addedTasks.splitAt(Seq(launchTokens, addedTasks.size).min)

        declineTasks.foreach { declineTask =>
          val ts = Timestamp.now()
          val status = new TaskStatusUpdate(ts, declineTask.getTaskId, MarathonTaskStatus.LaunchDenied)
          taskStatusEmitter.publish(status)
        }

        val newData = data.addTasks(launchTasks)
        launchTokens -= launchTasks.size
        newData
      }
      catch {
        case NonFatal(e) =>
          log.error(s"unexpected error processing tasks for $offerId from ${sender()}", e)
          data
      }

      val nextConsumerOpt = if (data.deadline.isOverdue()) {
        log.warning(s"Deadline for ${data.offer.getId} overdue. Scheduled ${data.tasks.size} tasks so far.")
        None
      }
      else if (launchTokens <= 0) {
        log.warning(s"No launch tokens left for ${data.offer.getId}. Scheduled ${data.tasks.size} tasks so far.")
        None
      }
      else {
        dataWithTasks.nextConsumerOpt
      }

      nextConsumerOpt match {
        case Some((nextConsumer, newData)) =>
          nextConsumer ! OfferMatcherMultiplexerActor.MatchOffer(newData.deadline, newData.offer)
          offerQueues += offerId -> newData
        case None =>
          data.sender ! OfferMatcherMultiplexerActor.LaunchTasks(dataWithTasks.offer.getId, dataWithTasks.tasks)
          offerQueues -= offerId
          log.info(s"Finished processing ${data.offer.getId}. Launching ${data.tasks.size} tasks.")
      }
    }
  }
}

