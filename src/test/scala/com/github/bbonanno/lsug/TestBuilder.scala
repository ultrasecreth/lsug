package com.github.bbonanno.lsug

import com.github.bbonanno.lsug.Event.OrderStatusEvent
import org.scalactic.{Equality, Prettifier}

trait TestBuilder {

  implicit def EventEq[E <: Event]: Equality[E] =
    (a: E, b: Any) =>
      (a, b) match {
        case (os1: OrderStatusEvent, os2: OrderStatusEvent) => os1.orderStatus == os2.orderStatus
        case _                                              => false
    }

  implicit class AnyOps(a: Any) {
    def prettify(implicit p: Prettifier): String = p(a)
  }
  implicit val MyPrettifier: Prettifier = Prettifier {
    case bd: BigDecimal               => bd.bigDecimal.stripTrailingZeros.toPlainString
    case Ccy(ccy)                     => ccy
    case CcyPair(base, quote)         => s"$base/$quote"
    case Quantity(amount, ccy)        => s"${amount.prettify}.${ccy.prettify}"
    case Price(rate, ccyPair)         => s"1.${ccyPair.base} at ${rate.prettify}.${ccyPair.quote.prettify}"
    case LimitOrder(quantity, price)  => s"LimitOrder(${quantity.prettify} at ${price.rate.prettify}.${price.ccyPair.quote.prettify})"
    case OrderStatus(quantity, price) => s"OrderStatus(${quantity.prettify} at ${price.rate.prettify}.${price.ccyPair.quote.prettify})"
    case os: OrderStatusEvent         => s"OrderStatusEvent(orderStatus=${os.orderStatus.prettify})"
    case t: Iterable[_]               => s"Collection(${t.map(_.prettify).mkString(", ")})"
    case other                        => Prettifier.default(other)
  }

  implicit class NumberOps(n: Double) {
    def GBP: Quantity = Quantity(n, Ccy("GBP"))
    def USD: Quantity = Quantity(n, Ccy("USD"))
  }

  implicit class QuantityOps(q: Quantity) {
    def at(p: Quantity): (Quantity, Price) = q -> Price(p.amount, q.ccy / p.ccy)
  }

  def limitOrder(t: (Quantity, Price)): LimitOrder            = LimitOrder(t._1, t._2)
  def orderStatus(t: (Quantity, Price)): OrderStatus          = OrderStatus(t._1, t._2)
  def orderStatusEvent(status: OrderStatus): OrderStatusEvent = new OrderStatusEvent(status)
}
