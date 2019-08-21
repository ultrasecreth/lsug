package com.github.bbonanno.lsug

import cats.implicits._
import com.github.bbonanno.lsug.ClientError.Unauthorized
import com.github.bbonanno.lsug.Event.OrderStatusEvent
import org.mockito.cats.IdiomaticMockitoCats
import org.mockito.scalatest.IdiomaticMockito
import org.scalatest.concurrent.Eventually
import org.scalatest.{ FreeSpec, Matchers, OptionValues }

import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Pros:
 *  Don't have to write the stubs
 *  Use of mocks/stubs is standard across the app
 *  no java-scala compat issues
 *  the framework does a lot of verifications for us
 *  natural api
 *  automatic eqTo
 *  Equality & Prettifier
 *  Only specify the useful values for stubbing
 * Cons:
 *  I have an extra dependency
 */
class ExecutionActorTest_IdiomaticMockitoCats
    extends FreeSpec
    with Matchers
    with Eventually
    with OptionValues
    with TestBuilder
    with IdiomaticMockito
    with IdiomaticMockitoCats {

  trait Setup {
    implicit val token1 = AuthToken("good token")
    val token2          = AuthToken("good token2")

    val credentials            = Credentials("test-email", "test-key")
    val httpClient: HttpClient = mock[HttpClient].login(credentials) returnsF Right(token1) // just to show non composed applicative
    val eventLogger            = mock[EventLogger]
    val testObj                = new ExecutionActor(httpClient, eventLogger, credentials)
  }

  "ExecutionActor" - {

    "should login on startup" in new Setup {
      httpClient.login(credentials) was called
    }

    "should send an order and record the result" in new Setup {
      val status = orderStatus(100.GBP at 1.2999.USD)
      val order  = limitOrder(100.GBP at 1.3.USD)
      httpClient.submitOrder(order) returnsFG status

      testObj ! order

      eventually {
        eventLogger.record(orderStatusEvent(status)) was called
      }
    }

    "should re-login if the token expires" in new Setup {
      httpClient.login(credentials) returnsFG token2
      val status = orderStatus(100.GBP at 1.2999.USD)
      val order  = limitOrder(100.GBP at 1.3.USD)
      httpClient.submitOrder(order) raisesG Unauthorized("out!")
      httpClient.submitOrder(order)(token2) returnsFG status

      testObj ! order

      eventually {
        eventLogger.record(orderStatusEvent(status)) was called
      }
    }
  }
}
