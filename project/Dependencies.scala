/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora

import sbt._

object Dependencies {
  // should be the same version as in docker-compose.yml,
  // make sure to use the same version in ops-deploy repository when deploying new DSP releases!
  val fusekiImage = "daschswiss/apache-jena-fuseki:5.2.0"
  // base image the knora-sipi image is created from
  val sipiImage = "daschswiss/sipi:v3.15.0"

  val ScalaVersion = "3.3.5"

  val PekkoActorVersion = "1.1.3"
  val PekkoHttpVersion  = "1.1.0"

  val MonocleVersion = "3.3.0"

  val Rdf4jVersion         = "5.1.2"
  val TopbraidShaclVersion = "1.4.4"
  val JenaVersion          = "5.2.0" // should be aligned with the version topbraid-shacl uses

  val ZioConfigVersion            = "4.0.3"
  val ZioLoggingVersion           = "2.4.0"
  val ZioNioVersion               = "2.0.2"
  val ZioMetricsConnectorsVersion = "2.3.1"
  val ZioPreludeVersion           = "1.0.0-RC37"
  val ZioSchemaVersion            = "0.2.0"
  val ZioVersion                  = "2.1.15"

  // ZIO
  val zio               = "dev.zio" %% "zio"                 % ZioVersion
  val zioConfig         = "dev.zio" %% "zio-config"          % ZioConfigVersion
  val zioConfigMagnolia = "dev.zio" %% "zio-config-magnolia" % ZioConfigVersion
  val zioConfigTypesafe = "dev.zio" %% "zio-config-typesafe" % ZioConfigVersion

  // jwt-scala 10.0.1 depends on zio-json 0.7.4
  // though newer version compatible with a newer zio-json version is available
  // it is not yet published: https://github.com/jwt-scala/jwt-scala/issues/642
  val ZioJsonVersion        = "0.7.4"
  val zioJson               = "dev.zio"                       %% "zio-json"                  % ZioJsonVersion
  val zioLogging            = "dev.zio"                       %% "zio-logging"               % ZioLoggingVersion
  val zioLoggingSlf4jBridge = "dev.zio"                       %% "zio-logging-slf4j2-bridge" % ZioLoggingVersion
  val zioNio                = "dev.zio"                       %% "zio-nio"                   % ZioNioVersion
  val zioMacros             = "dev.zio"                       %% "zio-macros"                % ZioVersion
  val zioPrelude            = "dev.zio"                       %% "zio-prelude"               % ZioPreludeVersion
  val zioSttp               = "com.softwaremill.sttp.client3" %% "zio"                       % "3.10.3"

  // refined
  val refined = Seq(
    "eu.timepit" %% "refined"                  % "0.11.3",
    "dev.zio"    %% "zio-json-interop-refined" % ZioJsonVersion,
  )

  // monocle
  val monocle = Seq(
    "dev.optics" %% "monocle-core"    % MonocleVersion,
    "dev.optics" %% "monocle-macro"   % MonocleVersion,
    "dev.optics" %% "monocle-refined" % MonocleVersion,
  )

  // zio-test and friends
  val zioTest    = "dev.zio" %% "zio-test"     % ZioVersion
  val zioTestSbt = "dev.zio" %% "zio-test-sbt" % ZioVersion

  // pekko
  val pekkoActor         = "org.apache.pekko" %% "pekko-actor"           % PekkoActorVersion
  val pekkoHttp          = "org.apache.pekko" %% "pekko-http"            % PekkoHttpVersion
  val pekkoHttpCors      = "org.apache.pekko" %% "pekko-http-cors"       % PekkoHttpVersion
  val pekkoHttpSprayJson = "org.apache.pekko" %% "pekko-http-spray-json" % PekkoHttpVersion
  val pekkoSlf4j         = "org.apache.pekko" %% "pekko-slf4j"           % PekkoActorVersion
  val pekkoStream        = "org.apache.pekko" %% "pekko-stream"          % PekkoActorVersion

  // rdf and graph libraries
  val jenaCore      = "org.apache.jena"   % "jena-core"           % JenaVersion
  val jenaText      = "org.apache.jena"   % "jena-text"           % JenaVersion
  val rdf4jClient   = "org.eclipse.rdf4j" % "rdf4j-client"        % Rdf4jVersion
  val rdf4jShacl    = "org.eclipse.rdf4j" % "rdf4j-shacl"         % Rdf4jVersion
  val rdf4jSparql   = "org.eclipse.rdf4j" % "rdf4j-sparqlbuilder" % Rdf4jVersion
  val topbraidShacl = "org.topbraid"      % "shacl"               % TopbraidShaclVersion

