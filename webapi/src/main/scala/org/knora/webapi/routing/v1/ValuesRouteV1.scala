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

import java.io.File
import java.util.UUID

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.model.Multipart.BodyPart
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.FileInfo
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.FileIO
import com.typesafe.scalalogging.Logger
import org.knora.webapi.messages.v1.responder.sipimessages.{SipiResponderConversionFileRequestV1, SipiResponderConversionPathRequestV1}
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v1.responder.valuemessages.ApiValueV1JsonProtocol._
import org.knora.webapi.messages.v1.responder.valuemessages._
import org.knora.webapi.routing.{Authenticator, RouteUtilV1}
import org.knora.webapi.util.InputValidation.RichtextComponents
import org.knora.webapi.util.{DateUtilV1, InputValidation}
import org.knora.webapi.{BadRequestException, FileUploadException, IRI, SettingsImpl}
import org.slf4j.LoggerFactory

import scala.concurrent.Future

/**
  * Provides a spray-routing function for API routes that deal with values.
  */
object ValuesRouteV1 extends Authenticator {

    private def makeVersionHistoryRequestMessage(iris: Seq[IRI], userProfile: UserProfileV1): ValueVersionHistoryGetRequestV1 = {
        if (iris.length != 3) throw BadRequestException("Version history request requires resource IRI, property IRI, and current value IRI")

        val Seq(resourceIriStr, propertyIriStr, currentValueIriStr) = iris

        val resourceIri = InputValidation.toIri(resourceIriStr, () => throw BadRequestException(s"Invalid resource IRI: $resourceIriStr"))
        val propertyIri = InputValidation.toIri(propertyIriStr, () => throw BadRequestException(s"Invalid property IRI: $propertyIriStr"))
        val currentValueIri = InputValidation.toIri(currentValueIriStr, () => throw BadRequestException(s"Invalid value IRI: $currentValueIriStr"))

        ValueVersionHistoryGetRequestV1(
            resourceIri = resourceIri,
            propertyIri = propertyIri,
            currentValueIri = currentValueIri,
            userProfile = userProfile
        )
    }

    private def makeLinkValueGetRequestMessage(iris: Seq[IRI], userProfile: UserProfileV1): LinkValueGetRequestV1 = {
        if (iris.length != 3) throw BadRequestException("Link value request requires subject IRI, predicate IRI, and object IRI")

        val Seq(subjectIriStr, predicateIriStr, objectIriStr) = iris

        val subjectIri = InputValidation.toIri(subjectIriStr, () => throw BadRequestException(s"Invalid subject IRI: $subjectIriStr"))
        val predicateIri = InputValidation.toIri(predicateIriStr, () => throw BadRequestException(s"Invalid predicate IRI: $predicateIriStr"))
        val objectIri = InputValidation.toIri(objectIriStr, () => throw BadRequestException(s"Invalid object IRI: $objectIriStr"))

        LinkValueGetRequestV1(
            subjectIri = subjectIri,
            predicateIri = predicateIri,
            objectIri = objectIri,
            userProfile = userProfile
        )
    }

