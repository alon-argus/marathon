package mesosphere.marathon.core.leadership

import mesosphere.marathon.api.LeaderInfo

class DefaultLeadershipModule(val leaderInfo: LeaderInfo) extends LeadershipModule
