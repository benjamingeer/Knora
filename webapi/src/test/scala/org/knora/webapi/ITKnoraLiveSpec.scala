/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model._
import akka.stream.Materializer
import akka.testkit.TestKit
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import com.typesafe.scalalogging.Logger
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Suite
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import spray.json._
import zio._

import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import org.knora.webapi.app.ApplicationActor
import org.knora.webapi.auth.JWTService
import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.AppConfigForTestContainers
import org.knora.webapi.core.Core
import org.knora.webapi.core.Logging
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.app.appmessages.AppStart
import org.knora.webapi.messages.app.appmessages.SetAllowReloadOverHTTPState
import org.knora.webapi.messages.store.sipimessages._
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.messages.util.rdf.JsonLDDocument
import org.knora.webapi.messages.util.rdf.JsonLDUtil
import org.knora.webapi.settings._
import org.knora.webapi.store.cache.CacheServiceManager
import org.knora.webapi.store.cache.impl.CacheServiceInMemImpl
import org.knora.webapi.store.iiif.IIIFServiceManager
import org.knora.webapi.store.iiif.impl.IIIFServiceSipiImpl
import org.knora.webapi.store.triplestore.TriplestoreServiceManager
import org.knora.webapi.store.triplestore.impl.TriplestoreServiceHttpConnectorImpl
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdater
import org.knora.webapi.testcontainers.FusekiTestContainer
import org.knora.webapi.testcontainers.SipiTestContainer
import org.knora.webapi.testservices.FileToUpload
import org.knora.webapi.testservices.TestActorSystemService
import org.knora.webapi.testservices.TestClientService
import org.knora.webapi.util.StartupUtils

object ITKnoraLiveSpec {
  val defaultConfig: Config = ConfigFactory.load()
}

/**
 * This class can be used in End-to-End testing. It starts the Knora server and
 * provides access to settings and logging.
 */
