/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.Logger
import zio._

import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.State
import org.knora.webapi.core.domain.AppState

/**
 * A route used for rejecting requests to certain paths depending on the state of the app or the configuration.
 *
 * If the current state of the application is anything other then [[AppStates.Running]], then return [[StatusCodes.ServiceUnavailable]].
 * If the current state of the application is [[AppStates.Running]], then reject requests to paths as defined
 * in 'application.conf'.
 *
 * TODO: This should probably be refactored into a ZIO-HTTP middleware, when the transistion to ZIO-HTTP is done.
 */
final case class RejectingRoute(private val appConfig: AppConfig, private val runtime: Runtime[State]) { self =>

  val log: Logger = Logger(this.getClass)

  /**
   * Gets the app state from the State service
   */
  private val getAppState: Future[AppState] =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .runToFuture(
          for {
            state <- ZIO.service[State]
            state <- state.getAppState
          } yield state
        )
    }

  /**
   * Returns the route.
   */
  def makeRoute: Route =
    path(Remaining) { wholePath =>
      // check to see if route is on the rejection list
      val rejectRequest = appConfig.routesToReject.exists(wholePath.startsWith)

      onComplete(getAppState) {

        case Success(appState) =>
          appState match {
            case AppState.Running if rejectRequest =>
              log.info(s"Request to $wholePath not allowed as per configuration for routes to reject.")
              complete(StatusCodes.NotFound, "The requested path is deactivated.")
            case AppState.Running =>
              // route is allowed. by rejecting, the request is passed through so that some other route can match
              reject()
            case other =>
              // if any state other then 'Running', then return ServiceUnavailable
              val msg =
                s"Request to $wholePath rejected. Application not available at the moment (state = $other). Please try again later."
              log.info(msg)
              complete(StatusCodes.ServiceUnavailable, msg)
          }

        case Failure(ex) =>
          log.error("RejectingRoute - ex: {}", ex)
          complete(StatusCodes.ServiceUnavailable, ex.getMessage)
      }
    }
}
