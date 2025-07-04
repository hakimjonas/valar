// Sonatype settings
import xerial.sbt.Sonatype.sonatypeCentralHost
enablePlugins(SbtPgp)

// Import for cross-platform builds
import scala.scalanative.build._
import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}
import scalanativecrossproject.ScalaNativeCrossPlugin.autoImport._

// mdoc documentation plugin
import _root_.mdoc.MdocPlugin

// ===== Build‑wide Settings =====
ThisBuild / organization := "net.ghoula"
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / scalaVersion := "3.7.1"
ThisBuild / homepage := Some(url("https://github.com/hakimjonas/valar"))
ThisBuild / licenses := Seq("MIT" -> url("https://opensource.org/licenses/MIT"))
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

// ===== Project Definition =====
// Cross-platform project definition (JVM and Native)
lazy val valar = crossProject(JVMPlatform, NativePlatform)
  .crossType(CrossType.Pure) // Same sources for both platforms
  .in(file("."))
  .settings(
    name := "valar",

    // Common settings for both platforms
    // sbt-pgp signing configuration
    usePgpKeyHex("9614A0CE1CE76975"),
    useGpgAgent := true,

    // Aliases for formatting & linting (only apply to JVM)
    addCommandAlias("prepare", "valarJVM/scalafixAll; valarJVM/scalafmtAll; scalafmtSbt"),
    addCommandAlias("check", "valarJVM/scalafixAll --check; valarJVM/scalafmtCheckAll; scalafmtSbtCheck"),
    addCommandAlias("fix", "valarJVM/scalafixAll"),
    addCommandAlias("fixCheck", "valarJVM/scalafixAll --check"),
    addCommandAlias("fmt", "valarJVM/scalafmtAll; scalafmtSbt"),
    addCommandAlias("fmtCheck", "valarJVM/scalafmtCheckAll; scalafmtSbtCheck")
  )
  .jvmSettings(
    // JVM-specific settings - keep specs2
    libraryDependencies ++= Seq(
      "org.specs2" %% "specs2-core" % "5.6.4" % Test,
      "org.specs2" %% "specs2-matcher-extra" % "5.6.4" % Test
    ),

    // SemanticDB for Scalafix - ONLY for JVM platform
    semanticdbEnabled := true,
    semanticdbIncludeInJar := true,
    semanticdbVersion := scalafixSemanticdb.revision,

    // JVM testing settings
    Test / fork := true,
    Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat
  )
  .jvmConfigure(_.enablePlugins(MdocPlugin, SbtPgp))
  .jvmSettings(
    // Documentation (mdoc) - only for JVM
    mdocIn := file("docs-src"),
    mdocOut := file("docs"),
    mdocExtraArguments := Seq(
      "--out",
      (ThisBuild / baseDirectory).value.toString,
      "--include",
      "README.md"
    )
  )
  .nativeSettings(
    // Native-specific settings - use uTest
    libraryDependencies ++= Seq(
      "com.lihaoyi" %%% "utest" % "0.8.4" % Test
    ),
    testFrameworks += new TestFramework("utest.runner.Framework"),
    nativeConfig ~= { c =>
      c.withLTO(scala.scalanative.build.LTO.thin)
        .withMode(scala.scalanative.build.Mode.releaseFast)
        .withGC(scala.scalanative.build.GC.immix)
    },

    // Native testing settings
    Test / fork := false
  )

// Convenience aliases for platform-specific commands
lazy val valarJVM = valar.jvm
lazy val valarNative = valar.native
