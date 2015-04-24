package saxl

import scala.collection.immutable.HashMap
import scala.concurrent.{ExecutionContext, Future}
import scalaz.{ Applicative , Traverse }

trait FetchInstance[Return, Request[+_]] {

  sealed trait FetchStatus[+T]
  case object NotFetched extends FetchStatus[Nothing]
  case class FetchSuccess[T](value: T) extends FetchStatus[T]
  case class FetchFailure(t: Throwable) extends FetchStatus[Nothing]

  case class DataCache(private val map: HashMap[Request[Return],Atom[FetchStatus[Return]]] = new HashMap[Request[Return],Atom[FetchStatus[Return]]]) {
    def lookup[A<:Return](r: Request[A]): Option[Atom[FetchStatus[A]]] = map.get(r).map(_.asInstanceOf[Atom[FetchStatus[A]]])
    def insert[A<:Return](r: Request[A], v: Atom[FetchStatus[A]]): Unit = map + (r -> v)
  }

  case class BlockedRequest[+A<:Return](val request: Request[A], val fetchStatus: Atom[FetchStatus[A]])

  sealed trait Result[+A]
  case class Done[A](value: A) extends Result[A]
  case class Blocked[A](blockedRequests: Seq[BlockedRequest[Return]], continuation: Fetch[A]) extends Result[A]
  case class Throw(throwable: Throwable) extends Result[Nothing]

  case class Fetch[+A](result: Atom[DataCache] => Result[A]) {

    def flatMap[B](f: A => Fetch[B]): Fetch[B] = Fetch { dc =>
      result(dc) match {
        case Done(a)            => f(a).result(dc)
        case Blocked(br, cont)  => Blocked(br, cont flatMap f)
        case _throw : Throw     => _throw
      }
    }

    def map[B](f: A => B): Fetch[B] = flatMap { f andThen Fetch.unit }

  }

  object Fetch {

    def unit[A](a: A): Fetch[A] = Fetch[A](_ => Done(a))

    def throwF[A](throwable: Throwable): Fetch[A] = Fetch(_ => Throw(throwable))

    def ap[A, B](fa: => Fetch[A])(ff: => Fetch[A => B]): Fetch[B] = Fetch { dc =>
      val ra = fa.result(dc)
      val rf = ff.result(dc)
      (ra, rf) match {
        case (Done(a)           , Done(f)           ) => Done(f(a))
        case (Blocked(br, ca)   , Done(f)           ) => Blocked(br, ap(ca)(unit[A => B](f)))
        case (_throw:Throw      , Done(f)           ) => _throw
        case (Done(a)           , Blocked(br, cf)   ) => Blocked(br, ap(unit(a))(cf))
        case (Blocked(bra, ca)  , Blocked(brf, cf)  ) => Blocked(bra ++ brf /*@TODO <- ver eficiencia de esta operación, elegir estructura de datos adecuada*/ , ap(ca)(cf))
        case (Throw(t)          , Blocked(brf,cf)   ) => Blocked(brf, ap(throwF(t))(cf))
        case (_                 , _throw: Throw     ) => _throw
      }
    }

    implicit val applicativeInstance = new Applicative[Fetch] {
      override def point[A](a: => A): Fetch[A] = unit(a)

      override def ap[A, B](fa: => Fetch[A])(f: => Fetch[(A) => B]): Fetch[B] = ap(fa)(f)
    }

    def traverse[A, G[_], B](value: G[A])(f: A => Fetch[B])(implicit G: Traverse[G]): Fetch[G[B]] = applicativeInstance.traverse(value)(f)

  }

  def dataFetch[A<:Return](request: Request[A]): Fetch[A] = {
    def cont(box: Atom[FetchStatus[A]]) = Fetch { _ =>
      val FetchSuccess(a) = box() // este pattern match se garantiza exitóso en [runFetch]
      Done(a)
    }
    Fetch { dca =>
      val dc = dca()
      dc.lookup(request) match {
        case None =>
          val box = Atom[FetchStatus[A]](NotFetched)
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
  def processBlockedRequest[A<:Return](br: BlockedRequest[A], futureImpl: Future[A])(implicit executionContext: ExecutionContext): Future[Unit] = {
    futureImpl map { a =>
      br.fetchStatus.update(FetchSuccess(a))
    } recover { case t: Throwable =>
      br.fetchStatus.update(FetchFailure(t))
    }
  }


  /*
  type Interpreter[A] = Request[A] => Future[A]

  def processBlockedRequest[A<:Return](interpreter: Interpreter[A])(br: BlockedRequest[A])(implicit executionContext: ExecutionContext): Future[Unit] = {
    interpreter(br.request) map { a =>
      br.fetchStatus.update(FetchSuccess(a))
    } recover { case t: Throwable =>
      br.fetchStatus.update(FetchFailure(t))
    }
  }


  def processBlockedRequests(br: Seq[BlockedRequest[Return]])(implicit executionContext: ExecutionContext, interpreter: Interpreter[Return]): Future[Unit] = {
    for {
      _ <- Future.traverse(br)(processBlockedRequest(interpreter))
    } yield ()
  }
  */

  type Fetcher = Seq[BlockedRequest[Return]] => Future[Unit]

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