package mesosphere.marathon.core.launcher

import mesosphere.marathon.state.{ PathId, AppDefinition }
import mesosphere.marathon.tasks.TaskQueue
import mesosphere.marathon.tasks.TaskQueue.QueuedTaskCount

import scala.collection.immutable.Seq
import scala.concurrent.duration.Deadline

class CoreTaskQueue extends TaskQueue {
  override def list: Seq[QueuedTaskCount] = ???

  override def count(appId: PathId): Int = ???

  override def listApps: Seq[AppDefinition] = ???

  override def listWithDelay: Seq[(QueuedTaskCount, Deadline)] = ???

  override def purge(appId: PathId): Unit = ???

  override def add(app: AppDefinition, count: Int): Unit = ???
}
