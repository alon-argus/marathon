package mesosphere.marathon.core.tasks

trait TaskStatusEmitter {
  def publish(status: TaskStatusObservable.TaskStatusUpdate)
}
