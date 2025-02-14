/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder.ontologymessages

import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.vocabulary.XSD

import org.knora.webapi.*
import org.knora.webapi.LanguageCode.*
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Simple
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality.*
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadClassInfoV2Builder.*
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadPropertyInfoV2Builder.*
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo
import org.knora.webapi.slice.ontology.domain.model.Cardinality.*

/**
 * Rules for converting `knora-base` (or an ontology based on it) into `knora-api` in the [[ApiV2Simple]] schema.
 * See also [[OntologyConstants.CorrespondingIris]].
 */
object KnoraBaseToApiV2SimpleTransformationRules extends OntologyTransformationRules {

  private implicit val stringFormatter: StringFormatter = StringFormatter.getInstanceForConstantOntologies

  override val ontologyMetadata: OntologyMetadataV2 = OntologyMetadataV2(
    ontologyIri = OntologyConstants.KnoraApiV2Simple.KnoraApiOntologyIri.toSmartIri,
    projectIri = Some(KnoraProjectRepo.builtIn.SystemProject.id.value.toSmartIri),
    label = Some("The knora-api ontology in the simple schema"),
  )

  private val Label = makeOwlDatatypeProperty(RDFS.LABEL)

  private val Result = makeOwlDatatypeProperty(KnoraApiV2Simple.Result, XSD.STRING)
    .withRdfLabel(Map(DE -> "Ergebnis", EN -> "result", FR -> "résultat", IT -> "risultato"))
    .withRdfCommentEn("Provides a message indicating that an operation was successful")

  private val MayHaveMoreResults = makeOwlDatatypeProperty(KnoraApiV2Simple.MayHaveMoreResults, XSD.BOOLEAN)
    .withRdfLabelEn("May have more results")
    .withRdfCommentEn("Indicates whether more results may be available for a search query")

  private val Error = makeOwlDatatypeProperty(KnoraApiV2Simple.Error, XSD.STRING)
    .withRdfLabel(Map(DE -> "Fehler", EN -> "error", FR -> "erreur", IT -> "errore"))
    .withRdfCommentEn("Provides a message indicating that an operation was unsuccessful")

  private val ArkUrl = makeOwlDatatypeProperty(KnoraApiV2Simple.ArkUrl, XSD.ANYURI)
    .withRdfLabelEn("ARK URL")
    .withRdfCommentEn("Provides the ARK URL of a resource.")

  private val VersionArkUrl = makeOwlDatatypeProperty(KnoraApiV2Simple.VersionArkUrl, XSD.ANYURI)
    .withRdfLabelEn("version ARK URL")
    .withRdfCommentEn("Provides the ARK URL of a particular version of a resource.")

  private val ResourceProperty = makeRdfProperty(KnoraApiV2Simple.ResourceProperty)
    .withSubjectType(KnoraApiV2Simple.Resource)
    .withSubPropertyOf(OntologyConstants.KnoraApiV2Simple.ResourceProperty)
    .withRdfLabelEn("Resource property")
    .withRdfCommentEn(
      "The base property of properties that point from Knora resources to Knora resources or values. These properties are required to have cardinalities in the resource classes in which they are used.",
    )

  private val HasValue = makeOwlDatatypeProperty(KnoraApiV2Simple.HasValue)
    .withSubjectType(KnoraApiV2Simple.Resource)
    .withSubPropertyOf(KnoraApiV2Simple.ResourceProperty)
    .withRdfLabelEn("has value")
    .withRdfCommentEn("The base property of properties that point from Knora resources to Knora values.")

  private val SubjectType = makeRdfProperty(KnoraApiV2Simple.SubjectType)
    .withRdfLabelEn("Subject type")
    .withRdfCommentEn("Specifies the required type of the subjects of a property")

  private val ObjectType = makeRdfProperty(KnoraApiV2Simple.ObjectType)
    .withRdfLabelEn("Object type")
    .withRdfCommentEn("Specifies the required type of the objects of a property")

  private val Date =
    makeStringDatatypeClassWithPattern(
      KnoraApiV2Simple.Date,
      "(GREGORIAN|JULIAN|ISLAMIC):\\d{1,4}(-\\d{1,2}(-\\d{1,2})?)?( BC| AD| BCE| CE)?(:\\d{1,4}(-\\d{1,2}(-\\d{1,2})?)?( BC| AD| BCE| CE)?)?",
    )
      .withRdfsLabelEn("Date literal")
      .withRdfsCommentEn("Represents a date as a period with different possible precisions.")

