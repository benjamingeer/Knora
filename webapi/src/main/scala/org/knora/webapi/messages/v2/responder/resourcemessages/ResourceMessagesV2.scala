/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder.resourcemessages

import java.time.Instant
import java.util.UUID

import dsp.errors.*
import dsp.valueobjects.UuidUtil
import org.knora.webapi.*
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.RelayedMessage
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants.*
import org.knora.webapi.messages.ResponderRequest.KnoraRequestV2
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.*
import org.knora.webapi.messages.util.rdf.*
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.messages.util.standoff.XMLUtil
import org.knora.webapi.messages.v2.responder.*
import org.knora.webapi.messages.v2.responder.standoffmessages.MappingXMLtoStandoff
import org.knora.webapi.messages.v2.responder.valuemessages.*
import org.knora.webapi.messages.v2.responder.valuemessages.ValueMessagesV2Optics.*
import org.knora.webapi.slice.admin.api.model.Project
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.model.User

/**
 * An abstract trait for messages that can be sent to `ResourcesResponderV2`.
 */
sealed trait ResourcesResponderRequestV2 extends KnoraRequestV2 with RelayedMessage {

  /**
   * The user that made the request.
   */
  def requestingUser: User
}

/**
 * Requests a description of a resource. A successful response will be a [[ReadResourcesSequenceV2]].
 *
 * @param resourceIris         the IRIs of the resources to be queried.
 * @param propertyIri          if defined, requests only the values of the specified explicit property.
 * @param withDeleted          if defined, returns a deleted resource or a deleted value.
 * @param valueUuid            if defined, requests only the value with the specified UUID.
 * @param versionDate          if defined, requests the state of the resources at the specified time in the past.
 * @param targetSchema         the target API schema.
 * @param schemaOptions        the schema options submitted with the request.
 * @param requestingUser       the user making the request.
 */
case class ResourcesGetRequestV2(
  resourceIris: Seq[IRI],
  propertyIri: Option[SmartIri] = None,
  valueUuid: Option[UUID] = None,
  versionDate: Option[Instant] = None,
  withDeleted: Boolean = true,
  targetSchema: ApiV2Schema,
  schemaOptions: Set[Rendering] = Set.empty,
  requestingUser: User,
) extends ResourcesResponderRequestV2

/**
 * Requests a preview of one or more resources. A successful response will be a [[ReadResourcesSequenceV2]].
 *
 * @param resourceIris         the IRIs of the resources to obtain a preview for.
 * @param withDeletedResource  indicates if a preview of deleted resource should be returned.
 * @param targetSchema         the schema of the response.
 * @param requestingUser       the user making the request.
 */
case class ResourcesPreviewGetRequestV2(
  resourceIris: Seq[IRI],
  withDeletedResource: Boolean = true,
  targetSchema: ApiV2Schema,
  requestingUser: User,
) extends ResourcesResponderRequestV2

/**
 * Requests a IIIF manifest for the images that are `knora-base:isPartOf` the specified
 * resource.
 *
 * @param resourceIri          the resource IRI.
 * @param requestingUser       the user making the request.
 */
case class ResourceIIIFManifestGetRequestV2(
  resourceIri: IRI,
  requestingUser: User,
) extends ResourcesResponderRequestV2

/**
 * Represents a IIIF manifest for the images that are `knora-base:isPartOf` the specified
 * resource.
 *
 * @param manifest the IIIF manifest.
 */
case class ResourceIIIFManifestGetResponseV2(manifest: JsonLDDocument) extends KnoraJsonLDResponseV2 {
  override protected def toJsonLDDocument(
    targetSchema: ApiV2Schema,
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
  ): JsonLDDocument = manifest
}

/**
 * Requests the version history of the values of a resource.
 *
 * @param resourceIri          the IRI of the resource.
 * @param withDeletedResource  indicates if the version history of deleted resources should be returned or not.
 * @param startDate            the start of the time period to return, inclusive.
 * @param endDate              the end of the time period to return, exclusive.
 * @param requestingUser       the user making the request.
 */
case class ResourceVersionHistoryGetRequestV2(
  resourceIri: IRI,
  withDeletedResource: Boolean = false,
  startDate: Option[Instant] = None,
  endDate: Option[Instant] = None,
  requestingUser: User,
) extends ResourcesResponderRequestV2

/**
 * Requests the version history of all resources of a project.
 *
 * @param projectIri          the IRI of the project.
 * @param requestingUser       the user making the request.
 */
case class ProjectResourcesWithHistoryGetRequestV2(
  projectIri: IRI,
  requestingUser: User,
) extends ResourcesResponderRequestV2 {
  ProjectIri.from(projectIri).getOrElse(throw BadRequestException(s"Invalid project IRI: $projectIri"))
}

/**
 * Represents an item in the version history of a resource.
 *
 * @param versionDate the date when the modification occurred.
 * @param author      the IRI of the user that made the modification.
 */
case class ResourceHistoryEntry(versionDate: Instant, author: IRI)

/**
 * Represents the version history of the values of a resource.
 */
case class ResourceVersionHistoryResponseV2(history: Seq[ResourceHistoryEntry]) extends KnoraJsonLDResponseV2 {

  /**
   * Converts the response to a data structure that can be used to generate JSON-LD.
   *
   * @param targetSchema the Knora API schema to be used in the JSON-LD document.
   * @return a [[JsonLDDocument]] representing the response.
   */
  override def toJsonLDDocument(
    targetSchema: ApiV2Schema,
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
  ): JsonLDDocument = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    if (targetSchema != ApiV2Complex) {
      throw AssertionException("Version history can be returned only in the complex schema")
    }

    // Convert the history entries to an array of JSON-LD objects.

    val historyAsJsonLD: Seq[JsonLDObject] = history.map { (historyEntry: ResourceHistoryEntry) =>
      JsonLDObject(
        Map(
          KnoraApiV2Complex.VersionDate -> JsonLDUtil.datatypeValueToJsonLDObject(
            value = historyEntry.versionDate.toString,
            datatype = Xsd.DateTimeStamp.toSmartIri,
          ),
          KnoraApiV2Complex.Author -> JsonLDUtil.iriToJsonLDObject(historyEntry.author),
        ),
      )
    }

    // Make the JSON-LD context.

    val context = JsonLDUtil.makeContext(
      fixedPrefixes = Map(
        "rdf"                          -> Rdf.RdfPrefixExpansion,
        "rdfs"                         -> Rdfs.RdfsPrefixExpansion,
        "xsd"                          -> Xsd.XsdPrefixExpansion,
        KnoraApi.KnoraApiOntologyLabel -> KnoraApiV2Complex.KnoraApiV2PrefixExpansion,
      ),
    )

    // Make the JSON-LD document.

    val body = JsonLDObject(Map(JsonLDKeywords.GRAPH -> JsonLDArray(historyAsJsonLD)))

    JsonLDDocument(body = body, context = context)
  }
}

