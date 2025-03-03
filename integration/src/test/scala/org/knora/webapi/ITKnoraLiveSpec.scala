/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import com.typesafe.scalalogging.LazyLogging
import com.typesafe.scalalogging.Logger
import org.apache.pekko
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.client.RequestBuilding
import org.apache.pekko.http.scaladsl.model.*
import org.apache.pekko.testkit.TestKitBase
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import zio.*
import zio.json.ast.Json

import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.AppServer
import org.knora.webapi.core.LayersTestLive
import org.knora.webapi.core.TestStartupUtils
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.messages.util.rdf.JsonLDDocument
import org.knora.webapi.messages.util.rdf.JsonLDUtil
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.testservices.TestClientService
import org.knora.webapi.testservices.TestDspIngestClient
import org.knora.webapi.testservices.TestDspIngestClient.UploadedFile
import org.knora.webapi.util.LogAspect

/**
 * This class can be used in End-to-End testing. It starts the DSP stack and
 * provides access to configuration and logging.
 */
abstract class ITKnoraLiveSpec
    extends AnyWordSpec
    with TestKitBase
    with TestStartupUtils
    with Matchers
    with BeforeAndAfterAll
    with RequestBuilding
    with TriplestoreJsonProtocol
    with LazyLogging {

  /**
   * The `Environment` that we require to exist at startup.
   * Can be overriden in specs that need other implementations.
   */
  type Environment = LayersTestLive.Environment

  /**
   * The effect layers from which the App is built.
   * Can be overriden in specs that need other implementations.
   */
  lazy val effectLayers = LayersTestLive.layer

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
  val baseApiUrl: String          = appConfig.knoraApi.internalKnoraApiBaseUrl
  val baseInternalSipiUrl: String = appConfig.sipi.internalBaseUrl

  final override def beforeAll(): Unit =
    /* Here we start our app and initialize the repository before each suit runs */
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(
          for {
            _ <- AppServer.test
            _ <- prepareRepository(rdfDataObjects) @@ LogAspect.logSpan("prepare-repo")
          } yield (),
        )
        .getOrThrow()
    }

  final override def afterAll(): Unit =
    /* Stop ZIO runtime and release resources (e.g., running docker containers) */
    Unsafe.unsafe { implicit u =>
      runtime.unsafe.shutdown()
    }

  protected def getResponseStringOrThrow(request: HttpRequest): String =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(
          for {
            testClient <- ZIO.service[TestClientService]
            result     <- testClient.getResponseString(request)
          } yield result,
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
          } yield result,
        )
        .getOrThrowFiberFailure()
    }

  protected def getResponseJson(request: HttpRequest): Json.Obj =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(
          for {
            testClient <- ZIO.service[TestClientService]
            result     <- testClient.getResponseJson(request)
          } yield result,
        )
        .getOrThrowFiberFailure()
    }

  protected def singleAwaitingRequest(request: HttpRequest): HttpResponse =
    UnsafeZioRun.runOrThrow(ZIO.serviceWithZIO[TestClientService](_.singleAwaitingRequest(request)))

  protected def singleAwaitingRequest(request: HttpRequest, duration: zio.Duration): HttpResponse =
    UnsafeZioRun.runOrThrow(ZIO.serviceWithZIO[TestClientService](_.singleAwaitingRequest(request, Some(duration))))

  protected def getResponseJsonLD(request: HttpRequest): JsonLDDocument =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(
          for {
            testClient <- ZIO.service[TestClientService]
            result     <- testClient.getResponseJsonLD(request)
          } yield result,
        )
        .getOrThrowFiberFailure()
    }

  protected def uploadToIngest(fileToUpload: java.nio.file.Path): UploadedFile =
    UnsafeZioRun.runOrThrow(ZIO.serviceWithZIO[TestDspIngestClient](_.uploadFile(fileToUpload)))

  protected def responseToString(httpResponse: HttpResponse): String = {
    val responseBodyFuture: Future[String] =
      httpResponse.entity.toStrict(FiniteDuration(10L, TimeUnit.SECONDS)).map(_.data.decodeString("UTF-8"))
    Await.result(responseBodyFuture, FiniteDuration(10L, TimeUnit.SECONDS))
  }

  protected def responseToJsonLDDocument(httpResponse: HttpResponse): JsonLDDocument = {
    val responseBodyFuture: Future[String] =
      httpResponse.entity.toStrict(FiniteDuration(10L, TimeUnit.SECONDS)).map(_.data.decodeString("UTF-8"))
    val responseBodyStr = Await.result(responseBodyFuture, FiniteDuration(10L, TimeUnit.SECONDS))
    JsonLDUtil.parseJsonLD(responseBodyStr)
  }

}
