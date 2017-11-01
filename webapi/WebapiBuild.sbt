import sbt._
import NativePackagerHelper._

connectInput in run := true

// Bring the sbt-aspectj settings into this build
//aspectjSettings

lazy val webapi = (project in file(".")).
        configs(
            FusekiTest,
            FusekiIntegrationTest,
            GraphDBTest,
            EmbeddedJenaTDBTest,
            IntegrationTest
        ).
        settings(webApiCommonSettings:  _*).
        settings(inConfig(Test)(
            Defaults.testTasks ++ baseAssemblySettings
        ): _*).
        settings(inConfig(FusekiTest)(
            Defaults.testTasks ++ Seq(
                fork := true,
                javaOptions ++= javaFusekiTestOptions,
                testOptions += Tests.Argument("-oDF") // show full stack traces and test case durations
            )
        ): _*).
        settings(inConfig(FusekiIntegrationTest)(
            Defaults.testTasks ++ Seq(
                fork := true,
                javaOptions ++= javaFusekiIntegrationTestOptions,
                testOptions += Tests.Argument("-oDF") // show full stack traces and test case durations
            )
        ): _*).
        settings(inConfig(GraphDBTest)(
            Defaults.testTasks ++ Seq(
                fork := true,
                javaOptions ++= javaGraphDBTestOptions,
                testOptions += Tests.Argument("-oDF") // show full stack traces and test case durations
            )
        ): _*).
        settings(inConfig(EmbeddedJenaTDBTest)(
            Defaults.testTasks ++ Seq(
                fork := true,
                javaOptions ++= javaEmbeddedJenaTDBTestOptions,
                testOptions += Tests.Argument("-oDF") // show full stack traces and test case durations
            )
        ): _*).
        settings(inConfig(IntegrationTest)(
            Defaults.itSettings ++ Seq(
                fork := true,
                javaOptions ++= javaIntegrationTestOptions,
                testOptions += Tests.Argument("-oDF") // show full stack traces and test case durations
            ) ++ baseAssemblySettings
        ): _*).
        settings(
            libraryDependencies ++= webApiLibs,
            scalacOptions ++= Seq("-feature", "-unchecked", "-deprecation", "-Yresolve-term-conflict:package"),
            logLevel := Level.Info,
            fork in run := true,
            javaOptions in run ++= javaRunOptions,
            //javaOptions in run <++= AspectjKeys.weaverOptions in Aspectj,
            //javaOptions in Revolver.reStart <++= AspectjKeys.weaverOptions in Aspectj,
            mainClass in (Compile, run) := Some("org.knora.webapi.Main"),
            fork in Test := true,
            javaOptions in Test ++= javaTestOptions,
            parallelExecution in Test := false,
            // enable publishing the jar produced by `sbt test:package` and `sbt it:package`
            publishArtifact in (Test, packageBin) := true,
            publishArtifact in (IntegrationTest, packageBin) := true
        ).
        settings( // enable deployment staging with `sbt stage`. uses fat jar assembly.
            // we specify the name for our fat jars (main, test, it)
            assemblyJarName in assembly := s"assembly-${name.value}-main-${version.value}.jar",
            assemblyJarName in (Test, assembly) := s"assembly-${name.value}-test-${version.value}.jar",
            assemblyJarName in (IntegrationTest, assembly) := s"assembly-${name.value}-it-${version.value}.jar",
            // disable running of tests before fat jar assembly!
            test in assembly := {},
            test in (Test, assembly) := {},
            test in (IntegrationTest, assembly) := {},
            // Skip packageDoc task on stage
            mappings in (Compile, packageDoc) := Seq(),
            mappings in Universal := {
                // removes all jar mappings in universal and appends the fat jar
                // universalMappings: Seq[(File,String)]
                val universalMappings = (mappings in Universal).value
                val fatJar = (assembly in Compile).value
                // removing means filtering
                val filtered = universalMappings filter {
                    case (file, name) =>  ! name.endsWith(".jar")
                }
                // add the fat jar
                filtered :+ (fatJar -> ("lib/" + fatJar.getName))
            },
            mappings in Universal ++= {
                // copy the scripts folder
                directory("scripts") ++
                // copy the configuration files to config directory
                contentOf("configs").toMap.mapValues("config/" + _) ++
                // copy configuration files to config directory
                contentOf("src/main/resources").toMap.mapValues("config/" + _)
            },
            // the bash scripts classpath only needs the fat jar
            scriptClasspath := Seq( (assemblyJarName in assembly).value ),
            // add 'config' directory first in the classpath of the start script,
            scriptClasspath := Seq("../config/") ++ scriptClasspath.value,
            // add license
            licenses := Seq(("GNU AGPL", url("https://www.gnu.org/licenses/agpl-3.0"))),
            // need this here, so that the Manifest inside the jars has the correct main class set.
            mainClass in Compile := Some("org.knora.webapi.Main"),
            mainClass in Test := Some("org.scalatest.tools.Runner"),
            mainClass in IntegrationTest := Some("org.scalatest.tools.Runner")
        ).
        enablePlugins(SbtTwirl). // Enable the sbt-twirl plugin
        enablePlugins(JavaAppPackaging) // Enable the sbt-native-packager plugin

