import sbt.Keys._

import scalariform.formatter.preferences._

name := "fetch"

version := "0.0.0"

scalaVersion := "2.11.6"

val commonSettings = scalariformSettings ++ Seq(
  version := "0.0.0",
    scalaVersion := "2.11.6",
    resolvers += "bintray/non" at "http://dl.bintray.com/non/maven",
    libraryDependencies ++= Seq(
      CompileDeps.scalazCore,
      TestDeps.scalatest         % "test",
      TestDeps.scalazScalacheck  % "test",
      TestDeps.scalacheck        % "test"
    ),
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
      "-Ywarn-numeric-widen",
      "-Ywarn-value-discard",
      "-Xfuture"
    ),
    addCompilerPlugin(CompileDeps.kindProjector),
    ScalariformKeys.preferences := ScalariformKeys.preferences.value
    .setPreference(AlignSingleLineCaseStatements, true)
    .setPreference(AlignParameters, true)    
)

lazy val core = Project(
  id = "fetch-core",
  base = file("./core")
).settings(
    commonSettings: _*
  )

lazy val examples = Project(
  id = "fetch-examples",
  base = file("./examples")
).settings(
    commonSettings: _*
  ).dependsOn(core)

lazy val root = Project(
  id = "fetch",
  base = file(".")
).aggregate(core, examples)