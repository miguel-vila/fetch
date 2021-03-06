package fetch

import scalaz.DList

/**
 * Created by mglvl on 24/04/15.
 */
sealed trait Result[+R[_], +A]
case class Done[R[_], A](value: A) extends Result[R, A]
case class Blocked[R[_], A](blockedRequests: DList[BlockedRequest[R, _]], continuation: Fetch[R, A]) extends Result[R, A]
case class Throw(throwable: Throwable) extends Result[Nothing, Nothing]
