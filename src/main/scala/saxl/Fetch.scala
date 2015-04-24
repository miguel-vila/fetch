package saxl

import scala.concurrent.{ExecutionContext, Future}
import scalaz.{ Applicative , Traverse }

case class Fetch[A](result: Atom[DataCache] => Result[A]) {
  import Fetch._

  def flatMap[B](f: A => Fetch[B]): Fetch[B] = Fetch { dc =>
    result(dc) match {
      case Done(a)            => f(a).result(dc)
      case Blocked(br, cont)  => Blocked(br, cont flatMap f)
      case _throw : Throw     => _throw
    }
  }

  def map[B](f: A => B): Fetch[B] = flatMap { f andThen unit }

  def ap[B](ff: => Fetch[A => B]): Fetch[B] = Fetch { dc =>
    val ra = result(dc)
    val rf = ff.result(dc)
    (ra, rf) match {
      case (Done(a)           , Done(f)           ) => Done(f(a))
      case (Blocked(br, ca)   , Done(f)           ) => Blocked(br, ca.ap(unit[A => B](f)))
      case (_throw:Throw      , Done(f)           ) => _throw
      case (Done(a)           , Blocked(br, cf)   ) => Blocked(br, unit(a).ap(cf))
      case (Blocked(bra, ca)  , Blocked(brf, cf)  ) => Blocked(bra ++ brf /*@TODO <- ver eficiencia de esta operación, elegir estructura de datos adecuada*/ , ca.ap(cf))
      case (Throw(t)          , Blocked(brf,cf)   ) => Blocked(brf, throwF(t).ap(cf))
      case (_                 , _throw: Throw     ) => _throw
    }
  }

}

object Fetch {

  def unit[A](a: A): Fetch[A] = Fetch[A](_ => Done(a))

  def throwF[A](throwable: Throwable): Fetch[A] = Fetch(_ => Throw(throwable))

  implicit val applicativeInstance = new Applicative[Fetch] {
    override def point[A](a: => A): Fetch[A] = unit(a)

    override def ap[A, B](fa: => Fetch[A])(f: => Fetch[(A) => B]): Fetch[B] = fa.ap(f)
  }

  def traverse[A, G[_], B](value: G[A])(f: A => Fetch[B])(implicit G: Traverse[G]): Fetch[G[B]] = applicativeInstance.traverse(value)(f)

}

trait FetchInstance {

  def dataFetch[A](request: Request[A]): Fetch[A] = {
    def cont(box: Atom[FetchStatus[A]]) = Fetch { _ =>
      val FetchSuccess(a) = box() // este pattern match se garantiza exitóso en [runFetch]
      Done(a)
    }
    Fetch { dca =>
      val dc = dca()
      dc.lookup(request) match {
        case None =>
          val box: Atom[FetchStatus[A]] = Atom(NotFetched)
          dca.update(dc.insert(request,box))
          val br = BlockedRequest( request, box )
          Blocked(Seq(br), cont(box))
        case Some(box) =>
          box() match {
            case NotFetched           => Blocked(Seq.empty, cont(box))
            case FetchSuccess(value)  => Done(value)
            case FetchFailure(t)      => Throw(t)
          }
      }
    }
  }

  /**
   * Función utilitaria para setear el fetchStatus de un BloquedRequest
   * una vez se haya completado el Futuro
   *
   * Puede ser utilizada por implementaciones
   */
  def processBlockedRequest[A](br: BlockedRequest[A], futureImpl: Future[A])(implicit executionContext: ExecutionContext): Future[Unit] = {
    futureImpl map { a =>
      br.fetchStatus.update(FetchSuccess(a))
    } recover { case t: Throwable =>
      br.fetchStatus.update(FetchFailure(t))
    }
  }

  type Fetcher = Seq[BlockedRequest[_]] => Future[Unit]

  def runFetch[A](fetch: Fetch[A], fetcher: Fetcher)(implicit executionContext: ExecutionContext, dataCache: Atom[DataCache]): Future[A] = {
    fetch.result(dataCache) match {
      case Done(a)          => Future.successful(a)
      case Throw(t)         => Future.failed(t)
      case Blocked(br,cont) =>
        fetcher(br).flatMap { _ =>
          runFetch(cont,fetcher)
        }
    }
  }

}