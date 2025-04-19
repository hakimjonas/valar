addCommandAlias("prepare", "fix; fmt")
addCommandAlias("check", "+fixCheck; +fmtCheck")
addCommandAlias("fix", "scalafixAll")
addCommandAlias("fixCheck", "scalafixAll --check")
addCommandAlias("fmt", "+scalafmtSbt; +scalafmtAll")
addCommandAlias("fmtCheck", "+scalafmtSbtCheck; +scalafmtCheckAll")

ThisBuild / organization := "ghoula.net"
ThisBuild / version := "0.1.0"
ThisBuild / homepage := Some(url("https://github.com/hakimjonas/valar"))

scalaVersion := "3.6.4"

scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xfatal-warnings",
  "-Wunused:all",
  "-no-indent"
)

ThisBuild / javacOptions ++= Seq(
  "--release",
  "17"
)

ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbIncludeInJar := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

lazy val valar = (project in file("."))
  .settings(
    name := "valar",
    libraryDependencies ++= Seq(
      "org.specs2" %% "specs2-core" % "5.6.2" % Test,
      "org.specs2" %% "specs2-matcher-extra" % "5.6.2" % Test
    ),
    Test / fork := true,
    Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat
  )

ThisBuild / licenses := Seq("MIT" -> url("http://opensource.org/licenses/MIT"))
