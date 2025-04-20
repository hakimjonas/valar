// build.sbt

// ===== Build-wide Settings =====

ThisBuild / organization := "ghoula.net"

ThisBuild / scalaVersion := "3.6.4" // Define Scala version for ThisBuild

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

// Define publishing destination using sbt-sonatype helper
// This automatically points to the correct Sonatype staging repo for releases
// or the snapshots repo if the version ends in "-SNAPSHOT"
ThisBuild / publishTo := sonatypePublishToBundle.value

// Developer information (Required by Sonatype/Maven Central)
ThisBuild / developers := List(
  Developer(
    id = "hakimjonas", // Your GitHub ID or unique identifier
    name = "Hakim Jonas Ghoula", // Your Name
    email = "hakim@walkthisway.dk", // Your Email
    url = url("https://github.com/hakimjonas") // Optional URL
  )
)

// SCM (Source Control Management) information (Required by Sonatype/Maven Central)
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/hakimjonas/valar"),
    "scm:git@github.com:hakimjonas/valar.git"
  )
)

// Ensure sources and Javadoc JARs are published (Required by Sonatype/Maven Central)
// These settings configure the packageSrc and packageDoc tasks to be included
// when publishing.
ThisBuild / publishArtifact := true
ThisBuild / Compile / packageDoc / publishArtifact := true // Enable publishing of Javadoc JAR
ThisBuild / Compile / packageSrc / publishArtifact := true // Enable publishing of sources JAR

// ===== Project Definition =====

lazy val valar = (project in file("."))
  .settings(
    name := "valar", // Project name
    // libraryDependencies only contains test dependencies, which is fine
    libraryDependencies ++= Seq(
      "org.specs2" %% "specs2-core" % "5.6.2" % Test,
      "org.specs2" %% "specs2-matcher-extra" % "5.6.2" % Test
    ),
    Test / fork := true,
    Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat
    // scalaVersion is inherited from ThisBuild
  )

// ===== Command Aliases =====

addCommandAlias("prepare", "fix; fmt")
addCommandAlias("check", "+fixCheck; +fmtCheck")
addCommandAlias("fix", "scalafixAll")
addCommandAlias("fixCheck", "scalafixAll --check")
addCommandAlias("fmt", "+scalafmtSbt; +scalafmtAll")
addCommandAlias("fmtCheck", "+scalafmtSbtCheck; +scalafmtCheckAll")
