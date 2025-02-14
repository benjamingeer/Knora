/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi
package messages

import dsp.constants.SalsahGui
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri

/**
 * Contains string constants for IRIs from ontologies used by the application.
 */
object OntologyConstants {

  object Rdf {
    val RdfOntologyIri: IRI     = "http://www.w3.org/1999/02/22-rdf-syntax-ns"
    val RdfPrefixExpansion: IRI = RdfOntologyIri + "#"

    val Type: IRI       = RdfPrefixExpansion + "type"
    val Subject: IRI    = RdfPrefixExpansion + "subject"
    val Predicate: IRI  = RdfPrefixExpansion + "predicate"
    val Object: IRI     = RdfPrefixExpansion + "object"
    val Property: IRI   = RdfPrefixExpansion + "Property"
    val LangString: IRI = RdfPrefixExpansion + "langString"
  }

  object Rdfs {
    val RdfsOntologyIri: IRI     = "http://www.w3.org/2000/01/rdf-schema"
    val RdfsPrefixExpansion: IRI = RdfsOntologyIri + "#"

    val Label: IRI         = RdfsPrefixExpansion + "label"
    val Comment: IRI       = RdfsPrefixExpansion + "comment"
    val SubClassOf: IRI    = RdfsPrefixExpansion + "subClassOf"
    val SubPropertyOf: IRI = RdfsPrefixExpansion + "subPropertyOf"
    val Datatype: IRI      = RdfsPrefixExpansion + "Datatype"
  }

  object Owl {
    val OwlOntologyIri          = "http://www.w3.org/2002/07/owl"
    val OwlPrefixExpansion: IRI = OwlOntologyIri + "#"

    val Ontology: IRI       = OwlPrefixExpansion + "Ontology"
    val Restriction: IRI    = OwlPrefixExpansion + "Restriction"
    val OnProperty: IRI     = OwlPrefixExpansion + "onProperty"
    val Cardinality: IRI    = OwlPrefixExpansion + "cardinality"
    val MinCardinality: IRI = OwlPrefixExpansion + "minCardinality"
    val MaxCardinality: IRI = OwlPrefixExpansion + "maxCardinality"

    val ObjectProperty: IRI     = OwlPrefixExpansion + "ObjectProperty"
    val DatatypeProperty: IRI   = OwlPrefixExpansion + "DatatypeProperty"
    val AnnotationProperty: IRI = OwlPrefixExpansion + "AnnotationProperty"
    val TransitiveProperty: IRI = OwlPrefixExpansion + "TransitiveProperty"

    val Class: IRI = OwlPrefixExpansion + "Class"

    val WithRestrictions: IRI = OwlPrefixExpansion + "withRestrictions"
    val OnDatatype: IRI       = OwlPrefixExpansion + "onDatatype"

    val OneOf: IRI           = OwlPrefixExpansion + "oneOf"
    val EquivalentClass: IRI = OwlPrefixExpansion + "equivalentClass"

    /**
     * Cardinality IRIs expressed as OWL restrictions, which specify the properties that resources of
     * a particular type can have.
     */
    val cardinalityOWLRestrictions: Set[IRI] = Set(
      Cardinality,
      MinCardinality,
      MaxCardinality,
    )

    val NamedIndividual: IRI = OwlPrefixExpansion + "NamedIndividual"

    /**
     * Classes defined by OWL that can be used as knora-base:subjectClassConstraint or knora-base:objectClassConstraint.
     */
    val ClassesThatCanBeKnoraClassConstraints: Set[IRI] = Set(
      Ontology,
      Class,
      Restriction,
      ObjectProperty,
      KnoraBase.TextValueType,
    )
  }

  val ClassTypes: Set[IRI] = Set(
    Owl.Class,
    Rdfs.Datatype,
  )

  val PropertyTypes: Set[IRI] = Set(
    Rdf.Property,
    Owl.ObjectProperty,
    Owl.DatatypeProperty,
    Owl.AnnotationProperty,
  )

  object Xsd {
    val XsdPrefixExpansion: IRI = "http://www.w3.org/2001/XMLSchema#"

    val String: IRI             = XsdPrefixExpansion + "string"
    val Boolean: IRI            = XsdPrefixExpansion + "boolean"
    val Int: IRI                = XsdPrefixExpansion + "int"
    val Integer: IRI            = XsdPrefixExpansion + "integer"
    val NonNegativeInteger: IRI = XsdPrefixExpansion + "nonNegativeInteger"

    lazy val integerTypes: Set[IRI] = Set(
      Int,
      Integer,
      NonNegativeInteger,
    )

    val Decimal: IRI       = XsdPrefixExpansion + "decimal"
    val Uri: IRI           = XsdPrefixExpansion + "anyURI"
    val Pattern: IRI       = XsdPrefixExpansion + "pattern"
    val DateTime: IRI      = XsdPrefixExpansion + "dateTime"
    val Date: IRI          = XsdPrefixExpansion + "date"
    val DateTimeStamp: IRI = XsdPrefixExpansion + "dateTimeStamp"
  }

  /**
   * http://schema.org
   */
  object SchemaOrg {
    val SchemaOrgPrefixExpansion: IRI = "http://schema.org/"
    val Name: IRI                     = SchemaOrgPrefixExpansion + "name"
    val NumberOfItems: IRI            = SchemaOrgPrefixExpansion + "numberOfItems"
  }

  object KnoraInternal {
    // The start and end of an internal Knora ontology IRI.
    val InternalOntologyStart = "http://www.knora.org/ontology"
  }

  /**
   * The object types of resource metadata properties.
   */
  val ResourceMetadataPropertyAxioms: Map[IRI, IRI] = Map(
    OntologyConstants.Rdfs.Label -> OntologyConstants.Xsd.String,
  )

  /**
   * Ontology labels that are used only in the internal schema.
   */
  val InternalOntologyLabels: Set[String] = Set(
    KnoraBase.KnoraBaseOntologyLabel,
    KnoraAdmin.KnoraAdminOntologyLabel,
  )

  /**
   * Ontology labels that are reserved for built-in ontologies.
   */
  val BuiltInOntologyLabels: Set[String] = Set(
    KnoraBase.KnoraBaseOntologyLabel,
    KnoraAdmin.KnoraAdminOntologyLabel,
    KnoraApi.KnoraApiOntologyLabel,
    SalsahGui.SalsahGuiOntologyLabel,
    Standoff.StandoffOntologyLabel,
  )

  object KnoraBase {
    val KnoraBaseOntologyLabel: String = "knora-base"
    val KnoraBaseOntologyIri: IRI      = KnoraInternal.InternalOntologyStart + "/" + KnoraBaseOntologyLabel

    val KnoraBasePrefixExpansion: IRI = KnoraBaseOntologyIri + "#"

    val OntologyVersion: IRI = KnoraBasePrefixExpansion + "ontologyVersion"

    val IsShared: IRI          = KnoraBasePrefixExpansion + "isShared"
    val CanBeInstantiated: IRI = KnoraBasePrefixExpansion + "canBeInstantiated"
    val IsEditable: IRI        = KnoraBasePrefixExpansion + "isEditable"

    val Resource: IRI                  = KnoraBasePrefixExpansion + "Resource"
    val DeletedResource: IRI           = KnoraBasePrefixExpansion + "DeletedResource"
    val DeletedValue: IRI              = KnoraBasePrefixExpansion + "DeletedValue"
    val Representation: IRI            = KnoraBasePrefixExpansion + "Representation"
    val AudioRepresentation: IRI       = KnoraBasePrefixExpansion + "AudioRepresentation"
    val DDDRepresentation: IRI         = KnoraBasePrefixExpansion + "DDDRepresentation"
    val DocumentRepresentation: IRI    = KnoraBasePrefixExpansion + "DocumentRepresentation"
    val ArchiveRepresentation: IRI     = KnoraBasePrefixExpansion + "ArchiveRepresentation"
    val MovingImageRepresentation: IRI = KnoraBasePrefixExpansion + "MovingImageRepresentation"
    val StillImageRepresentation: IRI  = KnoraBasePrefixExpansion + "StillImageRepresentation"
    val TextRepresentation: IRI        = KnoraBasePrefixExpansion + "TextRepresentation"

