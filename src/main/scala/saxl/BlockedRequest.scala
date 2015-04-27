package saxl

/**
 * Created by mglvl on 24/04/15.
 */
case class BlockedRequest[+R[_]:Request,A](val request: R[A], val fetchStatus: Atom[FetchStatus[A]])