// build.sbt

// ===== Build-wide Settings =====

ThisBuild / organization := "ghoula.net"

ThisBuild / scalaVersion := "3.6.4"

ThisBuild / homepage := Some(url("https://github.com/hakimjonas/valar"))

ThisBuild / licenses := Seq("MIT" -> url("http://opensource.org/licenses/MIT"))

ThisBuild / scalacOptions ++= Seq(
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

// ===== Publishing Settings =====

// This is the Sonatype repository for publishing artifacts
ThisBuild / publishTo := sonatypePublishToBundle.value

// Developer information (Required by Sonatype/Maven Central)
ThisBuild / developers := List(
  Developer(
    id = "hakimjonas",
    name = "Hakim Jonas Ghoula",
    email = "hakim@walkthisway.dk",
    url = url("https://github.com/hakimjonas")
  )
)

// Project information (Required by Sonatype/Maven Central)
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/hakimjonas/valar"),
    "scm:git@github.com:hakimjonas/valar.git"
  )
)

// License information (Required by Sonatype/Maven Central)
ThisBuild / licenses := Seq(
  "MIT" -> url("http://opensource.org/licenses/MIT")
)

ThisBuild / publishArtifact := true
ThisBuild / Compile / packageDoc / publishArtifact := true
ThisBuild / Compile / packageSrc / publishArtifact := true

// ===== Project Definition =====

lazy val valar = (project in file("."))
  .enablePlugins(MdocPlugin)
  .settings(
    name := "valar",
    libraryDependencies ++= Seq(
      "org.specs2" %% "specs2-core" % "5.6.2" % Test,
      "org.specs2" %% "specs2-matcher-extra" % "5.6.2" % Test
    ),
    Test / fork := true,
    Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat,
    mdocIn := file("docs-src"),
    mdocOut := file("docs"),
    mdocExtraArguments := Seq(
      "--out",
      (ThisBuild / baseDirectory).value.toString,
      "--include",
      "README.md"
    )
  )

addCommandAlias("prepare", "fix; fmt")
addCommandAlias("check", "+fixCheck; +fmtCheck")
addCommandAlias("fix", "scalafixAll")
addCommandAlias("fixCheck", "scalafixAll --check")
addCommandAlias("fmt", "+scalafmtSbt; +scalafmtAll")
addCommandAlias("fmtCheck", "+scalafmtSbtCheck; +scalafmtCheckAll")
