val v = new {
  val app = "1.0"
  val scala = "2.13.2"
  val scalaJSDom = "1.0.0"
  val scalaJSReact = "1.7.0"
  val scalaCss = "0.6.1"
  val reactJS = "16.13.1"
}

name := "scalajs-react-template"
version := v.app
scalaVersion := v.scala

libraryDependencies ++= Seq(
  "org.scala-js" %%% "scalajs-dom" % v.scalaJSDom,
  "com.github.japgolly.scalajs-react" %%% "core" % v.scalaJSReact,
  "com.github.japgolly.scalajs-react" %%% "extra" % v.scalaJSReact,
  "com.github.japgolly.scalacss" %%% "core" % v.scalaCss,
  "com.github.japgolly.scalacss" %%% "ext-react" % v.scalaCss
)

val circeVersion = "0.13.0"

libraryDependencies ++= Seq(
  "io.circe" %%% "circe-core",
  "io.circe" %%% "circe-generic",
  "io.circe" %%% "circe-parser",
  "io.circe" %%% "circe-generic-extras"
).map(_ % circeVersion)

val monocleVersion = "2.0.4" // depends on cats 2.x

libraryDependencies ++= Seq(
  "com.github.julien-truffaut" %%% "monocle-core"  % monocleVersion,
  "com.github.julien-truffaut" %%% "monocle-macro" % monocleVersion,
  "com.github.julien-truffaut" %%% "monocle-law"   % monocleVersion % "test"
)

enablePlugins(ScalaJSPlugin)
enablePlugins(ScalaJSBundlerPlugin)

(scalaJSUseMainModuleInitializer in Compile) := true

// creates single js resource file for easy integration in html page
skip in packageJSDependencies := false

scalacOptions += "-feature"

webpackBundlingMode in fastOptJS := BundlingMode.LibraryOnly()
webpackBundlingMode in fullOptJS := BundlingMode.Application

npmDependencies in Compile ++= Seq(
  "react" -> v.reactJS,
  "react-dom" -> v.reactJS,
  "react-flip-move" -> "3.0.4"
)

// fixes unresolved deps issue: https://github.com/webjars/webjars/issues/1789
dependencyOverrides ++= Seq(
  "org.webjars.npm" % "js-tokens" % "4.0.0",
  "org.webjars.npm" % "scheduler" % "0.14.0"
)

//enablePlugins(WorkbenchPlugin)
// Live Reloading: WorkbenchPlugin must NOT be enabled at the same time
//enablePlugins(WorkbenchSplicePlugin)
//workbenchCompression := true
//workbenchStartMode := WorkbenchStartModes.OnCompile
