package com.github.bbonanno.lsug

import cats._
import com.github.bbonanno.lsug.ClientError.Unauthorized
import com.github.bbonanno.lsug.HttpClient.ClientResponse
import org.scalatest.{FreeSpec, Matchers, OptionValues}

import scala.collection.mutable.ListBuffer

/**
 * Pros:
 *  No mocks
 *  Stub per test
 *  Equality & Prettifier
 * Cons:
 *  Generic trait to detect undesired invocations
 *  A lot of noise in each test setup
 *  effort to write and ensure the stub does what I want
 */
class ExecutionActorTest_NoMocks extends FreeSpec with Matchers with OptionValues with TestBuilder {

  val token1 = AuthToken("good token")
  val token2 = AuthToken("good token2")

  val credentials = Credentials("test-email", "test-key")

  trait HttpClientStub extends HttpClient[Id] {
    override def login(credentials: Credentials): ClientResponse[AuthToken] = Right(token1)

    override def submitOrder(limitOrder: LimitOrder)(implicit token: AuthToken): ClientResponse[OrderStatus] = ???

    override def getRates(ccyPairs: Set[CcyPair])(implicit token: AuthToken): ClientResponse[Prices] = ???
  }

  class EventLoggerStub extends EventLogger[Id] {
    val _events = ListBuffer.empty[Event]

    override def record(e: Event): Unit = _events += e
  }

  trait Setup {
    val eventLogger = new EventLoggerStub
  }

  "ExecutionActor" - {

    "should login on startup" in new Setup {
      val httpClient = new HttpClientStub {
        var _credentials: Option[Credentials] = None
        override def login(credentials: Credentials): ClientResponse[AuthToken] = {
          _credentials = Some(credentials)
          super.login(credentials)
        }
      }

      val testObj = new ExecutionActor(httpClient, eventLogger, credentials)

      httpClient._credentials.value shouldBe credentials
    }

    "should send an order and record the result" in new Setup {
      //      val status = OrderStatus(Quantity(100, Ccy("GBP")), Price(1.2999, CcyPair(Ccy("GBP"), Ccy("USD"))))
      val status = orderStatus(100.GBP at 1.2999.USD)
      val httpClient = new HttpClientStub {
        val _tokens      = ListBuffer.empty[AuthToken]
        val _limitOrders = ListBuffer.empty[LimitOrder]
        override def submitOrder(limitOrder: LimitOrder)(implicit token: AuthToken): ClientResponse[OrderStatus] = {
          _tokens += token
          _limitOrders += limitOrder
          Right(status)
        }
      }

      val testObj = new ExecutionActor(httpClient, eventLogger, credentials)

      val order = limitOrder(100.GBP at 1.3.USD)
      testObj ! order

      httpClient._tokens should contain only token1
      httpClient._limitOrders should contain only order
      eventLogger._events should contain only orderStatusEvent(status)
    }

    "should re-login if the token expires" in new Setup {
      val status = orderStatus(100.GBP at 1.2999.USD)
      val httpClient = new HttpClientStub {
        val tokens = Iterator(token1, token2)
        override def login(credentials: Credentials): ClientResponse[AuthToken] =
          Right(tokens.next())

        val _tokens      = ListBuffer.empty[AuthToken]
        val _limitOrders = ListBuffer.empty[LimitOrder]
        override def submitOrder(limitOrder: LimitOrder)(implicit token: AuthToken): ClientResponse[OrderStatus] = {
          _tokens += token
          _limitOrders += limitOrder
          if (token == token1) Left(Unauthorized("out!")) else Right(status)
        }
      }

      val testObj = new ExecutionActor(httpClient, eventLogger, credentials)

      val order = limitOrder(100.GBP at 1.3.USD)
      testObj ! order

      httpClient._tokens should contain inOrderOnly (token1, token2)
      httpClient._limitOrders should have size 2
      httpClient._limitOrders.toSet should contain only order
      eventLogger._events should contain only orderStatusEvent(status)
    }
  }
}
