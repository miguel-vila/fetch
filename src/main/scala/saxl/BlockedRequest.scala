package saxl

/**
 * Created by mglvl on 24/04/15.
 */
case class BlockedRequest[A](val request: Request[A], val fetchStatus: Atom[FetchStatus[A]])