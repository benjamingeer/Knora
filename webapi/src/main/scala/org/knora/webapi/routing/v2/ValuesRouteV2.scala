/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

package org.knora.webapi.routing.v2

import java.time.Instant
import java.util.UUID

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{PathMatcher, Route}
import org.knora.webapi._
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.util.{JsonLDDocument, JsonLDUtil}
import org.knora.webapi.messages.v2.responder.resourcemessages.ResourcesGetRequestV2
import org.knora.webapi.messages.v2.responder.valuemessages._
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilV2}

import scala.concurrent.Future

object ValuesRouteV2 {
    val ValuesBasePath: PathMatcher[Unit] = PathMatcher("v2" / "values")
}

/**
 * Provides a routing function for API v2 routes that deal with values.
 */
class ValuesRouteV2(routeData: KnoraRouteData) extends KnoraRoute(routeData) with Authenticator {

    import ValuesRouteV2._

    /**
     * Returns the route.
     */
    override def knoraApiPath: Route = getValue ~ createValue ~ updateValue ~ deleteValue

    private def getValue: Route = path(ValuesBasePath / Segment / Segment) { (resourceIriStr: IRI, valueUuidStr: String) =>
        get {
            requestContext => {
                val resourceIri: SmartIri = resourceIriStr.toSmartIriWithErr(throw BadRequestException(s"Invalid resource IRI: $resourceIriStr"))

                if (!resourceIri.isKnoraResourceIri) {
                    throw BadRequestException(s"Invalid resource IRI: $resourceIriStr")
                }

                val valueUuid: UUID = stringFormatter.decodeUuidWithErr(valueUuidStr, throw BadRequestException(s"Invalid value UUID: $valueUuidStr"))

                val params: Map[String, String] = requestContext.request.uri.query().toMap

                // Was a version date provided?
                val versionDate: Option[Instant] = params.get("version").map {
                    versionStr =>
                        def errorFun: Nothing = throw BadRequestException(s"Invalid version date: $versionStr")

                        // Yes. Try to parse it as an xsd:dateTimeStamp.
                        try {
                            stringFormatter.xsdDateTimeStampToInstant(versionStr, errorFun)
                        } catch {
                            // If that doesn't work, try to parse it as a Knora ARK timestamp.
                            case _: Exception => stringFormatter.arkTimestampToInstant(versionStr, errorFun)
                        }
                }

                val targetSchema: ApiV2Schema = RouteUtilV2.getOntologySchema(requestContext)
                val schemaOptions: Set[SchemaOption] = RouteUtilV2.getSchemaOptions(requestContext)

                val requestMessageFuture: Future[ResourcesGetRequestV2] = for {
                    requestingUser <- getUserADM(requestContext)
                } yield ResourcesGetRequestV2(
                    resourceIris = Seq(resourceIri.toString),
                    valueUuid = Some(valueUuid),
                    versionDate = versionDate,
                    targetSchema = targetSchema,
                    requestingUser = requestingUser
                )

                RouteUtilV2.runRdfRouteWithFuture(
                    requestMessageF = requestMessageFuture,
                    requestContext = requestContext,
                    settings = settings,
                    responderManager = responderManager,
                    log = log,
                    targetSchema = targetSchema,
                    schemaOptions = schemaOptions
                )
            }
        }
    }

    private def createValue: Route = path(ValuesBasePath) {
        post {
            entity(as[String]) { jsonRequest =>
                requestContext => {
                    val requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

                    val requestMessageFuture: Future[CreateValueRequestV2] = for {
                        requestingUser <- getUserADM(requestContext)
                        requestMessage: CreateValueRequestV2 <- CreateValueRequestV2.fromJsonLD(
                            requestDoc,
                            apiRequestID = UUID.randomUUID,
                            requestingUser = requestingUser,
                            responderManager = responderManager,
                            storeManager = storeManager,
                            settings = settings,
                            log = log
                        )
                    } yield requestMessage

                    RouteUtilV2.runRdfRouteWithFuture(
                        requestMessageF = requestMessageFuture,
                        requestContext = requestContext,
                        settings = settings,
                        responderManager = responderManager,
                        log = log,
                        targetSchema = ApiV2Complex,
                        schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
                    )
                }
            }
        }
    }

    private def updateValue: Route = path(ValuesBasePath) {
        put {
            entity(as[String]) { jsonRequest =>
                requestContext => {
                    val requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

                    val requestMessageFuture: Future[UpdateValueRequestV2] = for {
                        requestingUser <- getUserADM(requestContext)
                        requestMessage: UpdateValueRequestV2 <- UpdateValueRequestV2.fromJsonLD(
                            requestDoc,
                            apiRequestID = UUID.randomUUID,
                            requestingUser = requestingUser,
                            responderManager = responderManager,
                            storeManager = storeManager,
                            settings = settings,
                            log = log
                        )
                    } yield requestMessage

                    RouteUtilV2.runRdfRouteWithFuture(
                        requestMessageF = requestMessageFuture,
                        requestContext = requestContext,
                        settings = settings,
                        responderManager = responderManager,
                        log = log,
                        targetSchema = ApiV2Complex,
                        schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
                    )
                }
            }
        }
    }

    private def deleteValue: Route = path(ValuesBasePath / "delete") {
        post {
            entity(as[String]) { jsonRequest =>
                requestContext => {
                    val requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

                    val requestMessageFuture: Future[DeleteValueRequestV2] = for {
                        requestingUser <- getUserADM(requestContext)
                        requestMessage: DeleteValueRequestV2 <- DeleteValueRequestV2.fromJsonLD(
                            requestDoc,
                            apiRequestID = UUID.randomUUID,
                            requestingUser = requestingUser,
                            responderManager = responderManager,
                            storeManager = storeManager,
                            settings = settings,
                            log = log
                        )
                    } yield requestMessage

                    RouteUtilV2.runRdfRouteWithFuture(
                        requestMessageF = requestMessageFuture,
                        requestContext = requestContext,
                        settings = settings,
                        responderManager = responderManager,
                        log = log,
                        targetSchema = ApiV2Complex,
                        schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
                    )
                }
            }
        }
    }
}
