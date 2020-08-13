/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 *  This file is part of Knora.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.http.handler

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{complete, extractRequest}
import akka.http.scaladsl.server.ExceptionHandler
import com.typesafe.scalalogging.LazyLogging
import org.knora.webapi.exceptions.{InternalServerException, RequestRejectedException}
import org.knora.webapi.http.status.{ApiStatusCodesV1, ApiStatusCodesV2}
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.util.{JsonLDDocument, JsonLDObject, JsonLDString}
import org.knora.webapi.settings.KnoraSettingsImpl
import spray.json.{JsNumber, JsObject, JsString, JsValue}

/**
 * The Knora exception handler is used by akka-http to convert any exceptions thrown during route processing
 * into HttpResponses. It is brought implicitly into scope at the top level [[KnoraLiveService]].
 */
object KnoraExceptionHandler extends LazyLogging {

    // A generic error message that we return to clients when an internal server error occurs.
    private val GENERIC_INTERNAL_SERVER_ERROR_MESSAGE = "The request could not be completed because of an internal server error."

    def apply(settingsImpl: KnoraSettingsImpl): ExceptionHandler = ExceptionHandler {

        /* TODO: Find out which response format should be generated, by looking at what the client is requesting / accepting (issue #292) */

        case rre: RequestRejectedException =>
            extractRequest { request =>
                val url = request.uri.path.toString

                // println(s"KnoraExceptionHandler - case: rre - url: $url")

                if (url.startsWith("/v1")) {
                    complete(exceptionToJsonHttpResponseV1(rre, settingsImpl))
                } else if (url.startsWith("/v2")) {
                    complete(exceptionToJsonHttpResponseV2(rre, settingsImpl))
                } else if (url.startsWith("/admin")) {
                    complete(exceptionToJsonHttpResponseADM(rre, settingsImpl))
                } else {
                    complete(exceptionToJsonHttpResponseADM(rre, settingsImpl))
                }
            }

        case ise: InternalServerException =>
            extractRequest { request =>
                val uri = request.uri
                val url = uri.path.toString

                // println(s"KnoraExceptionHandler - case: ise - url: $url")
                logger.error(s"Unable to run route $url", ise)

                if (url.startsWith("/v1")) {
                    complete(exceptionToJsonHttpResponseV1(ise, settingsImpl))
                } else if (url.startsWith("/v2")) {
                    complete(exceptionToJsonHttpResponseV2(ise, settingsImpl))
                } else if (url.startsWith("/admin")) {
                    complete(exceptionToJsonHttpResponseADM(ise, settingsImpl))
                } else {
                    complete(exceptionToJsonHttpResponseADM(ise, settingsImpl))
                }
            }

        case other =>
            extractRequest { request =>
                val uri = request.uri
                val url = uri.path.toString

                logger.debug(s"KnoraExceptionHandler - case: other - url: $url")
                logger.error(s"Unable to run route $url", other)

                if (url.startsWith("/v1")) {
                    complete(exceptionToJsonHttpResponseV1(other, settingsImpl))
                } else if (url.startsWith("/v2")) {
                    complete(exceptionToJsonHttpResponseV2(other, settingsImpl))
                } else if (url.startsWith("/admin")) {
                    complete(exceptionToJsonHttpResponseADM(other, settingsImpl))
                } else {
                    complete(exceptionToJsonHttpResponseADM(other, settingsImpl))
                }
            }
    }

    /**
     * Converts an exception to an HTTP response in JSON format specific to `V1`.
     *
     * @param ex the exception to be converted.
     * @return an [[HttpResponse]] in JSON format.
     */
    private def exceptionToJsonHttpResponseV1(ex: Throwable, settings: KnoraSettingsImpl): HttpResponse = {
        // Get the API status code that corresponds to the exception.
        val apiStatus: ApiStatusCodesV1.Value = ApiStatusCodesV1.fromException(ex)

        // Convert the API status code to the corresponding HTTP status code.
        val httpStatus: StatusCode = ApiStatusCodesV1.toHttpStatus(apiStatus)

        // Generate an HTTP response containing the error message, the API status code, and the HTTP status code.

        // this is a special case where we need to add 'access'
        val maybeAccess: Option[(String, JsValue)] = if (apiStatus == ApiStatusCodesV1.NO_RIGHTS_FOR_OPERATION) {
            Some("access" -> JsString("NO_ACCESS"))
        } else {
            None
        }

        val responseFields: Map[String, JsValue] = Map(
            "status" -> JsNumber(apiStatus.id),
            "error" -> JsString(makeClientErrorMessage(ex, settings))
        ) ++ maybeAccess

        HttpResponse(
            status = httpStatus,
            entity = HttpEntity(ContentType(MediaTypes.`application/json`), JsObject(responseFields).compactPrint)
        )
    }