    val XMLToStandoffMapping: IRI            = KnoraBasePrefixExpansion + "XMLToStandoffMapping"
    val HasMappingElement: IRI               = KnoraBasePrefixExpansion + "hasMappingElement"
    val MappingElement: IRI                  = KnoraBasePrefixExpansion + "MappingElement"
    val MappingStandoffDataTypeClass: IRI    = KnoraBasePrefixExpansion + "MappingStandoffDataTypeClass"
    val MappingComponent: IRI                = KnoraBasePrefixExpansion + "MappingComponent"
    val MappingXMLAttribute: IRI             = KnoraBasePrefixExpansion + "MappingXMLAttribute"
    val MappingHasStandoffClass: IRI         = KnoraBasePrefixExpansion + "mappingHasStandoffClass"
    val MappingHasStandoffProperty: IRI      = KnoraBasePrefixExpansion + "mappingHasStandoffProperty"
    val MappingHasXMLClass: IRI              = KnoraBasePrefixExpansion + "mappingHasXMLClass"
    val MappingHasXMLNamespace: IRI          = KnoraBasePrefixExpansion + "mappingHasXMLNamespace"
    val MappingHasXMLTagname: IRI            = KnoraBasePrefixExpansion + "mappingHasXMLTagname"
    val MappingHasXMLAttribute: IRI          = KnoraBasePrefixExpansion + "mappingHasXMLAttribute"
    val MappingHasXMLAttributename: IRI      = KnoraBasePrefixExpansion + "mappingHasXMLAttributename"
    val MappingHasStandoffDataTypeClass: IRI = KnoraBasePrefixExpansion + "mappingHasStandoffDataTypeClass"
    val MappingElementRequiresSeparator: IRI = KnoraBasePrefixExpansion + "mappingElementRequiresSeparator"

    val XSLTransformation: IRI                  = KnoraBasePrefixExpansion + "XSLTransformation"
    val MappingHasDefaultXSLTransformation: IRI = KnoraBasePrefixExpansion + "mappingHasDefaultXSLTransformation"

    val SubjectClassConstraint: IRI        = KnoraBasePrefixExpansion + "subjectClassConstraint"
    val ObjectClassConstraint: IRI         = KnoraBasePrefixExpansion + "objectClassConstraint"
    val ObjectDatatypeConstraint: IRI      = KnoraBasePrefixExpansion + "objectDatatypeConstraint"
    val StandoffParentClassConstraint: IRI = KnoraBasePrefixExpansion + "standoffParentClassConstraint"

    val LinkObj: IRI               = KnoraBasePrefixExpansion + "LinkObj"
    val HasLinkTo: IRI             = KnoraBasePrefixExpansion + "hasLinkTo"
    val HasLinkToValue: IRI        = KnoraBasePrefixExpansion + "hasLinkToValue"
    val IsPartOf: IRI              = KnoraBasePrefixExpansion + "isPartOf"
    val IsPartOfValue: IRI         = KnoraBasePrefixExpansion + "isPartOfValue"
    val Region: IRI                = KnoraBasePrefixExpansion + "Region"
    val IsSegmentOf: IRI           = KnoraBasePrefixExpansion + "isSegmentOf"
    val IsSegmentOfValue: IRI      = KnoraBasePrefixExpansion + "isSegmentOfValue"
    val IsAudioSegmentOf: IRI      = KnoraBasePrefixExpansion + "isAudioSegmentOf"
    val IsAudioSegmentOfValue: IRI = KnoraBasePrefixExpansion + "isAudioSegmentOfValue"
    val IsVideoSegmentOf: IRI      = KnoraBasePrefixExpansion + "isVideoSegmentOf"
    val IsVideoSegmentOfValue: IRI = KnoraBasePrefixExpansion + "isVideoSegmentOfValue"

    val Value: IRI                         = KnoraBasePrefixExpansion + "Value"
    val ValueHas: IRI                      = KnoraBasePrefixExpansion + "valueHas"
    val ObjectCannotBeMarkedAsDeleted: IRI = KnoraBasePrefixExpansion + "objectCannotBeMarkedAsDeleted"

    val ValueHasString: IRI                = KnoraBasePrefixExpansion + "valueHasString"
    val ValueHasUUID: IRI                  = KnoraBasePrefixExpansion + "valueHasUUID"
    val ValueHasMaxStandoffStartIndex: IRI = KnoraBasePrefixExpansion + "valueHasMaxStandoffStartIndex"
    val ValueHasLanguage: IRI              = KnoraBasePrefixExpansion + "valueHasLanguage"
    val ValueHasMapping: IRI               = KnoraBasePrefixExpansion + "valueHasMapping"
    val ValueHasInteger: IRI               = KnoraBasePrefixExpansion + "valueHasInteger"
    val ValueHasDecimal: IRI               = KnoraBasePrefixExpansion + "valueHasDecimal"
    val ValueHasStandoff: IRI              = KnoraBasePrefixExpansion + "valueHasStandoff"
    val ValueHasStartJDN: IRI              = KnoraBasePrefixExpansion + "valueHasStartJDN"
    val ValueHasEndJDN: IRI                = KnoraBasePrefixExpansion + "valueHasEndJDN"
    val ValueHasCalendar: IRI              = KnoraBasePrefixExpansion + "valueHasCalendar"
    val ValueHasStartPrecision: IRI        = KnoraBasePrefixExpansion + "valueHasStartPrecision"
    val ValueHasEndPrecision: IRI          = KnoraBasePrefixExpansion + "valueHasEndPrecision"
    val ValueHasBoolean: IRI               = KnoraBasePrefixExpansion + "valueHasBoolean"
    val ValueHasUri: IRI                   = KnoraBasePrefixExpansion + "valueHasUri"
    val ValueHasColor: IRI                 = KnoraBasePrefixExpansion + "valueHasColor"
    val ValueHasGeometry: IRI              = KnoraBasePrefixExpansion + "valueHasGeometry"
    val ValueHasListNode: IRI              = KnoraBasePrefixExpansion + "valueHasListNode"
    val ValueHasIntervalStart: IRI         = KnoraBasePrefixExpansion + "valueHasIntervalStart"
    val ValueHasIntervalEnd: IRI           = KnoraBasePrefixExpansion + "valueHasIntervalEnd"
    val ValueHasTimeStamp: IRI             = KnoraBasePrefixExpansion + "valueHasTimeStamp"
    val ValueHasOrder: IRI                 = KnoraBasePrefixExpansion + "valueHasOrder"
    val ValueHasRefCount: IRI              = KnoraBasePrefixExpansion + "valueHasRefCount"
    val ValueHasComment: IRI               = KnoraBasePrefixExpansion + "valueHasComment"
    val ValueHasGeonameCode: IRI           = KnoraBasePrefixExpansion + "valueHasGeonameCode"
    val Duration: IRI                      = KnoraBasePrefixExpansion + "duration"

    val PreviousValue: IRI = KnoraBasePrefixExpansion + "previousValue"

    val ResourceProperty: IRI        = KnoraBasePrefixExpansion + "resourceProperty"
    val HasValue: IRI                = KnoraBasePrefixExpansion + "hasValue"
    val HasIncomingLinkValue: IRI    = KnoraBasePrefixExpansion + "hasIncomingLinkValue"
    val HasFileValue: IRI            = KnoraBasePrefixExpansion + "hasFileValue"
    val HasStillImageFileValue: IRI  = KnoraBasePrefixExpansion + "hasStillImageFileValue"
    val HasMovingImageFileValue: IRI = KnoraBasePrefixExpansion + "hasMovingImageFileValue"
    val HasAudioFileValue: IRI       = KnoraBasePrefixExpansion + "hasAudioFileValue"
    val HasDDDFileValue: IRI         = KnoraBasePrefixExpansion + "hasDDDFileValue"
    val HasTextFileValue: IRI        = KnoraBasePrefixExpansion + "hasTextFileValue"
    val HasDocumentFileValue: IRI    = KnoraBasePrefixExpansion + "hasDocumentFileValue"
    val HasArchiveFileValue: IRI     = KnoraBasePrefixExpansion + "hasArchiveFileValue"
    val HasCopyrightHolder: IRI      = KnoraBasePrefixExpansion + "hasCopyrightHolder"
    val HasAuthorship: IRI           = KnoraBasePrefixExpansion + "hasAuthorship"
    val HasLicense: IRI              = KnoraBasePrefixExpansion + "hasLicense"

    val ResourceIcon: IRI = KnoraBasePrefixExpansion + "resourceIcon"

    val InternalMimeType: IRI = KnoraBasePrefixExpansion + "internalMimeType"
    val InternalFilename: IRI = KnoraBasePrefixExpansion + "internalFilename"
    val OriginalFilename: IRI = KnoraBasePrefixExpansion + "originalFilename"
    val OriginalMimeType: IRI = KnoraBasePrefixExpansion + "originalMimeType"
    val DimX: IRI             = KnoraBasePrefixExpansion + "dimX"
    val DimY: IRI             = KnoraBasePrefixExpansion + "dimY"
    val ExternalUrl: IRI      = KnoraBasePrefixExpansion + "externalUrl"
    val PageCount: IRI        = KnoraBasePrefixExpansion + "pageCount"
    val Fps: IRI              = KnoraBasePrefixExpansion + "fps"

