package com.github.bbonanno.lsug

import com.github.bbonanno.lsug.ClientError.Unauthorized
import com.github.bbonanno.lsug.Event.OrderStatusEvent
import com.github.bbonanno.lsug.HttpClient.ClientResponse
import org.scalatest.concurrent.Eventually
import org.scalatest.{ FreeSpec, Matchers, OptionValues }

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future

/**
 * Pros:
 *  No mocks
 *  Stub per suite
 *  Uses iterators to detect undesired invocations
 *  Equality & Prettifier
 * Cons:
 *  has to duplicate the setup of the complex dependency and testObj
 *  most of the noise is out of the test, but that means I can't just look at the test to understand whats going on
 *  still has the effort to write and ensure the stub does what I want
 */
class ExecutionActorTest_NoMocks2 extends FreeSpec with Matchers with Eventually with OptionValues with TestBuilder {

  val token1 = AuthToken("good token")
  val token2 = AuthToken("good token2")

  class HttpClientStub(tokens: Iterator[ClientResponse[AuthToken]] = Iterator(Right(token1)),
                       statuses: Iterator[ClientResponse[OrderStatus]] = Iterator.empty)
      extends HttpClient {

    var _credentials: Option[Credentials] = None
    override def login(credentials: Credentials): Future[ClientResponse[AuthToken]] = {
      _credentials = Some(credentials)
      Future.successful(tokens.next())
    }

    val _tokens      = ListBuffer.empty[AuthToken]
    val _limitOrders = ListBuffer.empty[LimitOrder]
    override def submitOrder(limitOrder: LimitOrder)(implicit token: AuthToken): Future[ClientResponse[OrderStatus]] = {
      _tokens += token
      _limitOrders += limitOrder
      Future.successful(statuses.next())
    }

    override def getRates(ccyPairs: Set[CcyPair])(implicit token: AuthToken): Future[ClientResponse[Prices]] = ???
  }

  class EventLoggerStub extends EventLogger {
    val _events                         = ListBuffer.empty[Event]
    override def record(e: Event): Unit = _events += e
  }

  trait Setup {
    val credentials = Credentials("test-email", "test-key")
    val eventLogger = new EventLoggerStub
  }

  "ExecutionActor" - {

    "should login on startup" in new Setup {
      val httpClient = new HttpClientStub()

      val testObj = new ExecutionActor(httpClient, eventLogger, credentials)

      httpClient._credentials.value shouldBe credentials
    }

    "should send an order and record the result" in new Setup {
      val status     = orderStatus(100.GBP at 1.2999.USD)
      val httpClient = new HttpClientStub(statuses = Iterator(Right(status)))
      val testObj    = new ExecutionActor(httpClient, eventLogger, credentials)
      val order      = limitOrder(100.GBP at 1.3.USD)

      testObj ! order

      eventually {
        httpClient._tokens should contain only token1
        httpClient._limitOrders should contain only order
        eventLogger._events should contain only orderStatusEvent(status)
      }
    }

    "should re-login if the token expires" in new Setup {
      val status = orderStatus(100.GBP at 1.2999.USD)
      val httpClient =
        new HttpClientStub(
          tokens = Iterator(Right(token1), Right(token2)),
          statuses = Iterator(Left(Unauthorized("out!")), Right(status))
        )
      val testObj = new ExecutionActor(httpClient, eventLogger, credentials)
      val order   = limitOrder(100.GBP at 1.3.USD)

      testObj ! order

      eventually {
        httpClient._tokens should contain inOrderOnly (token1, token2)
        httpClient._limitOrders should have size 2
        httpClient._limitOrders.toSet should contain only order
        eventLogger._events should contain only orderStatusEvent(status)
      }
    }
  }
}
