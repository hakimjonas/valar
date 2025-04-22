// Formatting & linting
addSbtPlugin("org.scalameta"       % "sbt-scalafmt"   % "2.5.4")
addSbtPlugin("ch.epfl.scala"      % "sbt-scalafix"   % "0.14.0")

// Documentation
addSbtPlugin("org.scalameta"       % "sbt-mdoc"       % "2.7.1")

// Publishing & release
addSbtPlugin("com.github.sbt"      % "sbt-pgp"        % "2.3.1")
addSbtPlugin("org.xerial.sbt"      % "sbt-sonatype"   % "3.11.2")
addSbtPlugin("com.github.sbt"      % "sbt-dynver"     % "5.1.0")
addSbtPlugin("com.github.sbt"      % "sbt-ci-release" % "1.5.12")
