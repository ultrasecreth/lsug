package com.github.bbonanno.lsug

trait EventLogger[Z[_]] {
  def record(e: Event): Z[Unit]
}

sealed trait Event
object Event {
  class OrderStatusEvent(val orderStatus: OrderStatus) extends Event
}