/**
 * Requests a resource as TEI/XML. A successful response will be a [[ResourceTEIGetResponseV2]].
 *
 * @param resourceIri           the IRI of the resource to be returned in TEI/XML.
 * @param textProperty          the property representing the text (to be converted to the body of a TEI document).
 * @param mappingIri            the IRI of the mapping to be used to convert from standoff to TEI/XML, if any. Otherwise the standard mapping is assumed.
 * @param gravsearchTemplateIri the gravsearch template to query the metadata for the TEI header, if provided.
 * @param headerXSLTIri         the IRI of the XSL transformation to convert the resource's metadata to the TEI header.
 * @param requestingUser        the user making the request.
 */
case class ResourceTEIGetRequestV2(
  resourceIri: IRI,
  textProperty: SmartIri,
  mappingIri: Option[IRI],
  gravsearchTemplateIri: Option[IRI],
  headerXSLTIri: Option[IRI],
  requestingUser: User,
) extends ResourcesResponderRequestV2

/**
 * Represents a Knora resource as TEI/XML.
 *
 * @param header the header of the TEI document, if given.
 * @param body   the body of the TEI document.
 */
case class ResourceTEIGetResponseV2(header: TEIHeader, body: TEIBody) {

  def toXML: String =
    s"""<?xml version="1.0" encoding="UTF-8"?>
       |<TEI version="3.3.0" xmlns="http://www.tei-c.org/ns/1.0">
       |${header.toXML}
       |${body.toXML}
       |</TEI>""".stripMargin
}

/**
 * Represents information that is going to be contained in the header of a TEI/XML document.
 *
 * @param headerInfo the resource representing the header information.
 * @param headerXSLT XSLT to be applied to the resource's metadata in RDF/XML.
 */
case class TEIHeader(
  headerInfo: ReadResourceV2,
  headerXSLT: Option[String],
  appConfig: AppConfig,
) {

  def toXML: String =
    if (headerXSLT.nonEmpty) {

      // Convert the resource to a JsonLDDocument.
      val headerJsonLD: JsonLDDocument =
        ReadResourcesSequenceV2(Seq(headerInfo)).toJsonLDDocument(ApiV2Complex, appConfig)

      // Convert the JsonLDDocument to an RdfModel.
      val rdfModel: RdfModel = headerJsonLD.toRdfModel

      // Format the RdfModel as RDF/XML. To ensure that it contains only rdf:Description elements,
      // set prettyPrint to false.
      val teiXmlHeader: String = RdfFormatUtil.format(
        rdfModel = rdfModel,
        rdfFormat = RdfXml,
        prettyPrint = false,
      )

      // Run an XSL transformation to convert the RDF/XML to a TEI/XML header.
      XMLUtil.applyXSLTransformation(xml = teiXmlHeader, xslt = headerXSLT.get)
    } else {
      s"""
         |<teiHeader>
         | <fileDesc>
         |     <titleStmt>
         |         <title>${headerInfo.label}</title>
         |     </titleStmt>
         |     <publicationStmt>
         |         <p>
         |             This is the TEI/XML representation of a resource identified by the Iri ${headerInfo.resourceIri}.
         |         </p>
         |     </publicationStmt>
         |     <sourceDesc>
         |        <p>Representation of the resource's text as TEI/XML</p>
         |     </sourceDesc>
         | </fileDesc>
         |</teiHeader>
         """.stripMargin
    }

}

/**
 * Represents the actual text that is going to be converted to the body of a TEI document.
 *
 * @param bodyInfo   the content of the text value that will be converted to TEI.
 * @param teiMapping the mapping from standoff to TEI/XML.
 * @param bodyXSLT   the XSLT transformation that completes the generation of TEI/XML.
 */
case class TEIBody(bodyInfo: TextValueContentV2, teiMapping: MappingXMLtoStandoff, bodyXSLT: String) {

  def toXML: String = {
    if (bodyInfo.standoff.isEmpty) throw BadRequestException(s"text is expected to have standoff markup")

    // create XML from standoff (temporary XML) that is going to be converted to TEI/XML
    val tmpXml = StandoffTagUtilV2.convertStandoffTagV2ToXML(bodyInfo.valueHasString, bodyInfo.standoff, teiMapping)

    XMLUtil.applyXSLTransformation(tmpXml, bodyXSLT)
  }

}

/**
 * Represents a Knora resource. Any implementation of `ResourceV2` is API operation specific.
 */
sealed trait ResourceV2 {

  /**
   * The IRI of the resource class.
   */
  def resourceClassIri: SmartIri

  /**
   * The resource's `rdfs:label`.
   */
  def label: String
}

/**
 * Represents a Knora resource when being read back from the triplestore.
 *
 * @param resourceIri          the IRI of the resource.
 * @param label                the resource's label.
 * @param resourceClassIri     the class the resource belongs to.
 * @param attachedToUser       the user that created the resource.
 * @param projectADM           the project that the resource belongs to.
 * @param permissions          the permissions that the resource grants to user groups.
 * @param userPermission       the permission the the requesting user has on the resource.
 * @param values               a map of property IRIs to values.
 * @param creationDate         the date when this resource was created.
 * @param lastModificationDate the date when this resource was last modified.
 * @param versionDate          if this is a past version of the resource, the date of the version.
 * @param deletionInfo         if this resource has been marked as deleted, provides the date when it was
 *                             deleted and the reason why it was deleted.
 */
