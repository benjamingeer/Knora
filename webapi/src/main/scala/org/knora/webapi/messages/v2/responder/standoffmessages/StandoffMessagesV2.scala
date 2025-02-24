/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder.standoffmessages

import java.time.Instant
import java.util.UUID
import scala.collection.immutable.SortedSet

import dsp.errors.AssertionException
import dsp.valueobjects.UuidUtil
import org.knora.webapi.*
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.RelayedMessage
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.ResponderRequest.KnoraRequestV2
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.rdf.*
import org.knora.webapi.messages.v2.responder.KnoraContentV2
import org.knora.webapi.messages.v2.responder.KnoraJsonLDResponseV2
import org.knora.webapi.messages.v2.responder.ontologymessages.StandoffEntityInfoGetResponseV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User

/**
 * An abstract trait representing a Knora v2 API request message that can be sent to `StandoffResponderV2`.
 */
sealed trait StandoffResponderRequestV2 extends KnoraRequestV2 with RelayedMessage

/**
 * Represents a request to create a mapping between XML elements and attributes and standoff classes and properties.
 * A successful response will be a [[CreateMappingResponseV2]].
 *
 * @param metadata             the metadata describing the mapping.
 * @param xml                  the mapping in XML syntax.
 * @param requestingUser       the the user making the request.
 * @param apiRequestID         the ID of the API request.
 */
case class CreateMappingRequestV2(
  metadata: CreateMappingRequestMetadataV2,
  xml: CreateMappingRequestXMLV2,
  requestingUser: User,
  apiRequestID: UUID,
) extends StandoffResponderRequestV2

/**
 * Represents the metadata describing the mapping that is to be created.
 *
 * @param label       the label describing the mapping.
 * @param projectIri  the IRI of the project the mapping belongs to.
 * @param mappingName the name of the mapping to be created.
 */
case class CreateMappingRequestMetadataV2(label: String, projectIri: ProjectIri, mappingName: String)
    extends StandoffResponderRequestV2

/**
 * Represents the mapping as an XML document.
 *
 * @param xml the mapping to be created.
 */
case class CreateMappingRequestXMLV2(xml: String) extends StandoffResponderRequestV2

/**
 * Provides the IRI of the created mapping.
 *
 * @param mappingIri the IRI of the resource (knora-base:XMLToStandoffMapping) representing the mapping that has been created.
 * @param label      the label describing the mapping.
 * @param projectIri the project the mapping belongs to.
 */
case class CreateMappingResponseV2(mappingIri: IRI, label: String, projectIri: ProjectIri)
    extends KnoraJsonLDResponseV2 {

  def toJsonLDDocument(
    targetSchema: ApiV2Schema,
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
  ): JsonLDDocument = {

    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    val body = JsonLDObject(
      Map(
        JsonLDKeywords.ID -> JsonLDString(mappingIri),
        JsonLDKeywords.TYPE -> JsonLDString(
          OntologyConstants.KnoraBase.XMLToStandoffMapping.toSmartIri.toOntologySchema(targetSchema).toString,
        ),
        OntologyConstants.Rdfs.Label -> JsonLDString(label),
        OntologyConstants.KnoraApiV2Complex.AttachedToProject.toSmartIri
          .toOntologySchema(targetSchema)
          .toString -> JsonLDUtil.iriToJsonLDObject(projectIri.toString),
      ),
    )

    val context = JsonLDObject(
      Map(
        "rdfs" -> JsonLDString("http://www.w3.org/2000/01/rdf-schema#"),
        "rdf"  -> JsonLDString("http://www.w3.org/1999/02/22-rdf-syntax-ns#"),
        "owl"  -> JsonLDString("http://www.w3.org/2002/07/owl#"),
        "xsd"  -> JsonLDString("http://www.w3.org/2001/XMLSchema#"),
      ),
    )

    JsonLDDocument(body, context)
  }
}

/**
 * Represents a request to get a mapping from XML elements and attributes to standoff entities.
 *
 * @param mappingIri           the IRI of the mapping.
 * @param requestingUser       the the user making the request.
 */
