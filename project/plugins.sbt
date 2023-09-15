resolvers ++= Seq(
  Resolver.typesafeRepo("releases")
)

// please don't remove or merge uncommented to main
//addDependencyTreePlugin

addSbtPlugin("com.github.sbt"    % "sbt-native-packager" % "1.9.16")
addSbtPlugin("io.kamon"          % "sbt-aspectj-runner"  % "1.1.2")
addSbtPlugin("com.typesafe.play" % "sbt-twirl"           % "1.6.0")
addSbtPlugin("com.eed3si9n"      % "sbt-assembly"        % "2.1.1")
addSbtPlugin("com.github.sbt"    % "sbt-javaagent"       % "0.1.7")
// also update the scalac-scoverage-runtime version in build.sbt
addSbtPlugin("org.scoverage"     % "sbt-scoverage" % "2.0.9")
addSbtPlugin("com.eed3si9n"      % "sbt-buildinfo" % "0.11.0")
addSbtPlugin("org.scalameta"     % "sbt-scalafmt"  % "2.5.2")
addSbtPlugin("ch.epfl.scala"     % "sbt-scalafix"  % "0.11.1")
addSbtPlugin("de.heikoseeberger" % "sbt-header"    % "5.10.0")

// ad-hoc plugins - uncomment on demenad and keep it commented out in main branch

// https://github.com/rtimush/sbt-updates
// addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.6.1")

// https://github.com/cb372/sbt-explicit-dependencies
// addSbtPlugin("com.github.cb372" % "sbt-explicit-dependencies" % "0.2.16")