case class ReadResourceV2(
  resourceIri: IRI,
  label: String,
  resourceClassIri: SmartIri,
  attachedToUser: IRI,
  projectADM: Project,
  permissions: String,
  userPermission: Permission.ObjectAccess,
  values: Map[SmartIri, Seq[ReadValueV2]],
  creationDate: Instant,
  lastModificationDate: Option[Instant],
  versionDate: Option[Instant],
  deletionInfo: Option[DeletionInfo],
) extends ResourceV2
    with KnoraReadV2[ReadResourceV2] {
  override def toOntologySchema(targetSchema: ApiV2Schema): ReadResourceV2 =
    copy(
      resourceClassIri = resourceClassIri.toOntologySchema(targetSchema),
      values = values.map { case (propertyIri, readValues) =>
        val propertyIriInTargetSchema = propertyIri.toOntologySchema(targetSchema)

        // In the simple schema, use link properties instead of link value properties.
        val adaptedPropertyIri = if (targetSchema == ApiV2Simple) {
          // If all the property's values are link values, it's a link value property.
          val isLinkProp = readValues.forall { readValue =>
            readValue.valueContent match {
              case _: LinkValueContentV2 => true
              case _                     => false
            }
          }

          // If it's a link value property, use the corresponding link property.
          if (isLinkProp) {
            propertyIriInTargetSchema.fromLinkValuePropToLinkProp
          } else {
            propertyIriInTargetSchema
          }
        } else {
          propertyIriInTargetSchema
        }

        adaptedPropertyIri -> readValues.map(_.toOntologySchema(targetSchema))
      },
    )

  def toJsonLD(
    targetSchema: ApiV2Schema,
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
  ): JsonLDObject = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    if (!resourceClassIri.getOntologySchema.contains(targetSchema)) {
      throw DataConversionException(s"ReadClassInfoV2 for resource $resourceIri is not in schema $targetSchema")
    }

    val propertiesAndValuesAsJsonLD: Map[IRI, JsonLDArray] = values.map {
      case (propIri: SmartIri, readValues: Seq[ReadValueV2]) =>
        val valuesAsJsonLD: Seq[JsonLDValue] = readValues.map { readValue =>
          readValue.toJsonLD(
            targetSchema = targetSchema,
            projectADM = projectADM,
            appConfig = appConfig,
            schemaOptions = schemaOptions,
          )
        }

        propIri.toString -> JsonLDArray(valuesAsJsonLD)
    }

    val metadataForComplexSchema: Map[IRI, JsonLDValue] = if (targetSchema == ApiV2Complex) {
      val requiredMetadataForComplexSchema: Map[IRI, JsonLDValue] = Map(
        KnoraApiV2Complex.AttachedToUser    -> JsonLDUtil.iriToJsonLDObject(attachedToUser),
        KnoraApiV2Complex.AttachedToProject -> JsonLDUtil.iriToJsonLDObject(projectADM.id),
        KnoraApiV2Complex.HasPermissions    -> JsonLDString(permissions),
        KnoraApiV2Complex.UserHasPermission -> JsonLDString(userPermission.toString),
        KnoraApiV2Complex.CreationDate -> JsonLDUtil.datatypeValueToJsonLDObject(
          value = creationDate.toString,
          datatype = Xsd.DateTimeStamp.toSmartIri,
        ),
      )

      val deletionInfoAsJsonLD: Map[IRI, JsonLDValue] = deletionInfo match {
        case Some(definedDeletionInfo) => definedDeletionInfo.toJsonLDFields(ApiV2Complex)
        case None                      => Map.empty[IRI, JsonLDValue]
      }

      val lastModDateAsJsonLD: Option[(IRI, JsonLDValue)] = lastModificationDate.map { definedLastModDate =>
        KnoraApiV2Complex.LastModificationDate -> JsonLDUtil.datatypeValueToJsonLDObject(
          value = definedLastModDate.toString,
          datatype = Xsd.DateTimeStamp.toSmartIri,
        )
      }

      // If this is a past version of the resource, include knora-api:versionDate.

      val versionDateAsJsonLD = versionDate.map { definedVersionDate =>
        KnoraApiV2Complex.VersionDate -> JsonLDUtil.datatypeValueToJsonLDObject(
          value = definedVersionDate.toString,
          datatype = Xsd.DateTimeStamp.toSmartIri,
        )
      }

      requiredMetadataForComplexSchema ++ deletionInfoAsJsonLD ++ lastModDateAsJsonLD ++ versionDateAsJsonLD
    } else {
      Map.empty[IRI, JsonLDValue]
    }

    // Make an ARK URL without a version timestamp.

    val resourceSmartIri: SmartIri = resourceIri.toSmartIri

    val arkUrlProp: IRI = targetSchema match {
      case ApiV2Simple  => KnoraApiV2Simple.ArkUrl
      case ApiV2Complex => KnoraApiV2Complex.ArkUrl
    }

    val arkUrlAsJsonLD: (IRI, JsonLDObject) =
      arkUrlProp -> JsonLDUtil.datatypeValueToJsonLDObject(
        value = resourceSmartIri.fromResourceIriToArkUrl(),
        datatype = Xsd.Uri.toSmartIri,
      )

    // Make an ARK URL with a version timestamp.

    val versionArkUrlProp: IRI = targetSchema match {
      case ApiV2Simple  => KnoraApiV2Simple.VersionArkUrl
      case ApiV2Complex => KnoraApiV2Complex.VersionArkUrl
    }

    val arkTimestamp = versionDate.getOrElse(lastModificationDate.getOrElse(creationDate))

    val versionArkUrlAsJsonLD: (IRI, JsonLDObject) =
      versionArkUrlProp -> JsonLDUtil.datatypeValueToJsonLDObject(
        value = resourceSmartIri.fromResourceIriToArkUrl(maybeTimestamp = Some(arkTimestamp)),
        datatype = Xsd.Uri.toSmartIri,
      )

    JsonLDObject(
      Map(
        JsonLDKeywords.ID   -> JsonLDString(resourceIri),
        JsonLDKeywords.TYPE -> JsonLDString(resourceClassIri.toString),
        Rdfs.Label          -> JsonLDString(label),
      ) ++ propertiesAndValuesAsJsonLD ++ metadataForComplexSchema + arkUrlAsJsonLD + versionArkUrlAsJsonLD,
    )
  }

  /**
   * Transform this Resource into a generic representation for a deleted resource.
   *
   * The resulting Resource is of type `knora-base:DeletedResource`, has `"Deleted Resource"` as its label,
   * and no values at all.
   * Otherwise it is similar to the initial resource (including the IRI).
   *
   * Note that the `deletionInfo` holds the deletion date and optionally the deletion comment.
   *
   * @return A generic DeletedResource
   */
  def asDeletedResource(): ReadResourceV2 = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    ReadResourceV2(
      resourceIri = this.resourceIri,
      label = "Deleted Resource",
      resourceClassIri = KnoraBase.DeletedResource.toSmartIri,
      attachedToUser = this.attachedToUser,
      projectADM = this.projectADM,
      permissions = this.permissions,
      userPermission = this.userPermission,
      values = Map.empty,
      creationDate = this.creationDate,
      lastModificationDate = this.lastModificationDate,
      versionDate = None,
      deletionInfo = this.deletionInfo,
    )
  }

  /**
   * Return a copy of the present resource, where all values that are marked as deleted,
   * are replaced with a generic DeletedValue
   *
   * @return
   */
  def withDeletedValues(): ReadResourceV2 = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    val delIri: SmartIri                          = KnoraBase.DeletedValue.toSmartIri
    val valuesWithDeletedValues: Map[SmartIri, Seq[ReadValueV2]] =
      this.values.toList
        .foldLeft(Seq.empty[(SmartIri, ReadValueV2)])((aggregator, valueMap) =>
          valueMap match {
            case (iri: SmartIri, valueSequence: Seq[ReadValueV2]) =>
              val withDeletedSeq = valueSequence
                .map(value =>
                  value.deletionInfo match {
                    case Some(_) => (delIri, value.asDeletedValue())
                    case None    => (iri, value)
                  },
                )
              aggregator ++ withDeletedSeq
          },
        )
        .groupMap(_._1)(_._2)
    this.copy(
      values = valuesWithDeletedValues,
    )
  }
}