case class GetMappingRequestV2(mappingIri: IRI) extends StandoffResponderRequestV2

/**
 * Represents a response to a [[GetMappingRequestV2]].
 *
 * @param mappingIri       the IRI of the requested mapping.
 * @param mapping          the requested mapping.
 * @param standoffEntities the standoff entities referred to in the mapping.
 */
case class GetMappingResponseV2(
  mappingIri: IRI,
  mapping: MappingXMLtoStandoff,
  standoffEntities: StandoffEntityInfoGetResponseV2,
) // TODO: there should be a route to obtain a mapping

/**
 * Represents a request that gets an XSL Transformation represented by a `knora-base:XSLTransformation`.
 *
 * @param xsltTextRepresentationIri the IRI of the `knora-base:XSLTransformation`.
 * @param requestingUser            the the user making the request.
 */
case class GetXSLTransformationRequestV2(
  xsltTextRepresentationIri: IRI,
  requestingUser: User,
) extends StandoffResponderRequestV2

/**
 * Represents a response to a [[GetXSLTransformationRequestV2]].
 *
 * @param xslt the XSLT to be applied to the XML created from standoff.
 */
case class GetXSLTransformationResponseV2(xslt: String)

/**
 * Represents a mapping between XML tags and standoff entities (classes and properties).
 *
 * Example:
 *
 * namespace = Map("myXMLNamespace" -> Map("myXMLTagName" -> Map("myXMLClassname" -> XMLTag(...))))
 *
 * The class names allow for the reuse of the same tag name. This is important when using HTML since the tag set is very limited.
 *
 * @param namespace                a Map of XML namespaces and a Map of tag names and [[XMLTag]].
 * @param defaultXSLTransformation the IRI of the default XSL transformation for the resulting XML, if any.
 */
case class MappingXMLtoStandoff(
  namespace: Map[String, Map[String, Map[String, XMLTag]]],
  defaultXSLTransformation: Option[IRI],
)

/**
 * Represents a mapping between an XML tag and standoff entities (classes and properties).
 *
 * @param name              the tag name.
 * @param mapping           the corresponding standoff entities.
 * @param separatorRequired indicates if the element requires a separator in the text once the markup has been converted to standoff.
 */
case class XMLTag(name: String, mapping: XMLTagToStandoffClass, separatorRequired: Boolean)

/**
 * Represents standoff entities referred to in the mapping.
 * The attributes are represented as a Map of namespaces and a Map of attribute names and standoff properties.
 *
 * Example for attributesToProps:
 *
 * attributesToProps = Map("myXMLNamespace" -> Map("myXMLAttributeName" -> "standoffPropertyIri"))
 *
 * @param standoffClassIri  the IRI of the standoff class.
 * @param attributesToProps a mapping between XML namespaces and attribute names and standoff properties.
 * @param dataType          the data type of the standoff class (e.g., a date).
 */
case class XMLTagToStandoffClass(
  standoffClassIri: IRI,
  attributesToProps: Map[String, Map[String, IRI]] = Map.empty[String, Map[String, IRI]],
  dataType: Option[XMLStandoffDataTypeClass],
)

/**
 * Represents a data type standoff class in mapping for an XML element.
 *
 * @param standoffDataTypeClass the data type of the standoff class (e.g., a date).
 * @param dataTypeXMLAttribute  the XML attribute holding the information needed for the standoff class data type (e.g., a date string).
 */
case class XMLStandoffDataTypeClass(standoffDataTypeClass: StandoffDataTypeClasses.Value, dataTypeXMLAttribute: String)

/**
 * Represents the data types of standoff classes.
 */
object StandoffDataTypeClasses extends Enumeration {

  val StandoffLinkTag: Value = Value(OntologyConstants.KnoraBase.StandoffLinkTag)

  val StandoffDateTag: Value = Value(OntologyConstants.KnoraBase.StandoffDateTag)

  val StandoffUriTag: Value = Value(OntologyConstants.KnoraBase.StandoffUriTag)

