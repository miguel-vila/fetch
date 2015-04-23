package saxl

import scala.collection.immutable.HashMap
import scala.concurrent.{ExecutionContext, Future}

trait FetchInstance[Return, Request[+_]] {

  sealed trait FetchStatus[+T]
  case object NotFetched extends FetchStatus[Nothing]
  case class FetchSuccess[T](value: T) extends FetchStatus[T]
  case class FetchFailure(t: Throwable) extends FetchStatus[Nothing]

  case class DataCache(private val map: HashMap[Request[Return],Atom[FetchStatus[Return]]]) {
    def lookup[A<:Return](r: Request[A]): Option[Atom[FetchStatus[A]]] = map.get(r).map(_.asInstanceOf[Atom[FetchStatus[A]]])
    def insert[A<:Return](r: Request[A], v: Atom[FetchStatus[A]]): Unit = map + (r -> v)
  }

  case class BlockedRequest[+A<:Return](val request: Request[A], val fetchStatus: Atom[FetchStatus[A]])

  sealed trait Result[+A]
  case class Done[A](value: A) extends Result[A]
  case class Blocked[A](blockedRequests: Seq[BlockedRequest[Return]], continuation: Fetch[A]) extends Result[A]
  case class Throw(throwable: Throwable) extends Result[Nothing]

  case class Fetch[+A](result: () => Result[A]) {

    def flatMap[B](f: A => Fetch[B]): Fetch[B] = {
      result() match {
        case Done(a)            => f(a)
        case Blocked(br, cont)  => Fetch { () => Blocked(br, cont flatMap f) }
        case Throw(throwable)   => Fetch.throwF(throwable)
      }
    }

    def map[B](f: A => B): Fetch[B] = Fetch { () =>
      result() match {
        case Done(a)          => Done(f(a))
        case Blocked(br,cont) => Blocked(br, cont map f)
        case _throw: Throw    => _throw
      }
    }

  }

  object Fetch {

    def unit[A](a: A): Fetch[A] = Fetch[A](() => Done(a))

    def throwF[A](throwable: Throwable): Fetch[A] = Fetch(() => Throw(throwable))

    def ap[A, B](fa: => Fetch[A])(ff: => Fetch[A => B]): Fetch[B] = Fetch { () =>
      val ra = fa.result()
      val rf = ff.result()
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

  }

  def dataFetch[A<:Return](request: Request[A]): Fetch[A] = {
    val box = Atom[FetchStatus[A]](NotFetched)
    val br = BlockedRequest(request, box)
    val cont = Fetch { () =>
      val FetchSuccess(a) = box()
      Done(a)
    }
    Fetch( () => Blocked(Seq(br),cont) )
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

  def runFetch[A](fetch: Fetch[A])(implicit executionContext: ExecutionContext, fetcher: Fetcher): Future[A] = {
    fetch.result() match {
      case Done(a) => Future.successful(a)
      case Blocked(br,cont) =>
        fetcher(br).flatMap { _ =>
          runFetch(cont)
        }
    }
  }

}