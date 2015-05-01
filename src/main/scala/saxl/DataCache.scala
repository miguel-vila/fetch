package saxl

import scala.collection.immutable.HashMap

/**
 * Created by mglvl on 24/04/15.
 */
case class DataCache[R[_]](private val map: HashMap[Any, Atom[FetchStatus[_]]] = new HashMap[Any, Atom[FetchStatus[_]]]) {

  def lookup[A](r: R[A]): Option[Atom[FetchStatus[A]]] = map.get(r).map(_.asInstanceOf[Atom[FetchStatus[A]]])

  def insert[A](r: R[A], v: Atom[FetchStatus[A]]): DataCache[R] = DataCache[R](map + (r -> v))

  override def toString() = {
    map.toStream.map { case (k, v) => s"$k -> $v" }.mkString("\n")
  }
}