  val StandoffColorTag: Value = Value(OntologyConstants.KnoraBase.StandoffColorTag)

  val StandoffIntegerTag: Value = Value(OntologyConstants.KnoraBase.StandoffIntegerTag)

  val StandoffDecimalTag: Value = Value(OntologyConstants.KnoraBase.StandoffDecimalTag)

  val StandoffIntervalTag: Value = Value(OntologyConstants.KnoraBase.StandoffIntervalTag)

  val StandoffTimeTag: Value = Value(OntologyConstants.KnoraBase.StandoffTimeTag)

  val StandoffBooleanTag: Value = Value(OntologyConstants.KnoraBase.StandoffBooleanTag)

  val StandoffInternalReferenceTag: Value = Value(OntologyConstants.KnoraBase.StandoffInternalReferenceTag)

  val valueMap: Map[IRI, Value] = values.toList.map(v => (v.toString, v)).toMap

  /**
   * Given the name of a value in this enumeration, returns the value. If the value is not found, throws an
   * exception.
   *
   * @param name     the name of the value.
   * @param errorFun the function to be called in case of an error.
   * @return the requested value.
   */
  def lookup(name: String, errorFun: => Nothing): Value =
    valueMap.get(name) match {
      case Some(value) => value
      case None        => errorFun
    }

  def getStandoffClassIris: SortedSet[IRI] = StandoffDataTypeClasses.values.map(_.toString)

}

/**
 * Represents collections of standoff properties.
 */
object StandoffProperties {

  // represents the standoff properties defined on the base standoff tag
  val systemProperties: Set[IRI] = Set(
    OntologyConstants.KnoraBase.StandoffTagHasStart,
    OntologyConstants.KnoraBase.StandoffTagHasEnd,
    OntologyConstants.KnoraBase.StandoffTagHasStartIndex,
    OntologyConstants.KnoraBase.StandoffTagHasEndIndex,
    OntologyConstants.KnoraBase.StandoffTagHasStartParent,
    OntologyConstants.KnoraBase.StandoffTagHasEndParent,
    OntologyConstants.KnoraBase.StandoffTagHasUUID,
    OntologyConstants.KnoraBase.StandoffTagHasOriginalXMLID,
    OntologyConstants.KnoraBase.TargetHasOriginalXMLID,
  )

  // represents the standoff properties defined on the date standoff tag
  val dateProperties: Set[IRI] = Set(
    OntologyConstants.KnoraBase.ValueHasCalendar,
    OntologyConstants.KnoraBase.ValueHasStartJDN,
    OntologyConstants.KnoraBase.ValueHasEndJDN,
    OntologyConstants.KnoraBase.ValueHasStartPrecision,
    OntologyConstants.KnoraBase.ValueHasEndPrecision,
  )

  // represents the standoff properties defined on the interval standoff tag
  val intervalProperties: Set[IRI] = Set(
    OntologyConstants.KnoraBase.ValueHasIntervalStart,
    OntologyConstants.KnoraBase.ValueHasIntervalEnd,
  )

  // represents the standoff properties defined on the time standoff tag
  val timeProperties: Set[IRI] = Set(
    OntologyConstants.KnoraBase.ValueHasTimeStamp,
  )

  // represents the standoff properties defined on the boolean standoff tag
  val booleanProperties: Set[IRI] = Set(OntologyConstants.KnoraBase.ValueHasBoolean)

  // represents the standoff properties defined on the decimal standoff tag
  val decimalProperties: Set[IRI] = Set(OntologyConstants.KnoraBase.ValueHasDecimal)

  // represents the standoff properties defined on the integer standoff tag
  val integerProperties: Set[IRI] = Set(OntologyConstants.KnoraBase.ValueHasInteger)

  // represents the standoff properties defined on the uri standoff tag
  val uriProperties: Set[IRI] = Set(OntologyConstants.KnoraBase.ValueHasUri)

  // represents the standoff properties defined on the color standoff tag
  val colorProperties: Set[IRI] = Set(OntologyConstants.KnoraBase.ValueHasColor)

