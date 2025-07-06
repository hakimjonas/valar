// ===== Imports =====
import xerial.sbt.Sonatype.autoImport.*
import xerial.sbt.Sonatype.{sonatypeCentralHost, sonatypeSettings}
enablePlugins(SbtPgp)

import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}
import scalanativecrossproject.ScalaNativeCrossPlugin.autoImport.*

import scala.scalanative.build.*

// mdoc documentation plugin
import _root_.mdoc.MdocPlugin

// ===== Build‑wide Settings =====
ThisBuild / organization := "net.ghoula"
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / scalaVersion := "3.7.1"
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

// ===== Publishing Settings =====
ThisBuild / sonatypeRepository := sonatypeCentralHost
ThisBuild / sonatypeProfileName := "net.ghoula"
ThisBuild / sonatypeCredentialHost := sonatypeCentralHost
ThisBuild / licenses := Seq("MIT" -> url("https://opensource.org/licenses/MIT"))
ThisBuild / homepage := Some(url("https://github.com/hakimjonas/valar"))
ThisBuild / developers := List(
  Developer("hakimjonas", "Hakim Jonas Ghoula", "hakim@ghoula.net", url("https://github.com/hakimjonas"))
)
ThisBuild / scmInfo := Some(
  ScmInfo(url("https://github.com/hakimjonas/valar"), "scm:git@github.com:hakimjonas/valar.git")
)

// ===== Compiler Settings =====
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
lazy val pgpSettings = Seq(
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

lazy val valarCore = crossProject(JVMPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("valar-core"))
  .settings(
    // All settings are now in a single block
    name := "valar-core",
    libraryDependencies ++= Seq(
      "io.github.cquiroz" %%% "scala-java-time" % "2.6.0",
      "io.github.cquiroz" %%% "scala-java-time-tzdb" % "2.6.0",
      "org.scalameta" %%% "munit" % "1.1.1" % Test
    )
  )
  .settings(sonatypeSettings) // Apply Sonatype settings
  .settings(pgpSettings) // Apply PGP settings
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

lazy val valarMunit = crossProject(JVMPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("valar-munit"))
  .dependsOn(valarCore)
  .settings(
    // All settings are now in a single block
    name := "valar-munit",
    libraryDependencies += "org.scalameta" %%% "munit" % "1.1.1"
  )
  .settings(sonatypeSettings) // Apply Sonatype settings
  .settings(pgpSettings) // Apply PGP settings
  .nativeSettings(
    testFrameworks += new TestFramework("munit.Framework")
  )

// ===== Convenience Aliases =====
lazy val valarCoreJVM = valarCore.jvm
lazy val valarCoreNative = valarCore.native
lazy val valarMunitJVM = valarMunit.jvm
lazy val valarMunitNative = valarMunit.native
