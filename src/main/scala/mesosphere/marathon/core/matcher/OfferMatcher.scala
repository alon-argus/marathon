package mesosphere.marathon.core.matcher

import mesosphere.marathon.state.Timestamp
import mesosphere.mesos.protos.Offer

object OfferMatcher

trait OfferMatcher {
  def processOffer(deadline: Timestamp, offer: Offer, launcher: TaskLauncher)

  //  def processOffer(deadline: Timestamp, offer: Offer): Future[LaunchTasks]
}