    private def makeCreateValueRequestMessage(apiRequest: CreateValueApiRequestV1, userProfile: UserProfileV1): CreateValueRequestV1 = {
        val projectIri = InputValidation.toIri(apiRequest.project_id, () => throw BadRequestException(s"Invalid project IRI ${apiRequest.project_id}"))
        val resourceIri = InputValidation.toIri(apiRequest.res_id, () => throw BadRequestException(s"Invalid resource IRI ${apiRequest.res_id}"))
        val propertyIri = InputValidation.toIri(apiRequest.prop, () => throw BadRequestException(s"Invalid property IRI ${apiRequest.prop}"))

        // TODO: these match-case statements with lots of underlines are ugly and confusing.

        // TODO: Support the rest of the value types.
        val (value: UpdateValueV1, commentStr: Option[String]) = apiRequest match {

            case CreateValueApiRequestV1(_, _, _, Some(richtext: CreateRichtextV1), _, _, _, _, _, _, _, _, _, _, _, comment) =>
                val richtextComponents: RichtextComponents = InputValidation.handleRichtext(richtext)

                (TextValueV1(InputValidation.toSparqlEncodedString(richtext.utf8str, () => throw BadRequestException(s"Invalid text: '${richtext.utf8str}'")),
                    textattr = richtextComponents.textattr,
                    resource_reference = richtextComponents.resource_reference),
                    comment)

            case CreateValueApiRequestV1(_, _, _, _, Some(intValue: Int), _, _, _, _, _, _, _, _, _, _, comment) => (IntegerValueV1(intValue), comment)

            case CreateValueApiRequestV1(_, _, _, _, _, Some(decimalValue: BigDecimal), _, _, _, _, _, _, _, _, _, comment) => (DecimalValueV1(decimalValue), comment)

            case CreateValueApiRequestV1(_, _, _, _, _, _, Some(booleanValue: Boolean), _, _, _, _, _, _, _, _, comment) => (BooleanValueV1(booleanValue), comment)

            case CreateValueApiRequestV1(_, _, _, _, _, _, _, Some(uriValue: String), _, _, _, _, _, _, _, comment) => (UriValueV1(InputValidation.toIri(uriValue, () => throw BadRequestException(s"Invalid URI: $uriValue"))), comment)

            case CreateValueApiRequestV1(_, _, _, _, _, _, _, _, Some(dateStr: String), _, _, _, _, _, _, comment) =>
                (DateUtilV1.createJDNValueV1FromDateString(dateStr), comment)

            case CreateValueApiRequestV1(_, _, _, _, _, _, _, _, _, Some(colorStr: String), _, _, _, _, _, comment) =>
                val colorValue = InputValidation.toColor(colorStr, () => throw BadRequestException(s"Invalid color value: $colorStr"))
                (ColorValueV1(colorValue), comment)

            case CreateValueApiRequestV1(_, _, _, _, _, _, _, _, _, _, Some(geomStr: String), _, _, _, _, comment) =>
                val geometryValue = InputValidation.toGeometryString(geomStr, () => throw BadRequestException(s"Invalid geometry value: $geomStr"))
                (GeomValueV1(geometryValue), comment)

            case CreateValueApiRequestV1(_, _, _, _, _, _, _, _, _, _, _, Some(targetResourceIri: IRI), _, _, _, comment) =>
                val resourceIRI = InputValidation.toIri(targetResourceIri, () => throw BadRequestException(s"Invalid resource IRI: $targetResourceIri"))
                (LinkUpdateV1(targetResourceIri = targetResourceIri), comment)

            case CreateValueApiRequestV1(_, _, _, _, _, _, _, _, _, _, _, _, Some(hlistValue), _, _, comment) =>
                val listNodeIri = InputValidation.toIri(hlistValue, () => throw BadRequestException(s"Invalid value IRI: $hlistValue"))
                (HierarchicalListValueV1(listNodeIri), comment)

            case CreateValueApiRequestV1(_, _, _, _, _, _, _, _, _, _, _, _, _, Some(Seq(timeval1: BigDecimal, timeval2: BigDecimal)), _, comment) =>
                (IntervalValueV1(timeval1, timeval2), comment)

            case CreateValueApiRequestV1(_, _, _, _, _, _, _, _, _, _, _, _, _, _, Some(geonameStr: String), comment) =>
                (GeonameValueV1(geonameStr), comment)

            case _ => throw BadRequestException(s"No value submitted")
        }

        CreateValueRequestV1(
            projectIri = projectIri,
            resourceIri = resourceIri,
            propertyIri = propertyIri,
            value = value,
            comment = commentStr.map(str => InputValidation.toSparqlEncodedString(str, () => throw BadRequestException(s"Invalid comment: '$str'"))),
            userProfile = userProfile,
            apiRequestID = UUID.randomUUID
        )
    }

