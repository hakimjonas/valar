import sbt.*
import sbt.Keys.*

// Sonatype settings
import xerial.sbt.Sonatype.sonatypeCentralHost
enablePlugins(SbtPgp)

// mdoc documentation plugin
import _root_.mdoc.MdocPlugin
// ===== Build‑wide Settings =====
ThisBuild / organization := "net.ghoula"
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / scalaVersion := "3.6.4"
ThisBuild / homepage := Some(url("https://github.com/hakimjonas/valar"))
ThisBuild / licenses := Seq("MIT" -> url("http://opensource.org/licenses/MIT"))
ThisBuild / developers := List(
  Developer("hakimjonas", "Hakim Jonas Ghoula", "hakim@ghoula.net", url("https://github.com/hakimjonas"))
)
ThisBuild / scmInfo := Some(
  ScmInfo(url("https://github.com/hakimjonas/valar"), "scm:git@github.com:hakimjonas/valar.git")
)
ThisBuild / sonatypeCredentialHost := sonatypeCentralHost
ThisBuild / publishTo := sonatypePublishToBundle.value

// Compiler options
ThisBuild / scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xfatal-warnings",
  "-Wunused:all",
  "-no-indent"
)
ThisBuild / javacOptions ++= Seq("--release", "17")

// SemanticDB for Scalafix
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbIncludeInJar := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

// ===== Project Definition =====
lazy val valar = (project in file("."))
  .enablePlugins(MdocPlugin, SbtPgp)
  .settings(
    name := "valar",
    libraryDependencies ++= Seq(
      "org.specs2" %% "specs2-core" % "5.6.2" % Test,
      "org.specs2" %% "specs2-matcher-extra" % "5.6.2" % Test
    ),
    // sbt-pgp signing configuration
    usePgpKeyHex("9614A0CE1CE76975"),
    useGpgAgent := true,

    // Testing
    Test / fork := true,
    Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat,

    // Documentation (mdoc)
    mdocIn := file("docs-src"),
    mdocOut := file("docs"),
    mdocExtraArguments := Seq(
      "--out",
      (ThisBuild / baseDirectory).value.toString,
      "--include",
      "README.md"
    ),

    // Aliases for formatting & linting
    addCommandAlias("prepare", "fix; fmt"),
    addCommandAlias("check", "+fixCheck; +fmtCheck"),
    addCommandAlias("fix", "scalafixAll"),
    addCommandAlias("fixCheck", "scalafixAll --check"),
    addCommandAlias("fmt", "+scalafmtSbt; +scalafmtAll"),
    addCommandAlias("fmtCheck", "+scalafmtSbtCheck; +scalafmtCheckAll")
  )
