package saxl

import scalaz.syntax.equal._
import org.scalacheck.Gen
import scalaz.Equal

/**
 * Created by mglvl on 29/04/15.
 */
class SaxlEqual[R[_],A](A: Equal[A]) {
  implicit val eqA = A

  implicit def dataCacheGen: Gen[DataCache[R]] = Gen.const(new DataCache[R]())

  implicit def doneEqual: Equal[Done[R,A]] = A.contramap { done: Done[R,A] => done.value }

  implicit def blockedEqual: Equal[Blocked[R,A]] = new Equal[Blocked[R, A]] {
    override def equal(a1: Blocked[R, A], a2: Blocked[R, A]): Boolean = {
      a1.blockedRequests.toSet == a2.blockedRequests.toSet && a1.continuation === a2.continuation
    }
  }

  implicit def throwableEqual: Equal[Throwable] = new Equal[Throwable] {
    override def equal(a1: Throwable, a2: Throwable): Boolean = a1.getClass == a2.getClass && a1.getMessage == a2.getMessage
  }

  implicit def throwEqual: Equal[Throw] = throwableEqual.contramap { t: Throw => t.throwable }

  implicit def resultEqual: Equal[Result[R,A]] = new Equal[Result[R, A]] {
    override def equal(a1: Result[R, A], a2: Result[R, A]): Boolean = {
      (a1, a2) match {
        case (d1: Done[R, A], d2: Done[R, A])       => d1 === d2
        case (b1: Blocked[R, A], b2: Blocked[R, A]) => b1 === b2
        case (t1: Throw, t2: Throw)                 => t1 === t2
        case _                                      => false
      }
    }
  }

  implicit def fetchEqual: Equal[Fetch[R,A]] = new Equal[Fetch[R, A]] {
    override def equal(a1: Fetch[R, A], a2: Fetch[R, A]): Boolean = {
      val sampleDC = dataCacheGen.sample match {
        case Some(dc) => Atom(dc)
        case None => sys.error(s"Could not generate a sample datacache")
      }
      a1.result(sampleDC) === a2.result(sampleDC)
    }
  }

}
