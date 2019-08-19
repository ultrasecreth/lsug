package com.github.bbonanno.lsug

trait EventLogger {
  def record(e: Event)
}

sealed trait Event
object Event {
  case class OrderStatusEvent(id: String, timestamp: Long, orderStatus: OrderStatus) extends Event
  case object AnotherEvent extends Event
}