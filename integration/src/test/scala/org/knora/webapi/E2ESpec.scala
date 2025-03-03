/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import com.typesafe.scalalogging.*
import org.apache.pekko
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.client.RequestBuilding
import org.apache.pekko.http.scaladsl.model.*
import org.apache.pekko.testkit.TestKitBase
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import zio.*
import zio.json.*
import zio.json.ast.Json

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import dsp.errors.AssertionException
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.AppServer
import org.knora.webapi.core.TestStartupUtils
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.messages.util.rdf.*
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.testservices.TestClientService
import org.knora.webapi.util.FileUtil
import org.knora.webapi.util.LogAspect

/**
 * This class can be used in End-to-End testing. It starts the DSP stack
 * and provides access to settings and logging.
 */
abstract class E2ESpec
    extends AnyWordSpec
    with TestKitBase
    with TestStartupUtils
    with TriplestoreJsonProtocol
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll
    with RequestBuilding {

  /**
   * The `Environment` that we require to exist at startup.
   * Can be overriden in specs that need other implementations.
   */
  type Environment = core.LayersTestLive.Environment

  /**
   * The effect layers from which the App is built.
   * Can be overriden in specs that need other implementations.
   */
  lazy val effectLayers = core.LayersTestLive.layer

  /**
   * `Bootstrap` will ensure that everything is instantiated when the Runtime is created
   * and cleaned up when the Runtime is shutdown.
   */
  private val bootstrap = util.Logger.text() >>> effectLayers

  // create a configured runtime
  implicit val runtime: Runtime.Scoped[Environment] =
    Unsafe.unsafe(implicit u => Runtime.unsafe.fromLayer(bootstrap))

  lazy val appConfig: AppConfig                        = UnsafeZioRun.service[AppConfig]
  implicit lazy val system: ActorSystem                = UnsafeZioRun.service[ActorSystem]
  implicit lazy val executionContext: ExecutionContext = system.dispatcher
  lazy val rdfDataObjects                              = List.empty[RdfDataObject]
  val log: Logger                                      = Logger(this.getClass)

  // needed by some tests
  val baseApiUrl = appConfig.knoraApi.internalKnoraApiBaseUrl

  final override def beforeAll(): Unit =
    /* Here we start our app and initialize the repository before each suit runs */
    UnsafeZioRun.runOrThrow(AppServer.test *> (prepareRepository(rdfDataObjects) @@ LogAspect.logSpan("prepare-repo")))

  final override def afterAll(): Unit =
    /* Stop ZIO runtime and release resources (e.g., running docker containers) */
    Unsafe.unsafe(implicit u => runtime.unsafe.shutdown())

  protected def singleAwaitingRequest(
    request: HttpRequest,
    timeout: Option[zio.Duration] = None,
    printFailure: Boolean = true,
  ): HttpResponse =
    UnsafeZioRun.runOrThrow(
      ZIO.serviceWithZIO[TestClientService](_.singleAwaitingRequest(request, timeout, printFailure)),
    )

  protected def singleAwaitingRequest(request: HttpRequest, duration: zio.Duration): HttpResponse =
    UnsafeZioRun.runOrThrow(ZIO.serviceWithZIO[TestClientService](_.singleAwaitingRequest(request, Some(duration))))

  protected def getResponseAsString(request: HttpRequest): String =
    UnsafeZioRun.runOrThrow(ZIO.serviceWithZIO[TestClientService](_.getResponseString(request)))

  protected def checkResponseOK(request: HttpRequest): Unit =
    UnsafeZioRun.runOrThrow(ZIO.serviceWithZIO[TestClientService](_.checkResponseOK(request)))

  protected def getSuccessResponseAs[A](request: HttpRequest)(implicit decoder: JsonDecoder[A]): A = UnsafeZioRun
    .runOrThrow(
      for {
        str <- ZIO.serviceWithZIO[TestClientService](_.getResponseString(request))
        obj <- ZIO
                 .fromEither(str.fromJson[A])
                 .mapError(e => new AssertionException(s"Error: $e\nFailed to parse json:\n$str"))
      } yield obj,
    )

  protected def getResponseAsJson(request: HttpRequest): Json.Obj =
    UnsafeZioRun.runOrThrow(ZIO.serviceWithZIO[TestClientService](_.getResponseJson(request)))

  protected def getResponseAsJsonLD(request: HttpRequest): JsonLDDocument =
    UnsafeZioRun.runOrThrow(ZIO.serviceWithZIO[TestClientService](_.getResponseJsonLD(request)))

  protected def responseToJsonLDDocument(httpResponse: HttpResponse): JsonLDDocument = {
    val responseBodyFuture: Future[String] =
      httpResponse.entity.toStrict(FiniteDuration(10L, TimeUnit.SECONDS)).map(_.data.decodeString("UTF-8"))
    val responseBodyStr = Await.result(responseBodyFuture, FiniteDuration(10L, TimeUnit.SECONDS))
    JsonLDUtil.parseJsonLD(responseBodyStr)
  }

  protected def responseToString(httpResponse: HttpResponse): String = {
    val responseBodyFuture: Future[String] =
      httpResponse.entity.toStrict(FiniteDuration(10L, TimeUnit.SECONDS)).map(_.data.decodeString("UTF-8"))
    Await.result(responseBodyFuture, FiniteDuration(10L, TimeUnit.SECONDS))
  }

  protected def doGetRequest(urlPath: String): String = {

    val request                = Get(s"$baseApiUrl$urlPath")
    val response: HttpResponse = singleAwaitingRequest(request)
    responseToString(response)
  }

  protected def parseTrig(trigStr: String): RdfModel =
    RdfFormatUtil.parseToRdfModel(rdfStr = trigStr, rdfFormat = TriG)

  protected def parseTurtle(turtleStr: String): RdfModel =
    RdfFormatUtil.parseToRdfModel(rdfStr = turtleStr, rdfFormat = Turtle)

  protected def parseRdfXml(rdfXmlStr: String): RdfModel =
    RdfFormatUtil.parseToRdfModel(rdfStr = rdfXmlStr, rdfFormat = RdfXml)

  protected def parseJsonLd(rdfXmlStr: String): RdfModel =
    RdfFormatUtil.parseToRdfModel(rdfStr = rdfXmlStr, rdfFormat = JsonLD)

  /**
   * Reads or writes a test data file.
   *
   * @param responseAsString the API response received from Knora.
   * @param file             the file in which the expected API response is stored.
   * @param writeFile        if `true`, writes the response to the file and returns it, otherwise returns the current contents of the file.
   * @return the expected response.
   */
  @deprecated("Use readTestData() and writeTestData() instead")
  protected def readOrWriteTextFile(responseAsString: String, file: Path, writeFile: Boolean = false): String = {
    val adjustedFile = adjustFilePath(file)
    if (writeFile) {
      Files.createDirectories(adjustedFile.getParent)
      FileUtil.writeTextFile(
        adjustedFile,
        responseAsString.replaceAll(appConfig.sipi.externalBaseUrl, "IIIF_BASE_URL"),
      )
      responseAsString
    } else {
      FileUtil.readTextFile(adjustedFile).replaceAll("IIIF_BASE_URL", appConfig.sipi.externalBaseUrl)
    }
  }

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
