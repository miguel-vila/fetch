package saxl

import org.scalacheck.{Gen, Arbitrary, Properties, Prop}
import org.scalacheck.Prop.forAll
import scalaz.Equal
import scalaz.scalacheck.ScalazProperties.monad
import saxl.Fetch.fetchInstance

/**
 * Created by seven4n on 27/04/15.
 */
class FetchTests extends Properties("") {

  def checkAll(name: String, props: Properties) = {
    for ((name2, prop) <- props.properties) yield {
      property(name + ":" + name2) = prop
    }
  }

  import Fetch.fetchInstance
  trait TestRequest[T]

  val exceptionGen: Gen[Exception] = Gen.const(new Exception("Some exception"))

  class SaxlGens[R[_],A](A: Gen[A], RA: Gen[R[A]]) {
    implicit val aGen = A
    implicit val raGen = RA

    val fetchSuccess: Gen[FetchSuccess[A]] = A.map(a => FetchSuccess(a))

    val fetchFailure: Gen[FetchFailure] = exceptionGen.map(e => FetchFailure(e))

    val notFetched: Gen[NotFetched.type] = Gen.const(NotFetched)

    implicit val fetchStatus: Gen[FetchStatus[A]] = Gen.oneOf(fetchSuccess, fetchFailure, notFetched)

    implicit def atomGen[T](implicit T: Gen[T]): Gen[Atom[T]] = T.map(t => Atom(t))

    implicit val blockedRequest: Gen[BlockedRequest[R,A]] = for {
      ra <- RA
      fs <- atomGen[FetchStatus[A]]
    } yield BlockedRequest(ra, fs)

    implicit val done: Gen[Done[R,A]] = A.map( a => Done(a) )

    implicit val blocked: Gen[Blocked[R,A]] = for {
      brs <- Gen.listOf(blockedRequest).map(_.toSeq)
      f <- fetch
    } yield Blocked(brs, f)

    implicit val throwG: Gen[Throw] = exceptionGen.map(e => Throw(e))

    implicit val result: Gen[Result[R,A]] = Gen.oneOf(done, blocked, throwG)

    implicit val fetch: Gen[Fetch[R,A]] = result.map(r => Fetch(_ => r))

  }

  class SaxlEqual[R[_],A](A: Equal[A]) {
    implicit val eqA = A

    implicit val dataCacheGen: Gen[DataCache[R]] = Gen.const(new DataCache[R]()) //@TODO generar valores mas complejos

    implicit val doneEqual: Equal[Done[R,A]] = A.contramap { done: Done[R,A] => done.value }

    implicit val blockedEqual: Equal[Blocked[R,A]] = new Equal[Blocked[R, A]] {
      override def equal(a1: Blocked[R, A], a2: Blocked[R, A]): Boolean = {
        a1.blockedRequests.toSet == a2.blockedRequests.toSet && fetchEqual.equal(a1.continuation, a2.continuation)
      }
    }

    implicit val throwableEqual: Equal[Throwable] = new Equal[Throwable] {
      override def equal(a1: Throwable, a2: Throwable): Boolean = a1.getClass == a2.getClass
    }

    implicit val throwEqual: Equal[Throw] = throwableEqual.contramap { t: Throw => t.throwable }

    implicit val resultEqual: Equal[Result[R,A]] = new Equal[Result[R, A]] {
      override def equal(a1: Result[R, A], a2: Result[R, A]): Boolean = (a1,a2) match {
        case (d1: Done[R,A], d2: Done[R,A]) => doneEqual.equal(d1,d2)
        case (b1: Blocked[R,A], b2: Blocked[R,A]) => blockedEqual.equal(b1,b2)
        case (t1: Throw, t2: Throw) => throwEqual.equal(t1,t2)
        case _ => false
      }
    }

    implicit val fetchEqual: Equal[Fetch[R,A]] = new Equal[Fetch[R, A]] {
      override def equal(a1: Fetch[R, A], a2: Fetch[R, A]): Boolean = {
        val samples = List.fill(100)(dataCacheGen.sample).collect {
          case Some(dc) => Atom(dc)
          case None => sys.error(s"Could not generate a sample datacache")
        }
        samples.forall( dca => resultEqual.equal(a1.result(dca), a2.result(dca)) )
      }
    }

  }




//  implicit def fetchArbitrary[R[_], A:Arbitrary]: Arbitrary[Fetch[R,A]] = ???

//  implicit def testFetchEqual[A: Gen] = fetchEqual[]

//  checkAll("",monad.laws[Fetch[TestRequest,?]])

}