    val ValueBase: IRI    = KnoraBasePrefixExpansion + "ValueBase"
    val DateBase: IRI     = KnoraBasePrefixExpansion + "DateBase"
    val UriBase: IRI      = KnoraBasePrefixExpansion + "UriBase"
    val BooleanBase: IRI  = KnoraBasePrefixExpansion + "BooleanBase"
    val IntBase: IRI      = KnoraBasePrefixExpansion + "IntBase"
    val DecimalBase: IRI  = KnoraBasePrefixExpansion + "DecimalBase"
    val IntervalBase: IRI = KnoraBasePrefixExpansion + "IntervalBase"
    val TimeBase: IRI     = KnoraBasePrefixExpansion + "TimeBase"
    val ColorBase: IRI    = KnoraBasePrefixExpansion + "ColorBase"

    val TextValue: IRI                   = KnoraBasePrefixExpansion + "TextValue"
    val IntValue: IRI                    = KnoraBasePrefixExpansion + "IntValue"
    val BooleanValue: IRI                = KnoraBasePrefixExpansion + "BooleanValue"
    val UriValue: IRI                    = KnoraBasePrefixExpansion + "UriValue"
    val DecimalValue: IRI                = KnoraBasePrefixExpansion + "DecimalValue"
    val DateValue: IRI                   = KnoraBasePrefixExpansion + "DateValue"
    val ColorValue: IRI                  = KnoraBasePrefixExpansion + "ColorValue"
    val GeomValue: IRI                   = KnoraBasePrefixExpansion + "GeomValue"
    val ListValue: IRI                   = KnoraBasePrefixExpansion + "ListValue"
    val IntervalValue: IRI               = KnoraBasePrefixExpansion + "IntervalValue"
    val TimeValue: IRI                   = KnoraBasePrefixExpansion + "TimeValue"
    val LinkValue: IRI                   = KnoraBasePrefixExpansion + "LinkValue"
    val GeonameValue: IRI                = KnoraBasePrefixExpansion + "GeonameValue"
    val FileValue: IRI                   = KnoraBasePrefixExpansion + "FileValue"
    val AudioFileValue: IRI              = KnoraBasePrefixExpansion + "AudioFileValue"
    val DDDFileValue: IRI                = KnoraBasePrefixExpansion + "DDDFileValue"
    val DocumentFileValue: IRI           = KnoraBasePrefixExpansion + "DocumentFileValue"
    val ArchiveFileValue: IRI            = KnoraBasePrefixExpansion + "ArchiveFileValue"
    val StillImageFileValue: IRI         = KnoraBasePrefixExpansion + "StillImageFileValue"
    val StillImageExternalFileValue: IRI = KnoraBasePrefixExpansion + "StillImageExternalFileValue"
    val MovingImageFileValue: IRI        = KnoraBasePrefixExpansion + "MovingImageFileValue"
    val TextFileValue: IRI               = KnoraBasePrefixExpansion + "TextFileValue"

    val HasTextValueType: IRI         = KnoraBasePrefixExpansion + "hasTextValueType"
    val HasTextValuePropertyType: IRI = KnoraBasePrefixExpansion + "hasTextValuePropertyType"
    val TextValueType: IRI            = KnoraBasePrefixExpansion + "TextValueType"
    val UnformattedText: IRI          = KnoraBasePrefixExpansion + "UnformattedText"
    val FormattedText: IRI            = KnoraBasePrefixExpansion + "FormattedText"
    val CustomFormattedText: IRI      = KnoraBasePrefixExpansion + "CustomFormattedText"
    val UndefinedTextType: IRI        = KnoraBasePrefixExpansion + "UndefinedTextType"

    val FileValueClasses: Set[IRI] = Set(
      FileValue,
      StillImageFileValue,
      StillImageExternalFileValue,
      MovingImageFileValue,
      AudioFileValue,
      DDDFileValue,
      TextFileValue,
      DocumentFileValue,
      ArchiveFileValue,
    )

    val ValueClasses: Set[IRI] = Set(
      TextValue,
      IntValue,
      BooleanValue,
      UriValue,
      DecimalValue,
      DateValue,
      ColorValue,
      GeomValue,
      ListValue,
      IntervalValue,
      TimeValue,
      LinkValue,
      GeonameValue,
      FileValue,
      AudioFileValue,
      DDDFileValue,
      DocumentFileValue,
      ArchiveFileValue,
      StillImageFileValue,
      MovingImageFileValue,
      TextFileValue,
    )

    val ListNode: IRI         = KnoraBasePrefixExpansion + "ListNode"
    val ListNodeName: IRI     = KnoraBasePrefixExpansion + "listNodeName"
    val IsRootNode: IRI       = KnoraBasePrefixExpansion + "isRootNode"
    val HasRootNode: IRI      = KnoraBasePrefixExpansion + "hasRootNode"
    val HasSubListNode: IRI   = KnoraBasePrefixExpansion + "hasSubListNode"
    val ListNodePosition: IRI = KnoraBasePrefixExpansion + "listNodePosition"

    val IsDeleted: IRI = KnoraBasePrefixExpansion + "isDeleted"

    val IsMainResource: IRI = KnoraBasePrefixExpansion + "isMainResource"

    /* Resource creator */
    val AttachedToUser: IRI = KnoraBasePrefixExpansion + "attachedToUser"

    /* Resource's and list's project */
    val AttachedToProject: IRI = KnoraBasePrefixExpansion + "attachedToProject"

    /* Permissions */
    val HasPermissions: IRI = KnoraBasePrefixExpansion + "hasPermissions"

    val PermissionListDelimiter: Char = '|'
    val GroupListDelimiter: Char      = ','

    /* Standoff */

    val StandoffTag: IRI                 = KnoraBasePrefixExpansion + "StandoffTag"
    val StandoffTagHasStart: IRI         = KnoraBasePrefixExpansion + "standoffTagHasStart"
    val StandoffTagHasEnd: IRI           = KnoraBasePrefixExpansion + "standoffTagHasEnd"
    val StandoffTagHasStartIndex: IRI    = KnoraBasePrefixExpansion + "standoffTagHasStartIndex"
    val StandoffTagHasEndIndex: IRI      = KnoraBasePrefixExpansion + "standoffTagHasEndIndex"
    val StandoffTagHasStartParent: IRI   = KnoraBasePrefixExpansion + "standoffTagHasStartParent"
    val StandoffTagHasEndParent: IRI     = KnoraBasePrefixExpansion + "standoffTagHasEndParent"
    val StandoffTagHasUUID: IRI          = KnoraBasePrefixExpansion + "standoffTagHasUUID"
    val StandoffTagHasOriginalXMLID: IRI = KnoraBasePrefixExpansion + "standoffTagHasOriginalXMLID"
    val TargetHasOriginalXMLID: IRI =
      KnoraBasePrefixExpansion + "targetHasOriginalXMLID" // virtual property, used only in CONSTRUCT
    val StandoffTagHasInternalReference: IRI = KnoraBasePrefixExpansion + "standoffTagHasInternalReference"
    val StandoffTagHasStartAncestor: IRI     = KnoraBasePrefixExpansion + "standoffTagHasStartAncestor"

    val StandoffTagHasLink: IRI     = KnoraBasePrefixExpansion + "standoffTagHasLink"
    val HasStandoffLinkTo: IRI      = KnoraBasePrefixExpansion + "hasStandoffLinkTo"
    val HasStandoffLinkToValue: IRI = KnoraBasePrefixExpansion + "hasStandoffLinkToValue"

    val StandoffDateTag: IRI              = KnoraBasePrefixExpansion + "StandoffDateTag"
    val StandoffColorTag: IRI             = KnoraBasePrefixExpansion + "StandoffColorTag"
    val StandoffIntegerTag: IRI           = KnoraBasePrefixExpansion + "StandoffIntegerTag"
    val StandoffDecimalTag: IRI           = KnoraBasePrefixExpansion + "StandoffDecimalTag"
    val StandoffIntervalTag: IRI          = KnoraBasePrefixExpansion + "StandoffIntervalTag"
    val StandoffTimeTag: IRI              = KnoraBasePrefixExpansion + "StandoffTimeTag"
    val StandoffBooleanTag: IRI           = KnoraBasePrefixExpansion + "StandoffBooleanTag"
    val StandoffLinkTag: IRI              = KnoraBasePrefixExpansion + "StandoffLinkTag"
    val StandoffUriTag: IRI               = KnoraBasePrefixExpansion + "StandoffUriTag"
    val StandoffInternalReferenceTag: IRI = KnoraBasePrefixExpansion + "StandoffInternalReferenceTag"

    val StandardMapping: IRI = "http://rdfh.ch/standoff/mappings/StandardMapping"
    val TEIMapping: IRI      = "http://rdfh.ch/standoff/mappings/TEIMapping"

    val CreationDate: IRI      = KnoraBasePrefixExpansion + "creationDate"
    val ValueCreationDate: IRI = KnoraBasePrefixExpansion + "valueCreationDate"

    val LastModificationDate: IRI = KnoraBasePrefixExpansion + "lastModificationDate"