/**
 * The value of a Knora property sent to Knora to be created in a new resource.
 *
 * @param valueContent            the content of the new value. If the client wants to create a link, this must be a [[LinkValueContentV2]].
 * @param customValueIri          the optional custom value IRI.
 * @param customValueUUID         the optional custom value UUID.
 * @param customValueCreationDate the optional custom value creation date.
 * @param permissions             the permissions to be given to the new value. If not provided, these will be taken from defaults.
 */
case class CreateValueInNewResourceV2(
  valueContent: ValueContentV2,
  customValueIri: Option[SmartIri] = None,
  customValueUUID: Option[UUID] = None,
  customValueCreationDate: Option[Instant] = None,
  permissions: Option[String] = None,
)

/**
 * Represents a Knora resource to be created.
 *
 * @param resourceIri      the IRI that should be given to the resource.
 * @param resourceClassIri the class the resource belongs to.
 * @param label            the resource's label.
 * @param values           the resource's values.
 * @param projectADM       the project that the resource should belong to.
 * @param permissions      the permissions to be given to the new resource. If not provided, these will be taken from defaults.
 * @param creationDate     the optional creation date of the resource.
 */
case class CreateResourceV2(
  resourceIri: Option[SmartIri],
  resourceClassIri: SmartIri,
  label: String,
  values: Map[SmartIri, Seq[CreateValueInNewResourceV2]],
  projectADM: Project,
  permissions: Option[String] = None,
  creationDate: Option[Instant] = None,
) extends ResourceV2 {
  lazy val flatValues: Iterable[CreateValueInNewResourceV2] = values.values.flatten

  /**
   * Converts this [[CreateResourceV2]] to the specified ontology schema.
   *
   * @param targetSchema the target ontology schema.
   * @return a copy of this [[CreateResourceV2]] in the specified ontology schema.
   */
  def toOntologySchema(targetSchema: OntologySchema): CreateResourceV2 =
    copy(
      resourceClassIri = resourceClassIri.toOntologySchema(targetSchema),
      values = values.map { case (propertyIri, valuesToCreate) =>
        propertyIri.toOntologySchema(targetSchema) -> valuesToCreate.map { valueToCreate =>
          valueToCreate.copy(
            valueContent = valueToCreate.valueContent.toOntologySchema(targetSchema),
          )
        }
      },
    )
}

/**
 * Represents a request to create a resource.
 *
 * @param createResource       the resource to be created.
 * @param requestingUser       the user making the request.
 * @param apiRequestID         the API request ID.
 */
case class CreateResourceRequestV2(
  createResource: CreateResourceV2,
  requestingUser: User,
  apiRequestID: UUID,
) extends ResourcesResponderRequestV2

/**
 * Represents a request to update a resource's metadata.
 *
 * @param resourceIri               the IRI of the resource.
 * @param resourceClassIri          the IRI of the resource class.
 * @param maybeLastModificationDate the resource's last modification date, if any.
 * @param maybeLabel                the resource's new `rdfs:label`, if any.
 * @param maybePermissions          the resource's new permissions, if any.
 * @param maybeNewModificationDate  the resource's new last modification date, if any.
 */
case class UpdateResourceMetadataRequestV2(
  resourceIri: IRI,
  resourceClassIri: SmartIri,
  maybeLastModificationDate: Option[Instant] = None,
  maybeLabel: Option[String] = None,
  maybePermissions: Option[String] = None,
  maybeNewModificationDate: Option[Instant] = None,
  requestingUser: User,
  apiRequestID: UUID,
) extends ResourcesResponderRequestV2

/**
 * Represents a response after updating a resource's metadata.
 *
 * @param resourceIri               the IRI of the resource.
 * @param resourceClassIri          the IRI of the resource class.
 * @param lastModificationDate      the resource's last modification date.
 * @param maybeLabel                the resource's new `rdfs:label`, if any.
 * @param maybePermissions          the resource's new permissions, if any.
 */
case class UpdateResourceMetadataResponseV2(
  resourceIri: IRI,
  resourceClassIri: SmartIri,
  lastModificationDate: Instant,
  maybeLabel: Option[String] = None,
  maybePermissions: Option[String] = None,
) extends KnoraJsonLDResponseV2 {

  /**
   * Converts the response to a data structure that can be used to generate JSON-LD.
   *
   * @param targetSchema the Knora API schema to be used in the JSON-LD document.
   * @return a [[JsonLDDocument]] representing the response.
   */
  override protected def toJsonLDDocument(
    targetSchema: ApiV2Schema,
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
  ): JsonLDDocument = {

    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    // Make the knora-api prefix for the target schema.

    val knoraApiPrefixExpansion: IRI = targetSchema match {
      case ApiV2Simple  => KnoraApiV2Simple.KnoraApiV2PrefixExpansion
      case ApiV2Complex => KnoraApiV2Complex.KnoraApiV2PrefixExpansion
    }

    // Make the JSON-LD document.

    val context: JsonLDObject = JsonLDUtil.makeContext(
      fixedPrefixes = Map(
        "rdf"                          -> Rdf.RdfPrefixExpansion,
        "rdfs"                         -> Rdfs.RdfsPrefixExpansion,
        "xsd"                          -> Xsd.XsdPrefixExpansion,
        KnoraApi.KnoraApiOntologyLabel -> knoraApiPrefixExpansion,
      ),
    )

    val label_map = maybeLabel match {
      case Some(maybeLabel) => Map(Rdfs.Label -> JsonLDString(maybeLabel))
      case None             => Map.empty[String, JsonLDValue]
    }

    val lastModificationDate_map =
      Map(
        KnoraApiV2Complex.LastModificationDate -> JsonLDUtil.datatypeValueToJsonLDObject(
          value = lastModificationDate.toString,
          datatype = Xsd.DateTimeStamp.toSmartIri,
        ),
      )

    val permissions_map = maybePermissions match {
      case Some(maybePermissions) =>
        Map(KnoraApiV2Complex.HasPermissions -> JsonLDString(maybePermissions))
      case None => Map.empty[String, JsonLDValue]
    }

    val resourceIri_resourceClassIri_map =
      Map(
        KnoraApiV2Complex.ResourceIri      -> JsonLDString(resourceIri),
        KnoraApiV2Complex.ResourceClassIri -> JsonLDString(resourceClassIri.toString),
      )

    val body = JsonLDObject(
      resourceIri_resourceClassIri_map ++ label_map ++ lastModificationDate_map ++ permissions_map,
    )

    JsonLDDocument(body = body, context = context)

  }
}

/**
 * Represents a request to mark a resource as deleted or to erase it from the triplestore.
 *
 * @param resourceIri               the IRI of the resource.
 * @param resourceClassIri          the IRI of the resource class.
 * @param maybeDeleteComment        a comment explaining why the resource is being marked as deleted.
 * @param maybeDeleteDate           a timestamp indicating when the resource was marked as deleted. If not supplied,
 *                                  the current time will be used.
 * @param maybeLastModificationDate the resource's last modification date, if any.
 * @param erase                     if `true`, the resource will be erased from the triplestore, otherwise it will be marked as deleted.
 */