  private val Color = makeStringDatatypeClassWithPattern(KnoraApiV2Simple.Color, "#([0-9a-fA-F]{3}){1,2}")
    .withRdfsLabelEn("Color literal")
    .withRdfsCommentEn("Represents a color.")

  private val Interval = makeStringDatatypeClassWithPattern(KnoraApiV2Simple.Interval, "\\d+(\\.\\d+)?,\\d+(\\.\\d+)?")
    .withRdfsLabelEn("Interval literal")
    .withRdfsCommentEn("Represents an interval.")

  private val Geoname = makeStringDatatypeClassWithPattern(KnoraApiV2Simple.Geoname, "\\d{1,8}")
    .withRdfsLabelEn("Geoname code")
    .withRdfsCommentEn("Represents a Geoname code.")

  private val Geom = makeStringDatatypeClass(KnoraApiV2Simple.Geom)
    .withRdfsLabelEn("Geometry specification")
    .withRdfsCommentEn("Represents a geometry specification in JSON.")

  private val File = makeUriDatatypeClass(KnoraApiV2Simple.File)
    .withRdfsLabelEn("File URI")
    .withRdfsCommentEn("Represents a file URI.")

  private val ListNode = makeStringDatatypeClass(KnoraApiV2Simple.ListNode)
    .withRdfsLabelEn("List Node")
    .withRdfsCommentEn("Represents a list node.")

  private val HasIncomingLink = makeOwlObjectProperty(KnoraApiV2Simple.HasIncomingLink, KnoraApiV2Simple.Resource)
    .withSubjectType(KnoraApiV2Simple.Resource)
    .withSubPropertyOf(KnoraApiV2Simple.HasLinkTo)
    .withRdfLabel(Map(DE -> "hat eingehenden Verweis", EN -> "has incoming link"))
    .withRdfCommentEn("Indicates that this resource referred to by another resource")

  private val ResourceCardinalites = Map(
    KnoraApiV2Simple.HasIncomingLink -> Unbounded,
    KnoraApiV2Simple.ArkUrl          -> ExactlyOne,
    KnoraApiV2Simple.VersionArkUrl   -> ExactlyOne,
  )

