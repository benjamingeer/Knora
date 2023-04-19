import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper._
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.{Docker, dockerRepository}
import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}
import org.knora.Dependencies
import sbt.Keys.version
import sbt._

import scala.language.postfixOps

//////////////////////////////////////
// GLOBAL SETTINGS
//////////////////////////////////////

// when true enables run cancellation w/o exiting sbt
// use Ctrl-c to stop current task but not quit SBT
Global / cancelable := true

Global / scalaVersion                                   := Dependencies.ScalaVersion
Global / semanticdbEnabled                              := true
Global / semanticdbVersion                              := scalafixSemanticdb.revision
Global / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.6.0"

lazy val aggregatedProjects: Seq[ProjectReference] = Seq(webapi, sipi)

lazy val buildSettings = Seq(
  organization := "org.knora",
  version      := (ThisBuild / version).value,
  headerLicense := Some(
    HeaderLicense.Custom(
      """|Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
         |SPDX-License-Identifier: Apache-2.0
         |""".stripMargin
    )
  )
)

lazy val rootBaseDir = ThisBuild / baseDirectory

lazy val dockerImageTag = taskKey[String]("Returns the docker image tag")

lazy val root: Project = Project(id = "root", file("."))
  .aggregate(
    webapi,
    sipi,
    shared,
    schemaCore
  )
  .enablePlugins(GitVersioning, GitBranchPrompt)
  .settings(
    // values set for all sub-projects
    // These are normal sbt settings to configure for release, skip if already defined
    Global / onChangedBuildSource := ReloadOnSourceChanges,
    ThisBuild / licenses          := Seq("Apache License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    ThisBuild / homepage          := Some(url("https://github.com/dasch-swiss/dsp-api")),
    ThisBuild / scmInfo := Some(
      ScmInfo(url("https://github.com/dasch-swiss/dsp-api"), "scm:git:git@github.com:dasch-swiss/dsp-api.git")
    ),
    Global / scalaVersion := Dependencies.ScalaVersion,
    // use 'git describe' for deriving the version
    git.useGitDescribe := true,
    // override generated version string because docker hub rejects '+' in tags
    ThisBuild / version ~= (_.replace('+', '-')),
    dockerImageTag := (ThisBuild / version).value,
    publish / skip := true,
    name           := "dsp-api"
  )

addCommandAlias("fmt", "; all root/scalafmtSbt root/scalafmtAll; root/scalafixAll")
addCommandAlias(
  "headerCreateAll",
  "; all webapi/headerCreate webapi/Test/headerCreate webapi/IntegrationTest/headerCreate"
)
addCommandAlias("check", "; all root/scalafmtSbtCheck root/scalafmtCheckAll; root/scalafixAll --check")
addCommandAlias("it", "IntegrationTest/test")

lazy val customScalacOptions = Seq(
  "-feature",
  "-unchecked",
  "-deprecation",
  "-Yresolve-term-conflict:package",
  "-Ymacro-annotations",
  "-Wunused"
)

//////////////////////////////////////
// DSP's custom SIPI
//////////////////////////////////////

lazy val sipi: Project = Project(id = "sipi", base = file("sipi"))
  .enablePlugins(DockerPlugin)
  .settings(
    Compile / packageDoc / mappings := Seq(),
    Compile / packageSrc / mappings := Seq(),
    Docker / dockerRepository       := Some("daschswiss"),
    Docker / packageName            := "knora-sipi",
    dockerUpdateLatest              := true,
    dockerBaseImage                 := Dependencies.sipiImage,
    dockerBuildxPlatforms           := Seq("linux/arm64/v8", "linux/amd64"),
    Docker / maintainer             := "support@dasch.swiss",
    Docker / dockerExposedPorts ++= Seq(1024),
    Docker / defaultLinuxInstallLocation := "/sipi",
    Universal / mappings ++= {
      directory("sipi/scripts")
    },
    dockerCommands += Cmd(
      """HEALTHCHECK --interval=15s --timeout=5s --retries=3 --start-period=30s \
        |CMD bash /sipi/scripts/healthcheck.sh || exit 1""".stripMargin
    ),
    // use filterNot to return all items that do NOT meet the criteria
    dockerCommands := dockerCommands.value.filterNot {

      // ExecCmd is a case class, and args is a varargs variable, so you need to bind it with @
      // remove ENTRYPOINT
      case ExecCmd("ENTRYPOINT", args @ _*) => true

      // remove CMD
      case ExecCmd("CMD", args @ _*) => true

      case Cmd("USER", args @ _*) => true

      // don't filter the rest; don't filter out anything that doesn't match a pattern
      case cmd => false
    }
  )

//////////////////////////////////////
// WEBAPI (./webapi)
//////////////////////////////////////

run / connectInput := true

lazy val webApiCommonSettings = Seq(
  name := "webapi"
)
testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))