abstract class ITKnoraLiveSpec(_system: ActorSystem)
    extends TestKit(_system)
    with Core
    with StartupUtils
    with Suite
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with RequestBuilding
    with TriplestoreJsonProtocol
    with LazyLogging {

  /* constructors */
  def this(name: String, config: Config) =
    this(
      ActorSystem(name, config.withFallback(ITKnoraLiveSpec.defaultConfig))
    )

  def this(config: Config) =
    this(
      ActorSystem(
        "IntegrationTests",
        config.withFallback(ITKnoraLiveSpec.defaultConfig)
      )
    )
  def this(name: String) =
    this(ActorSystem(name, ITKnoraLiveSpec.defaultConfig))

  def this() =
    this(ActorSystem("IntegrationTests", ITKnoraLiveSpec.defaultConfig))

  /* needed by the core trait (represents the KnoraTestCore trait)*/
  implicit lazy val settings: KnoraSettingsImpl   = KnoraSettings(system)
  implicit val materializer: Materializer         = Materializer.matFromSystem(system)
  implicit val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)

  // can be overridden in individual spec
  lazy val rdfDataObjects = Seq.empty[RdfDataObject]

  /* Needs to be initialized before any responders */
  StringFormatter.initForTest()
  val log: Logger = Logger(this.getClass)

  /**
   * The effect for building a cache service manager, a IIIF service manager,
   * and the sipi test client.
   */
  lazy val managers = for {
    csm       <- ZIO.service[CacheServiceManager]
    iiifsm    <- ZIO.service[IIIFServiceManager]
    tssm      <- ZIO.service[TriplestoreServiceManager]
    appConfig <- ZIO.service[AppConfig]
  } yield (csm, iiifsm, tssm, appConfig)

  /**
   * The effect layers which will be used to run the managers effect.
   * Can be overriden in specs that need other implementations.
   */
  lazy val effectLayers =
    ZLayer.make[CacheServiceManager & IIIFServiceManager & TriplestoreServiceManager & AppConfig & TestClientService](
      CacheServiceManager.layer,
      CacheServiceInMemImpl.layer,
      IIIFServiceManager.layer,
      IIIFServiceSipiImpl.layer, // alternative: MockSipiImpl.layer
      AppConfigForTestContainers.testcontainers,
      JWTService.layer,
      SipiTestContainer.layer,
      TriplestoreServiceManager.layer,
      TriplestoreServiceHttpConnectorImpl.layer,
      RepositoryUpdater.layer,
      FusekiTestContainer.layer,
      Logging.slf4j,
      TestClientService.layer,
      TestActorSystemService.layer
    )

  // The ZIO runtime used to run functional effects
  lazy val runtime =
    Unsafe.unsafe { implicit u =>
      Runtime.unsafe.fromLayer(effectLayers ++ Runtime.removeDefaultLoggers)
    }

  /**
   * Create both managers and the sipi client by unsafe running them.
   */
  val (cacheServiceManager, iiifServiceManager, triplestoreServiceManager, appConfig) =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe.run(managers).getOrElse(c => throw FiberFailure(c))
    }

  // start the Application Actor
  lazy val appActor: ActorRef =
    system.actorOf(
      Props(new ApplicationActor(cacheServiceManager, iiifServiceManager, triplestoreServiceManager, appConfig)),
      name = APPLICATION_MANAGER_ACTOR_NAME
    )

  protected val baseApiUrl: String          = appConfig.knoraApi.internalKnoraApiBaseUrl
  protected val baseInternalSipiUrl: String = appConfig.sipi.internalBaseUrl
  protected val baseExternalSipiUrl: String = appConfig.sipi.externalBaseUrl

  final override def beforeAll(): Unit = {

    // set allow reload over http
    appActor ! SetAllowReloadOverHTTPState(true)

    // Start Knora, reading data from the repository
    appActor ! AppStart(ignoreRepository = true, requiresIIIFService = true)

    // waits until knora is up and running
    applicationStateRunning()

    // loadTestData
    loadTestData(rdfDataObjects)
  }

  final override def afterAll(): Unit = {

    /* Stop ZIO runtime and release resources (e.g., running docker containers) */
    Unsafe.unsafe { implicit u =>
      runtime.unsafe.shutdown()
    }

    /* Stop the server when everything else has finished */
    TestKit.shutdownActorSystem(system)

  }

  protected def loadTestData(rdfDataObjects: Seq[RdfDataObject]): Unit =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe.run(
        (for {
          testClient <- ZIO.service[TestClientService]
          result     <- testClient.loadTestData(rdfDataObjects)
        } yield result)
      )
    }

  protected def getResponseStringOrThrow(request: HttpRequest): String =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(
          (for {
            testClient <- ZIO.service[TestClientService]
            result     <- testClient.getResponseString(request)
          } yield result)
        )
        .getOrElse(c => throw FiberFailure(c))
    }

  protected def checkResponseOK(request: HttpRequest): Unit =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe.run(
        (for {
          testClient <- ZIO.service[TestClientService]
          result     <- testClient.checkResponseOK(request)
        } yield result)
      )
    }

  protected def getResponseJson(request: HttpRequest): JsObject =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(
          (for {
            testClient <- ZIO.service[TestClientService]
            result     <- testClient.getResponseJson(request)
          } yield result)
        )
        .getOrElse(c => throw FiberFailure(c))
    }

  protected def singleAwaitingRequest(request: HttpRequest, duration: zio.Duration = 15.seconds): HttpResponse =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(
          (for {
            testClient <- ZIO.service[TestClientService]
            result     <- testClient.singleAwaitingRequest(request, duration)
          } yield result)
        )
        .getOrElse(c => throw FiberFailure(c))
    }

  protected def getResponseJsonLD(request: HttpRequest): JsonLDDocument =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(
          (for {
            testClient <- ZIO.service[TestClientService]
            result     <- testClient.getResponseJsonLD(request)
          } yield result)
        )
        .getOrElse(c => throw FiberFailure(c))
    }

  protected def uploadToSipi(loginToken: String, filesToUpload: Seq[FileToUpload]): SipiUploadResponse =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(
          (for {
            testClient <- ZIO.service[TestClientService]
            result     <- testClient.uploadToSipi(loginToken, filesToUpload)
          } yield result)
        )
        .getOrThrow()
    }

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
