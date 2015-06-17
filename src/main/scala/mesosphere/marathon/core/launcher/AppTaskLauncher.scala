package mesosphere.marathon.core.launcher

import akka.actor.Actor
import mesosphere.marathon.state.AppDefinition

object AppTaskLauncher {
  sealed trait Requests

  /**
    * Increase the task count of the receiver.
    * The actor responds with a [[Count]] message.
    */
  case class AddTasks(count: Int) extends Requests
  /**
    * Get the current count.
    * The actor responds with a [[Count]] message.
    */
  case object GetCount extends Requests

  sealed trait Response
  case class Count(app: AppDefinition, count: Int)
}

/**
  * Allows processing offers for starting tasks for the given app.
  */
class AppTaskLauncher(app: AppDefinition, initialCount: Int) extends Actor {
  var count = initialCount

  override def receive: Receive = {
    Seq(
      receiveGetCurrentCount
    ).reduce(_.orElse[Any, Unit](_))
  }

  private[this] def receiveGetCurrentCount: Receive = {
    case AppTaskLauncher.GetCount => sender() ! AppTaskLauncher.Count(app, count)
  }

  private[this] def receiveAddCount: Receive = {
    case AppTaskLauncher.AddTasks(addCount) =>
      count += addCount
      sender() ! AppTaskLauncher.Count(app, count)
  }

  private[this] def receiveProcessOffers: Receive = {
    ???
  }
}
