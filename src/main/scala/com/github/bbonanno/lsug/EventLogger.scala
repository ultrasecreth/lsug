package com.github.bbonanno.lsug

trait EventLogger[F[_]] {
  def record(e: Event): F[Unit]
}

sealed trait Event
object Event {
  class OrderStatusEvent(val orderStatus: OrderStatus) extends Event
}
