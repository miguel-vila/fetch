package saxl

import scala.collection.immutable.HashMap
import scala.concurrent.{ ExecutionContext, Future }
import scalaz.{ Monad, Applicative, Traverse }

case class Fetch[R[_], +A](result: Atom[DataCache[R]] => Result[R, A]) {
  import Fetch._

  def flatMap[B](f: A => Fetch[R, B]): Fetch[R, B] = Fetch[R, B] { dc =>
    result(dc) match {
      case Done(a)           => f(a).result(dc)
      case Blocked(br, cont) => Blocked[R, B](br, cont flatMap f)
      case _throw: Throw     => _throw
    }
  }

  def map[B](f: A => B): Fetch[R, B] = flatMap { f andThen unit[R, B] }

  def ap[B](ff: => Fetch[R, A => B]): Fetch[R, B] = Fetch[R, B] { dc =>
    val ra = result(dc)
    val rf = ff.result(dc)
    (ra, rf) match {
      case (Done(a), Done(f))                   => Done(f(a))
      case (Blocked(br, ca), Done(f))           => Blocked(br, ca.ap(unit[R, A => B](f)))
      case (_throw: Throw, Done(f))             => _throw
      case (Done(a), Blocked(br, cf))           => Blocked[R, B](br, unit[R, A](a).ap(cf))
      case (Blocked(bra, ca), Blocked(brf, cf)) => Blocked(bra ++ brf /*@TODO <- ver eficiencia de esta operación, elegir estructura de datos adecuada*/ , ca.ap(cf))
      case (Throw(t), Blocked(brf, cf))         => Blocked(brf, throwF[R, A](t).ap(cf))
      case (_, _throw: Throw)                   => _throw
    }
  }

  private def run(dataSource: R[_] => DataSource[R])(implicit executionContext: ExecutionContext,
                                                     dataCache: Atom[DataCache[R]],
                                                     statsAtom: Atom[Stats]): Future[A] = {
    result(dataCache) match {
      case Done(a)  =>
        println("Fucking done!!!"); Future.successful(a)
      case Throw(t) => Future.failed(t)
      case Blocked(brs, cont) =>
        val t0 = System.currentTimeMillis()
        val groupedByDataSource = brs.groupBy(br => dataSource(br.request))
        //@TODO para recolectar estadísticas éste código queda con muchas cosas, tal vez haya alguna forma de separarlo?

        val traverse = Future.traverse(groupedByDataSource) {
          case (ds, brs) =>
            val dsT0 = System.currentTimeMillis()
            ds.fetch(brs).map { _ =>
              val dsTf = (System.currentTimeMillis() - dsT0).toInt
              val dsStats = DataSourceRoundStats(dataSourceFetches = brs.length, dataSourceTimeMillis = dsTf)
              (ds.name, dsStats)
            }
        }
        //@TODO es posible que esto lo haga el compilador? saber qué datasource llamar según el tipo del request?

        for {
          namesAndStats <- traverse
          roundTime = (System.currentTimeMillis() - t0).toInt
          hm = namesAndStats.foldLeft(HashMap[String, DataSourceRoundStats]()) { case (hm, (dsName, dsStats)) => hm.updated(dsName, dsStats) }
          roundStats = RoundStats(roundTime, hm)
          stats = statsAtom()
        } yield statsAtom.update(stats.copy(stats = roundStats :: stats.stats))

        traverse.flatMap { _ =>
          cont.run(dataSource)
        }
    }
  }

  def catchF[B >: A](handle: Throwable => Fetch[R, B]): Fetch[R, B] = Fetch[R, B] { dc =>
    val r = result(dc)
    r match {
      case Throw(e)          => handle(e).result(dc)
      case Blocked(br, cont) => cont.catchF(handle).result(dc)
      case _                 => r
    }
  }

}

object Fetch {

  def unit[R[_], A](a: A): Fetch[R, A] = Fetch[R, A](_ => Done(a))

  def throwF[R[_], A](throwable: Throwable): Fetch[R, A] = Fetch[R, A](_ => Throw(throwable))

  implicit def fetchInstance[R[_]] = new Monad[Fetch[R, ?]] with Applicative[Fetch[R, ?]] {
    override def bind[A, B](fa: Fetch[R, A])(f: A => Fetch[R, B]): Fetch[R, B] = fa.flatMap(f)

    override def point[A](a: => A): Fetch[R, A] = unit(a)

    override def ap[A, B](fa: => Fetch[R, A])(f: => Fetch[R, (A) => B]): Fetch[R, B] = fa.ap(f)
  }

  def traverse[R[_], A, G[_], B](value: G[A])(f: A => Fetch[R, B])(implicit G: Traverse[G]): Fetch[R, G[B]] = fetchInstance.traverse(value)(f)

  def map2[R[_], A, B, C](f1: Fetch[R, A], f2: Fetch[R, B])(f: (A, B) => C): Fetch[R, C] = f2.ap(f1.ap(unit(f.curried)))

  def dataFetch[R[_], A](request: R[A]): Fetch[R, A] = {
    def cont(box: Atom[FetchStatus[A]]) = Fetch[R, A] { _ =>
      val FetchSuccess(a) = box() // este pattern match se garantiza exitóso en [Fetch.run]
      Done(a)
    }
    Fetch[R, A] { dca =>
      val dc = dca()
      dc.lookup(request) match {
        case None =>
          val box: Atom[FetchStatus[A]] = Atom(NotFetched)
          dca.update(dc.insert(request, box))
          val br = BlockedRequest(request, box)
          Blocked(Seq(br), cont(box))
        case Some(box) =>
          box() match {
            case NotFetched          => Blocked(Seq.empty, cont(box))
            case FetchSuccess(value) => Done(value)
            case FetchFailure(t)     => Throw(t)
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
  def processBlockedRequest[R[_], A](br: BlockedRequest[R, A], futureImpl: => Future[A])(implicit executionContext: ExecutionContext): Future[Unit] = {

    val t0 = System.currentTimeMillis()
    futureImpl map { a =>
      println(s"Processed: ${br.request} -> ${System.currentTimeMillis() - t0}")
      br.fetchStatus.update(FetchSuccess(a))
    } recover {
      case t: Throwable =>
        br.fetchStatus.update(FetchFailure(t))
    }
  }

  def run[R[_], A](fetch: Fetch[R, A])(dataSource: R[_] => DataSource[R], dataCache: DataCache[R] = DataCache[R]())(implicit executionContext: ExecutionContext): Future[(A, DataCache[R], Stats)] = {
    implicit val dca = Atom(dataCache)
    implicit val stats = Atom(Stats())
    for {
      value <- fetch.run(dataSource)
    } yield (value, dca(), stats())
  }

}