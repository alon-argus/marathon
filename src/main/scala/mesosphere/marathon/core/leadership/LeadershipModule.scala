package mesosphere.marathon.core.leadership

import mesosphere.marathon.api.LeaderInfo

trait LeadershipModule {
  def leaderInfo: LeaderInfo
}