lazy val webApiCommonSettings = Seq(
    organization := "org.knora",
    name := "webapi",
    version := "0.1.0-beta",
    ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) },
    scalaVersion := "2.12.1"
)

lazy val akkaVersion = "2.4.19"
lazy val akkaHttpVersion = "10.0.7"

lazy val webApiLibs = Seq(
    // akka
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-agent" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-xml" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,

    "org.scala-lang.modules" %% "scala-xml" % "1.0.6",

    // testing
    "org.scalatest" %% "scalatest" % "3.0.0" % "test",
    //CORS support
    "ch.megard" %% "akka-http-cors" % "0.1.10",
    // jena
    "org.apache.jena" % "apache-jena-libs" % "3.4.0" exclude("org.slf4j", "slf4j-log4j12") exclude("commons-codec", "commons-codec"),
    "org.apache.jena" % "jena-text" % "3.4.0" exclude("org.slf4j", "slf4j-log4j12") exclude("commons-codec", "commons-codec"),
    // http client
    // "net.databinder.dispatch" %% "dispatch-core" % "0.11.2",
    // logging
    "com.typesafe.scala-logging" %% "scala-logging" % "3.7.2",
    "ch.qos.logback" % "logback-classic" % "1.1.7",
    // input validation
    "commons-validator" % "commons-validator" % "1.6" exclude("commons-logging", "commons-logging"),
    // authentication
    "org.bouncycastle" % "bcprov-jdk15on" % "1.58",
    "org.springframework.security" % "spring-security-core" % "4.2.3.RELEASE" exclude("commons-logging", "commons-logging") exclude("org.springframework", "spring-aop"),
    // caching
    "net.sf.ehcache" % "ehcache" % "2.10.0",
    // monitoring - disabled for now
    //"org.aspectj" % "aspectjweaver" % "1.8.7",
    //"org.aspectj" % "aspectjrt" % "1.8.7",
    //"io.kamon" %% "kamon-core" % "0.5.2",
    //"io.kamon" %% "kamon-spray" % "0.5.2",
    //"io.kamon" %% "kamon-statsd" % "0.5.2",
    //"io.kamon" %% "kamon-log-reporter" % "0.5.2",
    //"io.kamon" %% "kamon-system-metrics" % "0.5.2",
    //"io.kamon" %% "kamon-newrelic" % "0.5.2",
    // other
    //"javax.transaction" % "transaction-api" % "1.1-rev-1",
    "org.apache.commons" % "commons-lang3" % "3.4",
    "commons-io" % "commons-io" % "2.4",
    "commons-beanutils" % "commons-beanutils" % "1.9.2" exclude("commons-logging", "commons-logging"), // not used by us, but need newest version to prevent this problem: http://stackoverflow.com/questions/14402745/duplicate-classes-in-commons-collections-and-commons-beanutils
    "org.jodd" % "jodd" % "3.2.6",
    "joda-time" % "joda-time" % "2.9.1",
    "org.joda" % "joda-convert" % "1.8",
    "com.sksamuel.diff" % "diff" % "1.1.11",
    "org.xmlunit" % "xmlunit-core" % "2.1.1",
    "io.igl" %% "jwt" % "1.2.2" exclude("commons-codec", "commons-codec"),
    // testing
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test, fuseki, graphdb, tdb, it, fuseki-it",
    "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % "test, fuseki, graphdb, tdb, it, fuseki-it",
    "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % "test, fuseki, graphdb, tdb, it, fuseki-it",
    "org.scalatest" %% "scalatest" % "3.0.0" % "test, fuseki, graphdb, tdb, it, fuseki-it",
    "org.eclipse.rdf4j" % "rdf4j-rio-turtle" % "2.2.1",
    "org.eclipse.rdf4j" % "rdf4j-queryparser-sparql" % "2.2.1",
    "org.rogach" %% "scallop" % "2.0.5",
    "com.google.gwt" % "gwt-servlet" % "2.8.0",
    "net.sf.saxon" % "Saxon-HE" % "9.7.0-14",
    "com.github.jsonld-java" % "jsonld-java" % "0.10.0",
    "com.jsuereth" % "scala-arm_2.12" % "2.0"
)

