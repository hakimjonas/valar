// Sonatype settings
import xerial.sbt.Sonatype.sonatypeCentralHost
enablePlugins(SbtPgp)

// Import for cross-platform builds
import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}
import scalanativecrossproject.ScalaNativeCrossPlugin.autoImport.*
import scala.scalanative.build.*

// mdoc documentation plugin
import _root_.mdoc.MdocPlugin

// ===== Build‑wide Settings =====
ThisBuild / organization := "net.ghoula"
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / scalaVersion := "3.7.1"

// Enable SemanticDB for all subprojects, required by Scalafix
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

ThisBuild / homepage := Some(url("https://github.com/hakimjonas/valar"))
ThisBuild / licenses := Seq("MIT" -> url("https://opensource.org/licenses/MIT"))
ThisBuild / developers := List(
  Developer("hakimjonas", "Hakim Jonas Ghoula", "hakim@ghoula.net", url("https://github.com/hakimjonas"))
)
ThisBuild / scmInfo := Some(
  ScmInfo(url("https://github.com/hakimjonas/valar"), "scm:git@github.com:hakimjonas/valar.git")
)

// Compiler options, including the one required by Scalafix's OrganizeImports
ThisBuild / scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xfatal-warnings",
  "-Wunused:all",
  "-Wunused:imports",
  "-no-indent"
)
ThisBuild / javacOptions ++= Seq("--release", "17")

// ===== Shared Settings =====
lazy val commonPublishSettings = Seq(
  sonatypeCredentialHost := sonatypeCentralHost,
  publishTo := sonatypePublishToBundle.value,
  usePgpKeyHex("9614A0CE1CE76975"),
  useGpgAgent := true
)

// ===== Project Definitions =====
lazy val root = (project in file("."))
  .aggregate(valarCoreJVM, valarCoreNative, valarMunitJVM, valarMunitNative)
  .settings(
    name := "valar-root",
    publish / skip := true
  )

// 1. The Core Library Module
lazy val valarCore = crossProject(JVMPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("valar-core"))
  .settings(
    name := "valar-core",
    libraryDependencies ++= Seq(
      "io.github.cquiroz" %%% "scala-java-time" % "2.6.0",
      "io.github.cquiroz" %%% "scala-java-time-tzdb" % "2.6.0",
      "org.scalameta" %%% "munit" % "1.1.1" % Test
    )
  )
  .settings(commonPublishSettings)
  .jvmSettings(
    mdocIn := file("docs-src"),
    mdocOut := file("."),
    addCommandAlias("prepare", "scalafixAll; scalafmtAll; scalafmtSbt"),
    addCommandAlias("check", "scalafixAll --check; scalafmtCheckAll; scalafmtSbtCheck")
  )
  .jvmConfigure(_.enablePlugins(MdocPlugin))
  .nativeSettings(
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
    libraryDependencies += "org.scalameta" %%% "munit" % "1.1.1",
    publish / skip := false
  )
  .settings(commonPublishSettings)
  .nativeSettings(
    testFrameworks += new TestFramework("munit.Framework")
  )

// Convenience aliases
lazy val valarCoreJVM = valarCore.jvm
lazy val valarCoreNative = valarCore.native
lazy val valarMunitJVM = valarMunit.jvm
lazy val valarMunitNative = valarMunit.native