  /**
   * Properties to remove from `knora-base` before converting it to the [[ApiV2Simple]] schema.
   * See also [[OntologyConstants.CorrespondingIris]].
   */
  override val internalPropertiesToRemove: Set[SmartIri] = Set(
    OntologyConstants.KnoraBase.OntologyVersion,
    OntologyConstants.KnoraBase.CreationDate,
    OntologyConstants.KnoraBase.LastModificationDate,
    OntologyConstants.KnoraBase.IsEditable,
    OntologyConstants.KnoraBase.CanBeInstantiated,
    OntologyConstants.KnoraBase.HasPermissions,
    OntologyConstants.KnoraBase.AttachedToUser,
    OntologyConstants.KnoraBase.AttachedToProject,
    OntologyConstants.KnoraBase.IsDeleted,
    OntologyConstants.KnoraBase.DeleteDate,
    OntologyConstants.KnoraBase.DeletedBy,
    OntologyConstants.KnoraBase.DeleteComment,
    OntologyConstants.KnoraBase.ObjectCannotBeMarkedAsDeleted,
    OntologyConstants.KnoraBase.ObjectDatatypeConstraint,
    OntologyConstants.KnoraBase.ObjectClassConstraint,
    OntologyConstants.KnoraBase.SubjectClassConstraint,
    OntologyConstants.KnoraBase.StandoffParentClassConstraint,
    OntologyConstants.KnoraBase.ValueHasLanguage,
    OntologyConstants.KnoraBase.ValueHasStandoff,
    OntologyConstants.KnoraBase.ValueHasMapping,
    OntologyConstants.KnoraBase.ValueHasMaxStandoffStartIndex,
    OntologyConstants.KnoraBase.HasMappingElement,
    OntologyConstants.KnoraBase.MappingHasStandoffClass,
    OntologyConstants.KnoraBase.MappingHasStandoffProperty,
    OntologyConstants.KnoraBase.MappingHasXMLClass,
    OntologyConstants.KnoraBase.MappingHasXMLNamespace,
    OntologyConstants.KnoraBase.MappingHasXMLTagname,
    OntologyConstants.KnoraBase.MappingHasXMLAttribute,
    OntologyConstants.KnoraBase.MappingHasXMLAttributename,
    OntologyConstants.KnoraBase.MappingHasDefaultXSLTransformation,
    OntologyConstants.KnoraBase.MappingHasStandoffDataTypeClass,
    OntologyConstants.KnoraBase.MappingElementRequiresSeparator,
    OntologyConstants.KnoraBase.StandoffTagHasLink,
    OntologyConstants.KnoraBase.StandoffTagHasInternalReference,
    OntologyConstants.KnoraBase.StandoffTagHasStart,
    OntologyConstants.KnoraBase.StandoffTagHasEnd,
    OntologyConstants.KnoraBase.StandoffTagHasStartIndex,
    OntologyConstants.KnoraBase.StandoffTagHasEndIndex,
    OntologyConstants.KnoraBase.StandoffTagHasStartParent,
    OntologyConstants.KnoraBase.StandoffTagHasStartAncestor,
    OntologyConstants.KnoraBase.StandoffTagHasEndParent,
    OntologyConstants.KnoraBase.StandoffTagHasUUID,
    OntologyConstants.KnoraBase.StandoffTagHasOriginalXMLID,
    OntologyConstants.KnoraBase.TargetHasOriginalXMLID,
    OntologyConstants.KnoraBase.IsRootNode,
    OntologyConstants.KnoraBase.HasRootNode,
    OntologyConstants.KnoraBase.HasSubListNode,
    OntologyConstants.KnoraBase.HasLicense,
    OntologyConstants.KnoraBase.ListNodeName,
    OntologyConstants.KnoraBase.ListNodePosition,
    OntologyConstants.KnoraBase.ValueCreationDate,
    OntologyConstants.KnoraBase.ValueHasUUID,
    OntologyConstants.KnoraBase.ValueHas,
    OntologyConstants.KnoraBase.ValueHasComment,
    OntologyConstants.KnoraBase.ValueHasCalendar,
    OntologyConstants.KnoraBase.ValueHasColor,
    OntologyConstants.KnoraBase.ValueHasStartJDN,
    OntologyConstants.KnoraBase.ValueHasEndJDN,
    OntologyConstants.KnoraBase.ValueHasStartPrecision,
    OntologyConstants.KnoraBase.ValueHasEndPrecision,
    OntologyConstants.KnoraBase.ValueHasDecimal,
    OntologyConstants.KnoraBase.ValueHasGeometry,
    OntologyConstants.KnoraBase.ValueHasGeonameCode,
    OntologyConstants.KnoraBase.ValueHasInteger,
    OntologyConstants.KnoraBase.ValueHasBoolean,
    OntologyConstants.KnoraBase.ValueHasUri,
    OntologyConstants.KnoraBase.ValueHasIntervalStart,
    OntologyConstants.KnoraBase.ValueHasIntervalEnd,
    OntologyConstants.KnoraBase.ValueHasTimeStamp,
    OntologyConstants.KnoraBase.ValueHasListNode,
    OntologyConstants.KnoraBase.Duration,
    OntologyConstants.KnoraBase.DimX,
    OntologyConstants.KnoraBase.DimY,
    OntologyConstants.KnoraBase.Fps,
    OntologyConstants.KnoraBase.InternalFilename,
    OntologyConstants.KnoraBase.InternalMimeType,
    OntologyConstants.KnoraBase.OriginalFilename,
    OntologyConstants.KnoraBase.OriginalMimeType,
    OntologyConstants.KnoraBase.ValueHasOrder,
    OntologyConstants.KnoraBase.PreviousValue,
    OntologyConstants.KnoraBase.ValueHasRefCount,
    OntologyConstants.KnoraBase.ValueHasString,
    OntologyConstants.KnoraBase.PreviousValue,
    OntologyConstants.KnoraBase.HasExtResValue,
    OntologyConstants.KnoraBase.ExtResAccessInfo,
    OntologyConstants.KnoraBase.ExtResId,
    OntologyConstants.KnoraBase.ExtResProvider,
    OntologyConstants.KnoraBase.PageCount,
  ).map(_.toSmartIri)

