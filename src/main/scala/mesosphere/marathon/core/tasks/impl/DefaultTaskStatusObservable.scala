package mesosphere.marathon.core.tasks.impl

import mesosphere.marathon.core.tasks.{ TaskStatusEmitter, TaskStatusObservable }
import mesosphere.marathon.core.tasks.TaskStatusObservable.TaskStatusUpdate
import mesosphere.marathon.state.PathId
import rx.lang.scala.Observable
import rx.lang.scala.subjects.PublishSubject

class DefaultTaskStatusObservable extends TaskStatusObservable {
  private[this] val allSubject = PublishSubject[TaskStatusUpdate]()
  private[this] val subjects = Map.empty[PathId, Observable[TaskStatusUpdate]]

  private[impl] val emitter = new TaskStatusEmitter {
    override def publish(status: TaskStatusUpdate): Unit = {
      allSubject.onNext(status)
    }
  }

  override val forAll: Observable[TaskStatusUpdate] = allSubject

  override def forAppId(appId: PathId): Observable[TaskStatusUpdate] = {
    subjects.get(appId) match {
      case Some(observable) => observable
      case None => //
    }
  }

  def removeSubject(appId: PathId)
}
