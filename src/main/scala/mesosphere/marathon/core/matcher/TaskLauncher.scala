package mesosphere.marathon.core.matcher

import org.apache.mesos.Protos.{ TaskInfo, OfferID }

trait TaskLauncher {
  def launchTasks(offerID: OfferID, taskInfos: Seq[TaskInfo])
  def declineOffer(offerID: OfferID)
}
