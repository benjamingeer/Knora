/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import sttp.tapir.Schema
import zio.NonEmptyChunk
import zio.json.*

import scala.util.matching.Regex

import dsp.valueobjects.Iri
import dsp.valueobjects.Iri.isProjectIri
import dsp.valueobjects.Iri.validateAndEscapeProjectIri
import dsp.valueobjects.IriErrorMessages
import dsp.valueobjects.UuidUtil
import dsp.valueobjects.V2
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.common.StringBasedValueCompanion
import org.knora.webapi.slice.common.Value
import org.knora.webapi.slice.common.Value.BooleanValue
import org.knora.webapi.slice.common.Value.StringValue
import org.knora.webapi.slice.common.WithFrom
import org.knora.webapi.slice.resourceinfo.domain.InternalIri

case class KnoraProject(
  id: ProjectIri,
  shortname: Shortname,
  shortcode: Shortcode,
  longname: Option[Longname],
  description: NonEmptyChunk[Description],
  keywords: List[Keyword],
  logo: Option[Logo],
  status: Status,
  selfjoin: SelfJoin,
  ontologies: List[InternalIri]
)

object KnoraProject {

  final case class ProjectIri private (override val value: String) extends AnyVal with StringValue

  object ProjectIri extends StringBasedValueCompanion[ProjectIri] {

    def from(str: String): Either[String, ProjectIri] = str match {
      case str if str.isEmpty        => Left(IriErrorMessages.ProjectIriMissing)
      case str if !isProjectIri(str) => Left(IriErrorMessages.ProjectIriInvalid)
      case str if UuidUtil.hasValidLength(str.split("/").last) && !UuidUtil.hasSupportedVersion(str) =>
        Left(IriErrorMessages.UuidVersionInvalid)
      case _ => validateAndEscapeProjectIri(str).toRight(IriErrorMessages.ProjectIriInvalid).map(ProjectIri.apply)
    }
  }

  final case class Shortcode private (override val value: String) extends AnyVal with StringValue

  object Shortcode extends StringBasedValueCompanion[Shortcode] {

    private val shortcodeRegex: Regex = "^\\p{XDigit}{4}$".r

    def from(value: String): Either[String, Shortcode] =
      if (shortcodeRegex.matches(value.toUpperCase)) Right(Shortcode(value.toUpperCase))
      else if (value.isEmpty) Left("Shortcode cannot be empty.")
      else Left(s"Shortcode is invalid: $value")
  }

  final case class Shortname private (override val value: String) extends AnyVal with StringValue

  object Shortname extends StringBasedValueCompanion[Shortname] {

    private val shortnameRegex: Regex = "^[a-zA-Z][a-zA-Z0-9_-]{2,19}$".r

    def from(value: String): Either[String, Shortname] =
      if (value.isEmpty) Left("Shortname cannot be empty.")
      else {
        val maybeShortname = value match {
          case "DefaultSharedOntologiesProject" => Some(value)
          case _                                => shortnameRegex.findFirstIn(value)
        }
        maybeShortname
          .flatMap(Iri.toSparqlEncodedString)
          .toRight(s"Shortname is invalid: $value")
          .map(Shortname.apply)
      }
  }

  final case class Longname private (override val value: String) extends AnyVal with StringValue

  object Longname extends StringBasedValueCompanion[Longname] {

    private val longnameRegex: Regex = "^.{3,256}$".r

    def from(value: String): Either[String, Longname] =
      if (longnameRegex.matches(value)) Right(Longname(value))
      else Left("Longname must be 3 to 256 characters long.")
  }

  final case class Description private (override val value: V2.StringLiteralV2)
      extends AnyVal
      with Value[V2.StringLiteralV2]

  object Description extends WithFrom[V2.StringLiteralV2, Description] {

    def from(literal: V2.StringLiteralV2): Either[String, Description] =
      if (literal.value.length >= 3 && literal.value.length <= 40960) Right(Description(literal))
      else Left("Description must be 3 to 40960 characters long.")
  }

  final case class Keyword private (override val value: String) extends AnyVal with StringValue

  object Keyword extends StringBasedValueCompanion[Keyword] {

    private val keywordRegex: Regex = "^.{3,64}$".r
    def from(str: String): Either[String, Keyword] =
      if (keywordRegex.matches(str)) Right(Keyword(str))
      else Left("Keyword must be 3 to 64 characters long.")
  }

  final case class Logo private (override val value: String) extends AnyVal with StringValue

  object Logo extends StringBasedValueCompanion[Logo] {

    def from(str: String): Either[String, Logo] =
      if (str.isEmpty) Left("Logo cannot be empty.")
      else Right(Logo(str))
  }

  sealed trait Status extends BooleanValue

  object Status {

    case object Active   extends Status { val value = true  }
    case object Inactive extends Status { val value = false }

    def from(value: Boolean): Status = if (value) Active else Inactive

    implicit val codec: JsonCodec[Status] = JsonCodec[Boolean].transformOrFail(b => Right(Status.from(b)), _.value)

    implicit val schema: Schema[Status] = Schema.schemaForBoolean.map(b => Some(Status.from(b)))(_.value)
  }

  sealed trait SelfJoin extends BooleanValue

  object SelfJoin {

    case object CanJoin    extends SelfJoin { val value = true  }
    case object CannotJoin extends SelfJoin { val value = false }

    def from(value: Boolean): SelfJoin = if (value) CanJoin else CannotJoin

    implicit val codec: JsonCodec[SelfJoin] = JsonCodec[Boolean].transformOrFail(b => Right(SelfJoin.from(b)), _.value)

    implicit val schema: Schema[SelfJoin] = Schema.schemaForBoolean.map(b => Some(SelfJoin.from(b)))(_.value)
  }
}
