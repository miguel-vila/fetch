name := "haxl-poc"

version := "0.0"

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
  "org.scalaz"      %%  "scalaz-core"               % "7.1.1",
  "org.scalatest"   %   "scalatest_2.11"            % "2.2.1"   % "test",
  "org.scalaz"      %%  "scalaz-scalacheck-binding" % "7.1.1"   % "test",
  "org.scalacheck"  %%  "scalacheck"                % "1.12.2"  % "test")

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",//para poder ver feature warnings al compilar
  "-language:postfixOps", //para cosas como '5 seconds'
  "-language:implicitConversions",
  "-language:existentials",
  "-language:higherKinds",
  "-unchecked",
  "-Xfatal-warnings",
  "-Xlint",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",        // N.B. doesn't work well with the ??? hole
  //"-Ywarn-numeric-widen",
  //"-Ywarn-value-discard",
  "-Xfuture"
)

resolvers += "bintray/non" at "http://dl.bintray.com/non/maven"

// for scala 2.11
addCompilerPlugin("org.spire-math" % "kind-projector_2.11" % "0.5.2")