  // represents the standoff properties defined on the link standoff tag
  val linkProperties: Set[IRI] = Set(OntologyConstants.KnoraBase.StandoffTagHasLink)

  // represents the standoff properties defined on the internal reference standoff tag
  val internalReferenceProperties: Set[IRI] = Set(OntologyConstants.KnoraBase.StandoffTagHasInternalReference)

  val dataTypeProperties: Set[IRI] =
    dateProperties ++ intervalProperties ++ timeProperties ++ booleanProperties ++ decimalProperties ++
      integerProperties ++ uriProperties ++ colorProperties ++ linkProperties ++ internalReferenceProperties
}

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Standoff tags and their components.

/**
 * A trait representing an attribute attached to a standoff tag.
 */
sealed trait StandoffTagAttributeV2 extends KnoraContentV2[StandoffTagAttributeV2] {
  implicit protected val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  def standoffPropertyIri: SmartIri

  def stringValue: String

  def rdfValue: String

  def toJsonLD: (IRI, JsonLDValue)
}

/**
 * Represents a standoff tag attribute of type IRI.
 *
 * @param standoffPropertyIri the IRI of the standoff property
 * @param value               the value of the standoff property.
 * @param targetExists        `true` if the specified IRI already exists in the triplestore, `false` if it is
 *                            being created in the same transaction.
 */
case class StandoffTagIriAttributeV2(standoffPropertyIri: SmartIri, value: IRI, targetExists: Boolean = true)
    extends StandoffTagAttributeV2 {

  def stringValue: String = value

  def rdfValue: String = s"<$value>"

  override def toOntologySchema(targetSchema: OntologySchema): StandoffTagAttributeV2 =
    copy(standoffPropertyIri = standoffPropertyIri.toOntologySchema(targetSchema))

  override def toJsonLD: (IRI, JsonLDValue) =
    standoffPropertyIri.toString -> JsonLDUtil.iriToJsonLDObject(value)
}

/**
 * Represents a standoff tag attribute of type URI.
 *
 * @param standoffPropertyIri the IRI of the standoff property
 * @param value               the value of the standoff property.
 */
case class StandoffTagUriAttributeV2(standoffPropertyIri: SmartIri, value: String) extends StandoffTagAttributeV2 {

  def stringValue: String = value

  def rdfValue: String = s""""${stringValue.toString}"^^xsd:anyURI"""

  override def toOntologySchema(targetSchema: OntologySchema): StandoffTagAttributeV2 =
    copy(standoffPropertyIri = standoffPropertyIri.toOntologySchema(targetSchema))

  override def toJsonLD: (IRI, JsonLDValue) =
    standoffPropertyIri.toString -> JsonLDUtil.datatypeValueToJsonLDObject(
      value = value,
      datatype = OntologyConstants.Xsd.Uri.toSmartIri,
    )
}

/**
 * Represents a standoff tag attribute that refers to another standoff node.
 *
 * @param standoffPropertyIri the IRI of the standoff property
 * @param value               the value of the standoff property.
 */
case class StandoffTagInternalReferenceAttributeV2(standoffPropertyIri: SmartIri, value: IRI)
    extends StandoffTagAttributeV2 {

  def stringValue: String = value.toString

  def rdfValue: String = s"<$value>"

  override def toOntologySchema(targetSchema: OntologySchema): StandoffTagAttributeV2 =
    copy(standoffPropertyIri = standoffPropertyIri.toOntologySchema(targetSchema))

  override def toJsonLD: (IRI, JsonLDValue) =
    standoffPropertyIri.toString -> JsonLDUtil.iriToJsonLDObject(value)
}

/**
 * Represents a standoff tag attribute of type string.
 *
 * @param standoffPropertyIri the IRI of the standoff property
 * @param value               the value of the standoff property.
 */
case class StandoffTagStringAttributeV2(standoffPropertyIri: SmartIri, value: String) extends StandoffTagAttributeV2 {

  def stringValue: String = value

  def rdfValue: String = s"""\"\"\"$value\"\"\""""

  override def toOntologySchema(targetSchema: OntologySchema): StandoffTagAttributeV2 =
    copy(standoffPropertyIri = standoffPropertyIri.toOntologySchema(targetSchema))

  override def toJsonLD: (IRI, JsonLDValue) =
    standoffPropertyIri.toString -> JsonLDString(value)
}

