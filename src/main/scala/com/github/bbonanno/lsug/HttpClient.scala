package com.github.bbonanno.lsug

import com.github.bbonanno.lsug.HttpClient.ClientResponse

case class Credentials(email: String, apiKey: String)
case class AuthToken(token: String)
case class Ccy(iso: String) {
  def /(quote: Ccy): CcyPair = CcyPair(this, quote)
}
object Ccy {
  val GBP = Ccy("GBP")
  val USD = Ccy("USD")
}
case class CcyPair(base: Ccy, quote: Ccy)
case class Quantity(amount: BigDecimal, ccy: Ccy)
case class Price(rate: BigDecimal, ccyPair: CcyPair)
case class LimitOrder(quantity: Quantity, price: Price)
case class OrderStatus(quantity: Quantity, price: Price)
case class Prices(rates: Map[CcyPair, Price])
object Prices {
  val Empty = Prices(Map.empty)
}

object HttpClient {
  type ClientResponse[A] = Either[ClientError, A]
}

trait HttpClient[F[_]] {

  def login(credentials: Credentials): F[ClientResponse[AuthToken]]

  def getRates(ccyPairs: Set[CcyPair])(implicit token: AuthToken): F[ClientResponse[Prices]]

  def submitOrder(limitOrder: LimitOrder)(implicit token: AuthToken): F[ClientResponse[OrderStatus]]

}

sealed trait ClientError {
  def message: String
}

object ClientError {
  case class Unauthorized(message: String) extends ClientError
  case class UnknownError(message: String) extends ClientError
}