    val DeleteDate: IRI    = KnoraBasePrefixExpansion + "deleteDate"
    val DeletedBy: IRI     = KnoraBasePrefixExpansion + "deletedBy"
    val DeleteComment: IRI = KnoraBasePrefixExpansion + "deleteComment"

    val HasExtResValue: IRI   = OntologyConstants.KnoraBase.KnoraBasePrefixExpansion + "hasExtResValue"
    val ExtResAccessInfo: IRI = OntologyConstants.KnoraBase.KnoraBasePrefixExpansion + "extResAccessInfo"
    val ExtResId: IRI         = OntologyConstants.KnoraBase.KnoraBasePrefixExpansion + "extResId"
    val ExtResProvider: IRI   = OntologyConstants.KnoraBase.KnoraBasePrefixExpansion + "extResProvider"

    val ExternalResource: IRI = OntologyConstants.KnoraBase.KnoraBasePrefixExpansion + "ExternalResource"
    val ExternalResValue: IRI = OntologyConstants.KnoraBase.KnoraBasePrefixExpansion + "ExternalResValue"

  }

  object KnoraAdmin {
    val KnoraAdminOntologyLabel: String = "knora-admin"
    val KnoraAdminOntologyIri: IRI      = KnoraInternal.InternalOntologyStart + "/" + KnoraAdminOntologyLabel

    val KnoraAdminPrefix: String       = KnoraAdminOntologyLabel + ":"
    val KnoraAdminPrefixExpansion: IRI = KnoraAdminOntologyIri + "#"

    /* User */
    val User: IRI                  = KnoraAdminPrefixExpansion + "User"
    val Username: IRI              = KnoraAdminPrefixExpansion + "username"
    val Email: IRI                 = KnoraAdminPrefixExpansion + "email"
    val GivenName: IRI             = KnoraAdminPrefixExpansion + "givenName"
    val FamilyName: IRI            = KnoraAdminPrefixExpansion + "familyName"
    val Password: IRI              = KnoraAdminPrefixExpansion + "password"
    val PreferredLanguage: IRI     = KnoraAdminPrefixExpansion + "preferredLanguage"
    val IsInProject: IRI           = KnoraAdminPrefixExpansion + "isInProject"
    val IsInProjectAdminGroup: IRI = KnoraAdminPrefixExpansion + "isInProjectAdminGroup"
    val IsInGroup: IRI             = KnoraAdminPrefixExpansion + "isInGroup"
    val IsInSystemAdminGroup: IRI  = KnoraAdminPrefixExpansion + "isInSystemAdminGroup"

    /* Status used for User, Group and Project */
    val StatusProp: IRI = KnoraAdminPrefixExpansion + "status"

    /* Project */
    val KnoraProject: IRI                   = KnoraAdminPrefixExpansion + "knoraProject"
    val ProjectShortname: IRI               = KnoraAdminPrefixExpansion + "projectShortname"
    val ProjectShortcode: IRI               = KnoraAdminPrefixExpansion + "projectShortcode"
    val ProjectLongname: IRI                = KnoraAdminPrefixExpansion + "projectLongname"
    val ProjectDescription: IRI             = KnoraAdminPrefixExpansion + "projectDescription"
    val ProjectKeyword: IRI                 = KnoraAdminPrefixExpansion + "projectKeyword"
    val ProjectLogo: IRI                    = KnoraAdminPrefixExpansion + "projectLogo"
    val ProjectRestrictedViewSize: IRI      = KnoraAdminPrefixExpansion + "projectRestrictedViewSize"
    val ProjectRestrictedViewWatermark: IRI = KnoraAdminPrefixExpansion + "projectRestrictedViewWatermark"
    val HasSelfJoinEnabled: IRI             = KnoraAdminPrefixExpansion + "hasSelfJoinEnabled"

    /* Group */
    val UserGroup: IRI         = KnoraAdminPrefixExpansion + "UserGroup"
    val GroupName: IRI         = KnoraAdminPrefixExpansion + "groupName"
    val GroupDescriptions: IRI = KnoraAdminPrefixExpansion + "groupDescriptions"
    val BelongsToProject: IRI  = KnoraAdminPrefixExpansion + "belongsToProject"

    /* Permissions */
    val Permission: IRI                    = KnoraAdminPrefixExpansion + "Permission"
    val AdministrativePermission: IRI      = KnoraAdminPrefixExpansion + "AdministrativePermission"
    val DefaultObjectAccessPermission: IRI = KnoraAdminPrefixExpansion + "DefaultObjectAccessPermission"
    val ForProject: IRI                    = KnoraAdminPrefixExpansion + "forProject"
    val ForGroup: IRI                      = KnoraAdminPrefixExpansion + "forGroup"
    val ForResourceClass: IRI              = KnoraAdminPrefixExpansion + "forResourceClass"
    val ForProperty: IRI                   = KnoraAdminPrefixExpansion + "forProperty"

    val HasDefaultRestrictedViewPermission: IRI = KnoraAdminPrefixExpansion + "hasDefaultRestrictedViewPermission"
    val HasDefaultViewPermission: IRI           = KnoraAdminPrefixExpansion + "hasDefaultViewPermission"
    val HasDefaultModifyPermission: IRI         = KnoraAdminPrefixExpansion + "hasDefaultModifyPermission"
    val HasDefaultDeletePermission: IRI         = KnoraAdminPrefixExpansion + "hasDefaultDeletePermission"
    val HasDefaultChangeRightsPermission: IRI   = KnoraAdminPrefixExpansion + "hasDefaultChangeRightsPermission"

    val DefaultPermissionProperties: Set[IRI] = Set(
      HasDefaultRestrictedViewPermission,
      HasDefaultViewPermission,
      HasDefaultModifyPermission,
      HasDefaultDeletePermission,
      HasDefaultChangeRightsPermission,
    )

    val DefaultSharedOntologiesProject: ProjectIri =
      ProjectIri.unsafeFrom(KnoraAdminPrefixExpansion + "DefaultSharedOntologiesProject")
  }

  object Standoff {
    val StandoffOntologyLabel: String = "standoff"
    val StandoffOntologyIri: IRI      = KnoraInternal.InternalOntologyStart + "/" + StandoffOntologyLabel
    val StandoffPrefixExpansion: IRI  = StandoffOntologyIri + "#"

    val StandoffRootTag: IRI          = StandoffPrefixExpansion + "StandoffRootTag"
    val StandoffParagraphTag: IRI     = StandoffPrefixExpansion + "StandoffParagraphTag"
    val StandoffItalicTag: IRI        = StandoffPrefixExpansion + "StandoffItalicTag"
    val StandoffBoldTag: IRI          = StandoffPrefixExpansion + "StandoffBoldTag"
    val StandoffUnderlineTag: IRI     = StandoffPrefixExpansion + "StandoffUnderlineTag"
    val StandoffStrikethroughTag: IRI = StandoffPrefixExpansion + "StandoffStrikethroughTag"

    val StandoffHeader1Tag: IRI = StandoffPrefixExpansion + "StandoffHeader1Tag"
    val StandoffHeader2Tag: IRI = StandoffPrefixExpansion + "StandoffHeader2Tag"
    val StandoffHeader3Tag: IRI = StandoffPrefixExpansion + "StandoffHeader3Tag"
    val StandoffHeader4Tag: IRI = StandoffPrefixExpansion + "StandoffHeader4Tag"
    val StandoffHeader5Tag: IRI = StandoffPrefixExpansion + "StandoffHeader5Tag"
    val StandoffHeader6Tag: IRI = StandoffPrefixExpansion + "StandoffHeader6Tag"

    val StandoffSuperscriptTag: IRI   = StandoffPrefixExpansion + "StandoffSuperscriptTag"
    val StandoffSubscriptTag: IRI     = StandoffPrefixExpansion + "StandoffSubscriptTag"
    val StandoffOrderedListTag: IRI   = StandoffPrefixExpansion + "StandoffOrderedListTag"
    val StandoffUnorderedListTag: IRI = StandoffPrefixExpansion + "StandoffUnorderedListTag"
    val StandoffListElementTag: IRI   = StandoffPrefixExpansion + "StandoffListElementTag"
  }

  object KnoraApi {
    // The hostname of a Knora API ontology IRI.
    val ApiOntologyHostname: String = "http://api.knora.org"

    // The start and end of a Knora API ontology IRI.
    val ApiOntologyStart: String = ApiOntologyHostname + "/ontology/"

    val KnoraApiOntologyLabel: String = "knora-api"

    /**
     * The IRIs representing `knora-api:Resource` in Knora API v2, in the simple and complex schemas.
     */
    lazy val KnoraApiV2ResourceIris: Set[IRI] = Set(
      OntologyConstants.KnoraApiV2Simple.Resource,
      OntologyConstants.KnoraApiV2Complex.Resource,
    )

    /**
     * The IRIs representing `knora-api:GravsearchOptions` in Knora API v2, in the simple and complex schemas.
     */
    lazy val GravsearchOptionsIris: Set[IRI] = Set(
      OntologyConstants.KnoraApiV2Simple.GravsearchOptions,
      OntologyConstants.KnoraApiV2Complex.GravsearchOptions,
    )

