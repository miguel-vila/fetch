package saxl

import scala.collection.immutable.HashMap

/**
 * Created by mglvl on 24/04/15.
 */
case class DataCache(private val map: HashMap[Request[_],Atom[FetchStatus[_]]] = new HashMap[Request[_],Atom[FetchStatus[_]]]) {

  def lookup[A](r: Request[A]): Option[Atom[FetchStatus[A]]] = map.get(r).map(_.asInstanceOf[Atom[FetchStatus[A]]])

  def insert[A](r: Request[A], v: Atom[FetchStatus[A]]): DataCache = DataCache(map + (r -> v))

  override def toString() = {
    map.toStream.map { case (k,v) => s"$k -> $v" }.mkString("\n")
  }
}