  /**
   * Classes to remove from `knora-base` before converting it to the [[ApiV2Simple]] schema. Standoff classes
   * are removed, too, but aren't included here, because this is taken care of in [[ReadOntologyV2]].
   */
  override val internalClassesToRemove: Set[SmartIri] = Set(
    OntologyConstants.KnoraBase.ValueBase,
    OntologyConstants.KnoraBase.DateBase,
    OntologyConstants.KnoraBase.UriBase,
    OntologyConstants.KnoraBase.BooleanBase,
    OntologyConstants.KnoraBase.IntBase,
    OntologyConstants.KnoraBase.DecimalBase,
    OntologyConstants.KnoraBase.IntervalBase,
    OntologyConstants.KnoraBase.TimeBase,
    OntologyConstants.KnoraBase.ColorBase,
    OntologyConstants.KnoraBase.Value,
    OntologyConstants.KnoraBase.TextValue,
    OntologyConstants.KnoraBase.IntValue,
    OntologyConstants.KnoraBase.BooleanValue,
    OntologyConstants.KnoraBase.UriValue,
    OntologyConstants.KnoraBase.DecimalValue,
    OntologyConstants.KnoraBase.DateValue,
    OntologyConstants.KnoraBase.ColorValue,
    OntologyConstants.KnoraBase.GeomValue,
    OntologyConstants.KnoraBase.ListValue,
    OntologyConstants.KnoraBase.IntervalValue,
    OntologyConstants.KnoraBase.TimeValue,
    OntologyConstants.KnoraBase.LinkValue,
    OntologyConstants.KnoraBase.GeonameValue,
    OntologyConstants.KnoraBase.FileValue,
    OntologyConstants.KnoraBase.MappingElement,
    OntologyConstants.KnoraBase.MappingComponent,
    OntologyConstants.KnoraBase.MappingStandoffDataTypeClass,
    OntologyConstants.KnoraBase.MappingXMLAttribute,
    OntologyConstants.KnoraBase.XMLToStandoffMapping,
    OntologyConstants.KnoraBase.ExternalResource,
    OntologyConstants.KnoraBase.ExternalResValue,
    OntologyConstants.KnoraBase.ListNode,
  ).map(_.toSmartIri)

  /**
   * After `knora-base` has been converted to the [[ApiV2Simple]] schema, these cardinalities must be
   * added to the specified classes to obtain `knora-api`.
   */
  override val externalCardinalitiesToAdd: Map[SmartIri, Map[SmartIri, KnoraCardinalityInfo]] = Map(
    OntologyConstants.KnoraBase.Resource -> ResourceCardinalites,
  ).map { case (classIri, cardinalities) =>
    classIri.toSmartIri.toOntologySchema(ApiV2Simple) -> cardinalities.map { case (propertyIri, cardinality) =>
      propertyIri.toSmartIri.toOntologySchema(ApiV2Simple) -> KnoraCardinalityInfo(cardinality)
    }
  }

  /**
   * Classes that need to be added to `knora-base`, after converting it to the [[ApiV2Simple]] schema, to obtain `knora-api`.
   */
  override val externalClassesToAdd: Map[SmartIri, ReadClassInfoV2] = Set(
    File,
    Date,
    Color,
    Interval,
    Geoname,
    Geom,
    ListNode,
  )
    .map(_.withApiV2SimpleSchema)
    .map(_.build())
    .map(it => it.entityInfoContent.classIri -> it)
    .toMap

  /**
   * Properties that need to be added to `knora-base`, after converting it to the [[ApiV2Simple]] schema, to obtain `knora-api`.
   * See also [[OntologyConstants.CorrespondingIris]].
   */
  override val externalPropertiesToAdd: Map[SmartIri, ReadPropertyInfoV2] = Set(
    Label,
    Result,
    MayHaveMoreResults,
    Error,
    ArkUrl,
    VersionArkUrl,
    HasValue,
    ResourceProperty,
    SubjectType,
    ObjectType,
    HasIncomingLink,
  ).map(_.withApiV2SimpleSchema)
    .map(_.build())
    .map(it => it.entityInfoContent.propertyIri -> it)
    .toMap
}