    /**
     * The IRIs representing `knora-api:useInference` in Knora API v2, in the simple and complex schemas.
     */
    lazy val UseInferenceIris: Set[IRI] = Set(
      OntologyConstants.KnoraApiV2Simple.UseInference,
      OntologyConstants.KnoraApiV2Complex.UseInference,
    )

    /**
     * Returns the IRI of `knora-api:subjectType` in the specified schema.
     */
    def getSubjectTypePredicate(apiV2Schema: ApiV2Schema): IRI =
      apiV2Schema match {
        case ApiV2Simple  => KnoraApiV2Simple.SubjectType
        case ApiV2Complex => KnoraApiV2Complex.SubjectType
      }

    /**
     * Returns the IRI of `knora-api:objectType` in the specified schema.
     */
    def getObjectTypePredicate(apiV2Schema: ApiV2Schema): IRI =
      apiV2Schema match {
        case ApiV2Simple  => KnoraApiV2Simple.ObjectType
        case ApiV2Complex => KnoraApiV2Complex.ObjectType
      }
  }

  object KnoraApiV2Complex {

    val VersionSegment = "/v2"

    val KnoraApiOntologyIri: IRI       = KnoraApi.ApiOntologyStart + KnoraApi.KnoraApiOntologyLabel + VersionSegment
    val KnoraApiV2PrefixExpansion: IRI = KnoraApiOntologyIri + "#"

    val Result: IRI             = KnoraApiV2PrefixExpansion + "result"
    val Error: IRI              = KnoraApiV2PrefixExpansion + "error"
    val CanDo: IRI              = KnoraApiV2PrefixExpansion + "canDo"
    val CannotDoReason: IRI     = KnoraApiV2PrefixExpansion + "cannotDoReason"
    val CannotDoContext: IRI    = KnoraApiV2PrefixExpansion + "cannotDoContext"
    val MayHaveMoreResults: IRI = KnoraApiV2PrefixExpansion + "mayHaveMoreResults"
    val EventType: IRI          = KnoraApiV2PrefixExpansion + "eventType"
    val EventBody: IRI          = KnoraApiV2PrefixExpansion + "eventBody"
    val ResourceClassIri: IRI   = KnoraApiV2PrefixExpansion + "resourceClassIri"
    val ResourceIri: IRI        = KnoraApiV2PrefixExpansion + "resourceIri"

    val License: IRI = KnoraApiV2PrefixExpansion + "License"

    val IsShared: IRI  = KnoraApiV2PrefixExpansion + "isShared"
    val IsBuiltIn: IRI = KnoraApiV2PrefixExpansion + "isBuiltIn"

    val SubjectType: IRI = KnoraApiV2PrefixExpansion + "subjectType"
    val ObjectType: IRI  = KnoraApiV2PrefixExpansion + "objectType"

    val KnoraProject: IRI = KnoraApiV2PrefixExpansion + "knoraProject"

    val IsEditable: IRI          = KnoraApiV2PrefixExpansion + "isEditable"
    val IsLinkProperty: IRI      = KnoraApiV2PrefixExpansion + "isLinkProperty"
    val IsLinkValueProperty: IRI = KnoraApiV2PrefixExpansion + "isLinkValueProperty"
    val IsResourceClass: IRI     = KnoraApiV2PrefixExpansion + "isResourceClass"
    val IsResourceProperty: IRI  = KnoraApiV2PrefixExpansion + "isResourceProperty"
    val IsStandoffClass: IRI     = KnoraApiV2PrefixExpansion + "isStandoffClass"
    val CanBeInstantiated: IRI   = KnoraApiV2PrefixExpansion + "canBeInstantiated"
    val IsValueClass: IRI        = KnoraApiV2PrefixExpansion + "isValueClass"
    val IsInherited: IRI         = KnoraApiV2PrefixExpansion + "isInherited"
    val OntologyName: IRI        = KnoraApiV2PrefixExpansion + "ontologyName"

    val ValueAsString: IRI      = KnoraApiV2PrefixExpansion + "valueAsString"
    val ValueCreationDate: IRI  = KnoraApiV2PrefixExpansion + "valueCreationDate"
    val ValueHasUUID: IRI       = KnoraApiV2PrefixExpansion + "valueHasUUID"
    val ValueHasComment: IRI    = KnoraApiV2PrefixExpansion + "valueHasComment"
    val NewValueVersionIri: IRI = KnoraApiV2PrefixExpansion + "newValueVersionIri"

    val User: IRI                   = KnoraApiV2PrefixExpansion + "User"
    val AttachedToUser: IRI         = KnoraApiV2PrefixExpansion + "attachedToUser"
    val AttachedToProject: IRI      = KnoraApiV2PrefixExpansion + "attachedToProject"
    val HasStandoffLinkTo: IRI      = KnoraApiV2PrefixExpansion + "hasStandoffLinkTo"
    val HasStandoffLinkToValue: IRI = KnoraApiV2PrefixExpansion + "hasStandoffLinkToValue"
    val HasPermissions: IRI         = KnoraApiV2PrefixExpansion + "hasPermissions"
    val HasCopyrightHolder: IRI     = KnoraApiV2PrefixExpansion + "hasCopyrightHolder"
    val HasAuthorship: IRI          = KnoraApiV2PrefixExpansion + "hasAuthorship"
    val HasLicense: IRI             = KnoraApiV2PrefixExpansion + "hasLicense"
    val UserHasPermission: String   = KnoraApiV2PrefixExpansion + "userHasPermission"
    val CreationDate: IRI           = KnoraApiV2PrefixExpansion + "creationDate"
    val LastModificationDate: IRI   = KnoraApiV2PrefixExpansion + "lastModificationDate"
    val VersionDate: IRI            = KnoraApiV2PrefixExpansion + "versionDate"
    val NewModificationDate: IRI    = KnoraApiV2PrefixExpansion + "newModificationDate"
    val IsDeleted: IRI              = KnoraApiV2PrefixExpansion + "isDeleted"
    val DeleteDate: IRI             = KnoraApiV2PrefixExpansion + "deleteDate"
    val DeleteComment: IRI          = KnoraApiV2PrefixExpansion + "deleteComment"
    val ArkUrl: IRI                 = KnoraApiV2PrefixExpansion + "arkUrl"
    val VersionArkUrl: IRI          = KnoraApiV2PrefixExpansion + "versionArkUrl"
    val Author: IRI                 = KnoraApiV2PrefixExpansion + "author"

    val Resource: IRI                  = KnoraApiV2PrefixExpansion + "Resource"
    val DeletedResource: IRI           = KnoraApiV2PrefixExpansion + "DeletedResource"
    val Region: IRI                    = KnoraApiV2PrefixExpansion + "Region"
    val Representation: IRI            = KnoraApiV2PrefixExpansion + "Representation"
    val StillImageRepresentation: IRI  = KnoraApiV2PrefixExpansion + "StillImageRepresentation"
    val MovingImageRepresentation: IRI = KnoraApiV2PrefixExpansion + "MovingImageRepresentation"
    val AudioRepresentation: IRI       = KnoraApiV2PrefixExpansion + "AudioRepresentation"
    val DDDRepresentation: IRI         = KnoraApiV2PrefixExpansion + "DDDRepresentation"
    val TextRepresentation: IRI        = KnoraApiV2PrefixExpansion + "TextRepresentation"
    val DocumentRepresentation: IRI    = KnoraApiV2PrefixExpansion + "DocumentRepresentation"
    val ArchiveRepresentation: IRI     = KnoraApiV2PrefixExpansion + "ArchiveRepresentation"
    val XMLToStandoffMapping: IRI      = KnoraApiV2PrefixExpansion + "XMLToStandoffMapping"
    val ListNode: IRI                  = KnoraApiV2PrefixExpansion + "ListNode"
    val LinkObj: IRI                   = KnoraApiV2PrefixExpansion + "LinkObj"

    val IntBase: IRI      = KnoraApiV2PrefixExpansion + "IntBase"
    val BooleanBase: IRI  = KnoraApiV2PrefixExpansion + "BooleanBase"
    val UriBase: IRI      = KnoraApiV2PrefixExpansion + "UriBase"
    val IntervalBase: IRI = KnoraApiV2PrefixExpansion + "IntervalBase"
    val ColorBase: IRI    = KnoraApiV2PrefixExpansion + "ColorBase"
    val DateBase: IRI     = KnoraApiV2PrefixExpansion + "DateBase"
    val TimeBase: IRI     = KnoraApiV2PrefixExpansion + "TimeBase"
    val DecimalBase: IRI  = KnoraApiV2PrefixExpansion + "DecimalBase"

    val ValueBaseClasses: Set[IRI] = Set(
      IntBase,
      BooleanBase,
      UriBase,
      IntervalBase,
      ColorBase,
      DateBase,
      TimeBase,
      DecimalBase,
    )

