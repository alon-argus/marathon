package mesosphere.marathon.core.matcher

trait OfferMatcherMultiplexer extends OfferMatcher {
  def addSubMatcher(offerMatcher: OfferMatcher)
  def removeSubMatcher(offerMatcher: OfferMatcher)
}
