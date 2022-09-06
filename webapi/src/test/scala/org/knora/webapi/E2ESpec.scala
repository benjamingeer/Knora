/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model._
import akka.testkit.TestKitBase
import com.typesafe.scalalogging._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import spray.json._
import zio.Runtime
import zio.ZIO
import zio.ZLayer
import zio._
import zio.logging.backend.SLF4J

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import dsp.errors.FileWriteException
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.TestStartupUtils
import org.knora.webapi.messages.store.sipimessages.SipiUploadResponse
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.settings._
import org.knora.webapi.testservices.FileToUpload
import org.knora.webapi.testservices.TestClientService
import org.knora.webapi.util.FileUtil

/**
 * This class can be used in End-to-End testing. It starts the Knora-API server
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
  type Environment = core.LayersTest.DefaultTestEnvironmentWithoutSipi

  /**
   * The effect layers from which the App is built.
   * Can be overriden in specs that need other implementations.
   */
  lazy val effectLayers = core.LayersTest.defaultLayersTestWithoutSipi

  /**
   * `Bootstrap` will ensure that everything is instantiated when the Runtime is created
   * and cleaned up when the Runtime is shutdown.
   */
  private val bootstrap: ZLayer[
    Any,
    Any,
    Environment
  ] = ZLayer.empty ++ Runtime.removeDefaultLoggers >>> SLF4J.slf4j ++ effectLayers

  // no idea why we need that, but we do
  private val environmentTag: EnvironmentTag[Environment] = EnvironmentTag[Environment]

  // add scope to bootstrap
  private val bootstrapWithScope = Scope.default >>>
    bootstrap +!+ ZLayer.environment[Scope]

  // maybe a configured runtime?
  val runtime = Unsafe.unsafe { implicit u =>
    Runtime.unsafe
      .fromLayer(bootstrapWithScope)
  }

  // An effect for getting stuff out, so that we can pass them
  // to some legacy code
  val routerAndConfig = for {
    router <- ZIO.service[core.AppRouter]
    config <- ZIO.service[AppConfig]
  } yield (router, config)

  /**
   * Create router and config by unsafe running them.
   */
  val (router, config) =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(
          routerAndConfig
        )
        .getOrThrowFiberFailure()
    }

  // this effect represents our application
  private val appServerTest =
    for {
      _ <- core.AppServer.start(false, false)
      _ <- prepareRepository(rdfDataObjects) // main difference to the live version
    } yield ()

  /* Here we start our main effect in a separate fiber */
  Unsafe.unsafe { implicit u =>
    runtime.unsafe.run(appServerTest)
  }

  implicit lazy val system: akka.actor.ActorSystem     = router.system
  implicit lazy val executionContext: ExecutionContext = system.dispatcher
  implicit lazy val settings: KnoraSettingsImpl        = KnoraSettings(system)
  lazy val rdfDataObjects                              = List.empty[RdfDataObject]
  val log: Logger                                      = Logger(this.getClass())
  val appActor                                         = router.ref

  // needed by some tests
  val routeData  = KnoraRouteData(system, appActor)
  val baseApiUrl = settings.internalKnoraApiBaseUrl

  final override def beforeAll(): Unit = {}
  // create temp data dir if not present
  // createTmpFileDir()

  // waits until knora is up and running
  // applicationStateRunning(appActor, system)

  // loadTestData
  // loadTestData(rdfDataObjects)

  final override def afterAll(): Unit =
    /* Stop ZIO runtime and release resources (e.g., running docker containers) */
    Unsafe.unsafe { implicit u =>
      runtime.unsafe.shutdown()
    }

  protected def loadTestData(rdfDataObjects: Seq[RdfDataObject]): Unit =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(
          for {
            testClient <- ZIO.service[TestClientService]
            result     <- testClient.loadTestData(rdfDataObjects)
          } yield result
        )
        .getOrThrowFiberFailure()
    }

  protected def singleAwaitingRequest(request: HttpRequest, duration: zio.Duration = 30.seconds): HttpResponse =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(
          for {
            testClient <- ZIO.service[TestClientService]
            result     <- testClient.singleAwaitingRequest(request, duration)
          } yield result
        )
        .getOrThrowFiberFailure()
    }

  protected def getResponseAsString(request: HttpRequest): String =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(
          for {
            testClient <- ZIO.service[TestClientService]
            result     <- testClient.getResponseString(request)
          } yield result
        )
        .getOrThrowFiberFailure()
    }

  protected def checkResponseOK(request: HttpRequest): Unit =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(
          for {
            testClient <- ZIO.service[TestClientService]
            result     <- testClient.checkResponseOK(request)
          } yield result
        )
        .getOrThrowFiberFailure()
    }

  protected def getResponseAsJson(request: HttpRequest): JsObject =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(
          for {
            testClient <- ZIO.service[TestClientService]
            result     <- testClient.getResponseJson(request)
          } yield result
        )
        .getOrThrowFiberFailure()
    }

  protected def getResponseAsJsonLD(request: HttpRequest): JsonLDDocument =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(
          for {
            testClient <- ZIO.service[TestClientService]
            result     <- testClient.getResponseJsonLD(request)
          } yield result
        )
        .getOrThrowFiberFailure()
    }

  protected def uploadToSipi(loginToken: String, filesToUpload: Seq[FileToUpload]): SipiUploadResponse =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(
          for {
            testClient <- ZIO.service[TestClientService]
            result     <- testClient.uploadToSipi(loginToken, filesToUpload)
          } yield result
        )
        .getOrThrowFiberFailure()
    }

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

  protected def parseTrig(trigStr: String): RdfModel = {
    val rdfFormatUtil: RdfFormatUtil = RdfFeatureFactory.getRdfFormatUtil()
    rdfFormatUtil.parseToRdfModel(rdfStr = trigStr, rdfFormat = TriG)
  }

  protected def parseTurtle(turtleStr: String): RdfModel = {
    val rdfFormatUtil: RdfFormatUtil = RdfFeatureFactory.getRdfFormatUtil()
    rdfFormatUtil.parseToRdfModel(rdfStr = turtleStr, rdfFormat = Turtle)
  }

  protected def parseRdfXml(rdfXmlStr: String): RdfModel = {
    val rdfFormatUtil: RdfFormatUtil = RdfFeatureFactory.getRdfFormatUtil()
    rdfFormatUtil.parseToRdfModel(rdfStr = rdfXmlStr, rdfFormat = RdfXml)
  }

  /**
   * Reads or writes a test data file.
   *
   * @param responseAsString the API response received from Knora.
   * @param file             the file in which the expected API response is stored.
   * @param writeFile        if `true`, writes the response to the file and returns it, otherwise returns the current contents of the file.
   * @return the expected response.
   */
  protected def readOrWriteTextFile(responseAsString: String, file: Path, writeFile: Boolean = false): String =
    if (writeFile) {
      val testOutputDir = Paths.get("..", "test_data", "ontologyR2RV2")
      val newOutputFile = testOutputDir.resolve(file)
      Files.createDirectories(newOutputFile.getParent)
      FileUtil.writeTextFile(
        newOutputFile,
        responseAsString.replaceAll(settings.externalSipiIIIFGetUrl, "IIIF_BASE_URL")
      )
      responseAsString
    } else {
      FileUtil.readTextFile(file).replaceAll("IIIF_BASE_URL", settings.externalSipiIIIFGetUrl)
    }

  private def createTmpFileDir(): Unit = {
    // check if tmp datadir exists and create it if not
    val tmpFileDir = Path.of(settings.tmpDataDir)

    if (!Files.exists(tmpFileDir)) {
      try {
        Files.createDirectories(tmpFileDir)
      } catch {
        case e: Throwable =>
          throw FileWriteException(s"Tmp data directory ${settings.tmpDataDir} could not be created: ${e.getMessage}")
      }
    }
  }
}
