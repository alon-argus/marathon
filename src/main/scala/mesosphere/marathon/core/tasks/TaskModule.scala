package mesosphere.marathon.core.tasks

trait TaskModule {
  def taskStatusEmitter: TaskStatusEmitter
  def taskStatusBus: TaskStatusObservable
}
