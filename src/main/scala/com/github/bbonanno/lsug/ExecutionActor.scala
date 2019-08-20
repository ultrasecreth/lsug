package com.github.bbonanno.lsug

import cats._
import cats.implicits._
import com.github.bbonanno.lsug.ClientError.Unauthorized
import com.github.bbonanno.lsug.Event.OrderStatusEvent

import scala.collection.mutable.ListBuffer

class ExecutionActor[F[_]: Monad](httpClient: HttpClient[F], eventLog: EventLogger[F], credentials: Credentials) {

  private val stash = ListBuffer.empty[Any]

  private val catchAll: PartialFunction[Any, Unit] = {
    case any => stash += any
  }
  var ! : PartialFunction[Any, Unit] = catchAll

  private def login(): F[Unit] =
    httpClient
      .login(credentials)
      .map {
        case Right(token: AuthToken) =>
          println(s"Login successful, token: $token")
          this.! = onMessage(token)
          stash.foreach(m => this.!(m))
          stash.clear()
      }

  def onMessage(implicit token: AuthToken): PartialFunction[Any, Unit] = {
    case l: LimitOrder =>
      httpClient
        .submitOrder(l)
        .flatMap {
          case Right(status) =>
            println(s"Sending $l with $token")
            eventLog.record(new OrderStatusEvent(status))
          case Left(Unauthorized(error)) =>
            println(s"Got logged out: $error")
            stash += l
            this.! = catchAll
            login()
        }
  }

  login()

}
