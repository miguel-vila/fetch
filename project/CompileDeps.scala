import sbt._
import sbt.Keys._

object CompileDeps {
  val scalazVersion = "7.1.1"

  val scalazCore = "org.scalaz" %% "scalaz-core" % scalazVersion

  val kindProjector = "org.spire-math" % "kind-projector_2.11" % "0.5.2"
}