// Scalafmt for code formatting
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.4")

// Scalafix for refactoring and linting
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.14.0")

// For signing artifacts (needed for Maven Central)
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.2.1")

// For simplifying publishing to Sonatype OSSRH Repositories
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.11.0")
// Note: Removed duplicate sbt-sonatype line

// For dynamic versioning from Git (optional, but often used with ci-release)
addSbtPlugin("com.github.sbt" % "sbt-dynver" % "5.0.1")

// For mdoc documentation generation/checking
addSbtPlugin("org.scalameta" % "sbt-mdoc" % "2.3.8")

// *** ADDED: For automating Sonatype release process from CI ***
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.5.12") // Or latest version
