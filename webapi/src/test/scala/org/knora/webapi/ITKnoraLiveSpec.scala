/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.event.LoggingAdapter
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model._
import akka.stream.Materializer
import akka.testkit.TestKit
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
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
import org.knora.webapi.messages.util.rdf.RdfFeatureFactory
import org.knora.webapi.settings._
import org.knora.webapi.store.cacheservice.CacheServiceManager
import org.knora.webapi.store.cacheservice.impl.CacheServiceInMemImpl
import org.knora.webapi.store.iiif.IIIFServiceManager
import org.knora.webapi.store.iiif.impl.IIIFServiceSipiImpl
import org.knora.webapi.testcontainers.SipiTestContainer
import org.knora.webapi.testservices.FileToUpload
import org.knora.webapi.testservices.TestClientService
import org.knora.webapi.util.StartupUtils
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Suite
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import spray.json._
import zio.&
import zio.Runtime
import zio.ZEnvironment
import zio.ZIO
import zio.ZLayer
import zio._

import scala.concurrent.ExecutionContext

object ITKnoraLiveSpec {
  val defaultConfig: Config = ConfigFactory.load()
}

/**
 * This class can be used in End-to-End testing. It starts the Knora server and
 * provides access to settings and logging.
 */
class ITKnoraLiveSpec(_system: ActorSystem)
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
      ActorSystem(name, TestContainerFuseki.PortConfig.withFallback(config.withFallback(ITKnoraLiveSpec.defaultConfig)))
    )
  def this(config: Config) =
    this(
      ActorSystem(
        "IntegrationTests",
        TestContainerFuseki.PortConfig.withFallback(config.withFallback(ITKnoraLiveSpec.defaultConfig))
      )
    )
  def this(name: String) =
    this(ActorSystem(name, TestContainerFuseki.PortConfig.withFallback(ITKnoraLiveSpec.defaultConfig)))
  def this() =
    this(ActorSystem("IntegrationTests", TestContainerFuseki.PortConfig.withFallback(ITKnoraLiveSpec.defaultConfig)))

  /* needed by the core trait (represents the KnoraTestCore trait)*/
  implicit lazy val settings: KnoraSettingsImpl   = KnoraSettings(system)
  implicit val materializer: Materializer         = Materializer.matFromSystem(system)
  implicit val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)

  // can be overridden in individual spec
  lazy val rdfDataObjects = Seq.empty[RdfDataObject]

  /* Needs to be initialized before any responders */
  StringFormatter.initForTest()
  RdfFeatureFactory.init(settings)

  val log: LoggingAdapter = akka.event.Logging(system, this.getClass)

  // The ZIO runtime used to run functional effects
  val runtime = Runtime.unsafeFromLayer(Logging.fromInfo)

  /**
   * The effect for building a cache service manager, a IIIF service manager,
   * and the sipi test client.
   */
  val managers = for {
    csm       <- ZIO.service[CacheServiceManager]
    iiifsm    <- ZIO.service[IIIFServiceManager]
    appConfig <- ZIO.service[AppConfig]
  } yield (csm, iiifsm, appConfig)

  /**
   * The effect layers which will be used to run the managers effect.
   * Can be overriden in specs that need other implementations.
   */
  val effectLayers =
    ZLayer.make[CacheServiceManager & IIIFServiceManager & AppConfig](
      CacheServiceManager.layer,
      CacheServiceInMemImpl.layer,
      IIIFServiceManager.layer,
      IIIFServiceSipiImpl.layer, // alternative: MockSipiImpl.layer
      AppConfigForTestContainers.testcontainers,
      JWTService.layer,
      // FusekiTestContainer.layer,
      SipiTestContainer.layer
    )

  /**
   * Create both managers and the sipi client by unsafe running them.
   */
  val (cacheServiceManager, iiifServiceManager, appConfig) =
    runtime
      .unsafeRun(
        managers
          .provide(
            effectLayers
          )
      )

  // start the Application Actor
  lazy val appActor: ActorRef =
    system.actorOf(
      Props(new ApplicationActor(cacheServiceManager, iiifServiceManager, appConfig)),
      name = APPLICATION_MANAGER_ACTOR_NAME
    )

  protected val baseApiUrl: String          = appConfig.knoraApi.internalKnoraApiBaseUrl
  protected val baseInternalSipiUrl: String = appConfig.sipi.internalBaseUrl
  protected val baseExternalSipiUrl: String = appConfig.sipi.externalBaseUrl

  override def beforeAll(): Unit = {

    // set allow reload over http
    appActor ! SetAllowReloadOverHTTPState(true)

    // Start Knora, reading data from the repository
    appActor ! AppStart(ignoreRepository = true, requiresIIIFService = true)

    // waits until knora is up and running
    applicationStateRunning()

    // loadTestData
    loadTestData(rdfDataObjects)
  }

  override def afterAll(): Unit = {
    // turn on/off in logback-test.xml
    log.debug(TestContainersAll.SipiContainer.getLogs())
    /* Stop the server when everything else has finished */
    TestKit.shutdownActorSystem(system)
  }

  protected def loadTestData(rdfDataObjects: Seq[RdfDataObject]): Unit =
    runtime.unsafeRunTask(
      (for {
        testClient <- ZIO.service[TestClientService]
        result     <- testClient.loadTestData(rdfDataObjects)
      } yield result).provide(TestClientService.layer(appConfig, system))
    )

  protected def getResponseStringOrThrow(request: HttpRequest): String =
    runtime.unsafeRunTask(
      (for {
        testClient <- ZIO.service[TestClientService]
        result     <- testClient.getResponseString(request)
      } yield result).provide(TestClientService.layer(appConfig, system))
    )

  protected def checkResponseOK(request: HttpRequest): Unit =
    runtime.unsafeRunTask(
      (for {
        testClient <- ZIO.service[TestClientService]
        result     <- testClient.checkResponseOK(request)
      } yield result).provide(TestClientService.layer(appConfig, system))
    )

  protected def getResponseJson(request: HttpRequest): JsObject =
    runtime.unsafeRunTask(
      (for {
        testClient <- ZIO.service[TestClientService]
        result     <- testClient.getResponseJson(request)
      } yield result).provide(TestClientService.layer(appConfig, system))
    )

  protected def singleAwaitingRequest(request: HttpRequest, duration: zio.Duration = 15.seconds): HttpResponse =
    runtime.unsafeRunTask(
      (for {
        testClient <- ZIO.service[TestClientService]
        result     <- testClient.singleAwaitingRequest(request, duration)
      } yield result).provide(TestClientService.layer(appConfig, system))
    )

  protected def getResponseJsonLD(request: HttpRequest): JsonLDDocument =
    runtime.unsafeRunTask(
      (for {
        testClient <- ZIO.service[TestClientService]
        result     <- testClient.getResponseJsonLD(request)
      } yield result).provide(TestClientService.layer(appConfig, system))
    )

  protected def uploadToSipi(loginToken: String, filesToUpload: Seq[FileToUpload]): SipiUploadResponse =
    runtime.unsafeRunTask(
      (for {
        testClient <- ZIO.service[TestClientService]
        result     <- testClient.uploadToSipi(loginToken, filesToUpload)
      } yield result).provide(TestClientService.layer(appConfig, system))
    )

}
