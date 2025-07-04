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

// ===== Project Definitions =====

lazy val root = (project in file("."))
  .aggregate(valarCoreJVM, valarCoreNative, valarMunitJVM, valarMunitNative)
  .settings(
    name := "valar-root",
    publish / skip := true,
    addCommandAlias("prepare", "valarCoreJVM/scalafixAll; valarCoreJVM/scalafmtAll; scalafmtSbt"),
    addCommandAlias("check", "valarCoreJVM/scalafixAll --check; valarCoreJVM/scalafmtCheckAll; scalafmtSbtCheck")
  )

// 1. The Core Library Module
lazy val valarCore = crossProject(JVMPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("valar-core"))
  .settings(
    name := "valar-core",
    // Add MUnit as a Test dependency for internal testing
    libraryDependencies += "org.scalameta" %%% "munit" % "1.1.1" % Test,
    usePgpKeyHex("9614A0CE1CE76975"),
    useGpgAgent := true
  )
  .jvmSettings(
    semanticdbEnabled := true,
    semanticdbIncludeInJar := true,
    semanticdbVersion := scalafixSemanticdb.revision
  )
  .jvmConfigure(_.enablePlugins(MdocPlugin, SbtPgp))
  .jvmSettings(
    mdocIn := file("docs-src"),
    mdocOut := file("docs")
  )
  .nativeSettings(
    // Configure MUnit as the test framework for the core module
    testFrameworks += new TestFramework("munit.Framework"),
    nativeConfig ~= { c =>
      c.withLTO(LTO.thin).withMode(Mode.releaseFast).withGC(GC.immix)
    }
  )

// 2. The MUnit Testing Extension Module
lazy val valarMunit = crossProject(JVMPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("valar-munit"))
  .dependsOn(valarCore)
  .settings(
    name := "valar-munit",
    // MUnit is a Compile dependency for ValarSuite itself
    libraryDependencies += "org.scalameta" %%% "munit" % "1.1.1",
    // This artifact IS intended for publishing
    publish / skip := false
  )
  .jvmConfigure(_.enablePlugins(SbtPgp))
  .nativeSettings(
    // It can also have its own tests (to test ValarSuite)
    testFrameworks += new TestFramework("munit.Framework")
  )

// Convenience aliases
lazy val valarCoreJVM = valarCore.jvm
lazy val valarCoreNative = valarCore.native
lazy val valarMunitJVM = valarMunit.jvm
lazy val valarMunitNative = valarMunit.native
