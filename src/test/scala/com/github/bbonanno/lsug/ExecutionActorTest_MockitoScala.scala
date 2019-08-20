package com.github.bbonanno.lsug

import cats._
import com.github.bbonanno.lsug.ClientError.Unauthorized
import org.mockito.captor.ArgCaptor
import org.mockito.scalatest.MockitoSugar
import org.scalatest.{FreeSpec, Matchers, OptionValues}

/**
 * Pros:
 *  Don't have to write the stubs
 *  Use of mocks/stubs is standard across the app
 *  no java-scala compat issues
 *  the framework does a lot of verifications for us
 * Cons:
 *  better api, but still a bit verbose and non-natural
 *  I have an extra dependency
 *  Equality & Prettifier only when using eqTo
 */
class ExecutionActorTest_MockitoScala extends FreeSpec with Matchers with OptionValues with TestBuilder with MockitoSugar {

  trait Setup {
    implicit val token1 = AuthToken("good token")
    val token2          = AuthToken("good token2")

    val credentials = Credentials("test-email", "test-key")
    val httpClient  = mock[HttpClient[Id]]
    when(httpClient.login(credentials)) thenReturn Right(token1)
    val eventLogger = mock[EventLogger[Id]]
    val testObj     = new ExecutionActor(httpClient, eventLogger, credentials)
  }

  "ExecutionActor" - {

    "should login on startup" in new Setup {
      verify(httpClient).login(credentials)
    }

    "should send an order and record the result" in new Setup {
      val status = orderStatus(100.GBP at 1.2999.USD)
      val order  = limitOrder(100.GBP at 1.3.USD)
      when(httpClient.submitOrder(order)) thenReturn Right(status)

      testObj ! order

      val captor = ArgCaptor[Event]
      verify(eventLogger).record(captor)
      captor.value should ===(orderStatusEvent(status))
      //or
      captor hasCaptured orderStatusEvent(status)
    }

    "should re-login if the token expires" in new Setup {
      when(httpClient.login(credentials)) thenReturn Right(token2)
      val status = orderStatus(100.GBP at 1.2999.USD)
      val order  = limitOrder(100.GBP at 1.3.USD)
      when(httpClient.submitOrder(order)) thenReturn Left(Unauthorized("out!"))
      when(httpClient.submitOrder(order)(token2)) thenReturn Right(status)

      testObj ! order

      verify(eventLogger).record(eqTo(orderStatusEvent(status)))
    }
  }
}
