package saxl

import org.scalacheck.Gen

/**
 * Created by mglvl on 29/04/15.
 */
class SaxlGens[R[_], A](A: Gen[A], RA: Gen[R[A]]) {
  implicit val aGen = A
  implicit val raGen = RA

  val exceptionGen: Gen[Exception] = Gen.const(new Exception("Some exception"))

  val fetchSuccess: Gen[FetchSuccess[A]] = A.map(a => FetchSuccess(a))

  val fetchFailure: Gen[FetchFailure] = exceptionGen.map(e => FetchFailure(e))

  val notFetched: Gen[NotFetched.type] = Gen.const(NotFetched)

  implicit def fetchStatus: Gen[FetchStatus[A]] = Gen.oneOf(fetchSuccess, fetchFailure, notFetched)

  implicit def atomGen[T](implicit T: Gen[T]): Gen[Atom[T]] = T.map(t => Atom(t))

  implicit def blockedRequest: Gen[BlockedRequest[R, A]] = for {
    ra <- RA
    fs <- atomGen[FetchStatus[A]]
  } yield BlockedRequest(ra, fs)

  implicit def done: Gen[Done[R, A]] = A.map(a => Done(a))

  implicit def blocked: Gen[Blocked[R, A]] = for {
    brs <- Gen.listOf(blockedRequest).map(_.toSeq)
    f <- fetch
  } yield Blocked(brs, f)

  implicit def throwG: Gen[Throw] = exceptionGen.map(e => Throw(e))

  implicit def result: Gen[Result[R, A]] = Gen.oneOf(done, blocked, throwG)

  /**
   * Clase auxiliar para poder imprimir mejor valores de tipo fetch
   */
  class ConstantFetch(_result: Result[R, A]) extends Fetch[R, A](_ => _result) {
    override def toString() = s"DataCache => ( ${_result}} )"
  }

  implicit def fetch: Gen[Fetch[R, A]] = result.map(r => new ConstantFetch(r))

}