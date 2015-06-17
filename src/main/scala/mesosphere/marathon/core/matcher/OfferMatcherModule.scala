package mesosphere.marathon.core.matcher

import akka.actor.ActorRef
import mesosphere.marathon.core.actors.ActorsModule
import mesosphere.marathon.core.base.RandomModule
import mesosphere.marathon.core.tasks.TaskModule

trait OfferMatcherModule {
  def offerMatcherMultiplexer: ActorRef
}

class DefaultOfferMatcherModule(
  taskModule: TaskModule,
  randomModule: RandomModule,
  actorsModule: ActorsModule)
    extends OfferMatcherModule {
  override lazy val offerMatcherMultiplexer: ActorRef = {
    //    val props = OfferMatcherMultiplexerActor.props(randomModule.random, taskModule.taskStatusEmitter)
    //    val actorRef = actorsModule.actorSystem.actorOf(props, "OfferMatcherMultiplexer")
    //    implicit val dispatcher = actorsModule.actorSystem.dispatcher
    //    actorsModule.actorSystem.scheduler.schedule(
    //      30.seconds, 30.seconds, actorRef,
    //      OfferMatcherMultiplexerActor.SetTaskLaunchTokens(1000))
    //    actorRef
    ???
  }
}
