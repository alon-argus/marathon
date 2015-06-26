package mesosphere.marathon.api

import javax.servlet.FilterChain
import javax.servlet.http.{ HttpServletRequest, HttpServletResponse }

import mesosphere.marathon.MarathonSpec
import mesosphere.util.Mockito
import org.scalatest.GivenWhenThen

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class LimitConcurrentRequestsFilterTest extends MarathonSpec with GivenWhenThen with Mockito {

  test("Multiple requests below boundary get answered correctly") {
    Given("A http filter chain")
    val request = mock[HttpServletRequest]
    val response = mock[HttpServletResponse]
    val chain = mock[FilterChain]
    chain.doFilter(request, response) answers { args => Thread.sleep(1000) }
    val rf = new LimitConcurrentRequestsFilter(2)

    When("requests where made before the limit")
    Future(rf.doFilter(request, response, chain))
    Future(rf.doFilter(request, response, chain))

    Then("The requests got answered")
    verify(chain, times(2)).doFilter(request, response)
  }

  test("Multiple requests above boundary get a 503") {
    Given("A http filter chain")
    val request = mock[HttpServletRequest]
    val response = mock[HttpServletResponse]
    val chain = mock[FilterChain]
    chain.doFilter(request, response) answers { args => Thread.sleep(1000) }
    val rf = new LimitConcurrentRequestsFilter(1)

    When("requests where made before the limit")
    Future(rf.doFilter(request, response, chain))
    rf.doFilter(request, response, chain) //no future, since that should fail synchronously

    Then("The requests got answered")
    verify(chain, times(1)).doFilter(request, response)
    verify(response, times(1)).sendError(503, "Too many concurrent requests! Allowed: 1.")
  }
}
