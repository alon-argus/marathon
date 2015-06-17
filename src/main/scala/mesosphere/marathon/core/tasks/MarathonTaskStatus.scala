package mesosphere.marathon.core.tasks

import org.apache.mesos.Protos.TaskStatus

sealed trait MarathonTaskStatus {
  def terminal: Boolean = false

  def mesosStatus: Option[TaskStatus]
  def mesosHealth: Option[Boolean] = mesosStatus.flatMap { status =>
    if (status.hasHealthy) Some(status.getHealthy) else None
  }
}

object MarathonTaskStatus {
  /** Marathon has send the launch command to Mesos. */
  case object LaunchRequested extends MarathonTaskStatus {
    override def mesosStatus: Option[TaskStatus] = None
  }

  case class Staging(mesosStatus: Option[TaskStatus]) extends MarathonTaskStatus
  case class Starting(mesosStatus: Option[TaskStatus]) extends MarathonTaskStatus
  case class Running(mesosStatus: Option[TaskStatus]) extends MarathonTaskStatus

  sealed trait Terminal extends MarathonTaskStatus {
    override def terminal: Boolean = true
  }
  object Terminal {
    def unapply(terminal: Terminal): Option[Terminal] = Some(terminal)
  }
  /**
    * Marathon has decided to deny the task launch even before communicating it to Mesos,
    * e.g. because of throttling.
    */
  case object LaunchDenied extends Terminal {
    override def mesosStatus: Option[TaskStatus] = None
  }
  case class Finished(mesosStatus: Option[TaskStatus]) extends Terminal
  case class Failed(mesosStatus: Option[TaskStatus]) extends Terminal
  case class Killed(mesosStatus: Option[TaskStatus]) extends Terminal
  case class Lost(mesosStatus: Option[TaskStatus]) extends Terminal
  case class Error(mesosStatus: Option[TaskStatus]) extends Terminal
}
