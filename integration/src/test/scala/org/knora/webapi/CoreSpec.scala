/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import com.typesafe.scalalogging.Logger
import org.apache.pekko.actor
import org.apache.pekko.actor.ActorRef
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.ImplicitSender
import org.apache.pekko.testkit.TestKitBase
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import zio.*
import zio.ZIO

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.SECONDS

import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.AppServer
import org.knora.webapi.core.LayersTestLive
import org.knora.webapi.core.LayersTestMock
import org.knora.webapi.core.MessageRelayActorRef
import org.knora.webapi.core.TestStartupUtils
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.infrastructure.JwtService
import org.knora.webapi.slice.security.ScopeResolver
import org.knora.webapi.util.LogAspect

abstract class CoreSpec
    extends AnyWordSpec
    with TestKitBase
    with TestStartupUtils
    with Matchers
    with BeforeAndAfterAll
    with ImplicitSender { self =>

  /**
   * The `Environment` that we require to exist at startup.
   * Can be overriden in specs that need other implementations.
   */
  type Environment = LayersTestMock.Environment

  /**
   * The effect layers from which the App is built.
   * Can be overriden in specs that need other implementations.
   */
  lazy val effectLayers: ULayer[self.Environment] = LayersTestLive.layer

  /**
   * `Bootstrap` will ensure that everything is instantiated when the Runtime is created
   * and cleaned up when the Runtime is shutdown.
   */
  private val bootstrap = util.Logger.text() >>> effectLayers

  // create a configured runtime
  implicit val runtime: Runtime.Scoped[self.Environment] = Unsafe.unsafe { implicit u =>
    Runtime.unsafe.fromLayer(bootstrap)
  }

  lazy val appConfig: AppConfig                        = UnsafeZioRun.service[AppConfig]
  lazy val appActor: ActorRef                          = UnsafeZioRun.service[MessageRelayActorRef].ref
  implicit lazy val system: ActorSystem                = UnsafeZioRun.service[ActorSystem]
  implicit lazy val executionContext: ExecutionContext = system.dispatcher
  lazy val rdfDataObjects                              = List.empty[RdfDataObject]
  val log: Logger                                      = Logger(this.getClass)

  // the default timeout for all tests
  implicit val timeout: FiniteDuration = FiniteDuration(10, SECONDS)

  final override def beforeAll(): Unit =
    /* Here we start our app and initialize the repository before each suit runs */
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(AppServer.test *> prepareRepository(rdfDataObjects) @@ LogAspect.logSpan("prepare-repo"))
        .getOrThrow()
    }

  final override def afterAll(): Unit =
    /* Stop ZIO runtime and release resources (e.g., running docker containers) */
    Unsafe.unsafe { implicit u =>
      runtime.unsafe.shutdown()
    }

  protected def createJwtTokenString(user: User): ZIO[ScopeResolver & JwtService, Nothing, String] = for {
    scope <- ZIO.serviceWithZIO[ScopeResolver](_.resolve(user))
    token <- ZIO.serviceWithZIO[JwtService](_.createJwt(user.userIri, scope))
  } yield token.jwtString
}