case class DeleteOrEraseResourceRequestV2(
  resourceIri: IRI,
  resourceClassIri: SmartIri,
  maybeDeleteComment: Option[String] = None,
  maybeDeleteDate: Option[Instant] = None,
  maybeLastModificationDate: Option[Instant] = None,
  erase: Boolean = false,
  requestingUser: User,
  apiRequestID: UUID,
) extends ResourcesResponderRequestV2

/**
 * Represents a sequence of resources read back from Knora.
 *
 * @param resources          a sequence of resources that the user has permission to see.
 * @param hiddenResourceIris the IRIs of resources that were requested but that the user did not have permission to see.
 * @param mayHaveMoreResults `true` if more resources matching the request may be available.
 */
case class ReadResourcesSequenceV2(
  resources: Seq[ReadResourceV2],
  hiddenResourceIris: Set[IRI] = Set.empty,
  mayHaveMoreResults: Boolean = false,
) extends KnoraJsonLDResponseV2
    with KnoraReadV2[ReadResourcesSequenceV2]
    with UpdateResultInProject { self =>

  override def toOntologySchema(targetSchema: ApiV2Schema): ReadResourcesSequenceV2 =
    copy(
      resources = resources.map(_.toOntologySchema(targetSchema)),
    )

  private def getOntologiesFromResource(resource: ReadResourceV2): Set[SmartIri] = {
    val propertyIriOntologies: Set[SmartIri] = resource.values.keySet.map(_.getOntologyFromEntity)

    val valueOntologies: Set[SmartIri] = resource.values.values.flatten.collect {
      case readLinkValueV2: ReadLinkValueV2 =>
        readLinkValueV2.valueContent.nestedResource.map(nested => getOntologiesFromResource(nested))
    }.flatten.flatten.toSet

    propertyIriOntologies ++ valueOntologies + resource.resourceClassIri.getOntologyFromEntity
  }

  private def generateJsonLD(
    targetSchema: ApiV2Schema,
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
  ): JsonLDDocument = {
    val resourcesJsonObjects: Seq[JsonLDObject] = resources.map { (resource: ReadResourceV2) =>
      resource.toJsonLD(
        targetSchema = targetSchema,
        appConfig = appConfig,
        schemaOptions = schemaOptions,
      )
    }

    // Make JSON-LD prefixes for the project-specific ontologies used in the response.

    val projectSpecificOntologiesUsed: Set[SmartIri] = resources.flatMap { resource =>
      getOntologiesFromResource(resource)
    }.toSet
      .filter(!_.isKnoraBuiltInDefinitionIri)

    // Make the knora-api prefix for the target schema.

    val knoraApiPrefixExpansion: IRI = targetSchema match {
      case ApiV2Simple  => KnoraApiV2Simple.KnoraApiV2PrefixExpansion
      case ApiV2Complex => KnoraApiV2Complex.KnoraApiV2PrefixExpansion
    }

    // Make the JSON-LD document.

    val context: JsonLDObject = JsonLDUtil.makeContext(
      fixedPrefixes = Map(
        "rdf"                          -> Rdf.RdfPrefixExpansion,
        "rdfs"                         -> Rdfs.RdfsPrefixExpansion,
        "xsd"                          -> Xsd.XsdPrefixExpansion,
        KnoraApi.KnoraApiOntologyLabel -> knoraApiPrefixExpansion,
      ),
      knoraOntologiesNeedingPrefixes = projectSpecificOntologiesUsed,
    )

    val mayHaveMoreResultsStatement: Option[(IRI, JsonLDBoolean)] = if (mayHaveMoreResults) {
      val mayHaveMoreResultsProp: IRI = targetSchema match {
        case ApiV2Simple  => KnoraApiV2Simple.MayHaveMoreResults
        case ApiV2Complex => KnoraApiV2Complex.MayHaveMoreResults
      }

      Some(mayHaveMoreResultsProp -> JsonLDBoolean(mayHaveMoreResults))
    } else {
      None
    }

    val body = JsonLDObject(
      Map(
        JsonLDKeywords.GRAPH -> JsonLDArray(resourcesJsonObjects),
      ) ++ mayHaveMoreResultsStatement,
    )

    JsonLDDocument(body = body, context = context)

  }

  override def toJsonLDDocument(
    targetSchema: ApiV2Schema,
    appConfig: AppConfig,
    schemaOptions: Set[Rendering] = Set.empty,
  ): JsonLDDocument =
    toOntologySchema(targetSchema)
      .generateJsonLD(
        targetSchema = targetSchema,
        appConfig = appConfig,
        schemaOptions = schemaOptions,
      )

  /**
   * Checks that a [[ReadResourcesSequenceV2]] contains exactly one resource, and returns that resource.
   *
   * @param requestedResourceIri the IRI of the expected resource.
   * @return the resource.
   * @throws NotFoundException   if the resource is not found.
   * @throws ForbiddenException  if the user does not have permission to see the requested resource.
   * @throws BadRequestException if more than one resource was returned.
   */
  @throws[NotFoundException]("if the resource is not found.")
  @throws[ForbiddenException]("if the user does not have permission to see the requested resource.")
  def toResource(requestedResourceIri: IRI): ReadResourceV2 = {
    if (hiddenResourceIris.contains(requestedResourceIri)) {
      throw ForbiddenException(s"You do not have permission to see resource <$requestedResourceIri>")
    }

    if (resources.isEmpty) {
      throw NotFoundException(s"Expected <$requestedResourceIri>, but no resources were returned")
    }

    if (resources.size > 1) {
      throw BadRequestException(s"Expected one resource, <$requestedResourceIri>, but more than one was returned")
    }

    if (resources.head.resourceIri != requestedResourceIri) {
      throw NotFoundException(
        s"Expected resource <$requestedResourceIri>, but <${resources.head.resourceIri}> was returned",
      )
    }

    resources.head
  }

  /**
   * Checks that requested resources were found and that the user has permission to see them. If not, throws an exception.
   *
   * @param targetResourceIris the IRIs to be checked.
   * @param resourcesSequence  the result of requesting those IRIs.
   * @throws NotFoundException  if the requested resources are not found.
   * @throws ForbiddenException if the user does not have permission to see the requested resources.
   */
  def checkResourceIris(targetResourceIris: Set[IRI], resourcesSequence: ReadResourcesSequenceV2): Unit = {
    val hiddenTargetResourceIris: Set[IRI] = targetResourceIris.intersect(resourcesSequence.hiddenResourceIris)

    if (hiddenTargetResourceIris.nonEmpty) {
      throw ForbiddenException(
        s"You do not have permission to see one or more resources: ${hiddenTargetResourceIris.map(iri => s"<$iri>").mkString(", ")}",
      )
    }

    val missingResourceIris: Set[IRI] = targetResourceIris -- resourcesSequence.resources.map(_.resourceIri).toSet

    if (missingResourceIris.nonEmpty) {
      throw NotFoundException(
        s"One or more resources were not found:  ${missingResourceIris.map(iri => s"<$iri>").mkString(", ")}",
      )
    }
  }

  /**
   * Considers this [[ReadResourcesSequenceV2]] to be the result of an update operation in a single project
   * (since Knora never updates resources in more than one project at a time), and returns information about that
   * project. Throws [[AssertionException]] if this [[ReadResourcesSequenceV2]] is empty or refers to more than one
   * project.
   */
  override def projectADM: Project = {
    if (resources.isEmpty) {
      throw AssertionException("ReadResourcesSequenceV2 is empty")
    }

    val allProjects: Set[Project] = resources.map(_.projectADM).toSet

    if (allProjects.size != 1) {
      throw AssertionException("ReadResourcesSequenceV2 refers to more than one project")
    }

    allProjects.head
  }
}