    /**
     * Converts an exception to an HTTP response in JSON format specific to `V2`.
     *
     * @param ex the exception to be converted.
     * @return an [[HttpResponse]] in JSON format.
     */
    private def exceptionToJsonHttpResponseV2(ex: Throwable, settings: KnoraSettingsImpl): HttpResponse = {
        // Get the HTTP status code that corresponds to the exception.
        val httpStatus: StatusCode = ApiStatusCodesV2.fromException(ex)

        // Generate an HTTP response containing the error message...

        val jsonLDDocument = JsonLDDocument(
            body = JsonLDObject(
                Map(OntologyConstants.KnoraApiV2Complex.Error -> JsonLDString(makeClientErrorMessage(ex, settings)))
            ),
            context = JsonLDObject(
                Map(OntologyConstants.KnoraApi.KnoraApiOntologyLabel -> JsonLDString(OntologyConstants.KnoraApiV2Complex.KnoraApiV2PrefixExpansion))
            )
        )

        // ... and the HTTP status code.
        HttpResponse(
            status = httpStatus,
            entity = HttpEntity(ContentType(MediaTypes.`application/json`), jsonLDDocument.toCompactString)
        )
    }

    /**
     * Converts an exception to an HTTP response in JSON format specific to `ADM`.
     *
     * @param ex the exception to be converted.
     * @return an [[HttpResponse]] in JSON format.
     */
    private def exceptionToJsonHttpResponseADM(ex: Throwable, settings: KnoraSettingsImpl): HttpResponse = {

        // Get the HTTP status code that corresponds to the exception.
        val httpStatus: StatusCode = ApiStatusCodesV2.fromException(ex)

        // Generate an HTTP response containing the error message ...
        val responseFields: Map[String, JsValue] = Map(
            "error" -> JsString(makeClientErrorMessage(ex, settings))
        )

        // ... and the HTTP status code.
        HttpResponse(
            status = httpStatus,
            entity = HttpEntity(ContentType(MediaTypes.`application/json`), JsObject(responseFields).compactPrint)
        )
    }

    /**
     * Converts an exception to an HTTP response in HTML format specific to `V1`.
     *
     * @param ex the exception to be converted.
     * @return an [[HttpResponse]] in HTML format.
     */
    private def exceptionToHtmlHttpResponseV1(ex: Throwable, settings: KnoraSettingsImpl): HttpResponse = {
        // Get the API status code that corresponds to the exception.
        val apiStatus: ApiStatusCodesV1.Value = ApiStatusCodesV1.fromException(ex)

        // Convert the API status code to the corresponding HTTP status code.
        val httpStatus: StatusCode = ApiStatusCodesV1.toHttpStatus(apiStatus)

        // Generate an HTTP response containing the error message, the API status code, and the HTTP status code.
        HttpResponse(
            status = httpStatus,
            entity = HttpEntity(
                ContentTypes.`text/xml(UTF-8)`,
                <html xmlns="http://www.w3.org/1999/xhtml">
                    <head>
                        <title>Error</title>
                    </head>
                    <body>
                        <h2>Error</h2>
                        <p>
                            <code>
                                {makeClientErrorMessage(ex, settings)}
                            </code>
                        </p>
                        <h2>Status code</h2>
                        <p>
                            <code>
                                {apiStatus.id}
                            </code>
                        </p>
                    </body>
                </html>.toString()
            )
        )
    }

    /**
     * Converts an exception to an HTTP response in HTML format specific to `V2`.
     *
     * @param ex the exception to be converted.
     * @return an [[HttpResponse]] in HTML format.
     */
    private def exceptionToHtmlHttpResponseV2(ex: Throwable, settings: KnoraSettingsImpl): HttpResponse = {

        // Get the HTTP status code that corresponds to the exception.
        val httpStatus: StatusCode = ApiStatusCodesV2.fromException(ex)

        // Generate an HTTP response containing the error message and the HTTP status code.
        HttpResponse(
            status = httpStatus,
            entity = HttpEntity(
                ContentTypes.`text/xml(UTF-8)`,
                <html xmlns="http://www.w3.org/1999/xhtml">
                    <head>
                        <title>Error</title>
                    </head>
                    <body>
                        <h2>Error</h2>
                        <p>
                            <code>
                                {makeClientErrorMessage(ex, settings)}
                            </code>
                        </p>
                    </body>
                </html>.toString()
            )
        )
    }

    /**
     * Converts an exception to an HTTP response in HTML format specific to `ADM`.
     *
     * @param ex the exception to be converted.
     * @return an [[HttpResponse]] in HTML format.
     */
    private def exceptionToHtmlHttpResponseADM(ex: Throwable, settings: KnoraSettingsImpl): HttpResponse = {

        // Get the HTTP status code that corresponds to the exception.
        val httpStatus: StatusCode = ApiStatusCodesV2.fromException(ex)

        // Generate an HTTP response containing the error message and the HTTP status code.
        HttpResponse(
            status = httpStatus,
            entity = HttpEntity(
                ContentTypes.`text/xml(UTF-8)`,
                <html xmlns="http://www.w3.org/1999/xhtml">
                    <head>
                        <title>Error</title>
                    </head>
                    <body>
                        <h2>Error</h2>
                        <p>
                            <code>
                                {makeClientErrorMessage(ex, settings)}
                            </code>
                        </p>
                    </body>
                </html>.toString()
            )
        )
    }

    /**
     * Given an exception, returns an error message suitable for clients.
     *
     * @param ex       the exception.
     * @param settings the application settings.
     * @return an error message suitable for clients.
     */
    private def makeClientErrorMessage(ex: Throwable, settings: KnoraSettingsImpl): String = {
        ex match {
            case rre: RequestRejectedException => rre.toString

            case other =>
                if (settings.showInternalErrors) {
                    other.toString
                } else {
                    GENERIC_INTERNAL_SERVER_ERROR_MESSAGE
                }
        }
    }

}