    val Value: IRI         = KnoraApiV2PrefixExpansion + "Value"
    val TextValue: IRI     = KnoraApiV2PrefixExpansion + "TextValue"
    val IntValue: IRI      = KnoraApiV2PrefixExpansion + "IntValue"
    val DecimalValue: IRI  = KnoraApiV2PrefixExpansion + "DecimalValue"
    val BooleanValue: IRI  = KnoraApiV2PrefixExpansion + "BooleanValue"
    val DateValue: IRI     = KnoraApiV2PrefixExpansion + "DateValue"
    val GeomValue: IRI     = KnoraApiV2PrefixExpansion + "GeomValue"
    val IntervalValue: IRI = KnoraApiV2PrefixExpansion + "IntervalValue"
    val TimeValue: IRI     = KnoraApiV2PrefixExpansion + "TimeValue"
    val LinkValue: IRI     = KnoraApiV2PrefixExpansion + "LinkValue"
    val ListValue: IRI     = KnoraApiV2PrefixExpansion + "ListValue"
    val UriValue: IRI      = KnoraApiV2PrefixExpansion + "UriValue"
    val GeonameValue: IRI  = KnoraApiV2PrefixExpansion + "GeonameValue"
    val FileValue: IRI     = KnoraApiV2PrefixExpansion + "FileValue"
    val ColorValue: IRI    = KnoraApiV2PrefixExpansion + "ColorValue"

    val StillImageFileValue: IRI         = KnoraApiV2PrefixExpansion + "StillImageFileValue"
    val StillImageExternalFileValue: IRI = KnoraApiV2PrefixExpansion + "StillImageExternalFileValue"
    val MovingImageFileValue: IRI        = KnoraApiV2PrefixExpansion + "MovingImageFileValue"
    val AudioFileValue: IRI              = KnoraApiV2PrefixExpansion + "AudioFileValue"
    val DDDFileValue: IRI                = KnoraApiV2PrefixExpansion + "DDDFileValue"
    val TextFileValue: IRI               = KnoraApiV2PrefixExpansion + "TextFileValue"
    val DocumentFileValue: IRI           = KnoraApiV2PrefixExpansion + "DocumentFileValue"
    val ArchiveFileValue: IRI            = KnoraApiV2PrefixExpansion + "ArchiveFileValue"

    val HasTextValueType: IRI         = KnoraApiV2PrefixExpansion + "hasTextValueType"
    val HasTextValuePropertyType: IRI = KnoraApiV2PrefixExpansion + "hasTextValuePropertyType"
    val TextValueType: IRI            = KnoraApiV2PrefixExpansion + "TextValueType"
    val UnformattedText: IRI          = KnoraApiV2PrefixExpansion + "UnformattedText"
    val FormattedText: IRI            = KnoraApiV2PrefixExpansion + "FormattedText"
    val CustomFormattedText: IRI      = KnoraApiV2PrefixExpansion + "CustomFormattedText"
    val UndefinedTextType: IRI        = KnoraApiV2PrefixExpansion + "UndefinedTextType"

    val FileValueClasses: Set[IRI] = Set(
      FileValue,
      StillImageFileValue,
      MovingImageFileValue,
      AudioFileValue,
      DDDFileValue,
      TextFileValue,
      DocumentFileValue,
      ArchiveFileValue,
    )

    val ValueClasses: Set[IRI] = Set(
      TextValue,
      IntValue,
      DecimalValue,
      BooleanValue,
      DateValue,
      GeomValue,
      IntervalValue,
      TimeValue,
      LinkValue,
      ListValue,
      UriValue,
      GeonameValue,
      ColorValue,
    ) ++ FileValueClasses

    val ResourceProperty: IRI     = KnoraApiV2PrefixExpansion + "resourceProperty"
    val HasValue: IRI             = KnoraApiV2PrefixExpansion + "hasValue"
    val ValueHas: IRI             = KnoraApiV2PrefixExpansion + "valueHas"
    val HasLinkTo: IRI            = KnoraApiV2PrefixExpansion + "hasLinkTo"
    val HasLinkToValue: IRI       = KnoraApiV2PrefixExpansion + "hasLinkToValue"
    val HasIncomingLinkValue: IRI = KnoraApiV2PrefixExpansion + "hasIncomingLinkValue"

    val IsPartOf: IRI                = KnoraApiV2PrefixExpansion + "isPartOf"
    val IsPartOfValue: IRI           = KnoraApiV2PrefixExpansion + "isPartOfValue"
    val HasColor: IRI                = KnoraApiV2PrefixExpansion + "hasColor"
    val HasFileValue: IRI            = KnoraApiV2PrefixExpansion + "hasFileValue"
    val HasStillImageFileValue: IRI  = KnoraApiV2PrefixExpansion + "hasStillImageFileValue"
    val HasMovingImageFileValue: IRI = KnoraApiV2PrefixExpansion + "hasMovingImageFileValue"
    val HasAudioFileValue: IRI       = KnoraApiV2PrefixExpansion + "hasAudioFileValue"
    val HasTextFileValue: IRI        = KnoraApiV2PrefixExpansion + "hasTextFileValue"
    val HasDocumentFileValue: IRI    = KnoraApiV2PrefixExpansion + "hasDocumentFileValue"
    val HasArchiveFileValue: IRI     = KnoraApiV2PrefixExpansion + "hasArchiveFileValue"

    val DateValueHasStartYear: IRI  = KnoraApiV2PrefixExpansion + "dateValueHasStartYear"
    val DateValueHasEndYear: IRI    = KnoraApiV2PrefixExpansion + "dateValueHasEndYear"
    val DateValueHasStartMonth: IRI = KnoraApiV2PrefixExpansion + "dateValueHasStartMonth"
    val DateValueHasEndMonth: IRI   = KnoraApiV2PrefixExpansion + "dateValueHasEndMonth"
    val DateValueHasStartDay: IRI   = KnoraApiV2PrefixExpansion + "dateValueHasStartDay"
    val DateValueHasEndDay: IRI     = KnoraApiV2PrefixExpansion + "dateValueHasEndDay"
    val DateValueHasStartEra: IRI   = KnoraApiV2PrefixExpansion + "dateValueHasStartEra"
    val DateValueHasEndEra: IRI     = KnoraApiV2PrefixExpansion + "dateValueHasEndEra"
    val DateValueHasCalendar: IRI   = KnoraApiV2PrefixExpansion + "dateValueHasCalendar"

    val TextValueHasStandoff: IRI              = KnoraApiV2PrefixExpansion + "textValueHasStandoff"
    val TextValueHasMarkup: IRI                = KnoraApiV2PrefixExpansion + "textValueHasMarkup"
    val TextValueHasMaxStandoffStartIndex: IRI = KnoraApiV2PrefixExpansion + "textValueHasMaxStandoffStartIndex"
    val TextValueAsHtml: IRI                   = KnoraApiV2PrefixExpansion + "textValueAsHtml"
    val TextValueAsXml: IRI                    = KnoraApiV2PrefixExpansion + "textValueAsXml"
    val TextValueHasMapping: IRI               = KnoraApiV2PrefixExpansion + "textValueHasMapping"
    val TextValueHasLanguage: IRI              = KnoraApiV2PrefixExpansion + "textValueHasLanguage"
    val StandoffTag: IRI                       = KnoraApiV2PrefixExpansion + "StandoffTag"
    val StandoffTagHasStartParent: IRI         = KnoraApiV2PrefixExpansion + "standoffTagHasStartParent"
    val StandoffTagHasEndParent: IRI           = KnoraApiV2PrefixExpansion + "standoffTagHasEndParent"
    val StandoffTagHasStartParentIndex: IRI    = KnoraApiV2PrefixExpansion + "standoffTagHasStartParentIndex"
    val StandoffTagHasEndParentIndex: IRI      = KnoraApiV2PrefixExpansion + "standoffTagHasEndParentIndex"
    val StandoffTagHasStart: IRI               = KnoraApiV2PrefixExpansion + "standoffTagHasStart"
    val StandoffTagHasEnd: IRI                 = KnoraApiV2PrefixExpansion + "standoffTagHasEnd"
    val StandoffTagHasStartIndex: IRI          = KnoraApiV2PrefixExpansion + "standoffTagHasStartIndex"
    val StandoffTagHasEndIndex: IRI            = KnoraApiV2PrefixExpansion + "standoffTagHasEndIndex"
    val StandoffTagHasUUID: IRI                = KnoraApiV2PrefixExpansion + "standoffTagHasUUID"
    val StandoffTagHasOriginalXMLID: IRI       = KnoraApiV2PrefixExpansion + "standoffTagHasOriginalXMLID"
    val NextStandoffStartIndex: IRI            = KnoraApiV2PrefixExpansion + "nextStandoffStartIndex"