    private def makeAddValueVersionRequestMessage(valueIriStr: IRI, apiRequest: ChangeValueApiRequestV1, userProfile: UserProfileV1): ChangeValueRequestV1 = {
        val projectIri = InputValidation.toIri(apiRequest.project_id, () => throw BadRequestException(s"Invalid project IRI: ${apiRequest.project_id}"))
        val valueIri = InputValidation.toIri(valueIriStr, () => throw BadRequestException(s"Invalid value IRI: $valueIriStr"))

        // TODO: These match-case statements with lots of underlines are ugly and confusing.

        // TODO: Support the rest of the value types.
        val (value: UpdateValueV1, commentStr: Option[String]) = apiRequest match {
            case ChangeValueApiRequestV1(_, Some(richtext: CreateRichtextV1), _, _, _, _, _, _, _, _, _, _, _, comment) =>
                val richtextComponents: RichtextComponents = InputValidation.handleRichtext(richtext)

                (TextValueV1(InputValidation.toSparqlEncodedString(richtext.utf8str, () => throw BadRequestException(s"Invalid text: '${richtext.utf8str}'")),
                    textattr = richtextComponents.textattr,
                    resource_reference = richtextComponents.resource_reference),
                    comment)

            case ChangeValueApiRequestV1(_, _, Some(intValue: Int), _, _, _, _, _, _, _, _, _, _, comment) => (IntegerValueV1(intValue), comment)

            case ChangeValueApiRequestV1(_, _, _, Some(decimalValue: BigDecimal), _, _, _, _, _, _, _, _, _, comment) => (DecimalValueV1(decimalValue), comment)

            case ChangeValueApiRequestV1(_, _, _, _, Some(booleanValue: Boolean), _, _, _, _, _, _, _, _, comment) => (BooleanValueV1(booleanValue), comment)

            case ChangeValueApiRequestV1(_, _, _, _, _, Some(uriValue: String), _, _, _, _, _, _, _, comment) => (UriValueV1(InputValidation.toIri(uriValue, () => throw BadRequestException(s"Invalid URI: $uriValue"))), comment)

            case ChangeValueApiRequestV1(_, _, _, _, _, _, Some(dateStr: String), _, _, _, _, _, _, comment) =>
                (DateUtilV1.createJDNValueV1FromDateString(dateStr), comment)

            case ChangeValueApiRequestV1(_, _, _, _, _, _, _, Some(colorStr: String), _, _, _, _, _, comment) =>
                val colorValue = InputValidation.toColor(colorStr, () => throw BadRequestException(s"Invalid color value: $colorStr"))
                (ColorValueV1(colorValue), comment)

            case ChangeValueApiRequestV1(_, _, _, _, _, _, _, _, Some(geomStr: String), _, _, _, _, comment) =>
                val geometryValue = InputValidation.toGeometryString(geomStr, () => throw BadRequestException(s"Invalid geometry value: $geomStr"))
                (GeomValueV1(geometryValue), comment)

            case ChangeValueApiRequestV1(_, _, _, _, _, _, _, _, _, Some(linkValue: IRI), _, _, _, comment) =>
                val resourceIri = InputValidation.toIri(linkValue, () => throw BadRequestException(s"Invalid value IRI: $linkValue"))
                (LinkUpdateV1(targetResourceIri = resourceIri), comment)

            case ChangeValueApiRequestV1(_, _, _, _, _, _, _, _, _, _, Some(hlistValue: IRI), _, _, comment) =>
                val listNodeIri = InputValidation.toIri(hlistValue, () => throw BadRequestException(s"Invalid value IRI: $hlistValue"))
                (HierarchicalListValueV1(listNodeIri), comment)

            case ChangeValueApiRequestV1(_, _, _, _, _, _, _, _, _, _, _, Some(Seq(timeval1: BigDecimal, timeval2: BigDecimal)), _, comment) =>
                (IntervalValueV1(timeval1, timeval2), comment)

            case ChangeValueApiRequestV1(_, _, _, _, _, _, _, _, _, _, _, _, Some(geonameStr: String), comment) =>
                (GeonameValueV1(geonameStr), comment)

            case ChangeValueApiRequestV1(_, _, _, _, _, _, _, _, _, _, _, _, _, Some(comment)) =>
                throw BadRequestException(s"No value was submitted")

            case _ => throw BadRequestException(s"No value or comment was submitted")
        }

        ChangeValueRequestV1(
            valueIri = valueIri,
            value = value,
            comment = commentStr.map(str => InputValidation.toSparqlEncodedString(str, () => throw BadRequestException(s"Invalid comment: '$str'"))),
            userProfile = userProfile,
            apiRequestID = UUID.randomUUID
        )
    }

