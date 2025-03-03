/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import zio.*

import java.net.URLDecoder
import java.util.UUID

import dsp.errors.BadRequestException
import dsp.valueobjects.Iri
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.IRI
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

object RouteUtilZ { self =>

  /**
   * Url decodes a [[String]].
   * Fails if String is not a well-formed utf-8 [[String]] in `application/x-www-form-urlencoded` MIME format.
   *
   * Wraps Java's [[java.net.URLDecoder#decode(java.lang.String, java.lang.String)]] into the zio world.
   *
   * @param value The value in utf-8 to be url decoded
   * @param errorMsg Custom error message for the error type
   * @return '''success''' the decoded string
   *
   *         '''failure''' A [[BadRequestException]] with the `errorMsg`
   */
  def urlDecode(value: String, errorMsg: String = ""): IO[BadRequestException, IRI] =
    ZIO
      .attempt(URLDecoder.decode(value, "utf-8"))
      .orElseFail(
        BadRequestException(
          if (!errorMsg.isBlank) errorMsg else s"Not an url encoded utf-8 String '$value'",
        ),
      )

  def ensureExternalOntologyName(iri: SmartIri): IO[BadRequestException, SmartIri] =
    if (StringFormatter.isKnoraOntologyIri(iri)) {
      ZIO.fail(BadRequestException(s"Internal ontology <$iri> cannot be served"))
    } else {
      ZIO.succeed(iri)
    }

  def ensureIsNotKnoraOntologyIri(iri: SmartIri): IO[BadRequestException, SmartIri] =
    ZIO
      .succeed(iri)
      .filterOrFail(!_.isKnoraOntologyIri)(BadRequestException(s"Iri is a Knora ontology iri: $iri"))

  def ensureApiV2ComplexSchema(iri: SmartIri): IO[BadRequestException, SmartIri] =
    ZIO
      .succeed(iri)
      .filterOrFail(_.getOntologySchema.contains(ApiV2Complex))(BadRequestException(s"Invalid schema for <$iri>"))

  def validateAndEscapeIri(s: String, errorMsg: String): IO[BadRequestException, IRI] =
    Iri.validateAndEscapeIri(s).toZIO.orElseFail(BadRequestException(errorMsg))

  def toSmartIri(s: String): ZIO[IriConverter, Throwable, SmartIri] =
    ZIO.serviceWithZIO[IriConverter](_.asSmartIri(s))

  def toSmartIri(s: String, errorMsg: String): ZIO[IriConverter, BadRequestException, SmartIri] =
    toSmartIri(s).orElseFail(BadRequestException(errorMsg))

  def randomUuid(): UIO[UUID] = Random.nextUUID

  def toSparqlEncodedString(s: String, errorMsg: String): IO[BadRequestException, String] =
    ZIO.fromOption(Iri.toSparqlEncodedString(s)).orElseFail(BadRequestException(errorMsg))

  def externalApiV2ComplexOntologyIri(str: String): ZIO[IriConverter, BadRequestException, OntologyIri] =
    externalOntologyIri(str)
      .filterOrFail(_.smartIri.isApiV2ComplexSchema)(())
      .orElseFail(BadRequestException(s"Invalid external API V2 complex ontology IRI: $str"))

  def externalOntologyIri(str: String): ZIO[IriConverter, BadRequestException, OntologyIri] = self
    .ontologyIri(str)
    .filterOrFail(_.isExternal)(())
    .orElseFail(BadRequestException(s"Invalid external ontology IRI: $str"))

  def ontologyIri(str: String): ZIO[IriConverter, BadRequestException, OntologyIri] = self
    .toSmartIri(str)
    .flatMap(s => ZIO.fromEither(OntologyIri.from(s)))
    .orElseFail(BadRequestException(s"Invalid ontology IRI: $str"))
}
