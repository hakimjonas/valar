// Formatting & linting
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.4")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.14.3")

// Documentation
addSbtPlugin("org.scalameta" % "sbt-mdoc" % "2.7.1")

// Publishing & release
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.3.1")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.12.2")
addSbtPlugin("com.github.sbt" % "sbt-dynver" % "5.1.0")

// Cross-platform builds
addSbtPlugin("org.portable-scala" % "sbt-crossproject" % "1.3.2")
addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "1.3.2")

// Scala Native
addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.5.8")
