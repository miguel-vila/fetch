package saxl

import org.scalacheck.{ Arbitrary, Properties }
import scalaz.Equal
import scalaz.scalacheck.ScalazProperties.{ monad, functor, applicative, bind }
import scalaz.scalacheck.ScalazProperties

/**
 * Created by mglvl on 27/04/15.
 */
object FetchProperties {

  def properties(implicit am: Arbitrary[Fetch[TestRequest, Int]],
                 af: Arbitrary[Int => Fetch[TestRequest, Int]], ag: Arbitrary[Fetch[TestRequest, Int => Int]], e: Equal[Fetch[TestRequest, Int]]) = new Properties("Fetch properties") {
    include(functor.laws[Fetch[TestRequest, ?]])
    //property("apply.composition") = ScalazProperties.apply.composition[Fetch[TestRequest, ?], Int, Int, Int]
    property("applicative.identity") = applicative.identity[Fetch[TestRequest, ?], Int]
    property("applicative.homomorphism") = applicative.homomorphism[Fetch[TestRequest, ?], Int, Int]
    property("applicative.interchange") = applicative.interchange[Fetch[TestRequest, ?], Int, Int]
    //property("applicative.map consistent with ap") = applicative.mapApConsistency[Fetch[TestRequest, ?], Int, Int]

    property("bind.associativity") = bind.associativity[Fetch[TestRequest, ?], Int, Int, Int]

    property("monad.right identity") = monad.rightIdentity[Fetch[TestRequest, ?], Int]
    property("monad.left identity") = monad.leftIdentity[Fetch[TestRequest, ?], Int, Int]
  }.properties

}