/**
 * Requests a graph of resources that are reachable via links to or from a given resource. A successful response
 * will be a [[GraphDataGetResponseV2]].
 *
 * @param resourceIri     the IRI of the initial resource.
 * @param depth           the maximum depth of the graph, counting from the initial resource.
 * @param inbound         `true` to query inbound links.
 * @param outbound        `true` to query outbound links.
 * @param excludeProperty the IRI of a link property to exclude from the results.
 * @param requestingUser  the user making the request.
 */
case class GraphDataGetRequestV2(
  resourceIri: IRI,
  depth: Int,
  inbound: Boolean,
  outbound: Boolean,
  excludeProperty: Option[SmartIri],
  requestingUser: User,
) extends ResourcesResponderRequestV2 {
  if (!(inbound || outbound)) {
    throw BadRequestException("No link direction selected")
  }
}

/**
 * Represents a node (i.e. a resource) in a resource graph.
 *
 * @param resourceIri      the IRI of the resource.
 * @param resourceLabel    the label of the resource.
 * @param resourceClassIri the IRI of the resource's OWL class.
 */
case class GraphNodeV2(resourceIri: IRI, resourceClassIri: SmartIri, resourceLabel: String)
    extends KnoraReadV2[GraphNodeV2] {
  override def toOntologySchema(targetSchema: ApiV2Schema): GraphNodeV2 =
    copy(resourceClassIri = resourceClassIri.toOntologySchema(targetSchema))
}

/**
 * Represents an edge (i.e. a link) in a resource graph.
 *
 * @param source      the resource that is the source of the link.
 * @param propertyIri the link property that links the source to the target.
 * @param target      the resource that is the target of the link.
 */
case class GraphEdgeV2(source: IRI, propertyIri: SmartIri, target: IRI) extends KnoraReadV2[GraphEdgeV2] {
  override def toOntologySchema(targetSchema: ApiV2Schema): GraphEdgeV2 =
    copy(propertyIri = propertyIri.toOntologySchema(targetSchema))
}

/**
 * Represents a graph of resources.
 *
 * @param nodes the nodes in the graph.
 * @param edges the edges in the graph.
 */
case class GraphDataGetResponseV2(nodes: Seq[GraphNodeV2], edges: Seq[GraphEdgeV2], ontologySchema: OntologySchema)
    extends KnoraJsonLDResponseV2
    with KnoraReadV2[GraphDataGetResponseV2] {
  private def generateJsonLD(targetSchema: ApiV2Schema): JsonLDDocument = {
    val sortedNodesInTargetSchema: Seq[GraphNodeV2] = nodes.map(_.toOntologySchema(targetSchema)).sortBy(_.resourceIri)
    val edgesInTargetSchema: Seq[GraphEdgeV2]       = edges.map(_.toOntologySchema(targetSchema))

    // Make JSON-LD prefixes for the project-specific ontologies used in the response.

    val resourceOntologiesUsed: Set[SmartIri] = sortedNodesInTargetSchema
      .map(_.resourceClassIri.getOntologyFromEntity)
      .toSet
      .filter(!_.isKnoraBuiltInDefinitionIri)
    val propertyOntologiesUsed: Set[SmartIri] =
      edgesInTargetSchema.map(_.propertyIri.getOntologyFromEntity).toSet.filter(!_.isKnoraBuiltInDefinitionIri)
    val projectSpecificOntologiesUsed = resourceOntologiesUsed ++ propertyOntologiesUsed

    // Make the knora-api prefix for the target schema.

    val knoraApiPrefixExpansion = targetSchema match {
      case ApiV2Simple  => KnoraApiV2Simple.KnoraApiV2PrefixExpansion
      case ApiV2Complex => KnoraApiV2Complex.KnoraApiV2PrefixExpansion
    }

    // Make the JSON-LD context.

    val context = JsonLDUtil.makeContext(
      fixedPrefixes = Map(
        "rdf"                          -> Rdf.RdfPrefixExpansion,
        "rdfs"                         -> Rdfs.RdfsPrefixExpansion,
        "xsd"                          -> Xsd.XsdPrefixExpansion,
        KnoraApi.KnoraApiOntologyLabel -> knoraApiPrefixExpansion,
      ),
      knoraOntologiesNeedingPrefixes = projectSpecificOntologiesUsed,
    )

    // Group the edges by source IRI and add them to the nodes.

    val groupedEdges: Map[IRI, Seq[GraphEdgeV2]] = edgesInTargetSchema.groupBy(_.source)

    val nodesWithEdges: Seq[JsonLDObject] = sortedNodesInTargetSchema.map { (node: GraphNodeV2) =>
      // Convert the node to JSON-LD.
      val jsonLDNodeMap = Map(
        JsonLDKeywords.ID   -> JsonLDString(node.resourceIri),
        JsonLDKeywords.TYPE -> JsonLDString(node.resourceClassIri.toString),
        Rdfs.Label          -> JsonLDString(node.resourceLabel),
      )

      // Is this node the source of any edges?
      groupedEdges.get(node.resourceIri) match {
        case Some(nodeEdges: Seq[GraphEdgeV2]) =>
          // Yes. Convert them to JSON-LD and add them to the node.

          val nodeEdgesGroupedAndSortedByProperty: Vector[(SmartIri, Seq[GraphEdgeV2])] =
            nodeEdges.groupBy(_.propertyIri).toVector.sortBy(_._1)

          val jsonLDNodeEdges: Map[IRI, JsonLDArray] = nodeEdgesGroupedAndSortedByProperty.map {
            case (propertyIri: SmartIri, propertyEdges: Seq[GraphEdgeV2]) =>
              val sortedPropertyEdges = propertyEdges.sortBy(_.target)
              propertyIri.toString -> JsonLDArray(
                sortedPropertyEdges.map(propertyEdge => JsonLDUtil.iriToJsonLDObject(propertyEdge.target)),
              )
          }.toMap

          messages.util.rdf.JsonLDObject(jsonLDNodeMap ++ jsonLDNodeEdges)

        case None =>
          // This node isn't the source of any edges.
          JsonLDObject(jsonLDNodeMap)
      }
    }

    // Make the JSON-LD document.

    val body = JsonLDObject(Map(JsonLDKeywords.GRAPH -> JsonLDArray(nodesWithEdges)))

    JsonLDDocument(body = body, context = context)
  }

  override def toJsonLDDocument(
    targetSchema: ApiV2Schema,
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
  ): JsonLDDocument =
    toOntologySchema(targetSchema).generateJsonLD(targetSchema)

  override def toOntologySchema(targetSchema: ApiV2Schema): GraphDataGetResponseV2 =
    GraphDataGetResponseV2(
      nodes = nodes.map(_.toOntologySchema(targetSchema)),
      edges = edges.map(_.toOntologySchema(targetSchema)),
      ontologySchema = targetSchema,
    )
}

