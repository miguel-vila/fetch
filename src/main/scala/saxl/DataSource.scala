package saxl

import scala.concurrent.{ExecutionContext, Future}

/**
 * Created by mglvl on 25/04/15.
 */
abstract class DataSource[R[_]] {
  def fetch(blockedRequests: Seq[BlockedRequest[R,_]])(implicit executionContext: ExecutionContext): Future[Unit]
}
