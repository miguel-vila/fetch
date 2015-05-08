package fetch

import scala.collection.immutable.HashMap
import scala.concurrent.{ ExecutionContext, Future }
import scalaz.{ DList, Monad, Applicative, Traverse }

case class Fetch[R[_], +A](result: Atom[DataCache[R]] => Result[R, A]) {
  import Fetch._

  def flatMap[B](f: A => Fetch[R, B]): Fetch[R, B] = Fetch { dc =>
    result(dc) match {
      case Done(a)           => f(a).result(dc)
      case Blocked(br, cont) => Blocked(br, cont flatMap f)
      case _throw: Throw     => _throw
    }
  }

  def map[B](f: A => B): Fetch[R, B] = flatMap { f andThen unit }

  def ap[B](ff: => Fetch[R, A => B]): Fetch[R, B] = Fetch { dc =>
    val ra = result(dc)
    val rf = ff.result(dc)
    (ra, rf) match {
      case (Done(a), Done(f))                   => Done(f(a))
      case (Blocked(br, ca), Done(f))           => Blocked(br, ca.ap(unit(f)))
      case (_throw: Throw, Done(f))             => _throw
      case (Done(a), Blocked(br, cf))           => Blocked(br, unit(a).ap(cf))
      case (Blocked(bra, ca), Blocked(brf, cf)) => Blocked(bra ++ brf, ca.ap(cf))
      case (Throw(t), Blocked(brf, cf))         => Blocked(brf, throwF(t).ap(cf))
      case (_, _throw: Throw)                   => _throw
    }
  }

  private def run(dataSource: R[_] => DataSource[R], dataCache: Atom[DataCache[R]])(implicit executionContext: ExecutionContext): (Future[A], Future[Stats]) = {
    var statsFs = List.empty[Future[RoundStats]]

    def loop(fetch: Fetch[R, A]): Future[A] = {
      fetch.result(dataCache) match {
        case Done(a)  => Future.successful(a)
        case Throw(t) => Future.failed(t)
        case Blocked(brs, cont) =>
          val t0 = System.currentTimeMillis()

          // @TODO ver que la siguiente conversión a lista no tenga consecuencias de performance v.s. hacer el groupBy a mano como se muestra abajo
          val groupedByDataSource = brs.toList.groupBy(br => dataSource(br.request))

          /*
          val groupedByDataSource = brs.foldr(Map[DataSource[R], List[BlockedRequest[R, _]]]()) {
            (br, map) =>
              val req = br.request
              val ds = dataSource(req)
              val v = map.get(ds)
              v match {
                case None      => map.updated(ds, List(br))
                case Some(brs) => map.updated(ds, br :: brs)
              }
          }
          */
          //@TODO para recolectar estadísticas éste código queda con muchas cosas, tal vez haya alguna forma de separarlo?

          //@TODO si alguno de los siguientes falla entonces el futuro falla. Como almacenar el [[FetchFailure]] en ese caso?
          val traverse = Future.traverse(groupedByDataSource) {
            case (ds, brs) =>
              val dsT0 = System.currentTimeMillis()
              val requests = brs.map(_.request)
              ds.fetch(requests).map { results =>
                val dsTf = (System.currentTimeMillis() - dsT0).toInt
                val dsStats = DataSourceRoundStats(dataSourceFetches = brs.length, dataSourceTimeMillis = dsTf)
                assert(requests.length == results.length)
                (brs zip results) foreach {
                  case (br, res) =>
                    br.fetchStatus.update(FetchSuccess(res))
                }

                (ds.name, dsStats)
              }
          }
          //@TODO es posible que esto lo haga el compilador? saber qué datasource llamar según el tipo del request?

          val statF = for {
            namesAndStats <- traverse
            roundTime = (System.currentTimeMillis() - t0).toInt
            hm = namesAndStats.foldLeft(HashMap[String, DataSourceRoundStats]()) { case (hm, (dsName, dsStats)) => hm.updated(dsName, dsStats) }
          } yield RoundStats(roundTime, hm)

          statsFs = statF :: statsFs

          traverse.flatMap { _ =>
            loop(cont)
          }
      }
    }
    val result = loop(this)
    val statsF = for {
      value <- result
      stats <- Future.sequence(statsFs)
    } yield Stats(stats)
    (result, statsF)
  }

  def catchF[B >: A](handle: Throwable => Fetch[R, B]): Fetch[R, B] = Fetch { dc =>
    val r = result(dc)
    r match {
      case Throw(e)          => handle(e).result(dc)
      case Blocked(br, cont) => cont.catchF(handle).result(dc)
      case _                 => r
    }
  }

}

object Fetch {

  def unit[R[_], A](a: A): Fetch[R, A] = Fetch(_ => Done(a))

  def throwF[R[_], A](throwable: Throwable): Fetch[R, A] = Fetch(_ => Throw(throwable))

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
    Fetch { dca =>
      val dc = dca()
      dc.lookup(request) match {
        case None =>
          val box: Atom[FetchStatus[A]] = Atom(NotFetched)
          dca.update(dc.insert(request, box))
          val br = BlockedRequest(request, box)
          Blocked(DList[BlockedRequest[R, _]](br), cont(box))
        case Some(box) =>
          box() match {
            case NotFetched          => Blocked(DList[BlockedRequest[R, _]](), cont(box))
            case FetchSuccess(value) => Done(value)
            case FetchFailure(t)     => Throw(t)
          }
      }
    }
  }

  def run[R[_], A](fetch: Fetch[R, A])(dataSource: R[_] => DataSource[R], dataCache: DataCache[R] = DataCache[R]())(implicit executionContext: ExecutionContext): (Future[(A, DataCache[R])], Future[Stats]) = {
    val dca = Atom(dataCache)
    val (result, statsF) = fetch.run(dataSource, dca)
    val f = for {
      value <- result
    } yield (value, dca())
    (f, statsF)
  }

}