/**
 * Represents the version history of a resource or a value as events.
 *
 * @param eventType    the type of the operation that is one of [[ResourceAndValueEventsUtil]]
 * @param versionDate  the version date of the event.
 * @param author       the user which had performed the operation.
 * @param eventBody    the request body in the form of [[ResourceOrValueEventBody]] needed for the operation indicated
 *                     by eventType.
 */
case class ResourceAndValueHistoryEvent(
  eventType: String,
  versionDate: Instant,
  author: IRI,
  eventBody: ResourceOrValueEventBody,
)

abstract class ResourceOrValueEventBody

/**
 * Represents a resource event (create or delete) body with all the information required for the request body of this operation.
 * @param resourceIri          the IRI of the resource.
 * @param resourceClassIri     the class of the resource.
 * @param label                the label of the resource.
 * @param values               the values of the resource at creation time.
 * @param permissions          the permissions assigned to the new resource.
 * @param lastModificationDate the last modification date of the resource.
 * @param creationDate         the creation date of the resource.
 * @param deletionInfo         the deletion info of the resource.
 * @param projectADM           the project which the resource belongs to.
 */
case class ResourceEventBody(
  resourceIri: IRI,
  resourceClassIri: SmartIri,
  label: Option[String] = None,
  values: Map[SmartIri, Seq[ValueContentV2]] = Map.empty[SmartIri, Seq[ValueContentV2]],
  permissions: Option[String] = None,
  lastModificationDate: Option[Instant] = None,
  creationDate: Option[Instant] = None,
  deletionInfo: Option[DeletionInfo] = None,
  projectADM: Project,
) extends ResourceOrValueEventBody {

  def toJsonLD(
    targetSchema: ApiV2Schema,
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
  ): JsonLDObject = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    val propertiesAndValuesAsJsonLD: Map[IRI, JsonLDArray] = values.map {
      case (propIri: SmartIri, valueContents: Seq[ValueContentV2]) =>
        val valueContentsAsJsonLD: Seq[JsonLDValue] = valueContents.map { content =>
          content
            .toOntologySchema(targetSchema)
            .toJsonLDValue(
              targetSchema = targetSchema,
              projectADM = projectADM,
              appConfig = appConfig,
              schemaOptions = schemaOptions,
            )
        }

        propIri.toString -> JsonLDArray(valueContentsAsJsonLD)
    }

    val resourceLabel: Option[(IRI, JsonLDString)] = label.map { resourceLabel =>
      Rdfs.Label -> JsonLDString(resourceLabel)
    }

    val permissionAsJsonLD: Option[(IRI, JsonLDString)] = permissions.map { resourcePermission =>
      KnoraApiV2Complex.HasPermissions -> JsonLDString(resourcePermission)
    }

    val creationDateAsJsonLD: Option[(IRI, JsonLDValue)] = creationDate.map { resourceCreationDate =>
      KnoraApiV2Complex.CreationDate -> JsonLDUtil.datatypeValueToJsonLDObject(
        value = resourceCreationDate.toString,
        datatype = Xsd.DateTimeStamp.toSmartIri,
      )
    }
    val lastModificationDateAsJsonLD: Option[(IRI, JsonLDValue)] = lastModificationDate.map { lasModDate =>
      KnoraApiV2Complex.LastModificationDate -> JsonLDUtil.datatypeValueToJsonLDObject(
        value = lasModDate.toString,
        datatype = Xsd.DateTimeStamp.toSmartIri,
      )
    }

    val deletionInfoAsJsonLD: Map[IRI, JsonLDValue] = deletionInfo match {
      case Some(definedDeletionInfo) => definedDeletionInfo.toJsonLDFields(ApiV2Complex)
      case None                      => Map.empty[IRI, JsonLDValue]
    }
    JsonLDObject(
      Map(
        KnoraApiV2Complex.ResourceIri       -> JsonLDString(resourceIri),
        KnoraApiV2Complex.ResourceClassIri  -> JsonLDString(resourceClassIri.toString),
        KnoraApiV2Complex.AttachedToProject -> JsonLDUtil.iriToJsonLDObject(projectADM.id),
      ) ++ resourceLabel ++ creationDateAsJsonLD ++ propertiesAndValuesAsJsonLD ++ lastModificationDateAsJsonLD
        ++ deletionInfoAsJsonLD ++ permissionAsJsonLD,
    )
  }
}

/**
 * Represents an update resource Metadata event body with all the information required for the request body of this operation.
 * The version history of metadata changes are not kept, however every time metadata of a resource has changed, its lastModificationDate
 * is updated accordingly. An event is thus necessary to update the last modification date of the resource.
 * @param resourceIri          the IRI of the resource.
 * @param resourceClassIri     the class of the resource.
 * @param lastModificationDate the last modification date of the resource.
 * @param newModificationDate  the new modification date of the resource.
 */
case class ResourceMetadataEventBody(
  resourceIri: IRI,
  resourceClassIri: SmartIri,
  lastModificationDate: Instant,
  newModificationDate: Instant,
) extends ResourceOrValueEventBody {

  def toJsonLD: JsonLDObject = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    JsonLDObject(
      Map(
        KnoraApiV2Complex.ResourceIri      -> JsonLDString(resourceIri),
        KnoraApiV2Complex.ResourceClassIri -> JsonLDString(resourceClassIri.toString),
        KnoraApiV2Complex.LastModificationDate -> JsonLDUtil.datatypeValueToJsonLDObject(
          value = lastModificationDate.toString,
          datatype = Xsd.DateTimeStamp.toSmartIri,
        ),
        KnoraApiV2Complex.NewModificationDate -> JsonLDUtil.datatypeValueToJsonLDObject(
          value = newModificationDate.toString,
          datatype = Xsd.DateTimeStamp.toSmartIri,
        ),
      ),
    )
  }
}