/**
 * Represents a standoff tag attribute of type integer.
 *
 * @param standoffPropertyIri the IRI of the standoff property
 * @param value               the value of the standoff property.
 */
case class StandoffTagIntegerAttributeV2(standoffPropertyIri: SmartIri, value: Int) extends StandoffTagAttributeV2 {

  def stringValue: String = value.toString

  def rdfValue: String = value.toString

  override def toOntologySchema(targetSchema: OntologySchema): StandoffTagAttributeV2 =
    copy(standoffPropertyIri = standoffPropertyIri.toOntologySchema(targetSchema))

  override def toJsonLD: (IRI, JsonLDValue) =
    standoffPropertyIri.toString -> JsonLDInt(value)
}

/**
 * Represents a standoff tag attribute of type decimal.
 *
 * @param standoffPropertyIri the IRI of the standoff property
 * @param value               the value of the standoff property.
 */
case class StandoffTagDecimalAttributeV2(standoffPropertyIri: SmartIri, value: BigDecimal)
    extends StandoffTagAttributeV2 {

  def stringValue: String = value.toString

  def rdfValue: String = s""""${value.toString}"^^xsd:decimal"""

  override def toOntologySchema(targetSchema: OntologySchema): StandoffTagAttributeV2 =
    copy(standoffPropertyIri = standoffPropertyIri.toOntologySchema(targetSchema))

  override def toJsonLD: (IRI, JsonLDValue) =
    standoffPropertyIri.toString -> JsonLDUtil.datatypeValueToJsonLDObject(
      value = value.toString,
      datatype = OntologyConstants.Xsd.Decimal.toSmartIri,
    )
}

/**
 * Represents a standoff tag attribute of type boolean.
 *
 * @param standoffPropertyIri the IRI of the standoff property
 * @param value               the value of the standoff property.
 */
case class StandoffTagBooleanAttributeV2(standoffPropertyIri: SmartIri, value: Boolean) extends StandoffTagAttributeV2 {

  def stringValue: String = value.toString

  def rdfValue: String = value.toString

  override def toOntologySchema(targetSchema: OntologySchema): StandoffTagAttributeV2 =
    copy(standoffPropertyIri = standoffPropertyIri.toOntologySchema(targetSchema))

  override def toJsonLD: (IRI, JsonLDValue) =
    standoffPropertyIri.toString -> JsonLDBoolean(value)
}

/**
 * Represents a standoff tag attribute of type xsd:dateTimeStamp.
 *
 * @param standoffPropertyIri the IRI of the standoff property
 * @param value               the value of the standoff property.
 */
case class StandoffTagTimeAttributeV2(standoffPropertyIri: SmartIri, value: Instant) extends StandoffTagAttributeV2 {

  def stringValue: String = value.toString

  def rdfValue: String = s""""${value.toString}"^^xsd:dateTime"""

  override def toOntologySchema(targetSchema: OntologySchema): StandoffTagAttributeV2 =
    copy(standoffPropertyIri = standoffPropertyIri.toOntologySchema(targetSchema))

  override def toJsonLD: (IRI, JsonLDValue) =
    standoffPropertyIri.toString -> JsonLDUtil.datatypeValueToJsonLDObject(
      value = value.toString,
      datatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
    )
}

/**
 * Represents any subclass of a `knora-base:StandoffTag`.
 *
 * @param standoffTagClassIri the IRI of the standoff class to be created.
 * @param dataType            the data type of the standoff class, if any.
 * @param uuid                a [[UUID]] representing this tag and any other tags that
 *                            point to semantically equivalent ranges in other versions of the same text.
 * @param startPosition       the start position of the range of characters marked up with this tag.
 * @param endPosition         the end position of the range of characters marked up with this tag.
 * @param startIndex          the index of this tag (start index in case of a virtual hierarchy tag that has two parents). Indexes are numbered from 0 within the context of a particular text,
 *                            and make it possible to order tags that share the same position.
 * @param endIndex            the index of the end position (only in case of a virtual hierarchy tag).
 * @param startParentIndex    the index of the parent node (start index in case of a virtual hierarchy tag that has two parents), if any, that contains the start position.
 * @param endParentIndex      the index of the the parent node (only in case of a virtual hierarchy tag), if any, that contains the end position.
 * @param attributes          the attributes attached to this tag.
 */
