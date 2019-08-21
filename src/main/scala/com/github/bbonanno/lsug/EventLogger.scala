package com.github.bbonanno.lsug

trait EventLogger {
  def record(e: Event)
}

sealed trait Event
object Event {
  class OrderStatusEvent(val orderStatus: OrderStatus) extends Event
}
