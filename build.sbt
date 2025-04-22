// ===== Build-wide Settings =====
ThisBuild / organization := "ghoula.net"
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / scalaVersion := "3.6.4"
ThisBuild / homepage := Some(url("https://github.com/hakimjonas/valar"))
ThisBuild / licenses := Seq("MIT" -> url("http://opensource.org/licenses/MIT"))

ThisBuild / developers := List(
  Developer(
    id = "hakimjonas",
    name = "Hakim Jonas Ghoula",
    email = "hakim@ghoula.net",
    url = url("https://github.com/hakimjonas")
  )
)

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/hakimjonas/valar"),
    "scm:git@github.com:hakimjonas/valar.git"
  )
)

ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"

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

// SemanticDB (for Scalafix)
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbIncludeInJar := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

// ===== Publishing & Signing Settings =====

// ===== Project Definition =====
lazy val valar = (project in file("."))
  .enablePlugins(MdocPlugin)
  .settings(
    name := "valar",
    libraryDependencies ++= Seq(
      "org.specs2" %% "specs2-core" % "5.6.2" % Test,
      "org.specs2" %% "specs2-matcher-extra" % "5.6.2" % Test
    ),

    // Testing
    Test / fork := true,
    Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat,

    // Documentation
    mdocIn := file("docs-src"),
    mdocOut := file("docs"),
    mdocExtraArguments := Seq(
      "--out",
      (ThisBuild / baseDirectory).value.toString,
      "--include",
      "README.md"
    ),

    // Aliases
    addCommandAlias("prepare", "fix; fmt"),
    addCommandAlias("check", "+fixCheck; +fmtCheck"),
    addCommandAlias("fix", "scalafixAll"),
    addCommandAlias("fixCheck", "scalafixAll --check"),
    addCommandAlias("fmt", "+scalafmtSbt; +scalafmtAll"),
    addCommandAlias("fmtCheck", "+scalafmtSbtCheck; +scalafmtCheckAll")
  )