lazy val excludeDependencies = Seq(
    // commons-logging is replaced by jcl-over-slf4j
    ExclusionRule("commons-logging", "commons-logging")
)

lazy val javaRunOptions = Seq(
    // "-showversion",
    "-Xms1G",
    "-Xmx1G"
    // "-verbose:gc",
    //"-XX:+UseG1GC",
    //"-XX:MaxGCPauseMillis=500"
)

lazy val javaBaseTestOptions = Seq(
    // "-showversion",
    "-Xms2G",
    "-Xmx4G"
    // "-verbose:gc",
    //"-XX:+UseG1GC",
    //"-XX:MaxGCPauseMillis=500",
    //"-XX:MaxMetaspaceSize=4096m"
)

lazy val javaTestOptions = Seq(
    "-Dconfig.resource=graphdb.conf"
) ++ javaBaseTestOptions

lazy val FusekiTest = config("fuseki") extend(Test)
lazy val javaFusekiTestOptions = Seq(
    "-Dconfig.resource=fuseki.conf"
) ++ javaBaseTestOptions

lazy val FusekiIntegrationTest = config("fuseki-it") extend(IntegrationTest)
lazy val javaFusekiIntegrationTestOptions = Seq(
    "-Dconfig.resource=fuseki.conf"
) ++ javaBaseTestOptions

lazy val GraphDBTest = config("graphdb") extend(Test)
lazy val javaGraphDBTestOptions = Seq(
    "-Dconfig.resource=graphdb.conf"
) ++ javaBaseTestOptions

lazy val EmbeddedJenaTDBTest = config("tdb") extend(Test)
lazy val javaEmbeddedJenaTDBTestOptions = Seq(
    "-Dconfig.resource=jenatdb.conf"
) ++ javaBaseTestOptions

// The 'IntegrationTest' config does not need to be created here, as it is a built-in config!
// The standard testing tasks are available, but must be prefixed with 'it:', e.g., 'it:test'
// The test need to be stored in the 'it' (and not 'test') folder. The standard source hierarchy is used, e.g., 'src/it/scala'
lazy val javaIntegrationTestOptions = Seq(
    "-Dconfig.resource=graphdb.conf"
) ++ javaBaseTestOptions