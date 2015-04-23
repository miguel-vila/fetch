package saxl

import java.util.concurrent.atomic._

object Atom {
  def apply[A](init: A): Atom[A] = new Atom[A](new AtomicReference(init))
}
case class Atom[+A] private (state: AtomicReference[Any]) extends AnyVal {
  def apply(): A = state.get().asInstanceOf[A]
  def update(value: Any/*Machetazo para poder hacer que Atom sea covariante*/): Unit = {
    assert(value.isInstanceOf[A])
    state.set(value.asInstanceOf[A])
  }
}