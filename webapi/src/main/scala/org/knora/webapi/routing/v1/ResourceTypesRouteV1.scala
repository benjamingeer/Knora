/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

package org.knora.webapi.routing.v1

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import org.knora.webapi.messages.v1respondermessages.ontologymessages._
import org.knora.webapi.messages.v1respondermessages.usermessages.UserProfileV1
import org.knora.webapi.routing.{Authenticator, RouteUtilV1}
import org.knora.webapi.util.InputValidation
import org.knora.webapi.{BadRequestException, SettingsImpl}
import spray.routing.Directives._
import spray.routing._

import scala.util.Try

/**
  * Provides a spray-routing function for API routes that deal with resource types.
  */
object ResourceTypesRouteV1 extends Authenticator {

    def rapierPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {
        implicit val system = _system
        implicit val executionContext = system.dispatcher
        implicit val timeout = settings.defaultTimeout
        val responderManager = system.actorSelection("/user/responderManager")

        def makeResourceTypeRequestMessage(resourceTypeIri: String, userProfile: UserProfileV1): OntologyResponderRequestV1 = {

            ResourceTypeGetRequestV1(resourceTypeIri, userProfile)
        }

        path("v1" / "resourcetypes" / Segment) { iri =>
            get {
                requestContext =>
                    val requestMessageTry = Try {
                        val userProfile = getUserProfileV1(requestContext)
                        // TODO: Check that this is the IRI of a resource type and not just any IRI
                        val resourceTypeIri = InputValidation.toIri(iri, () => throw BadRequestException(s"Invalid resource type IRI: $iri"))
                        makeResourceTypeRequestMessage(iri, userProfile)
                    }
                    RouteUtilV1.runJsonRoute(
                        requestMessageTry,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            }
        } ~ path("v1" / "resourcetypes") {
            get {
                requestContext =>
                    val requestMessageTry = Try {
                        val userProfile = getUserProfileV1(requestContext)
                        val params = requestContext.request.uri.query.toMap

                        val vocabularyId = params.getOrElse("vocabulary", throw BadRequestException("Required param vocabulary is missing"))

                        val namedGraphIri = vocabularyId match {
                            case "0" => None // if param vocabulary is set to 0, query all named graphs
                            case other => Some(InputValidation.toIri(vocabularyId, () => throw BadRequestException(s"Invalid vocabulary IRI: $vocabularyId")))
                        }

                        ResourceTypesForNamedGraphGetRequestV1(namedGraphIri, userProfile)
                    }
                    RouteUtilV1.runJsonRoute(
                        requestMessageTry,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )

            }
        } ~ path("v1" / "propertylists") {
            get {
                requestContext =>
                    val requestMessageTry = Try {
                        val userProfile = getUserProfileV1(requestContext)
                        val params = requestContext.request.uri.query.toMap

                        val vocabularyId: Option[String] = params.get("vocabulary")
                        val resourcetypeId: Option[String] = params.get("restype")

                        // either the vocabulary or the restype param is set, but not both
                        if (vocabularyId.nonEmpty && resourcetypeId.nonEmpty) throw BadRequestException("Both vocabulary and restype params are set, only one is allowed")

                        vocabularyId match {
                            case Some("0") => // 0 means that all named graphs should be queried
                                PropertyTypesForNamedGraphGetRequestV1(namedGraph = None, userProfile = userProfile)
                            case Some(vocId) =>
                                val namedGraphIri = InputValidation.toIri(vocId, () => throw BadRequestException(s"Invalid vocabulary IRI: $vocabularyId"))
                                PropertyTypesForNamedGraphGetRequestV1(namedGraph = Some(namedGraphIri), userProfile = userProfile)
                            case None => // no vocabulary id given, check for restype
                                resourcetypeId match {
                                    case Some(restypeId) => // get property types for given resource type
                                        val resourceClassIri = InputValidation.toIri(restypeId, () => throw BadRequestException(s"Invalid vocabulary IRI: $restypeId"))
                                        PropertyTypesForResourceTypeGetRequestV1(restypeId, userProfile)
                                    case None => // no params given, get all property types (behaves like vocbulary=0)
                                        PropertyTypesForNamedGraphGetRequestV1(namedGraph = None, userProfile = userProfile)
                                }
                        }
                    }
                    RouteUtilV1.runJsonRoute(
                        requestMessageTry,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )

            }
        } ~ path("v1" / "vocabularies") {
            get {
                requestContext =>
                    val requestMessageTry = Try {
                        val userProfile = getUserProfileV1(requestContext)
                        NamedGraphsGetRequestV1(userProfile)
                    }
                    RouteUtilV1.runJsonRoute(
                        requestMessageTry,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )

            }
        }


    }
}