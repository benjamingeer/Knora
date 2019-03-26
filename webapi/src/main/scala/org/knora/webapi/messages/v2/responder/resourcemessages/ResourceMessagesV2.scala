/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.messages.v2.responder.resourcemessages

import java.io.{StringReader, StringWriter}
import java.time.Instant
import java.util.UUID

import akka.actor.ActorRef
import akka.event.LoggingAdapter
import akka.pattern._
import akka.util.Timeout
import org.eclipse.rdf4j.rio.rdfxml.util.RDFXMLPrettyWriter
import org.eclipse.rdf4j.rio.{RDFFormat, RDFParser, RDFWriter, Rio}
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.projectsmessages.{ProjectADM, ProjectGetRequestADM, ProjectGetResponseADM}
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.v2.responder._
import org.knora.webapi.messages.v2.responder.standoffmessages.MappingXMLtoStandoff
import org.knora.webapi.messages.v2.responder.valuemessages._
import org.knora.webapi.responders.v2.SearchResponderV2Constants
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.PermissionUtilADM.EntityPermission
import org.knora.webapi.util.jsonld._
import org.knora.webapi.util.standoff.{StandoffTagUtilV2, XMLUtil}
import org.knora.webapi.util.{ActorUtil, KnoraIdUtil, SmartIri, StringFormatter}

import scala.concurrent.{ExecutionContext, Future}

/**
  * An abstract trait for messages that can be sent to `ResourcesResponderV2`.
  */
sealed trait ResourcesResponderRequestV2 extends KnoraRequestV2 {
    /**
      * The user that made the request.
      */
    def requestingUser: UserADM
}

/**
  * Requests a description of a resource. A successful response will be a [[ReadResourcesSequenceV2]].
  *
  * @param resourceIris   the IRIs of the resources to be queried.
  * @param propertyIri    if defined, requests only the values of the specified explicit property.
  * @param versionDate    if defined, requests the state of the resources at the specified time in the past.
  * @param requestingUser the user making the request.
  */
case class ResourcesGetRequestV2(resourceIris: Seq[IRI],
                                 propertyIri: Option[SmartIri] = None,
                                 versionDate: Option[Instant] = None,
                                 requestingUser: UserADM) extends ResourcesResponderRequestV2

/**
  * Requests a preview of one or more resources. A successful response will be a [[ReadResourcesSequenceV2]].
  *
  * @param resourceIris   the IRIs of the resources to obtain a preview for.
  * @param requestingUser the user making the request.
  */
case class ResourcesPreviewGetRequestV2(resourceIris: Seq[IRI], requestingUser: UserADM) extends ResourcesResponderRequestV2

/**
  * Requests the version history of the values of a resource.
  *
  * @param resourceIri    the IRI of the resource.
  * @param startDate      the start of the time period to return, inclusive.
  * @param endDate        the end of the time period to return, exclusive.
  * @param requestingUser the user making the request.
  */
