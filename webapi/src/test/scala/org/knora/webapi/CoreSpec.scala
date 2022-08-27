/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import akka.actor
import akka.testkit.ImplicitSender
import akka.testkit.TestKitBase
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import zio._

import scala.concurrent.Await
import scala.concurrent.ExecutionContext

import org.knora.webapi.core.TestStartupUtils
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.ResponderData
import org.knora.webapi.settings.KnoraSettings
import org.knora.webapi.settings.KnoraSettingsImpl
import org.knora.webapi.settings._
import org.knora.webapi.store.cache.settings.CacheServiceSettings

abstract class CoreSpec
    extends ZIOApp
    with TestKitBase
    with TestStartupUtils
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with ImplicitSender {

  implicit lazy val system: actor.ActorSystem          = akka.actor.ActorSystem("CoreSpec", ConfigFactory.load())
  implicit lazy val executionContext: ExecutionContext = system.dispatcher
  implicit lazy val settings: KnoraSettingsImpl        = KnoraSettings(system)
  lazy val rdfDataObjects                              = List.empty[RdfDataObject]
  val log: Logger                                      = Logger(this.getClass())

  /**
   * The `Environment` that we require to exist at startup.
   */
  type Environment = core.TestLayers.DefaultTestEnvironmentWithoutSipi

  /**
   * `Bootstrap` will ensure that everything is instantiated when the Runtime is created
   * and cleaned up when the Runtime is shutdown.
   */
  override val bootstrap: ZLayer[
    ZIOAppArgs with Scope,
    Any,
    Environment
  ] =
    ZLayer.empty ++ Runtime.removeDefaultLoggers ++ logging.consoleJson() ++ effectLayers

  // no idea why we need that, but we do
  val environmentTag: EnvironmentTag[Environment] = EnvironmentTag[Environment]

  /* Here we start our TestApplication */
  val run =
    (for {
      _ <- ZIO.scoped(core.Startup.run(false, false))
      _ <- prepareRepository(rdfDataObjects)
      _ <- ZIO.never
    } yield ()).forever

  /**
   * The effect layers from which the App is built.
   * Can be overriden in specs that need other implementations.
   */
  lazy val effectLayers = core.TestLayers.defaultTestLayersWithoutSipi(system)

  // The appActor is built inside the AppRouter layer. We need to surface the reference to it,
  // so that we can use it in tests. This seems the easies way of doing it at the moment.
  val appActorF = system
    .actorSelection(APPLICATION_MANAGER_ACTOR_PATH)
    .resolveOne(new scala.concurrent.duration.FiniteDuration(1, scala.concurrent.duration.SECONDS))
  val appActor =
    Await.result(appActorF, new scala.concurrent.duration.FiniteDuration(1, scala.concurrent.duration.SECONDS))

  // needed by some tests
  val cacheServiceSettings = new CacheServiceSettings(system.settings.config)
  val responderData        = ResponderData(system, appActor, settings, cacheServiceSettings)

  final override def beforeAll(): Unit =
    // waits until knora is up and running
    applicationStateRunning(appActor, system)

  final override def afterAll(): Unit = {}
  /* Stop ZIO runtime and release resources (e.g., running docker containers) */

}
