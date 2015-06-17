package mesosphere.marathon.core.matcher

trait SubOfferMatcherManager {
  def addOfferMatcher(offerMatcher: OfferMatcher)
  def removeOfferMatcher(offerMatcher: OfferMatcher)
}
