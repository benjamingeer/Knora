/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api.model

import zio.json.JsonCodec

import java.time.Instant
import java.util.UUID
import scala.util.Try

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

final case class ValueVersionDate private (value: Instant) extends Value[Instant]
object ValueVersionDate extends WithFrom[String, ValueVersionDate] {

  given JsonCodec[ValueVersionDate]              = JsonCodec[String].transformOrFail(from, _.value.toString)
  given TapirCodec.StringCodec[ValueVersionDate] = TapirCodec.stringCodec(from, _.value.toString)

  override def from(str: String): Either[String, ValueVersionDate] =
    ValuesValidator
      .xsdDateTimeStampToInstant(str)
      .orElse(ValuesValidator.arkTimestampToInstant(str))
      .map(ValueVersionDate(_))
      .toRight(s"Invalid value version date: $str")
}