    val IntValueAsInt: IRI = KnoraApiV2PrefixExpansion + "intValueAsInt"

    val DecimalValueAsDecimal: IRI = KnoraApiV2PrefixExpansion + "decimalValueAsDecimal"

    val GeometryValueAsGeometry: IRI = KnoraApiV2PrefixExpansion + "geometryValueAsGeometry"

    val LinkValueHasTarget: IRI    = KnoraApiV2PrefixExpansion + "linkValueHasTarget"
    val LinkValueHasTargetIri: IRI = KnoraApiV2PrefixExpansion + "linkValueHasTargetIri"
    val LinkValueHasSource: IRI    = KnoraApiV2PrefixExpansion + "linkValueHasSource"
    val LinkValueHasSourceIri: IRI = KnoraApiV2PrefixExpansion + "linkValueHasSourceIri"

    val FileValueAsUrl: IRI       = KnoraApiV2PrefixExpansion + "fileValueAsUrl"
    val FileValueHasFilename: IRI = KnoraApiV2PrefixExpansion + "fileValueHasFilename"
    // deprecated, use StillImageFileValueHasExternalUrl instead
    val FileValueHasExternalUrl: IRI = KnoraApiV2PrefixExpansion + "fileValueHasExternalUrl"

    val StillImageFileValueHasDimX: IRI        = KnoraApiV2PrefixExpansion + "stillImageFileValueHasDimX"
    val StillImageFileValueHasDimY: IRI        = KnoraApiV2PrefixExpansion + "stillImageFileValueHasDimY"
    val StillImageFileValueHasIIIFBaseUrl: IRI = KnoraApiV2PrefixExpansion + "stillImageFileValueHasIIIFBaseUrl"
    val StillImageFileValueHasExternalUrl: IRI = KnoraApiV2PrefixExpansion + "stillImageFileValueHasExternalUrl"

    val IntervalValueHasStart: IRI = KnoraApiV2PrefixExpansion + "intervalValueHasStart"
    val IntervalValueHasEnd: IRI   = KnoraApiV2PrefixExpansion + "intervalValueHasEnd"

    val TimeValueAsTimeStamp: IRI = KnoraApiV2PrefixExpansion + "timeValueAsTimeStamp"

    val BooleanValueAsBoolean: IRI = KnoraApiV2PrefixExpansion + "booleanValueAsBoolean"

    val ListValueAsListNode: IRI = KnoraApiV2PrefixExpansion + "listValueAsListNode"

    val ColorValueAsColor: IRI = KnoraApiV2PrefixExpansion + "colorValueAsColor"

    val UriValueAsUri: IRI = KnoraApiV2PrefixExpansion + "uriValueAsUri"

    val GeonameValueAsGeonameCode: IRI = KnoraApiV2PrefixExpansion + "geonameValueAsGeonameCode"

    val ResourceIcon: IRI = KnoraApiV2PrefixExpansion + "resourceIcon"

    val MappingHasName: IRI = KnoraApiV2PrefixExpansion + "mappingHasName"

    val IsMainResource: IRI              = KnoraApiV2PrefixExpansion + "isMainResource"
    val ToSimpleDateFunction: IRI        = KnoraApiV2PrefixExpansion + "toSimpleDate"
    val MatchTextFunction: IRI           = KnoraApiV2PrefixExpansion + "matchText"
    val MatchTextInStandoffFunction: IRI = KnoraApiV2PrefixExpansion + "matchTextInStandoff"
    val MatchLabelFunction: IRI          = KnoraApiV2PrefixExpansion + "matchLabel"
    val StandoffLinkFunction: IRI        = KnoraApiV2PrefixExpansion + "standoffLink"

    val GravsearchOptions: IRI = KnoraApiV2PrefixExpansion + "GravsearchOptions"
    val UseInference: IRI      = KnoraApiV2PrefixExpansion + "useInference"
  }

  object KnoraApiV2Simple {

    val VersionSegment = "/simple/v2"

    val KnoraApiOntologyIri: IRI = KnoraApi.ApiOntologyStart + KnoraApi.KnoraApiOntologyLabel + VersionSegment

    val KnoraApiV2PrefixExpansion: IRI = KnoraApiOntologyIri + "#"

    val Result: IRI             = KnoraApiV2PrefixExpansion + "result"
    val Error: IRI              = KnoraApiV2PrefixExpansion + "error"
    val MayHaveMoreResults: IRI = KnoraApiV2PrefixExpansion + "mayHaveMoreResults"

    val SubjectType: IRI = KnoraApiV2PrefixExpansion + "subjectType"

    val ObjectType: IRI = KnoraApiV2PrefixExpansion + "objectType"

    val IsMainResource: IRI     = KnoraApiV2PrefixExpansion + "isMainResource"
    val MatchTextFunction: IRI  = KnoraApiV2PrefixExpansion + "matchText"
    val MatchLabelFunction: IRI = KnoraApiV2PrefixExpansion + "matchLabel"

    val ResourceProperty: IRI = KnoraApiV2PrefixExpansion + "resourceProperty"

    val Region: IRI                    = KnoraApiV2PrefixExpansion + "Region"
    val Representation: IRI            = KnoraApiV2PrefixExpansion + "Representation"
    val StillImageRepresentation: IRI  = KnoraApiV2PrefixExpansion + "StillImageRepresentation"
    val MovingImageRepresentation: IRI = KnoraApiV2PrefixExpansion + "MovingImageRepresentation"
    val AudioRepresentation: IRI       = KnoraApiV2PrefixExpansion + "AudioRepresentation"
    val DDDRepresentation: IRI         = KnoraApiV2PrefixExpansion + "DDDRepresentation"
    val TextRepresentation: IRI        = KnoraApiV2PrefixExpansion + "TextRepresentation"
    val ArchiveRepresentation: IRI     = KnoraApiV2PrefixExpansion + "ArchiveRepresentation"
    val LinkObj: IRI                   = KnoraApiV2PrefixExpansion + "LinkObj"

    val Date: IRI     = KnoraApiV2PrefixExpansion + "Date"
    val Geom: IRI     = KnoraApiV2PrefixExpansion + "Geom"
    val Color: IRI    = KnoraApiV2PrefixExpansion + "Color"
    val Interval: IRI = KnoraApiV2PrefixExpansion + "Interval"
    val Geoname: IRI  = KnoraApiV2PrefixExpansion + "Geoname"
    val ListNode: IRI = KnoraApiV2PrefixExpansion + "ListNode"

    val Resource: IRI = KnoraApiV2PrefixExpansion + "Resource"

    val ResourceIcon: IRI = KnoraApiV2PrefixExpansion + "resourceIcon"

    val HasValue: IRI = KnoraApiV2PrefixExpansion + "hasValue"

    val HasLinkTo: IRI       = KnoraApiV2PrefixExpansion + "hasLinkTo"
    val HasIncomingLink: IRI = KnoraApiV2PrefixExpansion + "hasIncomingLink"

    val IsPartOf: IRI = KnoraApiV2PrefixExpansion + "isPartOf"
    val HasColor: IRI = KnoraApiV2PrefixExpansion + "hasColor"

    val HasFile: IRI = KnoraApiV2PrefixExpansion + "hasFile"

    val HasStillImageFile: IRI  = KnoraApiV2PrefixExpansion + "hasStillImageFile"
    val HasMovingImageFile: IRI = KnoraApiV2PrefixExpansion + "hasMovingImageFile"
    val HasAudioFile: IRI       = KnoraApiV2PrefixExpansion + "hasAudioFile"
    val HasDDDFile: IRI         = KnoraApiV2PrefixExpansion + "hasDDDFile"
    val HasTextFile: IRI        = KnoraApiV2PrefixExpansion + "hasTextFile"
    val HasDocumentFile: IRI    = KnoraApiV2PrefixExpansion + "hasDocumentFile"
    val HasArchiveFile: IRI     = KnoraApiV2PrefixExpansion + "hasArchiveFile"

    val File: IRI = KnoraApiV2PrefixExpansion + "File"

    // The set of custom datatypes defined in knora-api in the simple schema. InstanceChecker and
    // JenaNodeFactory rely on this.
    lazy val KnoraDatatypes: Set[IRI] = Set(
      Date,
      ListNode,
      Geom,
      Color,
      Interval,
      Geoname,
      File,
    )

    val ArkUrl: IRI        = KnoraApiV2PrefixExpansion + "arkUrl"
    val VersionArkUrl: IRI = KnoraApiV2PrefixExpansion + "versionArkUrl"

    val GravsearchOptions: IRI = KnoraApiV2PrefixExpansion + "GravsearchOptions"
    val UseInference: IRI      = KnoraApiV2PrefixExpansion + "useInference"
  }

