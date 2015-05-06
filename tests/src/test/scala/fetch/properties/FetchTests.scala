package fetch.properties

import org.scalatest.PropSpec
import org.scalatest.prop.Checkers

/**
 * Created by mglvl on 29/04/15.
 */
class FetchTests extends PropSpec with Checkers {

  for {
    (name, prop) <- FetchProperties.properties
  } yield property(name) {
    check(prop)
  }

}
