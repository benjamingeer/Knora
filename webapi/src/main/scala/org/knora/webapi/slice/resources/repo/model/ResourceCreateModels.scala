/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo.model

import java.time.Instant
import java.util.UUID

import org.knora.webapi.messages.util.CalendarNameV2
import org.knora.webapi.messages.util.DatePrecisionV2
import org.knora.webapi.messages.v2.responder.valuemessages.FileValueV2
import org.knora.webapi.slice.resourceinfo.domain.InternalIri

final case class ResourceReadyToCreate(
  resourceIri: InternalIri,
  resourceClassIri: InternalIri,
  resourceLabel: String,
  creationDate: Instant,
  permissions: String,
  valueInfos: Seq[ValueInfo],
  standoffLinks: Seq[StandoffLinkValueInfo],
)

final case class ValueInfo(
  resourceIri: InternalIri,
  propertyIri: InternalIri,
  valueIri: InternalIri,
  valueTypeIri: InternalIri,
  valueUUID: UUID,
  value: TypeSpecificValueInfo,
  permissions: String,
  creator: InternalIri,
  creationDate: Instant,
  valueHasOrder: Int,
  valueHasString: String,
  comment: Option[String],
)

enum FormattedTextValueType {
  case StandardMapping
  case CustomMapping(mappingIri: InternalIri)
}

sealed trait FileValueTypeSpecificInfo {
  def fileValue: FileValueV2
}

enum TypeSpecificValueInfo {
  case LinkValueInfo(referredResourceIri: InternalIri)
  case UnformattedTextValueInfo(valueHasLanguage: Option[String])
  case FormattedTextValueInfo(
    valueHasLanguage: Option[String],
    mappingIri: InternalIri,
    maxStandoffStartIndex: Int,
    standoff: Seq[StandoffTagInfo],
    textValueType: FormattedTextValueType,
  )
  case IntegerValueInfo(valueHasInteger: Int)
  case DecimalValueInfo(valueHasDecimal: BigDecimal)
  case BooleanValueInfo(valueHasBoolean: Boolean)
  case UriValueInfo(valueHasUri: String)
  case DateValueInfo(
    valueHasStartJDN: Int,
    valueHasEndJDN: Int,
    valueHasStartPrecision: DatePrecisionV2,
    valueHasEndPrecision: DatePrecisionV2,
    valueHasCalendar: CalendarNameV2,
  )
  case ColorValueInfo(valueHasColor: String)
  case GeomValueInfo(valueHasGeometry: String)
  case StillImageFileValueInfo(
    fileValue: FileValueV2,
    dimX: Int,
    dimY: Int,
  ) extends TypeSpecificValueInfo with FileValueTypeSpecificInfo
  case StillImageExternalFileValueInfo(
    fileValue: FileValueV2,
    externalUrl: String,
  ) extends TypeSpecificValueInfo with FileValueTypeSpecificInfo
  case DocumentFileValueInfo(
    fileValue: FileValueV2,
    dimX: Option[Int],
    dimY: Option[Int],
    pageCount: Option[Int],
  )                                               extends TypeSpecificValueInfo with FileValueTypeSpecificInfo
  case OtherFileValueInfo(fileValue: FileValueV2) extends TypeSpecificValueInfo with FileValueTypeSpecificInfo
  case HierarchicalListValueInfo(valueHasListNode: InternalIri)
  case IntervalValueInfo(valueHasIntervalStart: BigDecimal, valueHasIntervalEnd: BigDecimal)
  case TimeValueInfo(valueHasTimeStamp: Instant)
  case GeonameValueInfo(valueHasGeonameCode: String)
}

final case class StandoffLinkValueInfo(
  linkPropertyIri: InternalIri,
  newLinkValueIri: InternalIri,
  linkTargetIri: InternalIri,
  newReferenceCount: Int,
  newLinkValueCreator: InternalIri,
  newLinkValuePermissions: String,
  valueUuid: String,
)

enum StandoffAttributeValue {
  case IriAttribute(value: InternalIri)
  case UriAttribute(value: String)
  case InternalReferenceAttribute(value: InternalIri)
  case StringAttribute(value: String)
  case IntegerAttribute(value: Int)
  case DecimalAttribute(value: BigDecimal)
  case BooleanAttribute(value: Boolean)
  case TimeAttribute(value: Instant)
}

final case class StandoffAttribute(
  propertyIri: InternalIri,
  value: StandoffAttributeValue,
)

final case class StandoffTagInfo(
  standoffTagClassIri: InternalIri,
  standoffTagInstanceIri: InternalIri,
  startParentIri: Option[InternalIri],
  endParentIri: Option[InternalIri],
  uuid: UUID,
  originalXMLID: Option[String],
  startIndex: Int,
  endIndex: Option[Int],
  startPosition: Int,
  endPosition: Int,
  attributes: Seq[StandoffAttribute],
)