  /**
   * A map of IRIs in each possible source schema to the corresponding IRIs in each possible target schema, for the
   * cases where this can't be done formally by [[SmartIri]].
   */
  val CorrespondingIris: Map[(OntologySchema, OntologySchema), Map[IRI, IRI]] = Map(
    (InternalSchema, ApiV2Simple) -> Map(
      // All the values of this map must be either properties or datatypes. PropertyInfoContentV2.toOntologySchema
      // relies on this assumption.
      KnoraBase.SubjectClassConstraint      -> KnoraApiV2Simple.SubjectType,
      KnoraBase.ObjectClassConstraint       -> KnoraApiV2Simple.ObjectType,
      KnoraBase.ObjectDatatypeConstraint    -> KnoraApiV2Simple.ObjectType,
      KnoraBase.TextValue                   -> Xsd.String,
      KnoraBase.IntValue                    -> Xsd.Integer,
      KnoraBase.BooleanValue                -> Xsd.Boolean,
      KnoraBase.UriValue                    -> Xsd.Uri,
      KnoraBase.DecimalValue                -> Xsd.Decimal,
      KnoraBase.TimeValue                   -> Xsd.DateTimeStamp,
      KnoraBase.DateValue                   -> KnoraApiV2Simple.Date,
      KnoraBase.ColorValue                  -> KnoraApiV2Simple.Color,
      KnoraBase.GeomValue                   -> KnoraApiV2Simple.Geom,
      KnoraBase.ListValue                   -> KnoraApiV2Simple.ListNode,
      KnoraBase.IntervalValue               -> KnoraApiV2Simple.Interval,
      KnoraBase.GeonameValue                -> KnoraApiV2Simple.Geoname,
      KnoraBase.FileValue                   -> KnoraApiV2Simple.File,
      KnoraBase.StillImageFileValue         -> KnoraApiV2Simple.File,
      KnoraBase.StillImageExternalFileValue -> KnoraApiV2Simple.File,
      KnoraBase.MovingImageFileValue        -> KnoraApiV2Simple.File,
      KnoraBase.AudioFileValue              -> KnoraApiV2Simple.File,
      KnoraBase.DDDFileValue                -> KnoraApiV2Simple.File,
      KnoraBase.TextFileValue               -> KnoraApiV2Simple.File,
      KnoraBase.DocumentFileValue           -> KnoraApiV2Simple.File,
      KnoraBase.ArchiveFileValue            -> KnoraApiV2Simple.File,
      KnoraBase.HasFileValue                -> KnoraApiV2Simple.HasFile,
      KnoraBase.HasStillImageFileValue      -> KnoraApiV2Simple.HasStillImageFile,
      KnoraBase.HasMovingImageFileValue     -> KnoraApiV2Simple.HasMovingImageFile,
      KnoraBase.HasAudioFileValue           -> KnoraApiV2Simple.HasAudioFile,
      KnoraBase.HasDDDFileValue             -> KnoraApiV2Simple.HasDDDFile,
      KnoraBase.HasTextFileValue            -> KnoraApiV2Simple.HasTextFile,
      KnoraBase.HasDocumentFileValue        -> KnoraApiV2Simple.HasDocumentFile,
      KnoraBase.HasArchiveFileValue         -> KnoraApiV2Simple.HasArchiveFile,
    ),
    (InternalSchema, ApiV2Complex) -> Map(
      KnoraBase.SubjectClassConstraint   -> KnoraApiV2Complex.SubjectType,
      KnoraBase.ObjectClassConstraint    -> KnoraApiV2Complex.ObjectType,
      KnoraBase.ObjectDatatypeConstraint -> KnoraApiV2Complex.ObjectType,
      KnoraBase.ValueHasString           -> KnoraApiV2Complex.ValueAsString,
      KnoraBase.ValueHasUri              -> KnoraApiV2Complex.UriValueAsUri,
      KnoraBase.ValueHasInteger          -> KnoraApiV2Complex.IntValueAsInt,
      KnoraBase.ValueHasDecimal          -> KnoraApiV2Complex.DecimalValueAsDecimal,
      KnoraBase.ValueHasBoolean          -> KnoraApiV2Complex.BooleanValueAsBoolean,
      KnoraBase.ValueHasIntervalStart    -> KnoraApiV2Complex.IntervalValueHasStart,
      KnoraBase.ValueHasIntervalEnd      -> KnoraApiV2Complex.IntervalValueHasEnd,
      KnoraBase.ValueHasTimeStamp        -> KnoraApiV2Complex.TimeValueAsTimeStamp,
      KnoraBase.ValueHasLanguage         -> KnoraApiV2Complex.TextValueHasLanguage,
      KnoraBase.ValueHasListNode         -> KnoraApiV2Complex.ListValueAsListNode,
      KnoraBase.ValueHasGeonameCode      -> KnoraApiV2Complex.GeonameValueAsGeonameCode,
      KnoraBase.ValueHasColor            -> KnoraApiV2Complex.ColorValueAsColor,
      KnoraBase.ValueHasStandoff         -> KnoraApiV2Complex.TextValueHasStandoff,
      KnoraAdmin.KnoraProject            -> Xsd.Uri,
      KnoraAdmin.User                    -> Xsd.Uri,
    ),
    (ApiV2Simple, InternalSchema) -> Map(
      // Not all types in ApiV2Simple can be converted here to types in KnoraBase. For example,
      // to know whether an xsd:string corresponds to a knora-base:TextValue, or whether it should remain
      // an xsd:string, we would need to know the context in which it is used, which we don't have here.
      KnoraApiV2Simple.SubjectType        -> KnoraBase.SubjectClassConstraint,
      KnoraApiV2Simple.ObjectType         -> KnoraBase.ObjectClassConstraint,
      KnoraApiV2Simple.Date               -> KnoraBase.DateValue,
      KnoraApiV2Simple.Color              -> KnoraBase.ColorValue,
      KnoraApiV2Simple.Geom               -> KnoraBase.GeomValue,
      KnoraApiV2Simple.Interval           -> KnoraBase.IntervalValue,
      KnoraApiV2Simple.Geoname            -> KnoraBase.GeonameValue,
      KnoraApiV2Simple.File               -> KnoraBase.FileValue,
      KnoraApiV2Simple.HasFile            -> KnoraBase.HasFileValue,
      KnoraApiV2Simple.HasStillImageFile  -> KnoraBase.HasStillImageFileValue,
      KnoraApiV2Simple.HasMovingImageFile -> KnoraBase.HasMovingImageFileValue,
      KnoraApiV2Simple.HasAudioFile       -> KnoraBase.HasAudioFileValue,
      KnoraApiV2Simple.HasDDDFile         -> KnoraBase.HasDDDFileValue,
      KnoraApiV2Simple.HasTextFile        -> KnoraBase.HasTextFileValue,
      KnoraApiV2Simple.HasDocumentFile    -> KnoraBase.HasDocumentFileValue,
      KnoraApiV2Simple.HasArchiveFile     -> KnoraBase.HasArchiveFileValue,
      KnoraApiV2Simple.ListNode           -> KnoraBase.ListValue,
    ),
    (ApiV2Complex, InternalSchema) -> Map(
      KnoraApiV2Complex.SubjectType               -> KnoraBase.SubjectClassConstraint,
      KnoraApiV2Complex.ObjectType                -> KnoraBase.ObjectClassConstraint,
      KnoraApiV2Complex.UriValueAsUri             -> KnoraBase.ValueHasUri,
      KnoraApiV2Complex.IntValueAsInt             -> KnoraBase.ValueHasInteger,
      KnoraApiV2Complex.DecimalValueAsDecimal     -> KnoraBase.ValueHasDecimal,
      KnoraApiV2Complex.BooleanValueAsBoolean     -> KnoraBase.ValueHasBoolean,
      KnoraApiV2Complex.IntervalValueHasStart     -> KnoraBase.ValueHasIntervalStart,
      KnoraApiV2Complex.IntervalValueHasEnd       -> KnoraBase.ValueHasIntervalEnd,
      KnoraApiV2Complex.TimeValueAsTimeStamp      -> KnoraBase.ValueHasTimeStamp,
      KnoraApiV2Complex.ValueAsString             -> KnoraBase.ValueHasString,
      KnoraApiV2Complex.TextValueHasLanguage      -> KnoraBase.ValueHasLanguage,
      KnoraApiV2Complex.ListValueAsListNode       -> KnoraBase.ValueHasListNode,
      KnoraApiV2Complex.GeonameValueAsGeonameCode -> KnoraBase.ValueHasGeonameCode,
      KnoraApiV2Complex.ColorValueAsColor         -> KnoraBase.ValueHasColor,
      KnoraApiV2Complex.TextValueHasStandoff      -> KnoraBase.ValueHasStandoff,
    ),
  )

  /**
   * For the named graphs in the admin slice refer to
   * [[org.knora.webapi.slice.admin.AdminConstants]].
   */
  object NamedGraphs {
    val DataNamedGraphStart: IRI = "http://www.knora.org/data"

  }

  object Fuseki {
    val luceneQueryPredicate = "http://jena.apache.org/text#query"
  }
}
