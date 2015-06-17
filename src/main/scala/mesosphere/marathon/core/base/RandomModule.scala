package mesosphere.marathon.core.base

import scala.util.Random

trait RandomModule {
  def random: Random
}

class DefaultRandomModule extends RandomModule {
  override def random: Random = Random
}
