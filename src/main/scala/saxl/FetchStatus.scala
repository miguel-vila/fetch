package saxl

/**
 * Created by mglvl on 24/04/15.
 */
sealed trait FetchStatus[+T]
case object NotFetched extends FetchStatus[Nothing]
case class FetchSuccess[T](value: T) extends FetchStatus[T]
case class FetchFailure(t: Throwable) extends FetchStatus[Nothing]