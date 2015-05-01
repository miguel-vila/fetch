package saxl

import org.scalatest.FunSuite
import scalaz.scalacheck.ScalazProperties.monad

/**
 * Created by mglvl on 29/04/15.
 */
class FetchTests extends FetchProperties {

  checkAll("monad laws", monad.laws[Fetch[TestRequest, ?]])

}