case class ResourceVersionHistoryGetRequestV2(resourceIri: IRI, startDate: Option[Instant], endDate: Option[Instant], requestingUser: UserADM) extends ResourcesResponderRequestV2

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
case class ResourceVersionHistoryResponseV2(history: Seq[ResourceHistoryEntry]) extends KnoraResponseV2 {
    /**
      * Converts the response to a data structure that can be used to generate JSON-LD.
      *
      * @param targetSchema the Knora API schema to be used in the JSON-LD document.
      * @return a [[JsonLDDocument]] representing the response.
      */
    override def toJsonLDDocument(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDDocument = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        if (targetSchema != ApiV2WithValueObjects) {
            throw AssertionException("Version history can be returned only in the complex schema")
        }

        // Convert the history entries to an array of JSON-LD objects.

        val historyAsJsonLD: Seq[JsonLDObject] = history.map {
            historyEntry: ResourceHistoryEntry =>
                JsonLDObject(
                    Map(
                        OntologyConstants.KnoraApiV2WithValueObjects.VersionDate -> JsonLDUtil.datatypeValueToJsonLDObject(
                            value = historyEntry.versionDate.toString,
                            datatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri
                        ),
                        OntologyConstants.KnoraApiV2WithValueObjects.Author -> JsonLDUtil.iriToJsonLDObject(historyEntry.author)
                    )
                )
        }

        // Make the JSON-LD context.

        val context = JsonLDUtil.makeContext(
            fixedPrefixes = Map(
                "rdf" -> OntologyConstants.Rdf.RdfPrefixExpansion,
                "rdfs" -> OntologyConstants.Rdfs.RdfsPrefixExpansion,
                "xsd" -> OntologyConstants.Xsd.XsdPrefixExpansion,
                OntologyConstants.KnoraApi.KnoraApiOntologyLabel -> OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiV2PrefixExpansion
            )
        )

        // Make the JSON-LD document.

        val body = JsonLDObject(Map(JsonLDConstants.GRAPH -> JsonLDArray(historyAsJsonLD)))

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
case class ResourceTEIGetRequestV2(resourceIri: IRI, textProperty: SmartIri, mappingIri: Option[IRI], gravsearchTemplateIri: Option[IRI], headerXSLTIri: Option[IRI], requestingUser: UserADM) extends ResourcesResponderRequestV2

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
           |</TEI>
        """.stripMargin

}

/**
  * Represents information that is going to be contained in the header of a TEI/XML document.
  *
  * @param headerInfo the resource representing the header information.
  * @param headerXSLT XSLT to be applied to the resource's metadata in RDF/XML.
  *
  */
case class TEIHeader(headerInfo: ReadResourceV2, headerXSLT: Option[String], settings: SettingsImpl) {

    def toXML: String = {

        if (headerXSLT.nonEmpty) {

            val headerJSONLD = ReadResourcesSequenceV2(1, Vector(headerInfo)).toJsonLDDocument(ApiV2WithValueObjects, settings)

            val rdfParser: RDFParser = Rio.createParser(RDFFormat.JSONLD)
            val stringReader = new StringReader(headerJSONLD.toCompactString)
            val stringWriter = new StringWriter()

            val rdfWriter: RDFWriter = new RDFXMLPrettyWriter(stringWriter)

            rdfParser.setRDFHandler(rdfWriter)
            rdfParser.parse(stringReader, "")

            val teiHeaderInfos = stringWriter.toString

            XMLUtil.applyXSLTransformation(teiHeaderInfos, headerXSLT.get)


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
        if (bodyInfo.standoffAndMapping.isEmpty) throw BadRequestException(s"text is expected to have standoff markup")

        // create XML from standoff (temporary XML) that is going to be converted to TEI/XML
        val tmpXml = StandoffTagUtilV2.convertStandoffTagV2ToXML(bodyInfo.valueHasString, bodyInfo.standoffAndMapping.get.standoff, teiMapping)

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

    /**
      * A map of property IRIs to [[IOValueV2]] objects.
      */
    def values: Map[SmartIri, Seq[IOValueV2]]
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
case class ReadResourceV2(resourceIri: IRI,
                          label: String,
                          resourceClassIri: SmartIri,
                          attachedToUser: IRI,
                          projectADM: ProjectADM,
                          permissions: String,
                          userPermission: EntityPermission,
                          values: Map[SmartIri, Seq[ReadValueV2]],
                          creationDate: Instant,
                          lastModificationDate: Option[Instant],
                          versionDate: Option[Instant],
                          deletionInfo: Option[DeletionInfo]) extends ResourceV2 with KnoraReadV2[ReadResourceV2] {
    override def toOntologySchema(targetSchema: ApiV2Schema): ReadResourceV2 = {
        copy(
            resourceClassIri = resourceClassIri.toOntologySchema(targetSchema),
            values = values.map {
                case (propertyIri, readValues) =>
                    val propertyIriInTargetSchema = propertyIri.toOntologySchema(targetSchema)

                    // In the simple schema, use link properties instead of link value properties.
                    val adaptedPropertyIri = if (targetSchema == ApiV2Simple) {
                        // If all the property's values are link values, it's a link value property.
                        val isLinkProp = readValues.forall {
                            readValue =>
                                readValue.valueContent match {
                                    case _: LinkValueContentV2 => true
                                    case _ => false
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
            }
        )
    }

    def toJsonLD(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDObject = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        if (!resourceClassIri.getOntologySchema.contains(targetSchema)) {
            throw DataConversionException(s"ReadClassInfoV2 for resource $resourceIri is not in schema $targetSchema")
        }

        val propertiesAndValuesAsJsonLD: Map[IRI, JsonLDArray] = values.map {
            case (propIri: SmartIri, readValues: Seq[ReadValueV2]) =>
                val valuesAsJsonLD: Seq[JsonLDValue] = readValues.map(_.toJsonLD(targetSchema, projectADM, settings))
                propIri.toString -> JsonLDArray(valuesAsJsonLD)
        }

        val metadataForComplexSchema: Map[IRI, JsonLDValue] = if (targetSchema == ApiV2WithValueObjects) {
            val requiredMetadataForComplexSchema: Map[IRI, JsonLDValue] = Map(
                OntologyConstants.KnoraApiV2WithValueObjects.AttachedToUser -> JsonLDUtil.iriToJsonLDObject(attachedToUser),
                OntologyConstants.KnoraApiV2WithValueObjects.AttachedToProject -> JsonLDUtil.iriToJsonLDObject(projectADM.id),
                OntologyConstants.KnoraApiV2WithValueObjects.HasPermissions -> JsonLDString(permissions),
                OntologyConstants.KnoraApiV2WithValueObjects.UserHasPermission -> JsonLDString(userPermission.toString),
                OntologyConstants.KnoraApiV2WithValueObjects.CreationDate -> JsonLDUtil.datatypeValueToJsonLDObject(
                    value = creationDate.toString,
                    datatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri
                )
            )

            val deletionInfoAsJsonLD: Map[IRI, JsonLDValue] = deletionInfo match {
                case Some(definedDeletionInfo) => definedDeletionInfo.toJsonLDFields(ApiV2WithValueObjects)
                case None => Map.empty[IRI, JsonLDValue]
            }

            val lastModDateAsJsonLD: Option[(IRI, JsonLDValue)] = lastModificationDate.map {
                definedLastModDate =>
                    OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate -> JsonLDUtil.datatypeValueToJsonLDObject(
                        value = definedLastModDate.toString,
                        datatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri
                    )
            }

            // If this is a past version of the resource, include knora-api:versionDate.

            val versionDateAsJsonLD = versionDate.map {
                definedVersionDate =>
                    OntologyConstants.KnoraApiV2WithValueObjects.VersionDate -> JsonLDUtil.datatypeValueToJsonLDObject(
                        value = definedVersionDate.toString,
                        datatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri
                    )
            }

            requiredMetadataForComplexSchema ++ deletionInfoAsJsonLD ++ lastModDateAsJsonLD ++ versionDateAsJsonLD
        } else {
            Map.empty[IRI, JsonLDValue]
        }

        // Make an ARK URL without a version timestamp.

        val arkUrlProp: IRI = targetSchema match {
            case ApiV2Simple => OntologyConstants.KnoraApiV2Simple.ArkUrl
            case ApiV2WithValueObjects => OntologyConstants.KnoraApiV2WithValueObjects.ArkUrl
        }

        val arkUrlAsJsonLD: (IRI, JsonLDObject) =
            arkUrlProp -> JsonLDUtil.datatypeValueToJsonLDObject(
                value = resourceIri.toSmartIri.fromResourceIriToArkUrl(),
                datatype = OntologyConstants.Xsd.Uri.toSmartIri
            )

        // Make an ARK URL with a version timestamp.

        val versionArkUrlProp: IRI = targetSchema match {
            case ApiV2Simple => OntologyConstants.KnoraApiV2Simple.VersionArkUrl
            case ApiV2WithValueObjects => OntologyConstants.KnoraApiV2WithValueObjects.VersionArkUrl
        }

        val arkTimestamp = versionDate.getOrElse(lastModificationDate.getOrElse(creationDate))

        val versionArkUrlAsJsonLD: (IRI, JsonLDObject) =
            versionArkUrlProp -> JsonLDUtil.datatypeValueToJsonLDObject(
                value = resourceIri.toSmartIri.fromResourceIriToArkUrl(Some(arkTimestamp)),
                datatype = OntologyConstants.Xsd.Uri.toSmartIri
            )

        JsonLDObject(
            Map(
                JsonLDConstants.ID -> JsonLDString(resourceIri),
                JsonLDConstants.TYPE -> JsonLDString(resourceClassIri.toString),
                OntologyConstants.Rdfs.Label -> JsonLDString(label)
            ) ++ propertiesAndValuesAsJsonLD ++ metadataForComplexSchema + arkUrlAsJsonLD + versionArkUrlAsJsonLD
        )
    }
}

/**
  * The value of a Knora property sent to Knora to be created in a new resource.
  *
  * @param valueContent the content of the new value. If the client wants to create a link, this must be a [[LinkValueContentV2]].
  * @param permissions  the permissions to be given to the new value. If not provided, these will be taken from defaults.
  */
case class CreateValueInNewResourceV2(valueContent: ValueContentV2,
                                      permissions: Option[String] = None) extends IOValueV2

/**
  * Represents a Knora resource to be created.
  *
  * @param resourceIri      the IRI that should be given to the resource.
  * @param resourceClassIri the class the resource belongs to.
  * @param label            the resource's label.
  * @param values           the resource's values.
  * @param projectADM       the project that the resource should belong to.
  * @param permissions      the permissions to be given to the new resource. If not provided, these will be taken from defaults.
  */
case class CreateResourceV2(resourceIri: IRI,
                            resourceClassIri: SmartIri,
                            label: String,
                            values: Map[SmartIri, Seq[CreateValueInNewResourceV2]],
                            projectADM: ProjectADM,
                            permissions: Option[String] = None,
                            creationDate: Option[Instant] = None) extends ResourceV2 {
    lazy val flatValues: Iterable[CreateValueInNewResourceV2] = values.values.flatten

    /**
      * Converts this [[CreateResourceV2]] to the specified ontology schema.
      *
      * @param targetSchema the target ontology schema.
      * @return a copy of this [[CreateResourceV2]] in the specified ontology schema.
      */
    def toOntologySchema(targetSchema: OntologySchema): CreateResourceV2 = {
        copy(
            resourceClassIri = resourceClassIri.toOntologySchema(targetSchema),
            values = values.map {
                case (propertyIri, valuesToCreate) =>
                    propertyIri.toOntologySchema(targetSchema) -> valuesToCreate.map {
                        valueToCreate =>
                            valueToCreate.copy(
                                valueContent = valueToCreate.valueContent.toOntologySchema(targetSchema)
                            )
                    }
            }
        )
    }
}

/**
  * Represents a request to create a resource.
  *
  * @param createResource the resource to be created.
  * @param requestingUser the user making the request.
  * @param apiRequestID   the API request ID.
  */
case class CreateResourceRequestV2(createResource: CreateResourceV2,
                                   requestingUser: UserADM,
                                   apiRequestID: UUID) extends ResourcesResponderRequestV2

object CreateResourceRequestV2 extends KnoraJsonLDRequestReaderV2[CreateResourceRequestV2] {
    /**
      * Converts JSON-LD input to a [[CreateResourceRequestV2]].
      *
      * @param jsonLDDocument   the JSON-LD input.
      * @param apiRequestID     the UUID of the API request.
      * @param requestingUser   the user making the request.
      * @param responderManager a reference to the responder manager.
      * @param storeManager     a reference to the store manager.
      * @param log              a logging adapter.
      * @param timeout          a timeout for `ask` messages.
      * @param executionContext an execution context for futures.
      * @return a case class instance representing the input.
      */
    override def fromJsonLD(jsonLDDocument: JsonLDDocument,
                            apiRequestID: UUID,
                            requestingUser: UserADM,
                            responderManager: ActorRef,
                            storeManager: ActorRef,
                            settings: SettingsImpl,
                            log: LoggingAdapter)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[CreateResourceRequestV2] = {
        // #getGeneralInstance
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
        // #getGeneralInstance
        val knoraIdUtil = new KnoraIdUtil

        for {
            // Get the resource class.
            resourceClassIri: SmartIri <- Future(jsonLDDocument.getTypeAsKnoraTypeIri)

            // Get the resource's rdfs:label.
            label: String = jsonLDDocument.requireStringWithValidation(OntologyConstants.Rdfs.Label, stringFormatter.toSparqlEncodedString)

            // Get the resource's project.
            projectIri: SmartIri = jsonLDDocument.requireIriInObject(OntologyConstants.KnoraApiV2WithValueObjects.AttachedToProject, stringFormatter.toSmartIriWithErr)

            // Get the resource's permissions.
            permissions = jsonLDDocument.maybeStringWithValidation(OntologyConstants.KnoraApiV2WithValueObjects.HasPermissions, stringFormatter.toSparqlEncodedString)

            // Get the resource's creation date.
            creationDate: Option[Instant] = jsonLDDocument.maybeDatatypeValueInObject(
                key = OntologyConstants.KnoraApiV2WithValueObjects.CreationDate,
                expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
                validationFun = stringFormatter.xsdDateTimeStampToInstant
            )

            // Get the resource's values.

            propertyIriStrs: Set[IRI] = jsonLDDocument.body.value.keySet --
                Set(
                    JsonLDConstants.ID,
                    JsonLDConstants.TYPE,
                    OntologyConstants.Rdfs.Label,
                    OntologyConstants.KnoraApiV2WithValueObjects.AttachedToProject,
                    OntologyConstants.KnoraApiV2WithValueObjects.HasPermissions,
                    OntologyConstants.KnoraApiV2WithValueObjects.CreationDate
                )

            valueFutures: Map[SmartIri, Seq[Future[CreateValueInNewResourceV2]]] = propertyIriStrs.map {
                propertyIriStr =>
                    // #toSmartIriWithErr
                    val propertyIri: SmartIri = propertyIriStr.toSmartIriWithErr(throw BadRequestException(s"Invalid property IRI: <$propertyIriStr>"))
                    // #toSmartIriWithErr
                    val valuesArray: JsonLDArray = jsonLDDocument.requireArray(propertyIriStr)

                    val propertyValues = valuesArray.value.map {
                        valueJsonLD =>
                            val valueJsonLDObject = valueJsonLD match {
                                case jsonLDObject: JsonLDObject => jsonLDObject
                                case _ => throw BadRequestException(s"Invalid JSON-LD as object of property <$propertyIriStr>")
                            }

                            for {
                                valueContent: ValueContentV2 <-
                                    ValueContentV2.fromJsonLDObject(
                                        jsonLDObject = valueJsonLDObject,
                                        requestingUser = requestingUser,
                                        responderManager = responderManager,
                                        storeManager = storeManager,
                                        settings = settings,
                                        log = log
                                    )

                                _ = if (valueJsonLDObject.value.get(JsonLDConstants.ID).nonEmpty) {
                                    throw BadRequestException("The @id of a value cannot be given in a request to create the value")
                                }

                                maybePermissions: Option[String] = valueJsonLDObject.maybeStringWithValidation(OntologyConstants.KnoraApiV2WithValueObjects.HasPermissions, stringFormatter.toSparqlEncodedString)
                            } yield CreateValueInNewResourceV2(
                                valueContent = valueContent,
                                permissions = maybePermissions
                            )
                    }

                    propertyIri -> propertyValues
            }.toMap

            values: Map[SmartIri, Seq[CreateValueInNewResourceV2]] <- ActorUtil.sequenceSeqFuturesInMap(valueFutures)

            // Get information about the project that the resource should be created in.
            projectInfoResponse: ProjectGetResponseADM <- (responderManager ? ProjectGetRequestADM(
                maybeIri = Some(projectIri.toString),
                requestingUser = requestingUser
            )).mapTo[ProjectGetResponseADM]

            // Generate a random IRI for the resource.
            resourceIri <- knoraIdUtil.makeUnusedIri(knoraIdUtil.makeRandomResourceIri(projectInfoResponse.project.shortcode), storeManager, log)
        } yield CreateResourceRequestV2(
            createResource = CreateResourceV2(
                resourceIri = resourceIri,
                resourceClassIri = resourceClassIri,
                label = label,
                values = values,
                projectADM = projectInfoResponse.project,
                permissions = permissions,
                creationDate = creationDate
            ),
            requestingUser = requestingUser,
            apiRequestID = apiRequestID
        )
    }
}

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
case class UpdateResourceMetadataRequestV2(resourceIri: IRI,
                                           resourceClassIri: SmartIri,
                                           maybeLastModificationDate: Option[Instant] = None,
                                           maybeLabel: Option[String] = None,
                                           maybePermissions: Option[String] = None,
                                           maybeNewModificationDate: Option[Instant] = None,
                                           requestingUser: UserADM,
                                           apiRequestID: UUID) extends ResourcesResponderRequestV2

object UpdateResourceMetadataRequestV2 extends KnoraJsonLDRequestReaderV2[UpdateResourceMetadataRequestV2] {
    /**
      * Converts JSON-LD input into an instance of [[UpdateResourceMetadataRequestV2]].
      *
      * @param jsonLDDocument   the JSON-LD input.
      * @param apiRequestID     the UUID of the API request.
      * @param requestingUser   the user making the request.
      * @param responderManager a reference to the responder manager.
      * @param storeManager     a reference to the store manager.
      * @param settings         the application settings.
      * @param log              a logging adapter.
      * @param timeout          a timeout for `ask` messages.
      * @param executionContext an execution context for futures.
      * @return a case class instance representing the input.
      */
    override def fromJsonLD(jsonLDDocument: JsonLDDocument,
                            apiRequestID: UUID,
                            requestingUser: UserADM,
                            responderManager: ActorRef,
                            storeManager: ActorRef,
                            settings: SettingsImpl,
                            log: LoggingAdapter)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[UpdateResourceMetadataRequestV2] = {
        Future {
            fromJsonLDSync(
                jsonLDDocument = jsonLDDocument,
                requestingUser = requestingUser,
                apiRequestID = apiRequestID
            )
        }
    }

    def fromJsonLDSync(jsonLDDocument: JsonLDDocument, requestingUser: UserADM, apiRequestID: UUID): UpdateResourceMetadataRequestV2 = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val resourceIri: SmartIri = jsonLDDocument.getIDAsKnoraDataIri

        if (!resourceIri.isKnoraResourceIri) {
            throw BadRequestException(s"Invalid resource IRI: <$resourceIri>")
        }

        val resourceClassIri: SmartIri = jsonLDDocument.getTypeAsKnoraTypeIri

        val maybeLastModificationDate: Option[Instant] = jsonLDDocument.maybeDatatypeValueInObject(
            key = OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate,
            expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
            validationFun = stringFormatter.xsdDateTimeStampToInstant
        )

        val maybeLabel: Option[String] = jsonLDDocument.maybeStringWithValidation(OntologyConstants.Rdfs.Label, stringFormatter.toSparqlEncodedString)
        val maybePermissions: Option[String] = jsonLDDocument.maybeStringWithValidation(OntologyConstants.KnoraApiV2WithValueObjects.HasPermissions, stringFormatter.toSparqlEncodedString)

        val maybeNewModificationDate: Option[Instant] = jsonLDDocument.maybeDatatypeValueInObject(
            key = OntologyConstants.KnoraApiV2WithValueObjects.NewModificationDate,
            expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
            validationFun = stringFormatter.xsdDateTimeStampToInstant
        )

        if (Seq(maybeLabel, maybePermissions, maybeNewModificationDate).forall(_.isEmpty)) {
            throw BadRequestException(s"No updated resource metadata provided")
        }

        UpdateResourceMetadataRequestV2(
            resourceIri = resourceIri.toString,
            resourceClassIri = resourceClassIri,
            maybeLastModificationDate = maybeLastModificationDate,
            maybeLabel = maybeLabel,
            maybePermissions = maybePermissions,
            maybeNewModificationDate = maybeNewModificationDate,
            requestingUser = requestingUser,
            apiRequestID = apiRequestID
        )
    }
}

/**
  * Represents a request to mark a resource as deleted.
  *
  * @param resourceIri               the IRI of the resource.
  * @param resourceClassIri          the IRI of the resource class.
  * @param maybeDeleteComment        a comment explaining why the resource is being marked as deleted.
  * @param maybeLastModificationDate the resource's last modification date, if any.
  */
case class DeleteResourceRequestV2(resourceIri: IRI,
                                   resourceClassIri: SmartIri,
                                   maybeDeleteComment: Option[String] = None,
                                   maybeLastModificationDate: Option[Instant] = None,
                                   requestingUser: UserADM,
                                   apiRequestID: UUID) extends ResourcesResponderRequestV2

object DeleteResourceRequestV2 extends KnoraJsonLDRequestReaderV2[DeleteResourceRequestV2] {
    /**
      * Converts JSON-LD input into an instance of [[DeleteResourceRequestV2]].
      *
      * @param jsonLDDocument   the JSON-LD input.
      * @param apiRequestID     the UUID of the API request.
      * @param requestingUser   the user making the request.
      * @param responderManager a reference to the responder manager.
      * @param storeManager     a reference to the store manager.
      * @param settings         the application settings.
      * @param log              a logging adapter.
      * @param timeout          a timeout for `ask` messages.
      * @param executionContext an execution context for futures.
      * @return a case class instance representing the input.
      */
    override def fromJsonLD(jsonLDDocument: JsonLDDocument,
                            apiRequestID: UUID,
                            requestingUser: UserADM,
                            responderManager: ActorRef,
                            storeManager: ActorRef,
                            settings: SettingsImpl,
                            log: LoggingAdapter)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[DeleteResourceRequestV2] = {
        Future {
            fromJsonLDSync(
                jsonLDDocument = jsonLDDocument,
                requestingUser = requestingUser,
                apiRequestID = apiRequestID
            )
        }
    }

    def fromJsonLDSync(jsonLDDocument: JsonLDDocument, requestingUser: UserADM, apiRequestID: UUID): DeleteResourceRequestV2 = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val resourceIri: SmartIri = jsonLDDocument.getIDAsKnoraDataIri

        if (!resourceIri.isKnoraResourceIri) {
            throw BadRequestException(s"Invalid resource IRI: <$resourceIri>")
        }

        val resourceClassIri: SmartIri = jsonLDDocument.getTypeAsKnoraTypeIri

        val maybeLastModificationDate: Option[Instant] = jsonLDDocument.maybeDatatypeValueInObject(
            key = OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate,
            expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
            validationFun = stringFormatter.xsdDateTimeStampToInstant
        )

        val maybeDeleteComment: Option[String] = jsonLDDocument.maybeStringWithValidation(OntologyConstants.KnoraApiV2WithValueObjects.DeleteComment, stringFormatter.toSparqlEncodedString)

        DeleteResourceRequestV2(
            resourceIri = resourceIri.toString,
            resourceClassIri = resourceClassIri,
            maybeDeleteComment = maybeDeleteComment,
            maybeLastModificationDate = maybeLastModificationDate,
            requestingUser = requestingUser,
            apiRequestID = apiRequestID
        )
    }
}

/**
  * Represents a sequence of resources read back from Knora.
  *
  * @param numberOfResources the amount of resources returned.
  * @param resources         a sequence of resources.
  */
case class ReadResourcesSequenceV2(numberOfResources: Int, resources: Seq[ReadResourceV2]) extends KnoraResponseV2 with KnoraReadV2[ReadResourcesSequenceV2] with UpdateResultInProject {

    override def toOntologySchema(targetSchema: ApiV2Schema): ReadResourcesSequenceV2 = {
        copy(
            resources = resources.map(_.toOntologySchema(targetSchema))
        )
    }

    // #generateJsonLD
    private def generateJsonLD(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDDocument = {
        // #generateJsonLD
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        // Generate JSON-LD for the resources.

        val resourcesJsonObjects: Seq[JsonLDObject] = resources.map {
            resource: ReadResourceV2 => resource.toJsonLD(targetSchema = targetSchema, settings = settings)
        }

        // Make JSON-LD prefixes for the project-specific ontologies used in the response.

        val projectSpecificOntologiesUsed: Set[SmartIri] = resources.flatMap {
            resource =>
                val resourceOntology = resource.resourceClassIri.getOntologyFromEntity

                val propertyOntologies = resource.values.keySet.map {
                    property => property.getOntologyFromEntity
                }

                propertyOntologies + resourceOntology
        }.toSet.filter(!_.isKnoraBuiltInDefinitionIri)

        // Make the knora-api prefix for the target schema.

        val knoraApiPrefixExpansion = targetSchema match {
            case ApiV2Simple => OntologyConstants.KnoraApiV2Simple.KnoraApiV2PrefixExpansion
            case ApiV2WithValueObjects => OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiV2PrefixExpansion
        }

        // Make the JSON-LD document.

        val context = JsonLDUtil.makeContext(
            fixedPrefixes = Map(
                "rdf" -> OntologyConstants.Rdf.RdfPrefixExpansion,
                "rdfs" -> OntologyConstants.Rdfs.RdfsPrefixExpansion,
                "xsd" -> OntologyConstants.Xsd.XsdPrefixExpansion,
                OntologyConstants.KnoraApi.KnoraApiOntologyLabel -> knoraApiPrefixExpansion
            ),
            knoraOntologiesNeedingPrefixes = projectSpecificOntologiesUsed
        )

        val body = JsonLDObject(Map(
            JsonLDConstants.GRAPH -> JsonLDArray(resourcesJsonObjects)
        ))

        JsonLDDocument(body = body, context = context)

    }

    // #toJsonLDDocument
    def toJsonLDDocument(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDDocument = {
        toOntologySchema(targetSchema).generateJsonLD(targetSchema, settings)
    }

    // #toJsonLDDocument

    /**
      * Checks that a [[ReadResourcesSequenceV2]] contains exactly one resource, and returns that resource. If the resource
      * is not present, or if it's `ForbiddenResource`, throws an exception.
      *
      * @param requestedResourceIri the IRI of the expected resource.
      * @return the resource.
      */
    def toResource(requestedResourceIri: IRI): ReadResourceV2 = {
        if (numberOfResources == 0) {
            throw AssertionException(s"Expected one resource, <$requestedResourceIri>, but no resources were returned")
        }

        if (numberOfResources > 1) {
            throw AssertionException(s"More than one resource returned with IRI <$requestedResourceIri>")
        }

        val resourceInfo = resources.head

        if (resourceInfo.resourceIri == SearchResponderV2Constants.forbiddenResourceIri) { // TODO: #953
            throw NotFoundException(s"Resource <$requestedResourceIri> does not exist, has been deleted, or you do not have permission to view it and/or the values of the specified property")
        }

        resourceInfo
    }

    /**
      * Considers this [[ReadResourcesSequenceV2]] to be the result of an update operation in a single project
      * (since Knora never updates resources in more than one project at a time), and returns information about that
      * project. Throws [[AssertionException]] if this [[ReadResourcesSequenceV2]] is empty or refers to more than one
      * project.
      */
    override def projectADM: ProjectADM = {
        if (resources.isEmpty) {
            throw AssertionException("ReadResourcesSequenceV2 is empty")
        }

        val allProjects: Set[ProjectADM] = resources.map(_.projectADM).toSet

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
case class GraphDataGetRequestV2(resourceIri: IRI,
                                 depth: Int,
                                 inbound: Boolean,
                                 outbound: Boolean,
                                 excludeProperty: Option[SmartIri],
                                 requestingUser: UserADM) extends ResourcesResponderRequestV2 {
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
case class GraphNodeV2(resourceIri: IRI, resourceClassIri: SmartIri, resourceLabel: String) extends KnoraReadV2[GraphNodeV2] {
    override def toOntologySchema(targetSchema: ApiV2Schema): GraphNodeV2 = {
        copy(resourceClassIri = resourceClassIri.toOntologySchema(targetSchema))
    }
}

/**
  * Represents an edge (i.e. a link) in a resource graph.
  *
  * @param source      the resource that is the source of the link.
  * @param propertyIri the link property that links the source to the target.
  * @param target      the resource that is the target of the link.
  */
case class GraphEdgeV2(source: IRI, propertyIri: SmartIri, target: IRI) extends KnoraReadV2[GraphEdgeV2] {
    override def toOntologySchema(targetSchema: ApiV2Schema): GraphEdgeV2 = {
        copy(propertyIri = propertyIri.toOntologySchema(targetSchema))
    }
}

/**
  * Represents a graph of resources.
  *
  * @param nodes the nodes in the graph.
  * @param edges the edges in the graph.
  */
case class GraphDataGetResponseV2(nodes: Seq[GraphNodeV2], edges: Seq[GraphEdgeV2], ontologySchema: OntologySchema) extends KnoraResponseV2 with KnoraReadV2[GraphDataGetResponseV2] {
    private def generateJsonLD(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDDocument = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val sortedNodesInTargetSchema: Seq[GraphNodeV2] = nodes.map(_.toOntologySchema(targetSchema)).sortBy(_.resourceIri)
        val edgesInTargetSchema: Seq[GraphEdgeV2] = edges.map(_.toOntologySchema(targetSchema))

        // Make JSON-LD prefixes for the project-specific ontologies used in the response.

        val resourceOntologiesUsed: Set[SmartIri] = sortedNodesInTargetSchema.map(_.resourceClassIri.getOntologyFromEntity).toSet.filter(!_.isKnoraBuiltInDefinitionIri)
        val propertyOntologiesUsed: Set[SmartIri] = edgesInTargetSchema.map(_.propertyIri.getOntologyFromEntity).toSet.filter(!_.isKnoraBuiltInDefinitionIri)
        val projectSpecificOntologiesUsed = resourceOntologiesUsed ++ propertyOntologiesUsed

        // Make the knora-api prefix for the target schema.

        val knoraApiPrefixExpansion = targetSchema match {
            case ApiV2Simple => OntologyConstants.KnoraApiV2Simple.KnoraApiV2PrefixExpansion
            case ApiV2WithValueObjects => OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiV2PrefixExpansion
        }

        // Make the JSON-LD context.

        val context = JsonLDUtil.makeContext(
            fixedPrefixes = Map(
                "rdf" -> OntologyConstants.Rdf.RdfPrefixExpansion,
                "rdfs" -> OntologyConstants.Rdfs.RdfsPrefixExpansion,
                "xsd" -> OntologyConstants.Xsd.XsdPrefixExpansion,
                OntologyConstants.KnoraApi.KnoraApiOntologyLabel -> knoraApiPrefixExpansion
            ),
            knoraOntologiesNeedingPrefixes = projectSpecificOntologiesUsed
        )

        // Group the edges by source IRI and add them to the nodes.

        val groupedEdges: Map[IRI, Seq[GraphEdgeV2]] = edgesInTargetSchema.groupBy(_.source)

        val nodesWithEdges: Seq[JsonLDObject] = sortedNodesInTargetSchema.map {
            node: GraphNodeV2 =>
                // Convert the node to JSON-LD.
                val jsonLDNodeMap = Map(
                    JsonLDConstants.ID -> JsonLDString(node.resourceIri),
                    JsonLDConstants.TYPE -> JsonLDString(node.resourceClassIri.toString),
                    OntologyConstants.Rdfs.Label -> JsonLDString(node.resourceLabel)
                )

                // Is this node the source of any edges?
                groupedEdges.get(node.resourceIri) match {
                    case Some(nodeEdges: Seq[GraphEdgeV2]) =>
                        // Yes. Convert them to JSON-LD and add them to the node.

                        val nodeEdgesGroupedAndSortedByProperty: Vector[(SmartIri, Seq[GraphEdgeV2])] = nodeEdges.groupBy(_.propertyIri).toVector.sortBy(_._1)

                        val jsonLDNodeEdges: Map[IRI, JsonLDArray] = nodeEdgesGroupedAndSortedByProperty.map {
                            case (propertyIri: SmartIri, propertyEdges: Seq[GraphEdgeV2]) =>
                                val sortedPropertyEdges = propertyEdges.sortBy(_.target)
                                propertyIri.toString -> JsonLDArray(sortedPropertyEdges.map(propertyEdge => JsonLDUtil.iriToJsonLDObject(propertyEdge.target)))
                        }.toMap

                        JsonLDObject(jsonLDNodeMap ++ jsonLDNodeEdges)

                    case None =>
                        // This node isn't the source of any edges.
                        JsonLDObject(jsonLDNodeMap)
                }
        }

        // Make the JSON-LD document.

        val body = JsonLDObject(Map(JsonLDConstants.GRAPH -> JsonLDArray(nodesWithEdges)))

        JsonLDDocument(body = body, context = context)
    }

    override def toJsonLDDocument(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDDocument = {
        toOntologySchema(targetSchema).generateJsonLD(targetSchema, settings)
    }

    override def toOntologySchema(targetSchema: ApiV2Schema): GraphDataGetResponseV2 = {
        GraphDataGetResponseV2(
            nodes = nodes.map(_.toOntologySchema(targetSchema)),
            edges = edges.map(_.toOntologySchema(targetSchema)),
            ontologySchema = targetSchema
        )
    }
}
