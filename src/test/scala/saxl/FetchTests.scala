package saxl

import org.scalacheck.{Arbitrary, Properties, Prop}
import org.scalacheck.Prop.forAll
import scalaz.Equal
import scalaz.scalacheck.ScalazProperties.monad
import saxl.Fetch.fetchInstance

/**
 * Created by seven4n on 27/04/15.
 */
class FetchTests extends Properties("") {

  def checkAll(name: String, props: Properties) = {
    for ((name2, prop) <- props.properties) yield {
      property(name + ":" + name2) = prop
    }
  }

  import Fetch.fetchInstance
  trait TestRequest[T]

  implicit def fetchEqual[R[_], A:Arbitrary]: Equal[Fetch[R,A]] = ???

  implicit def fetchArbitrary[R[_], A:Arbitrary]: Arbitrary[Fetch[R,A]] = ???

  checkAll("",monad.laws[Fetch[TestRequest,?]])

}