    private def makeChangeCommentRequestMessage(valueIriStr: IRI, comment: Option[String], userProfile: UserProfileV1): ChangeCommentRequestV1 = {
        ChangeCommentRequestV1(
            valueIri = InputValidation.toIri(valueIriStr, () => throw BadRequestException(s"Invalid value IRI: $valueIriStr")),
            comment = comment.map(str => InputValidation.toSparqlEncodedString(str, () => throw BadRequestException(s"Invalid comment: '$str'"))),
            userProfile = userProfile,
            apiRequestID = UUID.randomUUID
        )
    }

    private def makeDeleteValueRequest(valueIriStr: IRI, deleteComment: Option[String], userProfile: UserProfileV1): DeleteValueRequestV1 = {
        DeleteValueRequestV1(
            valueIri = InputValidation.toIri(valueIriStr, () => throw BadRequestException(s"Invalid value IRI: $valueIriStr")),
            deleteComment = deleteComment.map(comment => InputValidation.toSparqlEncodedString(comment, () => throw BadRequestException(s"Invalid comment: '$comment'"))),
            userProfile = userProfile,
            apiRequestID = UUID.randomUUID
        )
    }

    private def makeGetValueRequest(valueIriStr: IRI, userProfile: UserProfileV1): ValueGetRequestV1 = {
        ValueGetRequestV1(
            InputValidation.toIri(valueIriStr, () => throw BadRequestException(s"Invalid value IRI: $valueIriStr")),
            userProfile
        )
    }

    private def makeChangeFileValueRequest(resIriStr: IRI, apiRequest: Option[ChangeFileValueApiRequestV1], multipartConversionRequest: Option[SipiResponderConversionPathRequestV1], userProfile: UserProfileV1) = {
        if (apiRequest.nonEmpty && multipartConversionRequest.nonEmpty) throw BadRequestException("File information is present twice, only one is allowed.")

        val resourceIri = InputValidation.toIri(resIriStr, () => throw BadRequestException(s"Invalid resource IRI: $resIriStr"))

        if (apiRequest.nonEmpty) {
            // GUI-case
            val fileRequest = SipiResponderConversionFileRequestV1(
                originalFilename = InputValidation.toSparqlEncodedString(apiRequest.get.file.originalFilename, () => throw BadRequestException(s"The original filename is invalid: '${apiRequest.get.file.originalFilename}'")),
                originalMimeType = InputValidation.toSparqlEncodedString(apiRequest.get.file.originalMimeType, () => throw BadRequestException(s"The original MIME type is invalid: '${apiRequest.get.file.originalMimeType}'")),
                filename = InputValidation.toSparqlEncodedString(apiRequest.get.file.filename, () => throw BadRequestException(s"Invalid filename: '${apiRequest.get.file.filename}'")),
                userProfile = userProfile
            )
            ChangeFileValueRequestV1(
                resourceIri = resourceIri,
                file = fileRequest,
                apiRequestID = UUID.randomUUID,
                userProfile = userProfile)
        }
        else if (multipartConversionRequest.nonEmpty) {
            // non GUI-case
            ChangeFileValueRequestV1(
                resourceIri = resourceIri,
                file = multipartConversionRequest.get,
                apiRequestID = UUID.randomUUID,
                userProfile = userProfile)
        } else {
            // no file information was provided
            throw BadRequestException("A file value change was requested but no file information was provided")
        }

    }

