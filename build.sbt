// Command Aliases (Keep these useful helpers)
addCommandAlias("prepare", "fix; fmt")
addCommandAlias("check", "+fixCheck; +fmtCheck")
addCommandAlias("fix", "scalafixAll")
addCommandAlias("fixCheck", "scalafixAll --check")
addCommandAlias("fmt", "+scalafmtSbt; +scalafmtAll")
addCommandAlias("fmtCheck", "+scalafmtSbtCheck; +scalafmtCheckAll")

ThisBuild / organization := "ghoula.net"
ThisBuild / version := "0.1.0"
ThisBuild / homepage := Some(url("https://github.com/hakimjonas/valar"))

// --- Scala Settings ---
scalaVersion := "3.6.4"

scalacOptions ++= Seq(
  "-deprecation", // Emit warnings for deprecated APIs
  "-feature", // Warn about features that should be explicitly imported
  "-unchecked", // Enable additional warnings where generated code depends on assumptions
  "-Xfatal-warnings", // Fail the compilation if there are any warnings
  "-Wunused:all", // Warn for any unused imports, variables, etc.
  "-no-indent"
  // "-experimental"  // Keep only if you use experimental features NOT part of standard 3.6.4
)

// --- Java Settings ---
ThisBuild / javacOptions ++= Seq(
  "--release",
  "17" // Target Java 17
)

// --- Tooling Settings ---
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbIncludeInJar := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

// --- Project Definition ---
lazy val valar = (project in file(".")) // Define the main project
  .settings(
    name := "valar", // <<< CHANGE Project name

    // --- Dependencies ---
    libraryDependencies ++= Seq(
      // Test Dependencies
      "org.specs2" %% "specs2-core" % "5.6.2" % Test,
      "org.specs2" %% "specs2-matcher-extra" % "5.6.2" % Test
      // Add other dependencies ONLY if the validation CORE code needs them
      // (Currently, it seems self-contained with just the Scala standard library)
    ),

    // --- Test Configuration ---
    Test / fork := true, // Often good practice for tests
    Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat // Keep if needed
  )

ThisBuild / licenses := Seq("MIT" -> url("http://opensource.org/licenses/MIT"))