  // logging
  val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5"
  val slf4jApi     = "org.slf4j"                   % "slf4j-api"     % "2.0.16" // the logging interface

  // Metrics
  val aspectjweaver = "org.aspectj" % "aspectjweaver" % "1.9.22.1"

  // input validation
  val commonsValidator =
    "commons-validator" % "commons-validator" % "1.9.0" exclude ("commons-logging", "commons-logging")

  // authentication
  val jwtSprayJson = "com.github.jwt-scala" %% "jwt-zio-json" % "10.0.4"
  // jwtSprayJson -> 9.0.2 is the latest version that's compatible with spray-json; if it wasn't for spray, this would be Scala 3 compatible
  val springSecurityCore =
    "org.springframework.security" % "spring-security-core" % "6.4.2" exclude (
      "commons-logging",
      "commons-logging",
    ) exclude ("org.springframework", "spring-aop")
  val bouncyCastle = "org.bouncycastle" % "bcprov-jdk15to18" % "1.80"

  // caching
  val ehcache = "org.ehcache" % "ehcache" % "3.10.8"

  // other
  val diff           = "com.sksamuel.diff" % "diff"             % "1.1.11"
  val gwtServlet     = "com.google.gwt"    % "gwt-servlet"      % "2.10.0"
  val icu4j          = "com.ibm.icu"       % "icu4j"            % "76.1"
  val jakartaJSON    = "org.glassfish"     % "jakarta.json"     % "2.0.1"
  val saxonHE        = "net.sf.saxon"      % "Saxon-HE"         % "12.5"
  val scalaGraph     = "org.scala-graph"  %% "graph-core"       % "2.0.2"
  val titaniumJSONLD = "com.apicatalog"    % "titanium-json-ld" % "1.4.1"
  val xmlunitCore    = "org.xmlunit"       % "xmlunit-core"     % "2.10.0"

  // test
  val pekkoHttpTestkit   = "org.apache.pekko" %% "pekko-http-testkit"   % PekkoHttpVersion
  val pekkoStreamTestkit = "org.apache.pekko" %% "pekko-stream-testkit" % PekkoActorVersion
  val pekkoTestkit       = "org.apache.pekko" %% "pekko-testkit"        % PekkoActorVersion
  val scalaTest          = "org.scalatest"    %% "scalatest"            % "3.2.19"

  val testcontainers = "org.testcontainers" % "testcontainers" % "1.20.4"

  val wiremock = "org.wiremock" % "wiremock" % "3.11.0"

  // found/added by the plugin but deleted anyway
  val commonsLang3 = "org.apache.commons" % "commons-lang3" % "3.17.0"

  val tapirVersion = "1.11.14"

  val tapir = Seq(
    "com.softwaremill.sttp.tapir" %% "tapir-pekko-http-server" % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-zio-http-server"   % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-json-zio"          % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-json-spray"        % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-refined"           % tapirVersion,
  )
  val metrics = Seq(
    "dev.zio"                     %% "zio-metrics-connectors"            % ZioMetricsConnectorsVersion,
    "dev.zio"                     %% "zio-metrics-connectors-prometheus" % ZioMetricsConnectorsVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-zio-metrics"                 % tapirVersion,
  )

  val integrationTestDependencies = Seq(
    pekkoHttpTestkit,
    pekkoStreamTestkit,
    pekkoTestkit,
    rdf4jClient,
    scalaTest,
    testcontainers,
    wiremock,
    xmlunitCore,
    zioTest,
    zioTestSbt,
  ).map(_ % Test)

  val webapiTestDependencies = Seq(zioTest, zioTestSbt, wiremock).map(_ % Test)

  val webapiDependencies = monocle ++ refined ++ Seq(
    pekkoActor,
    pekkoHttp,
    pekkoHttpCors,
    pekkoHttpSprayJson,
    pekkoSlf4j,
    pekkoStream,
    bouncyCastle,
    commonsLang3,
    commonsValidator,
    diff,
    ehcache,
    gwtServlet,
    icu4j,
    jakartaJSON,
    jenaText,
    jwtSprayJson,
    rdf4jShacl,
    rdf4jSparql,
    saxonHE,
    scalaGraph,
    scalaLogging,
    slf4jApi,
    springSecurityCore,
    titaniumJSONLD,
    topbraidShacl,
    zio,
    zioConfig,
    zioConfigMagnolia,
    zioConfigTypesafe,
    zioJson,
    zioLogging,
    zioLoggingSlf4jBridge,
    zioMacros,
    zioNio,
    zioPrelude,
    zioSttp,
  ) ++ metrics ++ tapir
}