    def knoraApiPath(_system: ActorSystem, settings: SettingsImpl, loggingAdapter: LoggingAdapter): Route = {
        implicit val system: ActorSystem = _system
        implicit val materializer = ActorMaterializer()
        implicit val executionContext = system.dispatcher
        implicit val timeout = settings.defaultTimeout
        val responderManager = system.actorSelection("/user/responderManager")

        val log = Logger(LoggerFactory.getLogger(this.getClass))

        // Version history request requires 3 URL path segments: resource IRI, property IRI, and current value IRI
        path("v1" / "values" / "history" / Segments) { iris =>
            get {
                requestContext => {
                    val userProfile = getUserProfileV1(requestContext)
                    val requestMessage = makeVersionHistoryRequestMessage(iris = iris, userProfile = userProfile)


                    RouteUtilV1.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        loggingAdapter
                    )
                }
            }
        } ~ path("v1" / "values") {
            post {
                entity(as[CreateValueApiRequestV1]) { apiRequest => requestContext =>
                    val userProfile = getUserProfileV1(requestContext)
                    val requestMessage = makeCreateValueRequestMessage(apiRequest = apiRequest, userProfile = userProfile)

                    RouteUtilV1.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        loggingAdapter
                    )
                }
            }
        } ~ path("v1" / "values" / Segment) { valueIriStr =>
            get {
                requestContext => {
                    val userProfile = getUserProfileV1(requestContext)
                    val requestMessage = makeGetValueRequest(valueIriStr = valueIriStr, userProfile = userProfile)

                    RouteUtilV1.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        loggingAdapter
                    )
                }
            } ~ put {
                entity(as[ChangeValueApiRequestV1]) { apiRequest => requestContext =>
                    val userProfile = getUserProfileV1(requestContext)

                    // In API v1, you cannot change a value and its comment in a single request. So we know that here,
                    // we are getting a request to change either the value or the comment, but not both.
                    val requestMessage = apiRequest match {
                        case ChangeValueApiRequestV1(_, _, _, _, _, _, _, _, _, _, _, _, _, Some(comment)) => makeChangeCommentRequestMessage(valueIriStr = valueIriStr, comment = Some(comment), userProfile = userProfile)
                        case _ => makeAddValueVersionRequestMessage(valueIriStr = valueIriStr, apiRequest = apiRequest, userProfile = userProfile)
                    }

                    RouteUtilV1.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        loggingAdapter
                    )
                }
            } ~ delete {
                requestContext => {
                    val userProfile = getUserProfileV1(requestContext)
                    val params = requestContext.request.uri.query().toMap
                    val deleteComment = params.get("deleteComment")
                    val requestMessage = makeDeleteValueRequest(valueIriStr = valueIriStr, deleteComment = deleteComment, userProfile = userProfile)

                    RouteUtilV1.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        loggingAdapter
                    )
                }
            }
        } ~ path("v1" / "valuecomments" / Segment) { valueIriStr =>
            delete {
                requestContext => {
                    val userProfile = getUserProfileV1(requestContext)
                    val requestMessage = makeChangeCommentRequestMessage(valueIriStr = valueIriStr, comment = None, userProfile = userProfile)

                    RouteUtilV1.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        loggingAdapter
                    )
                }
            }
        } ~ path("v1" / "links" / Segments) { iris =>
                // Link value request requires 3 URL path segments: subject IRI, predicate IRI, and object IRI
                get {
                    requestContext => {
                        val userProfile = getUserProfileV1(requestContext)
                        val requestMessage = makeLinkValueGetRequestMessage(iris = iris, userProfile = userProfile)

                        RouteUtilV1.runJsonRoute(
                            requestMessage,
                            requestContext,
                            settings,
                            responderManager,
                            loggingAdapter
                        )
                    }
                }
            } ~ path("v1" / "filevalue" / Segment) { (resIriStr: IRI) =>
            put {
                entity(as[ChangeFileValueApiRequestV1]) { apiRequest => requestContext =>
                    val userProfile = getUserProfileV1(requestContext)
                    val requestMessage = makeChangeFileValueRequest(resIriStr = resIriStr, apiRequest = Some(apiRequest), multipartConversionRequest = None, userProfile = userProfile)

                    RouteUtilV1.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        loggingAdapter
                    )
                }
            } ~ put {
                entity(as[Multipart.FormData]) { formdata => requestContext =>

                    log.debug("/v1/filevalue - PUT - Multipart.FormData - Route")

                    val userProfile = getUserProfileV1(requestContext)

                    val FILE_PART = "file"

                    type Name = String

                    /* TODO: refactor to remove the need for this var */
                    /* makes sure that variables updated in another thread return the latest version */
                    @volatile var receivedFile: Option[File] = None

                    // this file will be deleted by Knora once it is not needed anymore
                    // TODO: add a script that cleans files in the tmp location that have a certain age
                    // TODO  (in case they were not deleted by Knora which should not happen -> this has also to be implemented for Sipi for the thumbnails)
                    // TODO: how to check if the user has sent multiple files?

                    /* get the file data and save file to temporary location */
                    // collect all parts of the multipart as it arrives into a map
                    val allPartsFuture: Future[Map[Name, Any]] = formdata.parts.mapAsync[(Name, Any)](1) {
                        case b: BodyPart if b.name == FILE_PART => {
                            log.debug(s"inside allPartsFuture - processing $FILE_PART")
                            val filename = b.filename.getOrElse(throw BadRequestException(s"Filename is not given"))
                            val tmpFile = InputValidation.createTempFile(settings)
                            val written = b.entity.dataBytes.runWith(FileIO.toPath(tmpFile.toPath))
                            written.map { written =>
                                log.debug(s"written result: ${written.wasSuccessful}, ${b.filename.get}, ${tmpFile.getAbsolutePath}")
                                receivedFile = Some(tmpFile)
                                (b.name, FileInfo(b.name, filename, b.entity.contentType))
                            }
                        }
                    }.runFold(Map.empty[Name, Any])((map, tuple) => map + tuple)

                    val requestMessageFuture = allPartsFuture.map { allParts =>

                        /* Check to see if a file was received */
                        val sourcePath = receivedFile.getOrElse(throw FileUploadException())

                        // get the file info containing the original filename and content type.
                        val fileInfo = allParts.getOrElse(FILE_PART, throw BadRequestException(s"MultiPart POST request was sent without required '$FILE_PART' part!")).asInstanceOf[FileInfo]
                        val originalFilename = fileInfo.fileName
                        val originalMimeType = fileInfo.contentType.toString

                        val sipiConvertPathRequest = SipiResponderConversionPathRequestV1(
                            originalFilename = InputValidation.toSparqlEncodedString(originalFilename, () => throw BadRequestException(s"The original filename is invalid: '$originalFilename'")),
                            originalMimeType = InputValidation.toSparqlEncodedString(originalMimeType, () => throw BadRequestException(s"The original MIME type is invalid: '$originalMimeType'")),
                            source = sourcePath,
                            userProfile = userProfile
                        )

                        makeChangeFileValueRequest(resIriStr = resIriStr, apiRequest = None, multipartConversionRequest = Some(sipiConvertPathRequest), userProfile = userProfile)
                    }

                    RouteUtilV1.runJsonRouteWithFuture(
                        requestMessageFuture,
                        requestContext,
                        settings,
                        responderManager,
                        loggingAdapter
                    )
                }
            }
        }
    }
}