case class StandoffTagV2(
  standoffTagClassIri: SmartIri,
  dataType: Option[StandoffDataTypeClasses.Value] = None,
  uuid: UUID,
  originalXMLID: Option[String],
  startPosition: Int,
  endPosition: Int,
  startIndex: Int,
  endIndex: Option[Int] = None,
  startParentIndex: Option[Int] = None,
  endParentIndex: Option[Int] = None,
  attributes: Seq[StandoffTagAttributeV2] = Seq.empty[StandoffTagAttributeV2],
) extends KnoraContentV2[StandoffTagV2] {
  override def toOntologySchema(targetSchema: OntologySchema): StandoffTagV2 = {
    if (targetSchema != ApiV2Complex) {
      throw AssertionException(s"Standoff is available only in the complex schema")
    }

    copy(
      standoffTagClassIri = standoffTagClassIri.toOntologySchema(targetSchema),
      attributes = attributes.map(_.toOntologySchema(targetSchema)),
    )
  }

  def toJsonLDValue(targetSchema: OntologySchema): JsonLDValue = {
    if (targetSchema != ApiV2Complex) {
      throw AssertionException(s"Standoff is available only in the complex schema")
    }

    val attributesAsJsonLD: Map[IRI, JsonLDValue] = attributes.map(_.toJsonLD).toMap

    val contentMap: Map[IRI, JsonLDValue] = Map(
      JsonLDKeywords.TYPE                                          -> JsonLDString(standoffTagClassIri.toString),
      OntologyConstants.KnoraApiV2Complex.StandoffTagHasUUID       -> JsonLDString(UuidUtil.base64Encode(uuid)),
      OntologyConstants.KnoraApiV2Complex.StandoffTagHasStart      -> JsonLDInt(startPosition),
      OntologyConstants.KnoraApiV2Complex.StandoffTagHasEnd        -> JsonLDInt(endPosition),
      OntologyConstants.KnoraApiV2Complex.StandoffTagHasStartIndex -> JsonLDInt(startIndex),
    )

    val endIndexStatement: Option[(IRI, JsonLDInt)] = endIndex.map { definedEndIndex =>
      OntologyConstants.KnoraApiV2Complex.StandoffTagHasEndIndex -> JsonLDInt(definedEndIndex)
    }

    val startParentIndexStatement: Option[(IRI, JsonLDInt)] = startParentIndex.map { definedStartParentIndex =>
      OntologyConstants.KnoraApiV2Complex.StandoffTagHasStartParentIndex -> JsonLDInt(definedStartParentIndex)
    }

    val endParentIndexStatement: Option[(IRI, JsonLDInt)] = endParentIndex.map { definedEndParentIndex =>
      OntologyConstants.KnoraApiV2Complex.StandoffTagHasEndParentIndex -> JsonLDInt(definedEndParentIndex)
    }

    val originalXMLIDStatement: Option[(IRI, JsonLDString)] = originalXMLID.map { definedOriginalXMLID =>
      OntologyConstants.KnoraApiV2Complex.StandoffTagHasOriginalXMLID -> JsonLDString(definedOriginalXMLID)
    }

    JsonLDObject(
      contentMap ++
        attributesAsJsonLD ++
        endIndexStatement ++
        startParentIndexStatement ++
        endParentIndexStatement ++
        originalXMLIDStatement,
    )
  }

  def getOntologyIrisUsed: Set[SmartIri] =
    attributes.map(_.standoffPropertyIri.getOntologyFromEntity).toSet + standoffTagClassIri.getOntologyFromEntity
}
