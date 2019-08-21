package com.github.bbonanno.lsug

import java.util.UUID

import com.github.bbonanno.lsug.ClientError.Unauthorized
import com.github.bbonanno.lsug.Event.OrderStatusEvent

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Success

class ExecutionActor(httpClient: HttpClient, eventLog: EventLogger, credentials: Credentials) {

  login()

  private val stash = ListBuffer.empty[Any]

  private val catchAll: PartialFunction[Any, Unit] = {
    case any => stash += any
  }
  var ! : PartialFunction[Any, Unit] = catchAll

  private def login() =
    httpClient
      .login(credentials)
      .onComplete {
        case Success(Right(token: AuthToken)) =>
          println(s"Login successful, token: $token")
          this.! = onMessage(token)
          stash.foreach(m => this.!(m))
          stash.clear()
      }

  def onMessage(implicit token: AuthToken): PartialFunction[Any, Unit] = {
    case l: LimitOrder =>
      httpClient
        .submitOrder(l)
        .onComplete {
          case Success(Right(status)) =>
            println(s"Sending $l with $token")
            eventLog.record(new OrderStatusEvent(status))
          case Success(Left(Unauthorized(error))) =>
            println(s"Got logged out: $error")
            stash += l
            this.! = catchAll
            login()
        }
  }

}
