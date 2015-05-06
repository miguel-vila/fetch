package fetch

import java.util.concurrent.atomic._

object Atom {
  def apply[A](init: A): Atom[A] = new Atom[A](new AtomicReference(init))
}
case class Atom[+A] private (state: AtomicReference[Any]) extends AnyVal {
  def apply(): A = state.get().asInstanceOf[A]
  def update[B >: A](value: B): Unit = {
    state.set(value)
  }
}