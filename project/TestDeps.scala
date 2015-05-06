import sbt._
import sbt.Keys._

object TestDeps {

  val scalatest = "org.scalatest" %% "scalatest" % "2.2.1"

  val scalazScalacheck = "org.scalaz" %% "scalaz-scalacheck-binding" % CompileDeps.scalazVersion

  val scalacheck = "org.scalacheck" %% "scalacheck" % "1.12.2"

  val mockito = "org.mockito"         %   "mockito-core"                  % "1.10.8"
}