/**
 * Represents a value event (create/update content/update permission/delete) body with all the information required for
 * the request body of the operation.
 * @param resourceIri  the IRI of the resource.
 * @param resourceClassIri the class of the resource.
 * @param projectADM          the project which the resource belongs to.
 * @param propertyIri         the IRI of the property.
 * @param valueIri            the IRI of the value.
 * @param valueTypeIri        the type of the value.
 * @param valueContent        the content of the value.
 * @param valueUUID           the UUID of the value.
 * @param valueCreationDate   the creation date of the value.
 * @param previousValueIri    in the case of update value/ delete value operation, this indicates the previous value IRI.
 * @param permissions         the permissions assigned to the value.
 * @param valueComment        the comment given for the value operation.
 * @param deletionInfo        in case of delete value operation, it contains the date of deletion and the given comment.
 */
case class ValueEventBody(
  resourceIri: IRI,
  resourceClassIri: SmartIri,
  projectADM: Project,
  propertyIri: SmartIri,
  valueIri: IRI,
  valueTypeIri: SmartIri,
  valueContent: Option[ValueContentV2] = None,
  valueUUID: Option[UUID] = None,
  valueCreationDate: Option[Instant] = None,
  previousValueIri: Option[IRI] = None,
  permissions: Option[String] = None,
  valueComment: Option[String] = None,
  deletionInfo: Option[DeletionInfo] = None,
) extends ResourceOrValueEventBody {

  def toJsonLD(
    targetSchema: ApiV2Schema,
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
  ): JsonLDObject = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    val contentAsJsonLD: Option[(String, JsonLDValue)] = valueContent.map { content =>
      val contentJsonLD = content
        .toOntologySchema(targetSchema)
        .toJsonLDValue(
          targetSchema = targetSchema,
          projectADM = projectADM,
          appConfig = appConfig,
          schemaOptions = schemaOptions,
        )
      propertyIri.toString -> contentJsonLD
    }

    val valueUUIDAsJsonLD: Option[(IRI, JsonLDValue)] = valueUUID.map { valueHasUUID =>
      KnoraApiV2Complex.ValueHasUUID -> JsonLDString(UuidUtil.base64Encode(valueHasUUID))
    }

    val valueCreationDateAsJsonLD: Option[(IRI, JsonLDValue)] = valueCreationDate.map { valueHasCreationDate =>
      KnoraApiV2Complex.ValueCreationDate -> JsonLDUtil.datatypeValueToJsonLDObject(
        value = valueHasCreationDate.toString,
        datatype = Xsd.DateTimeStamp.toSmartIri,
      )
    }
    val valuePermissionsAsJSONLD: Option[(IRI, JsonLDValue)] = permissions.map { hasPermissions =>
      KnoraApiV2Complex.HasPermissions -> JsonLDString(hasPermissions)
    }

    val deletionInfoAsJsonLD: Map[IRI, JsonLDValue] = deletionInfo match {
      case Some(definedDeletionInfo) => definedDeletionInfo.toJsonLDFields(ApiV2Complex)
      case None                      => Map.empty[IRI, JsonLDValue]
    }
    val valueHasCommentAsJsonLD: Option[(IRI, JsonLDValue)] = valueComment.map { definedComment =>
      KnoraApiV2Complex.ValueHasComment -> JsonLDString(definedComment)
    }

    val previousValueAsJsonLD: Option[(IRI, JsonLDValue)] = previousValueIri.map { previousIri =>
      KnoraBase.PreviousValue -> JsonLDString(previousIri.toString)
    }
    JsonLDObject(
      Map(
        JsonLDKeywords.ID                  -> JsonLDString(valueIri),
        JsonLDKeywords.TYPE                -> JsonLDString(valueTypeIri.toString),
        KnoraApiV2Complex.ResourceIri      -> JsonLDString(resourceIri),
        KnoraApiV2Complex.ResourceClassIri -> JsonLDString(resourceClassIri.toString),
        Rdf.Property                       -> JsonLDString(propertyIri.toString),
      ) ++ previousValueAsJsonLD ++ contentAsJsonLD ++ valueUUIDAsJsonLD ++ valueCreationDateAsJsonLD ++ valuePermissionsAsJSONLD
        ++ deletionInfoAsJsonLD ++ valueHasCommentAsJsonLD,
    )
  }
}

/**
 * Represents the resource and value history events.
 */
case class ResourceAndValueVersionHistoryResponseV2(historyEvents: Seq[ResourceAndValueHistoryEvent])
    extends KnoraJsonLDResponseV2 {

  /**
   * Converts the response to a data structure that can be used to generate JSON-LD.
   *
   * @param targetSchema the Knora API schema to be used in the JSON-LD document.
   * @return a [[JsonLDDocument]] representing the response.
   */
  override def toJsonLDDocument(
    targetSchema: ApiV2Schema,
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
  ): JsonLDDocument = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    if (targetSchema != ApiV2Complex) {
      throw AssertionException("Version history can be returned only in the complex schema")
    }

    // Convert the history entries to an array of JSON-LD objects.

    val historyEventsAsJsonLD: Seq[JsonLDObject] = historyEvents.map { (historyEntry: ResourceAndValueHistoryEvent) =>
      // convert event body to JsonLD object
      val eventBodyAsJsonLD: JsonLDObject = historyEntry.eventBody match {
        case valueEventBody: ValueEventBody => valueEventBody.toJsonLD(targetSchema, appConfig, schemaOptions)
        case resourceEventBody: ResourceEventBody =>
          resourceEventBody.toJsonLD(targetSchema, appConfig, schemaOptions)
        case resourceMetadataEventBody: ResourceMetadataEventBody =>
          resourceMetadataEventBody.toJsonLD
        case _ => throw NotFoundException(s"Event body is missing or has wrong type.")
      }

      JsonLDObject(
        Map(
          KnoraApiV2Complex.EventType -> JsonLDString(historyEntry.eventType),
          KnoraApiV2Complex.VersionDate -> JsonLDUtil.datatypeValueToJsonLDObject(
            value = historyEntry.versionDate.toString,
            datatype = Xsd.DateTimeStamp.toSmartIri,
          ),
          KnoraApiV2Complex.Author    -> JsonLDUtil.iriToJsonLDObject(historyEntry.author),
          KnoraApiV2Complex.EventBody -> eventBodyAsJsonLD,
        ),
      )
    }

    // Make the JSON-LD context.

    val context = JsonLDUtil.makeContext(
      fixedPrefixes = Map(
        "rdf"                            -> Rdf.RdfPrefixExpansion,
        "rdfs"                           -> Rdfs.RdfsPrefixExpansion,
        "xsd"                            -> Xsd.XsdPrefixExpansion,
        KnoraApi.KnoraApiOntologyLabel   -> KnoraApiV2Complex.KnoraApiV2PrefixExpansion,
        KnoraBase.KnoraBaseOntologyLabel -> KnoraBase.KnoraBasePrefixExpansion,
      ),
    )

    // Make the JSON-LD document.

    val body = JsonLDObject(Map(JsonLDKeywords.GRAPH -> JsonLDArray(historyEventsAsJsonLD)))

    JsonLDDocument(body = body, context = context)
  }
}
