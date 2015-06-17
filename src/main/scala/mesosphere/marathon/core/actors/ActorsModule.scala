package mesosphere.marathon.core.actors

import akka.actor.ActorSystem

/**
  * Contains basic dependencies used throughout the application disregarding the concrete function.
  */
trait ActorsModule {
  def actorSystem: ActorSystem
}
