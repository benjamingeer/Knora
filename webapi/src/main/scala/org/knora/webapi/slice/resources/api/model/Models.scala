/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api.model

import sttp.tapir.Codec
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.model.Delimited
import zio.json.JsonCodec

import java.time.Instant
import java.util.UUID
import scala.util.Try

import dsp.valueobjects.Iri
import dsp.valueobjects.UuidUtil
import org.knora.webapi.messages.ValuesValidator
import org.knora.webapi.slice.admin.api.Codecs.*
import org.knora.webapi.slice.common.Value
import org.knora.webapi.slice.common.WithFrom

final case class ValueUuid private (value: UUID) extends Value[UUID]
object ValueUuid extends WithFrom[String, ValueUuid] {

  given JsonCodec[ValueUuid]              = JsonCodec[String].transformOrFail(from, _.value.toString)
  given TapirCodec.StringCodec[ValueUuid] = TapirCodec.stringCodec(from, _.value.toString)

  override def from(str: String): Either[String, ValueUuid] =
    Try(UuidUtil.decode(str)).toEither.left.map(_.getMessage).map(ValueUuid(_))
}

final case class VersionDate private (value: Instant) extends Value[Instant]
object VersionDate extends WithFrom[String, VersionDate] {

  given JsonCodec[VersionDate]              = JsonCodec[String].transformOrFail(from, _.value.toString)
  given TapirCodec.StringCodec[VersionDate] = TapirCodec.stringCodec(from, _.value.toString)

  override def from(str: String): Either[String, VersionDate] =
    ValuesValidator
      .xsdDateTimeStampToInstant(str)
      .orElse(ValuesValidator.arkTimestampToInstant(str))
      .map(VersionDate(_))
      .toRight(s"Invalid value version date: $str")
}

final case class ResourceIri(value: String)
object ResourceIri {

  def from(str: String): Either[String, ResourceIri] =
    if Iri.isIri(str) then Right(ResourceIri(str)) else Left(s"Invalid IRI: $str")

  given TapirCodec.StringCodec[ResourceIri]                   = TapirCodec.stringCodec(ResourceIri.from, _.value)
  given Codec[String, Delimited[",", ResourceIri], TextPlain] = Codec.delimited
}
