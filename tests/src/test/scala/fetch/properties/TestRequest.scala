package fetch.properties

import org.scalacheck.{ Arbitrary, Gen }

import scalaz.Equal

/**
 * Created by mglvl on 29/04/15.
 */
case class TestRequest[T](t: T)

object TestRequest {

  implicit def Function1IntInt[A](implicit A: Arbitrary[Int]): Arbitrary[Int => Int] =
    Arbitrary(Gen.frequency[Int => Int](
      (1, Gen.const((x: Int) => x)),
      (1, Gen.const((x: Int) => x + 1)),
      (3, A.arbitrary.map(a => (_: Int) => a))))

  val genInt = Gen.oneOf(1, 2, 3)
  val genFInt = new SaxlGens[TestRequest, Int](genInt, genInt.map(n => TestRequest(n)))
  implicit val arbFetch = Arbitrary(genFInt.fetch)

  val arbIntToInt = Function1IntInt(Arbitrary(genInt))
  val genIntToInt = arbIntToInt.arbitrary
  val genFIntToInt = new SaxlGens[TestRequest, Int => Int](genIntToInt, genIntToInt.map(f => TestRequest(f)))
  implicit val arbFFetch = Arbitrary(genFIntToInt.fetch)

  implicit val intEq = new Equal[Int] {
    override def equal(a1: Int, a2: Int): Boolean = a1 == a2
  }

  val intSaxlEqual = new SaxlEqual[TestRequest, Int](intEq)
  implicit val fetchEq = intSaxlEqual.fetchEqual
}