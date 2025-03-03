/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import com.typesafe.scalalogging.Logger
import org.apache.pekko.http.scaladsl.model.HttpResponse
import org.apache.pekko.http.scaladsl.testkit.RouteTestTimeout
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import zio.*

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.NANOSECONDS

import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.AppServer
import org.knora.webapi.core.TestStartupUtils
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.rdf.*
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.util.FileUtil
import org.knora.webapi.util.LogAspect

/**
 * R(oute)2R(esponder) Spec base class. Please, for any new E2E tests, use E2ESpec.
 */
abstract class R2RSpec
    extends AnyWordSpec
    with TestStartupUtils
    with ScalatestRouteTest
    with Matchers
    with BeforeAndAfterAll {

  /**
   * The `Environment` that we require to exist at startup.
   * Can be overridden in specs that need other implementations.
   */
  type Environment = core.LayersTestMock.Environment

  /**
   * The effect layers from which the App is built.
   * Can be overridden in specs that need other implementations.
   */
  lazy val effectLayers: ULayer[Environment] = core.LayersTestMock.layer(Some(system))

  /**
   * `Bootstrap` will ensure that everything is instantiated when the Runtime is created
   * and cleaned up when the Runtime is shutdown.
   */
  private val bootstrap = util.Logger.text() >>> effectLayers

  // create a configured runtime
  implicit val runtime: Runtime.Scoped[Environment] = Unsafe.unsafe(implicit u => Runtime.unsafe.fromLayer(bootstrap))

  // the default timeout for route tests
  implicit def default: RouteTestTimeout = RouteTestTimeout(
    FiniteDuration(appConfig.defaultTimeout.toNanos, NANOSECONDS),
  )

  // main difference to other specs (no own systen and executionContext defined)
  lazy val rdfDataObjects  = List.empty[RdfDataObject]
  val log: Logger          = Logger(this.getClass)
  val appConfig: AppConfig = UnsafeZioRun.service[AppConfig]

  final override def beforeAll(): Unit =
    /* Here we start our app and initialize the repository before each suit runs */
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(AppServer.test *> prepareRepository(rdfDataObjects) @@ LogAspect.logSpan("prepare-repo"))
        .getOrThrow()
    }

  final override def afterAll(): Unit = {
    /* Stop ZIO runtime and release resources (e.g., running docker containers) */
    Unsafe.unsafe { implicit u =>
      runtime.unsafe.shutdown()
    }
    system.terminate()
    ()
  }

  protected def responseToJsonLDDocument(httpResponse: HttpResponse): JsonLDDocument = {
    val responseBodyFuture: Future[String] =
      httpResponse.entity
        .toStrict(scala.concurrent.duration.Duration(5.toLong, TimeUnit.SECONDS))
        .map(_.data.decodeString("UTF-8"))
    val responseBodyStr =
      Await.result(responseBodyFuture, scala.concurrent.duration.Duration(5.toLong, TimeUnit.SECONDS))
    JsonLDUtil.parseJsonLD(responseBodyStr)
  }

  protected def parseTurtle(turtleStr: String): RdfModel =
    RdfFormatUtil.parseToRdfModel(rdfStr = turtleStr, rdfFormat = Turtle)

  protected def parseRdfXml(rdfXmlStr: String): RdfModel =
    RdfFormatUtil.parseToRdfModel(rdfStr = rdfXmlStr, rdfFormat = RdfXml)

  private def adjustFilePath(file: Path): Path =
    Paths.get("..", "test_data", "generated_test_data").resolve(file).normalize()

  protected def readTestData(file: Path): String =
    FileUtil.readTextFile(adjustFilePath(file)).replaceAll("IIIF_BASE_URL", appConfig.sipi.externalBaseUrl)

  protected def writeTestData(responseAsString: String, file: Path): String = {
    val adjustedFile = adjustFilePath(file)
    Files.createDirectories(adjustedFile.getParent)
    FileUtil.writeTextFile(
      adjustedFile,
      responseAsString.replaceAll(appConfig.sipi.externalBaseUrl, "IIIF_BASE_URL"),
    )
    responseAsString
  }
}
