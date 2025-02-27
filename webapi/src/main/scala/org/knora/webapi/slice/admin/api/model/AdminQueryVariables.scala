/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.model

import sttp.tapir.Codec
import sttp.tapir.CodecFormat
import sttp.tapir.DecodeResult
import sttp.tapir.EndpointInput
import sttp.tapir.query

import dsp.errors.BadRequestException
import org.knora.webapi.slice.admin.api.Codecs.TapirCodec
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode

object AdminQueryVariables {

  private implicit val projectIriOptionCodec: Codec[List[String], Option[ProjectIri], CodecFormat.TextPlain] =
    Codec.listHeadOption

  private implicit val shortcodeOptionCodec: Codec[List[String], Option[Shortcode], CodecFormat.TextPlain] =
    Codec.listHeadOption(TapirCodec.shortcode)

  val projectIriOption: EndpointInput.Query[Option[ProjectIri]] = query[Option[ProjectIri]]("projectIri")
    .description("The (optional) IRI of the project.")
    .example(Some(ProjectIri.unsafeFrom("http://rdfh.ch/projects/0042")))

  val projectShortcodeOption: EndpointInput.Query[Option[Shortcode]] = query[Option[Shortcode]]("projectShortcode")
    .description("The (optional) shortcode of the project.")
    .example(Some(Shortcode.unsafeFrom("0042")))

  val projectIriOrShortcodeQueryOption =
    projectIriOption
      .and(projectShortcodeOption)
      .mapDecode[Option[Either[ProjectIri, Shortcode]]] {
        case (Some(iri), None)       => DecodeResult.Value(Some(Left(iri)))
        case (None, Some(shortcode)) => DecodeResult.Value(Some(Right(shortcode)))
        case (Some(_), Some(_)) =>
          DecodeResult.Error(
            "Query params project IRI and shortcode are mutually exclusive",
            BadRequestException("Provide either a project IRI or a project shortcode"),
          )
        case _ => DecodeResult.Value(None)
      } {
        case Some(Left(iri))        => (Some(iri), None)
        case Some(Right(shortcode)) => (None, Some(shortcode));
        case None                   => (None, None)
      }
}