lazy val webapi: Project = Project(id = "webapi", base = file("webapi"))
  .settings(buildSettings)
  .settings(
    inConfig(Test) {
      Defaults.testSettings
    },
    Test / testFrameworks     := Seq(new TestFramework("zio.test.sbt.ZTestFramework")),
    Test / fork               := true, // run tests in a forked JVM
    Test / testForkedParallel := true, // run tests in parallel
    Test / parallelExecution  := true, // run tests in parallel
    libraryDependencies ++= Dependencies.webapiDependencies ++ Dependencies.webapiTestDependencies
  )
  .enablePlugins(SbtTwirl, JavaAppPackaging, DockerPlugin, JavaAgent, BuildInfoPlugin)
  .settings(
    name := "webapi",
    resolvers ++= Seq(
      "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
    ),
    libraryDependencies ++= Dependencies.webapiDependencies ++ Dependencies.webapiTestDependencies ++ Dependencies.webapiIntegrationTestDependencies
  )
  .configs(IntegrationTest)
  .settings(
    inConfig(IntegrationTest) {
      Defaults.itSettings ++ Defaults.testTasks ++ baseAssemblySettings
    },
    libraryDependencies ++= Dependencies.webapiDependencies ++ Dependencies.webapiIntegrationTestDependencies
  )
  .settings(
    // add needed files to production jar
    Compile / packageBin / mappings ++= Seq(
      (rootBaseDir.value / "knora-ontologies" / "knora-admin.ttl")                         -> "knora-ontologies/knora-admin.ttl",
      (rootBaseDir.value / "knora-ontologies" / "knora-base.ttl")                          -> "knora-ontologies/knora-base.ttl",
      (rootBaseDir.value / "knora-ontologies" / "salsah-gui.ttl")                          -> "knora-ontologies/salsah-gui.ttl",
      (rootBaseDir.value / "knora-ontologies" / "standoff-data.ttl")                       -> "knora-ontologies/standoff-data.ttl",
      (rootBaseDir.value / "knora-ontologies" / "standoff-onto.ttl")                       -> "knora-ontologies/standoff-onto.ttl",
      (rootBaseDir.value / "webapi" / "scripts" / "fuseki-repository-config.ttl.template") -> "webapi/scripts/fuseki-repository-config.ttl.template" // needed for initialization of triplestore
    ),
    // use packaged jars (through packageBin) on classpaths instead of class directories for production
    Compile / exportJars := true,
    // add needed files to test jar
    IntegrationTest / packageBin / mappings ++= Seq(
      (rootBaseDir.value / "webapi" / "scripts" / "fuseki-repository-config.ttl.template") -> "webapi/scripts/fuseki-repository-config.ttl.template", // needed for initialization of triplestore
      (rootBaseDir.value / "sipi" / "config" / "sipi.docker-config.lua")                   -> "sipi/config/sipi.docker-config.lua"
    ),
    // use packaged jars (through packageBin) on classpaths instead of class directories for test
    IntegrationTest / exportJars := true
  )
  .settings(
    scalacOptions ++= Seq(
      "-feature",
      "-unchecked",
      "-deprecation",
      "-Yresolve-term-conflict:package",
      "-Ymacro-annotations",
      // silence twirl templates unused imports warnings
      "-Wconf:src=target/.*:s",
      "-Wunused:imports"
    ),
    logLevel := Level.Info,
    javaAgents += Dependencies.aspectjweaver,
    IntegrationTest / fork               := true,  // run tests in a forked JVM
    IntegrationTest / testForkedParallel := false, // not run forked tests in parallel
    IntegrationTest / parallelExecution  := false, // not run non-forked tests in parallel
    // Global / concurrentRestrictions += Tags.limit(Tags.Test, 1), // restrict the number of concurrently executing tests in all projects
    // IntegrationTest / javaOptions ++= Seq("-Dakka.log-config-on-start=on"), // prints out akka config
    // IntegrationTest / javaOptions ++= Seq("-Dconfig.trace=loads"), // prints out config locations
    // IntegrationTest / javaOptions += "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005", // starts sbt with debug port
    IntegrationTest / javaOptions += "-Dkey=" + sys.props.getOrElse("key", "akka"),
    IntegrationTest / testOptions += Tests.Argument("-oDF"), // show full stack traces and test case durations
    // add test framework for running zio-tests
    IntegrationTest / testFrameworks ++= Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )
  .settings(
    // prepare for publishing

    // Skip packageDoc and packageSrc task on stage
    Compile / packageDoc / mappings := Seq(),
    Compile / packageSrc / mappings := Seq(),
    // define folders inside container
    Universal / mappings ++= {
      // copy the scripts folder
      directory("webapi/scripts") ++
        // add knora-ontologies
        directory("knora-ontologies") ++
        // copy configuration files to config directory
        contentOf("webapi/src/main/resources").toMap.mapValues("config/" + _)
    },
    // add 'config' directory to the classpath of the start script,
    Universal / scriptClasspath := Seq("webapi/scripts", "knora-ontologies", "../config/") ++ scriptClasspath.value,
    // need this here, so that the Manifest inside the jars has the correct main class set.
    Compile / mainClass := Some("org.knora.webapi.Main"),
    // add dockerCommands used to create the image
    // docker:stage, docker:publishLocal, docker:publish, docker:clean
    Docker / dockerRepository := Some("daschswiss"),
    Docker / packageName      := "knora-api",
    dockerUpdateLatest        := true,
    dockerBaseImage           := "eclipse-temurin:17-jre-focal",
    dockerBuildxPlatforms     := Seq("linux/arm64/v8", "linux/amd64"),
    Docker / maintainer       := "support@dasch.swiss",
    Docker / dockerExposedPorts ++= Seq(3333, 3339),
    Docker / defaultLinuxInstallLocation := "/opt/docker",
    dockerCommands += Cmd(
      "RUN",
      "apt-get update && apt-get install -y jq && rm -rf /var/lib/apt/lists/*"
    ), // install jq for container healthcheck
    dockerCommands += Cmd(
      """HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=30s \
        |CMD bash /opt/docker/scripts/healthcheck.sh || exit 1""".stripMargin
    ),
    // use filterNot to return all items that do NOT meet the criteria
    dockerCommands := dockerCommands.value.filterNot {
      // Remove USER command
      case Cmd("USER", args @ _*) => true

      // don't filter the rest; don't filter out anything that doesn't match a pattern
      case cmd => false
    }
  )
  .settings(
    buildInfoKeys ++= Seq[BuildInfoKey](
      name,
      version,
      "akkaHttp" -> Dependencies.AkkaHttpVersion,
      "sipi"     -> Dependencies.sipiImage,
      "fuseki"   -> Dependencies.fusekiImage
    ),
    buildInfoPackage := "org.knora.webapi.http.version"
  )
  .dependsOn(shared, schemaCore)

//////////////////////////////////////
// DSP's new codebase
//////////////////////////////////////

// schema project
lazy val schemaCore = project
  .in(file("dsp-schema/core"))
  .settings(
    scalacOptions ++= customScalacOptions,
    name := "schemaCore",
    libraryDependencies ++= Dependencies.schemaCoreLibraryDependencies,
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )
  .dependsOn(shared)

// shared project
lazy val shared = project
  .in(file("dsp-shared"))
  .settings(
    scalacOptions ++= customScalacOptions,
    name := "shared",
    libraryDependencies ++= Dependencies.sharedLibraryDependencies,
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )
