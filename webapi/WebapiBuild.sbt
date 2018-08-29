import sbt._
import NativePackagerHelper._
import sbt.io.IO
import sbtassembly.MergeStrategy
import sbtassembly.MergeStrategy._
import sbt.librarymanagement.Resolver

connectInput in run := true

lazy val webApiCommonSettings = Seq(
    organization := "org.knora",
    name := "webapi",
    version := "1.7.1",
    scalaVersion := "2.12.4"
)

// custom test and it settings
lazy val GDBSE = config("gdbse") extend Test
lazy val GDBSEIt = config("gdbse-it") extend IntegrationTest
lazy val GDBFree = config("gdbfree") extend Test
lazy val GDBFreeIt = config("gdbfree-it") extend IntegrationTest
lazy val FusekiTest = config("fuseki") extend Test
lazy val FusekiIt = config("fuseki-it") extend IntegrationTest
lazy val EmbeddedJenaTDBTest = config("tdb") extend Test

lazy val webapi = (project in file(".")).
        configs(
            IntegrationTest,
            Gatling,
            GatlingIt,
            GDBSE,
            GDBSEIt,
            GDBFree,
            GDBFreeIt,
            FusekiTest,
            FusekiIt,
            EmbeddedJenaTDBTest
        ).
        settings(
            webApiCommonSettings,
            resolvers ++= Seq(
                Resolver.bintrayRepo("hseeberger", "maven")
            ),
            libraryDependencies ++= webApiLibs
        ).
        settings(
            inConfig(Test)(Defaults.testTasks ++ baseAssemblySettings),
            inConfig(IntegrationTest)(Defaults.testSettings),
            inConfig(Gatling)(Defaults.testTasks ++ Seq(forkOptions := Defaults.forkOptionsTask.value)),
            inConfig(GatlingIt)(Defaults.testTasks ++ Seq(forkOptions := Defaults.forkOptionsTask.value)),
            inConfig(GDBSE)(Defaults.testTasks ++ Seq(forkOptions := Defaults.forkOptionsTask.value)),
            inConfig(GDBSEIt)(Defaults.testTasks ++ Seq(forkOptions := Defaults.forkOptionsTask.value)),
            inConfig(GDBFree)(Defaults.testTasks ++ Seq(forkOptions := Defaults.forkOptionsTask.value)),
            inConfig(GDBFreeIt)(Defaults.testTasks ++ Seq(forkOptions := Defaults.forkOptionsTask.value)),
            inConfig(FusekiTest)(Defaults.testTasks ++ Seq(forkOptions := Defaults.forkOptionsTask.value)),
            inConfig(FusekiIt)(Defaults.testTasks ++ Seq(forkOptions := Defaults.forkOptionsTask.value)),
            inConfig(EmbeddedJenaTDBTest)(Defaults.testTasks ++ Seq(forkOptions := Defaults.forkOptionsTask.value))
        ).
        settings(
            scalacOptions ++= Seq("-feature", "-unchecked", "-deprecation", "-Yresolve-term-conflict:package"),

            logLevel := Level.Info,

            fork := true, // always fork

            cancelable in Global := true, // use Ctrl-c to stop current task and not quit SBT

            run / javaOptions := javaRunOptions,

            reStart / javaOptions ++= resolvedJavaAgents.value map { resolved =>
                "-javaagent:" + resolved.artifact.absolutePath + resolved.agent.arguments
            },// allows sbt-javaagent to work with sbt-revolver
            reStart / javaOptions ++= javaRunOptions,

            javaAgents += library.aspectJWeaver,

            Test / parallelExecution := false,
            Test / javaOptions ++= Seq("-Dconfig.resource=graphdb-se.conf") ++ javaTestOptions,
            // Test / javaOptions ++= Seq("-Dakka.log-config-on-start=on"), // prints out akka config
            // Test / javaOptions ++= Seq("-Dconfig.trace=loads"), // prints out config locations
            Test / testOptions += Tests.Argument("-oDF"), // show full stack traces and test case durations

            IntegrationTest / javaOptions := Seq("-Dconfig.resource=graphdb-se.conf") ++ javaTestOptions,
            IntegrationTest / testOptions += Tests.Argument("-oDF"), // show full stack traces and test case durations

            Gatling / javaOptions := Seq("-Dconfig.resource=graphdb-se.conf") ++ javaTestOptions,
            Gatling / testOptions := Seq(),
            GatlingIt / javaOptions := Seq("-Dconfig.resource=graphdb-se.conf") ++ javaTestOptions,
            GatlingIt / testOptions := Seq(),

            GDBSE / javaOptions := Seq("-Dconfig.resource=graphdb-se.conf") ++ javaTestOptions,
            GDBSEIt / javaOptions := Seq("-Dconfig.resource=graphdb-se.conf") ++ javaTestOptions,

            GDBFree / javaOptions := Seq("-Dconfig.resource=graphdb-free.conf") ++ javaTestOptions,
            GDBFreeIt / javaOptions := Seq("-Dconfig.resource=graphdb-free.conf") ++ javaTestOptions,

            FusekiTest / javaOptions := Seq("-Dconfig.resource=fuseki.conf") ++ javaTestOptions,
            FusekiIt / javaOptions := Seq("-Dconfig.resource=fuseki.conf") ++ javaTestOptions,

            EmbeddedJenaTDBTest / javaOptions := Seq("-Dconfig.resource=jenatdb.conf") ++ javaTestOptions,

            // enable publishing the jar produced by `sbt test:package` and `sbt it:package`
            Test / packageBin / publishArtifact := true,
            IntegrationTest / packageBin / publishArtifact := true
        ).
        settings(
            // enabled deployment staging with `sbt stage`. uses fat jar assembly.
            // we specify the name for our fat jars (main, test, it)
            assembly / assemblyJarName := s"assembly-${name.value}-main-${version.value}.jar",
            Test / assembly / assemblyJarName := s"assembly-${name.value}-test-${version.value}.jar",
            IntegrationTest / assembly / assemblyJarName := s"assembly-${name.value}-it-${version.value}.jar",

            // disable running of tests before fat jar assembly!
            assembly / test := {},
            // test in (Test, assembly) := {},
            // test in (IntegrationTest, assembly) := {},

            // need to use our custom merge strategy because of aop.xml (AspectJ)
            assembly / assemblyMergeStrategy := customMergeStrategy,

            // Skip packageDoc task on stage
            Compile / packageDoc / mappings := Seq(),

            Universal / mappings := {
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

            Universal / mappings ++= {
                // copy the scripts folder
                directory("scripts") ++
                // copy the configuration files to config directory
                contentOf("configs").toMap.mapValues("config/" + _) ++
                // copy configuration files to config directory
                contentOf("src/main/resources").toMap.mapValues("config/" + _) ++
                // copy the aspectj weaver jar
                contentOf("vendor").toMap.mapValues("aspectjweaver/" + _)
            },

            // the bash scripts classpath only needs the fat jar
            scriptClasspath := Seq( (assemblyJarName in assembly).value ),

            // add 'config' directory first in the classpath of the start script,
            scriptClasspath := Seq("../config/") ++ scriptClasspath.value,

            // add license
            licenses := Seq(("GNU AGPL", url("https://www.gnu.org/licenses/agpl-3.0"))),

            // need this here, so that the Manifest inside the jars has the correct main class set.
            Compile / mainClass := Some("org.knora.webapi.Main"),
            Compile / run / mainClass := Some("org.knora.webapi.Main"),
            Test / mainClass := Some("org.scalatest.tools.Runner"),
            IntegrationTest / mainClass := Some("org.scalatest.tools.Runner")
        ).
        enablePlugins(SbtTwirl). // Enable the sbt-twirl plugin
        enablePlugins(JavaAppPackaging). // Enable the sbt-native-packager plugin
        enablePlugins(GatlingPlugin). // load testing
        enablePlugins(JavaAgent). // Adds AspectJ Weaver configuration
        enablePlugins(RevolverPlugin)



lazy val webApiLibs = Seq(
    library.aspectJWeaver,
    library.akkaActor,
    library.akkaAgent,
    library.akkaHttp,
    library.akkaHttpCirce,
    library.akkaHttpCors,
    library.akkaHttpSprayJson,
    library.akkaHttpJacksonJava,
    library.akkaHttpTestkit,
    library.akkaHttpXml,
    library.akkaSlf4j,
    library.akkaStream,
    library.akkaStreamTestkit,
    library.akkaTestkit,
    library.bcprov,
    library.commonsBeanUtil,
    library.commonsIo,
    library.commonsText,
    library.commonsValidator,
    library.diff,
    library.ehcache,
    library.gatlingHighcharts,
    library.gatlingTestFramework,
    library.gwtServlet,
    library.jacksonScala,
    library.jsonldJava,
    library.jodd,
    library.jodaTime,
    library.jodaConvert,
    library.jenaLibs,
    library.jenaText,
    library.jwt,
    //library.kamonCore,
    //library.kamonAkka,
    //library.kamonAkkaHttp,
    //library.kamonPrometheus,
    //library.kamonZipkin,
    //library.kamonJaeger,
    library.logbackClassic,
    library.rdf4jRuntime,
    library.saxonHE,
    library.scalaArm,
    library.scalaJava8Compat,
    library.scalaLogging,
    library.scalaTest,
    library.scalaXml,
    library.scallop,
    library.springSecurityCore,
    library.swaggerAkkaHttp,
    library.typesafeConfig,
    library.xmlunitCore
)

lazy val library =
    new {
        object Version {
            val akkaBase = "2.5.13"
            val akkaHttp = "10.1.3"
            val jena = "3.4.0"
            val metrics = "4.0.1"
        }

        // akka
        val akkaActor              = "com.typesafe.akka"            %% "akka-actor"               % Version.akkaBase
        val akkaAgent              = "com.typesafe.akka"            %% "akka-agent"               % Version.akkaBase
        val akkaStream             = "com.typesafe.akka"            %% "akka-stream"              % Version.akkaBase
        val akkaSlf4j              = "com.typesafe.akka"            %% "akka-slf4j"               % Version.akkaBase
        val akkaTestkit            = "com.typesafe.akka"            %% "akka-testkit"             % Version.akkaBase    % "test, it, gdbse, gdbse-it, gdbfree, gdbfree-it, tdb, fuseki, fuseki-it"
        val akkaStreamTestkit      = "com.typesafe.akka"            %% "akka-stream-testkit"      % Version.akkaBase    % "test, it, gdbse, gdbse-it, gdbfree, gdbfree-it, tdb, fuseki, fuseki-it"

        // akka http
        val akkaHttp               = "com.typesafe.akka"            %% "akka-http"                % Version.akkaHttp
        val akkaHttpXml            = "com.typesafe.akka"            %% "akka-http-xml"            % Version.akkaHttp
        val akkaHttpSprayJson      = "com.typesafe.akka"            %% "akka-http-spray-json"     % Version.akkaHttp
        val akkaHttpJacksonJava    = "com.typesafe.akka"            %% "akka-http-jackson"        % Version.akkaHttp
        val akkaHttpTestkit        = "com.typesafe.akka"            %% "akka-http-testkit"        % Version.akkaHttp    % "test, it, gdbse, gdbse-it, gdbfree, gdbfree-it, tdb, fuseki, fuseki-it"

        val typesafeConfig         = "com.typesafe"                  % "config"                   % "1.3.3"

        // testing
        val scalaTest              = "org.scalatest"                %% "scalatest"                % "3.0.4"             % "test, it, gdbse, gdbse-it, gdbfree, gdbfree-it, tdb, fuseki, fuseki-it"
        val gatlingHighcharts      = "io.gatling.highcharts"         % "gatling-charts-highcharts"% "2.3.1"             % "test, it, gdbse, gdbse-it, gdbfree, gdbfree-it, tdb, fuseki, fuseki-it"
        val gatlingTestFramework   = "io.gatling"                    % "gatling-test-framework"   % "2.3.1"             % "test, it, gdbse, gdbse-it, gdbfree, gdbfree-it, tdb, fuseki, fuseki-it"

        //CORS support
        val akkaHttpCors           = "ch.megard"                    %% "akka-http-cors"           % "0.3.0"

        // jena
        val jenaLibs               = "org.apache.jena"               % "apache-jena-libs"         % Version.jena exclude("org.slf4j", "slf4j-log4j12") exclude("commons-codec", "commons-codec")
        val jenaText               = "org.apache.jena"               % "jena-text"                % Version.jena exclude("org.slf4j", "slf4j-log4j12") exclude("commons-codec", "commons-codec")

        // logging
        val commonsLogging         = "commons-logging"               % "commons-logging"          % "1.2"
        val scalaLogging           = "com.typesafe.scala-logging"   %% "scala-logging"            % "3.8.0"
        val logbackClassic         = "ch.qos.logback"                % "logback-classic"          % "1.2.3"

        // input validation
        val commonsValidator       = "commons-validator"             % "commons-validator"        % "1.6" exclude("commons-logging", "commons-logging")

        // authentication
        val bcprov                 = "org.bouncycastle"              % "bcprov-jdk15on"           % "1.59"
        val springSecurityCore     = "org.springframework.security"  % "spring-security-core"     % "4.2.5.RELEASE" exclude("commons-logging", "commons-logging") exclude("org.springframework", "spring-aop")
        val jwt                    = "io.igl"                       %% "jwt"                      % "1.2.2" exclude("commons-codec", "commons-codec")

        // caching
        val ehcache                = "net.sf.ehcache"                % "ehcache"                  % "2.10.0"

        // monitoring
        val kamonCore              = "io.kamon"                     %% "kamon-core"               % "1.1.3"
        val kamonAkka              = "io.kamon"                     %% "kamon-akka-2.5"           % "1.1.1"
        val kamonAkkaHttp          = "io.kamon"                     %% "kamon-akka-http-2.5"      % "1.1.0"
        val kamonPrometheus        = "io.kamon"                     %% "kamon-prometheus"         % "1.1.1"
        val kamonZipkin            = "io.kamon"                     %% "kamon-zipkin"             % "1.0.0"
        val kamonJaeger            = "io.kamon"                     %% "kamon-jaeger"             % "1.0.2"
        val aspectJWeaver          = "org.aspectj"                   % "aspectjweaver"            % "1.9.1"

        // other
        //"javax.transaction" % "transaction-api" % "1.1-rev-1",
        val commonsText            = "org.apache.commons"            % "commons-text"             % "1.3"
        val commonsIo              = "commons-io"                    % "commons-io"               % "2.6"
        val commonsBeanUtil        = "commons-beanutils"             % "commons-beanutils"        % "1.9.3" exclude("commons-logging", "commons-logging") // not used by us, but need newest version to prevent this problem: http://stackoverflow.com/questions/14402745/duplicate-classes-in-commons-collections-and-commons-beanutils
        val jodd                   = "org.jodd"                      % "jodd"                     % "3.2.6"
        val jodaTime               = "joda-time"                     % "joda-time"                % "2.9.1"
        val jodaConvert            = "org.joda"                      % "joda-convert"             % "1.8"
        val diff                   = "com.sksamuel.diff"             % "diff"                     % "1.1.11"
        val xmlunitCore            = "org.xmlunit"                   % "xmlunit-core"             % "2.1.1"

        // other
        val rdf4jRuntime           = "org.eclipse.rdf4j"             % "rdf4j-runtime"            % "2.3.2"
        val scallop                = "org.rogach"                   %% "scallop"                  % "2.0.5"
        val gwtServlet             = "com.google.gwt"                % "gwt-servlet"              % "2.8.0"
        val saxonHE                = "net.sf.saxon"                  % "Saxon-HE"                 % "9.7.0-14"

        val scalaXml               = "org.scala-lang.modules"       %% "scala-xml"                % "1.1.0"
        val scalaArm               = "com.jsuereth"                  % "scala-arm_2.12"           % "2.0"
        val scalaJava8Compat       = "org.scala-lang.modules"        % "scala-java8-compat_2.12"  % "0.8.0"

        // provides akka jackson (json) support
        val akkaHttpCirce          = "de.heikoseeberger"            %% "akka-http-circe"          % "1.21.0"
        val jacksonScala           = "com.fasterxml.jackson.module" %% "jackson-module-scala"     % "2.9.4"

        val jsonldJava             = "com.github.jsonld-java"        % "jsonld-java"              % "0.12.0"

        // swagger (api documentation)
        val swaggerAkkaHttp        = "com.github.swagger-akka-http" %% "swagger-akka-http"        % "0.14.0"
    }

lazy val javaRunOptions = Seq(
    // "-showversion",
    "-Xms1G",
    "-Xmx1G",
    // "-verbose:gc",
    //"-XX:+UseG1GC",
    //"-XX:MaxGCPauseMillis=500"
    "-Dcom.sun.management.jmxremote",
    "-Dcom.sun.management.jmxremote.port=1617",
    "-Dcom.sun.management.jmxremote.authenticate=false",
    "-Dcom.sun.management.jmxremote.ssl=false"
)

lazy val javaTestOptions = Seq(
    // "-showversion",
    "-Xms1G",
    "-Xmx1G"
    // "-verbose:gc",
    //"-XX:+UseG1GC",
    //"-XX:MaxGCPauseMillis=500",
    //"-XX:MaxMetaspaceSize=4096m"
)

// Create a new MergeStrategy for aop.xml files, as the aop.xml file is present in more than one package.
// When we create a fat JAR (assembly task), then we need to resolve this conflict.
val aopMerge: MergeStrategy = new MergeStrategy {
    val name = "aopMerge"
    import scala.xml._
    import scala.xml.dtd._

    def apply(tempDir: File, path: String, files: Seq[File]): Either[String, Seq[(File, String)]] = {
        val dt = DocType("aspectj", PublicID("-//AspectJ//DTD//EN", "http://www.eclipse.org/aspectj/dtd/aspectj.dtd"), Nil)
        val file = MergeStrategy.createMergeTarget(tempDir, path)
        val xmls: Seq[Elem] = files.map(XML.loadFile)
        val aspectsChildren: Seq[Node] = xmls.flatMap(_ \\ "aspectj" \ "aspects" \ "_")
        val weaverChildren: Seq[Node] = xmls.flatMap(_ \\ "aspectj" \ "weaver" \ "_")
        val options: String = xmls.map(x => (x \\ "aspectj" \ "weaver" \ "@options").text).mkString(" ").trim
        val weaverAttr = if (options.isEmpty) Null else new UnprefixedAttribute("options", options, Null)
        val aspects = new Elem(null, "aspects", Null, TopScope, false, aspectsChildren: _*)
        val weaver = new Elem(null, "weaver", weaverAttr, TopScope, false, weaverChildren: _*)
        val aspectj = new Elem(null, "aspectj", Null, TopScope, false, aspects, weaver)
        XML.save(file.toString, aspectj, "UTF-8", xmlDecl = false, dt)
        IO.append(file, IO.Newline.getBytes(IO.defaultCharset))
        Right(Seq(file -> path))
    }
}

// Use defaultMergeStrategy with a case for aop.xml
// I like this better than the inline version mentioned in assembly's README
val customMergeStrategy: String => MergeStrategy = {
    case PathList("META-INF", "aop.xml") =>
        aopMerge
    case PathList(ps @ _*) if ps.exists(_.contains("aopalliance")) || ps.exists(_.contains("lucene")) || ps.exists(_.contains("joda")) =>
        // Workaround for #855. TODO: find a better way to resolve these conflicts.
        MergeStrategy.first
    case s =>
        defaultMergeStrategy(s)
}
