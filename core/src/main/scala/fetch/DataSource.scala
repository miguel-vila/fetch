package fetch

import scala.concurrent.{ ExecutionContext, Future }

/**
 * Created by mglvl on 25/04/15.
 */
abstract class DataSource[R[_]] {
  def name: String
  def fetch(requests: Seq[R[_]])(implicit executionContext: ExecutionContext): Future[Seq[_]]
}
