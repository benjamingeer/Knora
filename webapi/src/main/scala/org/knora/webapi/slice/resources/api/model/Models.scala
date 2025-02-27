/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api.model

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

  given TapirCodec.StringCodec[ValueUuid] = TapirCodec.stringCodec(from, _.value.toString)

  override def from(str: String): Either[String, ValueUuid] =
    Try(UuidUtil.decode(str)).toEither.left.map(_.getMessage).map(ValueUuid(_))
}

final case class VersionDate private (value: Instant) extends Value[Instant]
object VersionDate extends WithFrom[String, VersionDate] {

  given TapirCodec.StringCodec[VersionDate] = TapirCodec.stringCodec(from, _.value.toString)

  override def from(str: String): Either[String, VersionDate] =
    ValuesValidator
      .xsdDateTimeStampToInstant(str)
      .orElse(ValuesValidator.arkTimestampToInstant(str))
      .map(VersionDate(_))
      .toRight(s"Invalid value for instant: $str")
}

final case class IriDto(value: String)
object IriDto {

  given TapirCodec.StringCodec[IriDto] = TapirCodec.stringCodec(IriDto.from, _.value)

  def from(str: String): Either[String, IriDto] =
    if Iri.isIri(str) then Right(IriDto(str)) else Left(s"Invalid IRI: $str")
}
