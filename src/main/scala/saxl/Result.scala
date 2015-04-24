package saxl

/**
 * Created by mglvl on 24/04/15.
 */
sealed trait Result[+A]
case class Done[A](value: A) extends Result[A]
case class Blocked[A](blockedRequests: Seq[BlockedRequest[_]], continuation: Fetch[A]) extends Result[A]
case class Throw(throwable: Throwable) extends Result[Nothing]
