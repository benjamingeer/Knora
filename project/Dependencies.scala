/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora

import sbt.Keys._
import sbt.{Def, _}

object Dependencies {
  // versions
  val akkaHttpVersion = "10.2.9"
  val akkaVersion = "2.6.19"
  val fusekiImage = "daschswiss/apache-jena-fuseki:2.0.8" // should be the same version as in docker-compose.yml
  val jenaVersion = "4.4.0"
  val metricsVersion = "4.0.1"
  val scalaVersion = "2.13.8"
  val sipiImage = "daschswiss/sipi:3.3.4" // base image the knora-sipi image is created from
  val ZioHttpVersion = "2.0.0-RC3"
  val ZioPreludeVersion = "1.0.0-RC10"
  val ZioVersion = "2.0.0-RC2"

  // ZIO - all Scala 3 compatible
  val zio = "dev.zio" %% "zio" % ZioVersion
  val zioHttp = "io.d11" %% "zhttp" % ZioHttpVersion
  val zioPrelude = "dev.zio" %% "zio-prelude" % ZioPreludeVersion
  val zioTest = "dev.zio" %% "zio-test" % ZioVersion
  val zioTestSbt = "dev.zio" %% "zio-test-sbt" % ZioVersion

  // akka
  val akkaActor = "com.typesafe.akka" %% "akka-actor" % akkaVersion // Scala 3 compatible
  val akkaHttp = "com.typesafe.akka" %% "akka-http" % akkaHttpVersion // Scala 3 incompatible
  val akkaHttpCors = "ch.megard" %% "akka-http-cors" % "1.0.0" // Scala 3 incompatible
  val akkaHttpSprayJson = "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion // Scala 3 incompatible
  val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % akkaVersion // Scala 3 compatible
  val akkaStream = "com.typesafe.akka" %% "akka-stream" % akkaVersion // Scala 3 compatible

  // jena
  val jenaText = "org.apache.jena" % "jena-text" % jenaVersion

  // logging
  val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.2.11"
  val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4" // Scala 3 compatible

  // Metrics
  val aspectjweaver = "org.aspectj" % "aspectjweaver" % "1.9.4"
  val kamonCore = "io.kamon" %% "kamon-core" % "2.5.0" // Scala 3 compatible
  val kamonScalaFuture = "io.kamon" %% "kamon-scala-future" % "2.1.5" // Scala 3 incompatible

  // input validation
  val commonsValidator =
    "commons-validator" % "commons-validator" % "1.7" exclude ("commons-logging", "commons-logging")

  // authentication
  val jwtSprayJson = "com.pauldijou" %% "jwt-spray-json" % "5.0.0" // Scala 3 incompatible
  val springSecurityCore =
    "org.springframework.security" % "spring-security-core" % "5.6.2" exclude ("commons-logging", "commons-logging") exclude ("org.springframework", "spring-aop")

  // caching
  val ehcache = "net.sf.ehcache" % "ehcache" % "2.10.9.2"
  val jedis = "redis.clients" % "jedis" % "4.2.1"

  // serialization
  val chill = "com.twitter" %% "chill" % "0.10.0" // Scala 3 incompatible

  // other
  val diff = "com.sksamuel.diff" % "diff" % "1.1.11"
  val gwtServlet = "com.google.gwt" % "gwt-servlet" % "2.9.0"
  val icu4j = "com.ibm.icu" % "icu4j" % "70.1"
  val jakartaJSON = "org.glassfish" % "jakarta.json" % "2.0.1"
  val jodd = "org.jodd" % "jodd" % "3.2.6"
  val rdf4jClient = "org.eclipse.rdf4j" % "rdf4j-client" % "3.4.4"
  val rdf4jShacl = "org.eclipse.rdf4j" % "rdf4j-shacl" % "3.4.4"
  val saxonHE = "net.sf.saxon" % "Saxon-HE" % "11.3"
  val scalaGraph = "org.scala-graph" %% "graph-core" % "1.13.1" // Scala 3 incompatible
  val scallop = "org.rogach" %% "scallop" % "4.1.0" // Scala 3 compatible
  val swaggerAkkaHttp = "com.github.swagger-akka-http" %% "swagger-akka-http" % "1.6.0" // Scala 3 incompatible
  val titaniumJSONLD = "com.apicatalog" % "titanium-json-ld" % "1.2.0"
  val xmlunitCore = "org.xmlunit" % "xmlunit-core" % "2.9.0"

  // test
  val akkaHttpTestkit = "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion // Scala 3 incompatible
  val akkaStreamTestkit = "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion // Scala 3 compatible
  val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % akkaVersion // Scala 3 compatible
  val gatlingHighcharts = "io.gatling.highcharts" % "gatling-charts-highcharts" % "3.7.6"
  val gatlingTestFramework = "io.gatling" % "gatling-test-framework" % "3.7.6"
  val scalaTest = "org.scalatest" %% "scalatest" % "3.2.2" // Scala 3 compatible
  val testcontainers = "org.testcontainers" % "testcontainers" % "1.16.3"

  val webapiLibraryDependencies = Seq(
    akkaActor,
    akkaHttp,
    akkaHttpCors,
    akkaHttpSprayJson,
    akkaHttpTestkit % Test,
    akkaSlf4j % Runtime,
    akkaStream,
    akkaStreamTestkit % Test,
    akkaTestkit % Test,
    chill,
    commonsValidator,
    diff,
    ehcache,
    gatlingHighcharts % Test,
    gatlingTestFramework % Test,
    gwtServlet,
    icu4j,
    jakartaJSON,
    jedis,
    jenaText,
    jodd,
    jwtSprayJson,
    kamonCore,
    kamonScalaFuture,
    logbackClassic % Runtime,
    rdf4jClient % Test,
    rdf4jShacl,
    saxonHE,
    scalaGraph,
    scalaLogging,
    scalaTest % Test,
    scallop,
    springSecurityCore,
    swaggerAkkaHttp,
    testcontainers % Test,
    titaniumJSONLD,
    xmlunitCore % Test,
    zio,
    zioPrelude,
    zioTest % Test,
    zioTestSbt % Test
  )

  val dspApiMainLibraryDependencies = Seq(
    zio
  )

  val schemaApiLibraryDependencies = Seq(
    zioHttp
  )

  val schemaCoreLibraryDependencies = Seq(
    zioPrelude
  )

  val schemaRepoLibraryDependencies = Seq()
  val schemaRepoEventStoreServiceLibraryDependencies = Seq()
  val schemaRepoSearchServiceLibraryDependencies = Seq()
}
