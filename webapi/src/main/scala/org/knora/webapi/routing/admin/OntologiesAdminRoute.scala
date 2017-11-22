/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.routing.admin

import java.util.UUID

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import org.knora.webapi.messages.admin.responder.ontologiesadminmessages._
import org.knora.webapi.routing.{Authenticator, RouteUtilAdmin}
import org.knora.webapi.util.StringFormatter
import org.knora.webapi.{BadRequestException, IRI, SettingsImpl}

import scala.concurrent.ExecutionContextExecutor

/**
  * Provides a spray-routing function for API routes that deal with lists.
  */
object OntologiesAdminRoute extends Authenticator with OntologiesAdminJsonProtocol {

    def knoraApiPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {
        implicit val system: ActorSystem = _system
        implicit val executionContext: ExecutionContextExecutor = system.dispatcher
        implicit val timeout: Timeout = settings.defaultTimeout
        val responderManager = system.actorSelection("/user/responderManager")
        val stringFormatter = StringFormatter.getGeneralInstance

        path("admin" / "ontologies") {
            get {
                /* return all ontologies */
                parameters("projectIri".?) { maybeProjectIri: Option[IRI] =>
                    requestContext =>
                        val userProfile = getUserProfileV1(requestContext)

                        val projectIri: Option[IRI] = maybeProjectIri match {
                            case Some(potentialProjectIri) => Some(stringFormatter.validateAndEscapeIri(potentialProjectIri, () => throw BadRequestException(s"Invalid param project IRI: $potentialProjectIri")))
                            case None => None
                        }

                        val requestMessage = OntologiesGetAdminRequest(projectIri, userProfile)

                        RouteUtilAdmin.runJsonRoute(
                            requestMessage,
                            requestContext,
                            settings,
                            responderManager,
                            log
                        )
                }
            } ~
            post {
                /* create an ontology */
                entity(as[CreateOntologyAdminPayload]) { apiRequest =>
                    requestContext =>
                        val userProfile = getUserProfileV1(requestContext)

                        val requestMessage = OntologyCreateAdminRequest(
                            ontologyName = apiRequest.ontologyName,
                            projectIri = apiRequest.projectIri,
                            apiRequestID = UUID.randomUUID(),
                            userProfile
                        )

                        RouteUtilAdmin.runJsonRoute(
                            requestMessage,
                            requestContext,
                            settings,
                            responderManager,
                            log
                        )
                }
            }
        } ~
        path("admin" / "ontologies" / Segment) {iri =>
            get {
                /* get an existing ontology dump as JSON-LD */
                requestContext =>
                    val userProfile = getUserProfileV1(requestContext)
                    val ontologyIri = stringFormatter.validateAndEscapeIri(iri, () => throw BadRequestException(s"Invalid param ontology IRI: $iri"))

                    val requestMessage = OntologyGetAdminRequest(ontologyIri, userProfile)

                    RouteUtilAdmin.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            } ~
            put {
                /* update (overwrite) an existing ontology */
                entity(as[String]) { updatePayload =>
                    requestContext =>
                        val userProfile = getUserProfileV1(requestContext)
                        val ontologyIri = stringFormatter.validateAndEscapeIri(iri, () => throw BadRequestException(s"Invalid param ontology IRI: $iri"))

                        val requestMessage = OntologyUpdateAdminRequest(
                            iri = ontologyIri,
                            data = updatePayload,
                            apiRequestID = UUID.randomUUID(),
                            userProfile
                        )

                        RouteUtilAdmin.runJsonRoute(
                            requestMessage,
                            requestContext,
                            settings,
                            responderManager,
                            log
                        )
                }
            } ~
            delete {
                /* delete an ontology */
                ???
            }
        }
    }
}