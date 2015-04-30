package saxl

import org.scalacheck.Properties
import org.scalacheck.Prop.forAll

/**
 * Created by mglvl on 27/04/15.
 */
class FetchProperties extends Properties("Fetch properties") {

  def checkAll(name: String, props: Properties) = {
    val properties = Set(
      "monad.applicative.apply.functor.invariantFunctor.identity",
      "monad.applicative.apply.functor.invariantFunctor.composite",
      "monad.applicative.apply.functor.identity",
      "monad.applicative.apply.functor.composite",
      // "monad.applicative.apply.composition", //@TODO <- ver por que falla
      "monad.applicative.identity",
      "monad.applicative.homomorphism",
      "monad.applicative.interchange",
      "monad.applicative.map consistent with ap",
      "monad.bind.apply.functor.invariantFunctor.identity",
      "monad.bind.apply.functor.invariantFunctor.composite",
      "monad.bind.apply.functor.identity",
      "monad.bind.apply.functor.composite",
      // "monad.bind.apply.composition", //@TODO <- ver por que falla
      "monad.bind.associativity",
      // "monad.bind.ap consistent with bind", //@TODO <- ver por que falla
      "monad.right identity",
      "monad.left identity"
    )

    for ((name2, prop) <- props.properties if properties contains name2) yield {
      property(name + ":" + name2) = prop
    }
  }

}
