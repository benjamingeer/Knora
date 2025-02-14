/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder.valuemessages

import org.apache.jena.rdf.model.Resource
import zio.IO
import zio.ZIO

import java.net.URI
import java.time.Instant
import java.util.UUID
import scala.language.implicitConversions
import scala.util.Try

import dsp.errors.AssertionException
import dsp.errors.BadRequestException
import dsp.errors.NotFoundException
import dsp.valueobjects.Iri
import dsp.valueobjects.UuidUtil
import org.knora.webapi.*
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex.*
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.ValuesValidator
import org.knora.webapi.messages.util.*
import org.knora.webapi.messages.util.rdf.*
import org.knora.webapi.messages.util.standoff.StandoffStringUtil
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.messages.util.standoff.XMLUtil
import org.knora.webapi.messages.v2.responder.*
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.standoffmessages.*
import org.knora.webapi.messages.v2.responder.valuemessages.ValueContentV2.FileInfo
import org.knora.webapi.routing.RouteUtilZ
import org.knora.webapi.slice.admin.api.model.MaintenanceRequests.AssetId
import org.knora.webapi.slice.admin.api.model.Project
import org.knora.webapi.slice.admin.domain.model.Authorship
import org.knora.webapi.slice.admin.domain.model.CopyrightHolder
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.LicenseIri
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.common.Value.StringValue
import org.knora.webapi.slice.common.jena.JenaConversions.given
import org.knora.webapi.slice.common.jena.ResourceOps
import org.knora.webapi.slice.common.jena.ResourceOps.*
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.slice.resources.IiifImageRequestUrl
import org.knora.webapi.store.iiif.api.FileMetadataSipiResponse
import org.knora.webapi.store.iiif.api.SipiService
import org.knora.webapi.util.WithAsIs

private def objectCommentOption(r: Resource): Either[String, Option[String]] =
  r.objectStringOption(ValueHasComment, str => Iri.toSparqlEncodedString(str).toRight(s"Invalid comment: $str"))

/**
 * Represents a successful response to a create value Request.
 *
 * @param valueIri          the IRI of the value that was created.
 * @param valueType         the type of the value that was created.
 * @param valueUUID         the value's UUID.
 * @param valueCreationDate the value's creationDate
 * @param projectADM        the project in which the value was created.
 */
case class CreateValueResponseV2(
  valueIri: IRI,
  valueType: SmartIri,
  valueUUID: UUID,
  valueCreationDate: Instant,
  projectADM: Project,
) extends KnoraJsonLDResponseV2
    with UpdateResultInProject {
  override def toJsonLDDocument(
    targetSchema: ApiV2Schema,
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
  ): JsonLDDocument = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    if (targetSchema != ApiV2Complex) {
      throw AssertionException(s"CreateValueResponseV2 can only be returned in the complex schema")
    }

    JsonLDDocument(
      body = JsonLDObject(
        Map(
          JsonLDKeywords.ID   -> JsonLDString(valueIri),
          JsonLDKeywords.TYPE -> JsonLDString(valueType.toOntologySchema(ApiV2Complex).toString),
          ValueHasUUID        -> JsonLDString(UuidUtil.base64Encode(valueUUID)),
          ValueCreationDate -> JsonLDUtil.datatypeValueToJsonLDObject(
            value = valueCreationDate.toString,
            datatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
          ),
        ),
      ),
      context = JsonLDUtil.makeContext(
        fixedPrefixes = Map(
          OntologyConstants.KnoraApi.KnoraApiOntologyLabel -> KnoraApiV2PrefixExpansion,
        ),
      ),
    )
  }
}

/**
 * Represents a successful response to an update value request.
 *
 * @param valueIri   the IRI of the value version that was created.
 * @param valueType  the type of the value that was updated.
 * @param valueUUID  the value's UUID.
 * @param projectADM the project in which the value was updated.
 */
case class UpdateValueResponseV2(valueIri: IRI, valueType: SmartIri, valueUUID: UUID, projectADM: Project)
    extends KnoraJsonLDResponseV2
    with UpdateResultInProject {
  override def toJsonLDDocument(
    targetSchema: ApiV2Schema,
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
  ): JsonLDDocument = {
    if (targetSchema != ApiV2Complex) {
      throw AssertionException(s"UpdateValueResponseV2 can only be returned in the complex schema")
    }

    JsonLDDocument(
      body = JsonLDObject(
        Map(
          JsonLDKeywords.ID   -> JsonLDString(valueIri),
          JsonLDKeywords.TYPE -> JsonLDString(valueType.toOntologySchema(ApiV2Complex).toString),
          ValueHasUUID        -> JsonLDString(UuidUtil.base64Encode(valueUUID)),
        ),
      ),
      context = JsonLDUtil.makeContext(
        fixedPrefixes = Map(
          OntologyConstants.KnoraApi.KnoraApiOntologyLabel -> KnoraApiV2PrefixExpansion,
        ),
      ),
    )
  }
}

/**
 * Requests that a value is marked as deleted. A successful response will be a [[SuccessResponseV2]].
 *
 * @param resourceIri          the IRI of the containing resource.
 * @param resourceClassIri     the IRI of the resource class.
 * @param propertyIri          the IRI of the property pointing to the value to be marked as deleted.
 * @param valueIri             the IRI of the value to be marked as deleted.
 * @param valueTypeIri         the IRI of the value class.
 * @param deleteComment        an optional comment explaining why the value is being marked as deleted.
 * @param deleteDate           an optional timestamp indicating when the value was deleted. If not supplied,
 *                             the current time will be used.
 */
case class DeleteValueV2(
  resourceIri: IRI,
  resourceClassIri: SmartIri,
  propertyIri: SmartIri,
  valueIri: IRI,
  valueTypeIri: SmartIri,
  deleteComment: Option[String] = None,
  deleteDate: Option[Instant] = None,
)

case class GenerateSparqlForValueInNewResourceV2(
  valueContent: ValueContentV2,
  customValueIri: Option[SmartIri],
  customValueUUID: Option[UUID],
  customValueCreationDate: Option[Instant],
  permissions: String,
)

/**
 * Provides information about the deletion of a resource or value.
 *
 * @param deleteDate         the date when the resource or value was deleted.
 * @param maybeDeleteComment the reason why the resource or value was deleted.
 */
case class DeletionInfo(deleteDate: Instant, maybeDeleteComment: Option[String]) {
  def toJsonLDFields(targetSchema: ApiV2Schema): Map[IRI, JsonLDValue] = {
    if (targetSchema != ApiV2Complex) {
      throw AssertionException("DeletionInfo is available in JSON-LD only in the complex schema")
    }

    val maybeDeleteCommentStatement = maybeDeleteComment.map { deleteComment =>
      DeleteComment -> JsonLDString(deleteComment)
    }

    Map(
      IsDeleted -> JsonLDBoolean(true),
      DeleteDate -> JsonLDObject(
        Map(
          JsonLDKeywords.TYPE  -> JsonLDString(OntologyConstants.Xsd.DateTimeStamp),
          JsonLDKeywords.VALUE -> JsonLDString(deleteDate.toString),
        ),
      ),
    ) ++ maybeDeleteCommentStatement
  }
}

/**
 * Represents a Knora value as read from the triplestore.
 */
sealed trait ReadValueV2 {

  /**
   * The IRI of the value.
   */
  def valueIri: IRI

  /**
   * The user that created the value.
   */
  def attachedToUser: IRI

  /**
   * The value's permissions.
   */
  def permissions: String

  /**
   * The permission that the requesting user has on the value.
   */
  def userPermission: Permission.ObjectAccess

  /**
   * The date when the value was created.
   */
  def valueCreationDate: Instant

  /**
   * The UUID shared by all the versions of this value.
   */
  def valueHasUUID: UUID

  /**
   * The content of the value.
   */
  def valueContent: ValueContentV2

  /**
   * The IRI of the previous version of this value. Not returned in API responses, but needed
   * here for testing.
   */
  def previousValueIri: Option[IRI]

  /**
   * If the value has been marked as deleted, information about its deletion.
   */
  def deletionInfo: Option[DeletionInfo]

  /**
   * Converts this value to the specified ontology schema.
   *
   * @param targetSchema the schema that the value should be converted to.
   */
  def toOntologySchema(targetSchema: ApiV2Schema): ReadValueV2

  /**
   * Converts this value to JSON-LD.
   *
   * @param targetSchema the target schema.
   * @return a JSON-LD representation of this value.
   */
  def toJsonLD(
    targetSchema: ApiV2Schema,
    projectADM: Project,
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
  ): JsonLDValue = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    val valueContentAsJsonLD = valueContent.toJsonLDValue(
      targetSchema = targetSchema,
      projectADM = projectADM,
      appConfig = appConfig,
      schemaOptions = schemaOptions,
    )

    // In the complex schema, add the value's IRI and type to the JSON-LD object that represents it.
    targetSchema match {
      case ApiV2Complex =>
        // In the complex schema, the value must be represented as a JSON-LD object.
        valueContentAsJsonLD match {
          case jsonLDObject: JsonLDObject =>
            // Add the value's metadata.

            val valueSmartIri = valueIri.toSmartIri

            val requiredMetadata = Map(
              JsonLDKeywords.ID   -> JsonLDString(valueIri),
              JsonLDKeywords.TYPE -> JsonLDString(valueContent.valueType.toString),
              AttachedToUser      -> JsonLDUtil.iriToJsonLDObject(attachedToUser),
              HasPermissions      -> JsonLDString(permissions),
              UserHasPermission   -> JsonLDString(userPermission.toString),
              ValueCreationDate -> JsonLDUtil.datatypeValueToJsonLDObject(
                value = valueCreationDate.toString,
                datatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
              ),
              ValueHasUUID -> JsonLDString(UuidUtil.base64Encode(valueHasUUID)),
              ArkUrl -> JsonLDUtil.datatypeValueToJsonLDObject(
                value = valueSmartIri.fromValueIriToArkUrl(valueUUID = valueHasUUID),
                datatype = OntologyConstants.Xsd.Uri.toSmartIri,
              ),
              VersionArkUrl -> JsonLDUtil.datatypeValueToJsonLDObject(
                value = valueSmartIri
                  .fromValueIriToArkUrl(valueUUID = valueHasUUID, maybeTimestamp = Some(valueCreationDate)),
                datatype = OntologyConstants.Xsd.Uri.toSmartIri,
              ),
            )

            val valueHasCommentAsJsonLD: Option[(IRI, JsonLDValue)] = valueContent.comment.map { definedComment =>
              ValueHasComment -> JsonLDString(definedComment)
            }

            val deletionInfoAsJsonLD: Map[IRI, JsonLDValue] = deletionInfo match {
              case Some(definedDeletionInfo) => definedDeletionInfo.toJsonLDFields(ApiV2Complex)
              case None                      => Map.empty[IRI, JsonLDValue]
            }

            JsonLDObject(jsonLDObject.value ++ requiredMetadata ++ valueHasCommentAsJsonLD ++ deletionInfoAsJsonLD)

          case other =>
            throw AssertionException(
              s"Expected value $valueIri to be a represented as a JSON-LD object in the complex schema, but found $other",
            )
        }

      case ApiV2Simple => valueContentAsJsonLD
    }
  }

  /**
   * Return a `knora-base:DeletedValue` representation of the present value.
   *
   * @return A ReadValueV2 object identical to the current one (including the IRI),
   *         but with valueContent of type [[DeletedValueContentV2]].
   */
  def asDeletedValue(): ReadValueV2 =
    ReadOtherValueV2(
      valueIri = this.valueIri,
      attachedToUser = this.attachedToUser,
      permissions = this.permissions,
      userPermission = this.userPermission,
      valueCreationDate = this.valueCreationDate,
      valueHasUUID = this.valueHasUUID,
      valueContent = DeletedValueContentV2(InternalSchema),
      previousValueIri = this.previousValueIri,
      deletionInfo = this.deletionInfo,
    )

}

/**
 * A text value, or a page of standoff markup attached to a text value, as read from the triplestore.
 *
 * @param valueIri                      the IRI of the value.
 * @param attachedToUser                the user that created the value.
 * @param permissions                   the permissions that the value grants to user groups.
 * @param userPermission                the permission that the requesting user has on the value.
 * @param valueHasUUID                  the UUID shared by all the versions of this value.
 * @param valueContent                  the content of the value.
 * @param valueHasMaxStandoffStartIndex if this text value has standoff markup, the highest
 *                                      `knora-base:standoffTagHasEndIndex`
 *                                      used in its standoff tags.
 * @param previousValueIri              the IRI of the previous version of this value. Not returned in API responses, but needed
 *                                      here for testing.
 * @param deletionInfo                  if this value has been marked as deleted, provides the date when it was
 *                                      deleted and the reason why it was deleted.
 */
case class ReadTextValueV2(
  valueIri: IRI,
  attachedToUser: IRI,
  permissions: String,
  userPermission: Permission.ObjectAccess,
  valueCreationDate: Instant,
  valueHasUUID: UUID,
  valueContent: TextValueContentV2,
  valueHasMaxStandoffStartIndex: Option[Int],
  previousValueIri: Option[IRI],
  deletionInfo: Option[DeletionInfo],
) extends ReadValueV2
    with KnoraReadV2[ReadTextValueV2] {

  /**
   * Converts this value to the specified ontology schema.
   *
   * @param targetSchema the target schema.
   */
  override def toOntologySchema(targetSchema: ApiV2Schema): ReadTextValueV2 =
    copy(valueContent = valueContent.toOntologySchema(targetSchema))
}

/**
 * A link value as read from the triplestore.
 *
 * @param valueIri         the IRI of the value.
 * @param attachedToUser   the user that created the value.
 * @param permissions      the permissions that the value grants to user groups.
 * @param userPermission   the permission that the requesting user has on the value.
 * @param valueHasUUID     the UUID shared by all the versions of this value.
 * @param valueContent     the content of the value.
 * @param valueHasRefCount if this is a link value, its reference count. Not returned in API responses, but needed
 *                         here for testing.
 * @param previousValueIri the IRI of the previous version of this value. Not returned in API responses, but needed
 *                         here for testing.
 * @param deletionInfo     if this value has been marked as deleted, provides the date when it was
 *                         deleted and the reason why it was deleted.
 */
case class ReadLinkValueV2(
  valueIri: IRI,
  attachedToUser: IRI,
  permissions: String,
  userPermission: Permission.ObjectAccess,
  valueCreationDate: Instant,
  valueHasUUID: UUID,
  valueContent: LinkValueContentV2,
  valueHasRefCount: Int,
  previousValueIri: Option[IRI] = None,
  deletionInfo: Option[DeletionInfo],
) extends ReadValueV2
    with KnoraReadV2[ReadLinkValueV2] {

  /**
   * Converts this value to the specified ontology schema.
   *
   * @param targetSchema the target schema.
   */
  override def toOntologySchema(targetSchema: ApiV2Schema): ReadLinkValueV2 =
    copy(valueContent = valueContent.toOntologySchema(targetSchema))
}

/**
 * A non-text, non-link value as read from the triplestore.
 *
 * @param valueIri         the IRI of the value.
 * @param attachedToUser   the user that created the value.
 * @param permissions      the permissions that the value grants to user groups.
 * @param userPermission   the permission that the requesting user has on the value.
 * @param valueHasUUID     the UUID shared by all the versions of this value.
 * @param valueContent     the content of the value.
 * @param previousValueIri the IRI of the previous version of this value. Not returned in API responses, but needed
 *                         here for testing.
 * @param deletionInfo     if this value has been marked as deleted, provides the date when it was
 *                         deleted and the reason why it was deleted.
 */
case class ReadOtherValueV2(
  valueIri: IRI,
  attachedToUser: IRI,
  permissions: String,
  userPermission: Permission.ObjectAccess,
  valueCreationDate: Instant,
  valueHasUUID: UUID,
  valueContent: ValueContentV2,
  previousValueIri: Option[IRI],
  deletionInfo: Option[DeletionInfo],
) extends ReadValueV2
    with KnoraReadV2[ReadOtherValueV2] {

  /**
   * Converts this value to the specified ontology schema.
   *
   * @param targetSchema the target schema.
   */
  override def toOntologySchema(targetSchema: ApiV2Schema): ReadOtherValueV2 =
    copy(valueContent = valueContent.toOntologySchema(targetSchema))
}

/**
 * Represents a Knora value to be created in an existing resource.
 *
 * @param resourceIri       the resource the new value should be attached to.
 * @param resourceClassIri  the resource class that the client believes the resource belongs to.
 * @param propertyIri       the property of the new value. If the client wants to create a link, this must be a link value property.
 * @param valueContent      the content of the new value. If the client wants to create a link, this must be a [[LinkValueContentV2]].
 * @param valueIri          the optional custom IRI supplied for the value.
 * @param valueUUID         the optional custom UUID supplied for the value.
 * @param valueCreationDate the optional custom creation date supplied for the value. If not supplied,
 *                          the current time will be used.
 * @param permissions       the permissions to be given to the new value. If not provided, these will be taken from defaults.
 */
case class CreateValueV2(
  resourceIri: IRI,
  resourceClassIri: SmartIri,
  propertyIri: SmartIri,
  valueContent: ValueContentV2,
  valueIri: Option[SmartIri] = None,
  valueUUID: Option[UUID] = None,
  valueCreationDate: Option[Instant] = None,
  permissions: Option[String] = None,
)

/** A trait for classes representing information to be updated in a value. */
sealed trait UpdateValueV2 {

  /**
   * The IRI of the resource containing the value.
   */
  val resourceIri: IRI

  /**
   * The external IRI of the resource class.
   */
  val resourceClassIri: SmartIri

  /**
   * The external IRI of the property pointing to the value.
   */
  val propertyIri: SmartIri

  /**
   * The value IRI.
   */
  val valueIri: IRI

  /**
   * A custom value creation date.
   */
  val valueCreationDate: Option[Instant]

  def valueType: SmartIri
}

/**
 * A new version of a value of a Knora property to be created.
 *
 * @param resourceIri        the resource that the current value version is attached to.
 * @param resourceClassIri   the resource class that the client believes the resource belongs to.
 * @param propertyIri        the property that the client believes points to the value. If the value is a link value,
 *                           this must be a link value property.
 * @param valueIri           the IRI of the value to be updated.
 * @param valueContent       the content of the new version of the value.
 * @param permissions        the permissions to be attached to the new value version.
 * @param valueCreationDate  an optional custom creation date to be attached to the new value version. If not provided,
 *                           the current time will be used.
 * @param newValueVersionIri an optional IRI to be used for the new value version. If not provided, a random IRI
 *                           will be generated.
 */
case class UpdateValueContentV2(
  resourceIri: IRI,
  resourceClassIri: SmartIri,
  propertyIri: SmartIri,
  valueIri: IRI,
  valueContent: ValueContentV2,
  permissions: Option[String] = None,
  valueCreationDate: Option[Instant] = None,
  newValueVersionIri: Option[SmartIri] = None,
) extends UpdateValueV2 {
  override def valueType: SmartIri = valueContent.valueType
}

/**
 * New permissions for a value.
 *
 * @param resourceIri        the resource that the current value version is attached to.
 * @param resourceClassIri   the resource class that the client believes the resource belongs to.
 * @param propertyIri        the property that the client believes points to the value. If the value is a link value,
 *                           this must be a link value property.
 * @param valueIri           the IRI of the value to be updated.
 * @param valueType          the IRI of the value type.
 * @param permissions        the permissions to be attached to the new value version.
 * @param valueCreationDate  an optional custom creation date to be attached to the new value version. If not provided,
 *                           the current time will be used.
 * @param newValueVersionIri an optional IRI to be used for the new value version. If not provided, a random IRI
 *                           will be generated.
 */
case class UpdateValuePermissionsV2(
  resourceIri: IRI,
  resourceClassIri: SmartIri,
  propertyIri: SmartIri,
  valueIri: IRI,
  valueType: SmartIri,
  permissions: String,
  valueCreationDate: Option[Instant] = None,
  newValueVersionIri: Option[SmartIri] = None,
) extends UpdateValueV2

/**
 * The IRI and content of a new value or value version whose existence in the triplestore needs to be verified.
 *
 * @param newValueIri  the IRI that was assigned to the new value.
 * @param newValueUUID the UUID attached to the new value.
 * @param valueContent the content of the new value (unescaped, as it would be read from the triplestore).
 * @param permissions  the permissions of the new value.
 * @param creationDate the new value's creation date.
 */
case class UnverifiedValueV2(
  newValueIri: IRI,
  newValueUUID: UUID,
  valueContent: ValueContentV2,
  permissions: String,
  creationDate: Instant,
)

/**
 * The content of the value of a Knora property.
 */
sealed trait ValueContentV2 extends KnoraContentV2[ValueContentV2] with WithAsIs[ValueContentV2] {
  protected implicit def stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  /**
   * The IRI of the value type.
   */
  def valueType: SmartIri

  /**
   * The string representation of this `ValueContentV2`.
   */
  def valueHasString: String

  /**
   * a comment on this [[ValueContentV2]], if any.
   */
  def comment: Option[String]

  /**
   * Converts this value to the specified ontology schema.
   *
   * @param targetSchema the target schema.
   */
  def toOntologySchema(targetSchema: OntologySchema): ValueContentV2

  /**
   * A representation of the `ValueContentV2` as a [[JsonLDValue]].
   *
   * @param targetSchema the API schema to be used.
   * @return a [[JsonLDValue]] that can be used to generate JSON-LD representing this value.
   */
  def toJsonLDValue(
    targetSchema: ApiV2Schema,
    projectADM: Project,
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
  ): JsonLDValue

  /**
   * Undoes the SPARQL-escaping of strings in this [[ValueContentV2]].
   *
   * @return the same [[ValueContentV2]] with its strings unescaped.
   */
  def unescape: ValueContentV2

  /**
   * Returns `true` if creating this [[ValueContentV2]] as a new value would duplicate the specified other value.
   * This means that if resource `R` has property `P` with value `V1`, and `V1` would duplicate `V2`, the API server
   * should not add another instance of property `P` with value `V2`. It does not necessarily mean that `V1 == V2`.
   *
   * @param that a [[ValueContentV2]] in the same resource, as read from the triplestore.
   * @return `true` if `other` would duplicate `this`.
   */
  def wouldDuplicateOtherValue(that: ValueContentV2): Boolean

  /**
   * Returns `true` if this [[ValueContentV2]] would be redundant as a new version of an existing value. This means
   * that if resource `R` has property `P` with value `V1`, and `V2` would duplicate `V1`, we should not add `V2`
   * as a new version of `V1`. It does not necessarily mean that `V1 == V2`.
   *
   * @param currentVersion the current version of the value, as read from the triplestore.
   * @return `true` if this [[ValueContentV2]] would duplicate `currentVersion`.
   */
  def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean
}

/**
 * Generates instances of value content classes (subclasses of [[ValueContentV2]]) from JSON-LD input.
 */
object ValueContentV2 {

  final case class FileInfo(filename: IRI, metadata: FileMetadataSipiResponse)

  /**
   * Given the jsonLd contains a FileValueHasFilename, it will try to fetch the FileInfo from Sipi or Dsp-Ingest.
   *
   * @param shortcode The shortcode of the project
   * @param jsonLd the jsonLd object
   * @return Some FileInfo if FileValueHasFilename found and the remote service returned the metadata.
   *         None if FileValueHasFilename is not found in the jsonLd object.
   *         Fails if the file is not found in the remote service or something goes wrong.
   */
  def getFileInfo(
    shortcode: Shortcode,
    jsonLd: JsonLDObject,
  ): ZIO[SipiService, Throwable, Option[FileInfo]] =
    fileInfoFromExternal(jsonLd.getString(FileValueHasFilename).toOption.flatten, shortcode)

  def fileInfoFromExternal(
    filenameMaybe: Option[String],
    shortcode: Shortcode,
  ): ZIO[SipiService, Throwable, Option[FileInfo]] =
    ZIO.foreach(filenameMaybe) { filename =>
      for {
        sipiService <- ZIO.service[SipiService]
        assetId <- ZIO
                     .fromEither(AssetId.fromFilename(filename))
                     .mapError(msg => BadRequestException(s"Invalid value for 'fileValueHasFilename': $msg"))
        meta <- sipiService.getFileMetadataFromDspIngest(shortcode, assetId).mapError {
                  case NotFoundException(_) =>
                    NotFoundException(
                      s"Asset '$filename' not found in dsp-ingest, make sure the old Sipi upload mechanism is not being used.",
                    )
                  case e => e
                }
      } yield FileInfo(filename, meta)
    }
}

/**
 * Represents a Knora date value.
 *
 * @param valueHasStartJDN       the start of the date as JDN.
 * @param valueHasEndJDN         the end of the date as JDN.
 * @param valueHasStartPrecision the precision of the start date.
 * @param valueHasEndPrecision   the precision of the end date.
 * @param valueHasCalendar       the calendar of the date.
 * @param comment                a comment on this [[DateValueContentV2]], if any.
 */
case class DateValueContentV2(
  ontologySchema: OntologySchema,
  valueHasStartJDN: Int,
  valueHasEndJDN: Int,
  valueHasStartPrecision: DatePrecisionV2,
  valueHasEndPrecision: DatePrecisionV2,
  valueHasCalendar: CalendarNameV2,
  comment: Option[String] = None,
) extends ValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.DateValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  private lazy val asCalendarDateRange: CalendarDateRangeV2 = {
    val startCalendarDate = CalendarDateV2.fromJulianDayNumber(
      julianDay = valueHasStartJDN,
      precision = valueHasStartPrecision,
      calendarName = valueHasCalendar,
    )

    val endCalendarDate = CalendarDateV2.fromJulianDayNumber(
      julianDay = valueHasEndJDN,
      precision = valueHasEndPrecision,
      calendarName = valueHasCalendar,
    )

    CalendarDateRangeV2(
      startCalendarDate = startCalendarDate,
      endCalendarDate = endCalendarDate,
    )
  }

  // We compute valueHasString instead of taking it from the triplestore, because the
  // string literal in the triplestore isn't in API v2 format.
  override lazy val valueHasString: String = asCalendarDateRange.toString

  override def toOntologySchema(targetSchema: OntologySchema): DateValueContentV2 = copy(ontologySchema = targetSchema)

  override def toJsonLDValue(
    targetSchema: ApiV2Schema,
    projectADM: Project,
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
  ): JsonLDValue =
    targetSchema match {
      case ApiV2Simple =>
        JsonLDUtil.datatypeValueToJsonLDObject(
          value = valueHasString,
          datatype = OntologyConstants.KnoraApiV2Simple.Date.toSmartIri,
        )

      case ApiV2Complex =>
        val startCalendarDate: CalendarDateV2 = asCalendarDateRange.startCalendarDate
        val endCalendarDate: CalendarDateV2   = asCalendarDateRange.endCalendarDate

        val startDateAssertions =
          Map(DateValueHasStartYear -> JsonLDInt(startCalendarDate.year)) ++
            startCalendarDate.maybeMonth.map(month => DateValueHasStartMonth -> JsonLDInt(month)) ++
            startCalendarDate.maybeDay.map(day => DateValueHasStartDay -> JsonLDInt(day)) ++
            startCalendarDate.maybeEra.map(era => DateValueHasStartEra -> JsonLDString(era.toString))

        val endDateAssertions =
          Map(DateValueHasEndYear -> JsonLDInt(endCalendarDate.year)) ++
            endCalendarDate.maybeMonth.map(month => DateValueHasEndMonth -> JsonLDInt(month)) ++
            endCalendarDate.maybeDay.map(day => DateValueHasEndDay -> JsonLDInt(day)) ++
            endCalendarDate.maybeEra.map(era => DateValueHasEndEra -> JsonLDString(era.toString))

        JsonLDObject(
          Map(
            ValueAsString        -> JsonLDString(valueHasString),
            DateValueHasCalendar -> JsonLDString(valueHasCalendar.toString),
          ) ++ startDateAssertions ++ endDateAssertions,
        )
    }

  override def unescape: ValueContentV2 =
    copy(comment = comment.map(commentStr => Iri.fromSparqlEncodedString(commentStr)))

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean =
    that match {
      case thatDateValue: DateValueContentV2 =>
        valueHasStartJDN == thatDateValue.valueHasStartJDN &&
        valueHasEndJDN == thatDateValue.valueHasEndJDN &&
        valueHasStartPrecision == thatDateValue.valueHasStartPrecision &&
        valueHasEndPrecision == thatDateValue.valueHasEndPrecision &&
        valueHasCalendar == thatDateValue.valueHasCalendar

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean =
    currentVersion match {
      case thatDateValue: DateValueContentV2 =>
        valueHasStartJDN == thatDateValue.valueHasStartJDN &&
        valueHasEndJDN == thatDateValue.valueHasEndJDN &&
        valueHasStartPrecision == thatDateValue.valueHasStartPrecision &&
        valueHasEndPrecision == thatDateValue.valueHasEndPrecision &&
        valueHasCalendar == thatDateValue.valueHasCalendar &&
        comment == thatDateValue.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
}

/**
 * Constructs [[DateValueContentV2]] objects based on JSON-LD input.
 */
object DateValueContentV2 {

  /**
   * Parses a string representing a date range in API v2 simple format.
   *
   * @param dateStr the string to be parsed.
   * @return a [[DateValueContentV2]] representing the date range.
   */
  def parse(dateStr: String): DateValueContentV2 = {
    val dateRange: CalendarDateRangeV2 = CalendarDateRangeV2.parse(dateStr)
    val (startJDN: Int, endJDN: Int)   = dateRange.toJulianDayRange

    DateValueContentV2(
      ontologySchema = ApiV2Simple,
      valueHasStartJDN = startJDN,
      valueHasEndJDN = endJDN,
      valueHasStartPrecision = dateRange.startCalendarDate.precision,
      valueHasEndPrecision = dateRange.endCalendarDate.precision,
      valueHasCalendar = dateRange.startCalendarDate.calendarName,
    )
  }

  def from(r: Resource): Either[String, DateValueContentV2] =
    for {
      startYear  <- r.objectInt(DateValueHasStartYear)
      startMonth <- r.objectIntOption(DateValueHasStartMonth)
      startDay   <- r.objectIntOption(DateValueHasStartDay)
      startEra   <- r.objectStringOption(DateValueHasStartEra, DateEraV2.fromString)

      endYear  <- r.objectInt(DateValueHasEndYear)
      endMonth <- r.objectIntOption(DateValueHasEndMonth)
      endDay   <- r.objectIntOption(DateValueHasEndDay)
      endEra   <- r.objectStringOption(DateValueHasEndEra, DateEraV2.fromString)

      calendarName <- r.objectString(DateValueHasCalendar, CalendarNameV2.fromString)

      // validate the combination of start/end dates and calendarName
      _ <- if (startMonth.isEmpty && startDay.isDefined) Left(s"Start day defined, missing start month") else Right(())
      _ <- if (endMonth.isEmpty && endDay.isDefined) Left(s"End day defined, missing end month") else Right(())
      _ <- if (calendarName.isInstanceOf[CalendarNameGregorianOrJulian] && (startEra.isEmpty || endEra.isEmpty))
             Left(s"Era is required in calendar $calendarName")
           else Right(())

      startDate          = CalendarDateV2(calendarName, startYear, startMonth, startDay, startEra)
      endDate            = CalendarDateV2(calendarName, endYear, endMonth, endDay, endEra)
      dateRange          = CalendarDateRangeV2(startDate, endDate)
      startEnd          <- Try(dateRange.toJulianDayRange).toEither.left.map(_.getMessage)
      (startJdn, endJdn) = startEnd

      comment <- objectCommentOption(r)
    } yield DateValueContentV2(
      ApiV2Complex,
      startJdn,
      endJdn,
      startDate.precision,
      endDate.precision,
      calendarName,
      comment,
    )
}

/**
 * Represents a [[StandoffTagV2]] for a standoff tag of a certain type (standoff tag class) that is about to be created in the triplestore.
 *
 * @param standoffNode           the standoff node to be created.
 * @param standoffTagInstanceIri the standoff node's IRI.
 * @param startParentIri         the IRI of the parent of the start tag.
 * @param endParentIri           the IRI of the parent of the end tag, if any.
 */
case class CreateStandoffTagV2InTriplestore(
  standoffNode: StandoffTagV2,
  standoffTagInstanceIri: IRI,
  startParentIri: Option[IRI] = None,
  endParentIri: Option[IRI] = None,
)

enum TextValueType {
  case UnformattedText
  case FormattedText
  case CustomFormattedText(mappingIri: InternalIri)
  case UndefinedTextType
}

/**
 * Represents a Knora text value, or a page of standoff markup that will be included in a text value.
 *
 * @param maybeValueHasString the string representation of this text value, if available.
 * @param standoff            the standoff markup attached to the text value, if any.
 * @param mappingIri          the IRI of the [[MappingXMLtoStandoff]] used by default with the text value, if any.
 * @param mapping             the [[MappingXMLtoStandoff]] used by default with the text value, if any.
 * @param comment             a comment on this [[TextValueContentV2]], if any.
 */
case class TextValueContentV2(
  ontologySchema: OntologySchema,
  maybeValueHasString: Option[String],
  textValueType: TextValueType,
  valueHasLanguage: Option[String] = None,
  standoff: Seq[StandoffTagV2] = Vector.empty,
  mappingIri: Option[IRI] = None,
  mapping: Option[MappingXMLtoStandoff] = None,
  xslt: Option[String] = None,
  comment: Option[String] = None,
) extends ValueContentV2 { self =>

  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.TextValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  /**
   * Returns the IRIs of any resources that are target of standoff link tags in this text value.
   */
  lazy val standoffLinkTagTargetResourceIris: Set[IRI] =
    standoffLinkTagIriAttributes.map(_.value)

  /**
   * Returns the IRI attributes representing the target IRIs of any standoff links in this text value.
   */
  lazy val standoffLinkTagIriAttributes: Set[StandoffTagIriAttributeV2] =
    standoff.foldLeft(Set.empty[StandoffTagIriAttributeV2]) { case (acc, standoffTag: StandoffTagV2) =>
      if (standoffTag.dataType.contains(StandoffDataTypeClasses.StandoffLinkTag)) {
        val iriAttributes: Set[StandoffTagIriAttributeV2] = standoffTag.attributes.collect {
          case iriAttribute: StandoffTagIriAttributeV2 => iriAttribute
        }.toSet

        acc ++ iriAttributes
      } else {
        acc
      }
    }

  override def valueHasString: String =
    maybeValueHasString.getOrElse(throw AssertionException("Text value has no valueHasString"))

  /**
   * The content of the text value without standoff, suitable for returning in API responses. This removes
   * INFORMATION SEPARATOR TWO, which is used only internally.
   */
  private lazy val valueHasStringWithoutStandoff: String =
    valueHasString.replace(StringFormatter.INFORMATION_SEPARATOR_TWO.toString, "")

  /**
   * The maximum start index in the standoff attached to this [[TextValueContentV2]]. This is used
   * only when writing a text value to the triplestore.
   */
  lazy val computedMaxStandoffStartIndex: Option[Int] = if (standoff.nonEmpty) {
    Some(standoff.map(_.startIndex).max)
  } else {
    None
  }

  override def toOntologySchema(targetSchema: OntologySchema): TextValueContentV2 = copy(ontologySchema = targetSchema)

  override def toJsonLDValue(
    targetSchema: ApiV2Schema,
    projectADM: Project,
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
  ): JsonLDValue =
    targetSchema match {
      case ApiV2Simple =>
        valueHasLanguage match {
          case Some(lang) =>
            // In the simple schema, if this text value specifies a language, return it using a JSON-LD
            // @language key as per <https://json-ld.org/spec/latest/json-ld/#string-internationalization>.
            JsonLDUtil.objectWithLangToJsonLDObject(
              obj = valueHasStringWithoutStandoff,
              lang = lang,
            )

          case None => JsonLDString(valueHasStringWithoutStandoff)
        }

      case ApiV2Complex =>
        val renderStandoffAsXml: Boolean = standoff.nonEmpty && SchemaOptions.renderMarkupAsXml(
          targetSchema = targetSchema,
          schemaOptions = schemaOptions,
        )

        val textValueTypeIri = textValueType match
          case TextValueType.UnformattedText        => UnformattedText
          case TextValueType.FormattedText          => FormattedText
          case TextValueType.CustomFormattedText(_) => CustomFormattedText
          case TextValueType.UndefinedTextType      => UndefinedTextType

        // Should we render standoff as XML?
        val objectMap: Map[IRI, JsonLDValue] = if (renderStandoffAsXml) {
          val definedMappingIri =
            mappingIri.getOrElse(throw BadRequestException(s"Cannot render standoff as XML without a mapping"))
          val definedMapping =
            mapping.getOrElse(throw BadRequestException(s"Cannot render standoff as XML without a mapping"))

          val xmlFromStandoff = StandoffTagUtilV2.convertStandoffTagV2ToXML(
            utf8str = valueHasString,
            standoff = standoff,
            mappingXMLtoStandoff = definedMapping,
          )

          // check if there is an XSL transformation
          xslt match {
            case Some(definedXslt) =>
              val xmlTransformed: String = XMLUtil.applyXSLTransformation(
                xml = xmlFromStandoff,
                xslt = definedXslt,
              )

              // the xml was converted to HTML
              Map(
                TextValueAsHtml -> JsonLDString(xmlTransformed),
                TextValueAsXml  -> JsonLDString(xmlFromStandoff),
                TextValueHasMapping -> JsonLDUtil.iriToJsonLDObject(
                  definedMappingIri,
                ),
              )

            case None =>
              Map(
                TextValueAsXml -> JsonLDString(xmlFromStandoff),
                TextValueHasMapping -> JsonLDUtil.iriToJsonLDObject(
                  definedMappingIri,
                ),
              )
          }
        } else {
          // We're not rendering standoff as XML. Return the text without markup.
          Map(ValueAsString -> JsonLDString(valueHasStringWithoutStandoff))
        }

        val objectMapWithType = objectMap + (HasTextValueType -> JsonLDUtil.iriToJsonLDObject(textValueTypeIri))

        // In the complex schema, if this text value specifies a language, return it using the predicate
        // knora-api:textValueHasLanguage.
        val objectMapWithLanguage: Map[IRI, JsonLDValue] = valueHasLanguage match {
          case Some(lang) =>
            objectMapWithType + (TextValueHasLanguage -> JsonLDString(lang))
          case None =>
            objectMapWithType
        }

        JsonLDObject(objectMapWithLanguage)
    }

  /**
   * A convenience method that creates an IRI for each [[StandoffTagV2]] and resolves internal references to standoff node Iris.
   *
   * @return a list of [[CreateStandoffTagV2InTriplestore]] each representing a [[StandoffTagV2]] object
   *         along with is standoff tag class and IRI that is going to identify it in the triplestore.
   */
  def prepareForSparqlInsert(valueIri: IRI): Seq[CreateStandoffTagV2InTriplestore] =
    if (standoff.nonEmpty) {
      // create an IRI for each standoff tag
      // internal references to XML ids are not resolved yet
      val standoffTagsWithOriginalXMLIDs: Seq[CreateStandoffTagV2InTriplestore] = standoff.map {
        (standoffNode: StandoffTagV2) =>
          CreateStandoffTagV2InTriplestore(
            standoffNode = standoffNode,
            standoffTagInstanceIri = StandoffStringUtil.makeRandomStandoffTagIri(
              valueIri = valueIri,
              startIndex = standoffNode.startIndex,
            ), // generate IRI for new standoff node
          )
      }

      // collect all the standoff tags that contain XML ids and
      // map the XML ids to standoff node Iris
      val iDsToStandoffNodeIris: Map[IRI, IRI] = standoffTagsWithOriginalXMLIDs.filter {
        (standoffTag: CreateStandoffTagV2InTriplestore) =>
          // filter those tags out that have an XML id
          standoffTag.standoffNode.originalXMLID.isDefined
      }.map { (standoffTagWithID: CreateStandoffTagV2InTriplestore) =>
        // return the XML id as a key and the standoff IRI as the value
        standoffTagWithID.standoffNode.originalXMLID.get -> standoffTagWithID.standoffTagInstanceIri
      }.toMap

      // Map the start index of each tag to its IRI, so we can resolve references to parent tags as references to
      // tag IRIs. We only care about start indexes here, because only hierarchical tags can be parents, and
      // hierarchical tags don't have end indexes.
      val startIndexesToStandoffNodeIris: Map[Int, IRI] = standoffTagsWithOriginalXMLIDs.map { tagWithIndex =>
        tagWithIndex.standoffNode.startIndex -> tagWithIndex.standoffTagInstanceIri
      }.toMap

      // resolve the original XML ids to standoff Iris every the `StandoffTagInternalReferenceAttributeV2`
      val standoffTagsWithNodeReferences: Seq[CreateStandoffTagV2InTriplestore] = standoffTagsWithOriginalXMLIDs.map {
        (standoffTag: CreateStandoffTagV2InTriplestore) =>
          // resolve original XML ids to standoff node Iris for `StandoffTagInternalReferenceAttributeV2`
          val attributesWithStandoffNodeIriReferences: Seq[StandoffTagAttributeV2] =
            standoffTag.standoffNode.attributes.map {
              case refAttr: StandoffTagInternalReferenceAttributeV2 =>
                // resolve the XML id to the corresponding standoff node IRI
                refAttr.copy(value = iDsToStandoffNodeIris(refAttr.value))
              case attr => attr
            }

          val startParentIndex: Option[Int] = standoffTag.standoffNode.startParentIndex
          val endParentIndex: Option[Int]   = standoffTag.standoffNode.endParentIndex

          // return standoff tag with updated attributes
          standoffTag.copy(
            standoffNode = standoffTag.standoffNode.copy(attributes = attributesWithStandoffNodeIriReferences),
            // If there's a start parent index, get its IRI, otherwise None
            startParentIri = startParentIndex.map(startIndexesToStandoffNodeIris(_)),
            // If there's an end parent index, get its IRI, otherwise None
            endParentIri = endParentIndex.map(startIndexesToStandoffNodeIris(_)),
          )
      }

      standoffTagsWithNodeReferences
    } else {
      Seq.empty[CreateStandoffTagV2InTriplestore]
    }

  override def unescape: ValueContentV2 = {
    // Unescape the text in standoff string attributes.
    val unescapedStandoff = standoff.map { standoffTag =>
      standoffTag.copy(
        attributes = standoffTag.attributes.map {
          case stringAttribute: StandoffTagStringAttributeV2 =>
            stringAttribute.copy(value = Iri.fromSparqlEncodedString(stringAttribute.value))

          case other => other
        },
      )
    }

    copy(
      maybeValueHasString = maybeValueHasString.map(str => Iri.fromSparqlEncodedString(str)),
      standoff = unescapedStandoff,
      comment = comment.map(commentStr => Iri.fromSparqlEncodedString(commentStr)),
    )
  }

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean =
    // It doesn't make sense for a resource to have two different text values associated with the same property,
    // containing the same text but different markup.
    that match {
      case other: TextValueContentV2 =>
        self.valueHasString == other.valueHasString &&
        self.valueHasLanguage == other.valueHasLanguage

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean =
    // It's OK to add a new version of a text value as long as something has been changed in it, even if it's only the markup
    // or the comment.
    currentVersion match {
      case other: TextValueContentV2 =>
        // compare standoff nodes (sort them first by index) and the XML-to-standoff mapping IRI
        val standoffIdentical = StandoffTagUtilV2.makeComparableStandoffCollection(standoff) == StandoffTagUtilV2
          .makeComparableStandoffCollection(other.standoff)

        wouldDuplicateOtherValue(currentVersion) &&
        self.mappingIri == other.mappingIri &&
        self.comment == other.comment &&
        standoffIdentical

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
}

/**
 * Constructs [[TextValueContentV2]] objects based on JSON-LD input.
 */
object TextValueContentV2 {
  private def getTextValue(
    maybeValueAsString: Option[IRI],
    maybeTextValueAsXml: Option[String],
    maybeValueHasLanguage: Option[IRI],
    maybeMappingResponse: Option[GetMappingResponseV2],
    comment: Option[String],
  ) =
    (maybeValueAsString, maybeTextValueAsXml, maybeMappingResponse) match {
      case (Some(valueAsString), None, None) => // Text without standoff.
        ZIO.succeed(
          TextValueContentV2(
            ontologySchema = ApiV2Complex,
            maybeValueHasString = Some(valueAsString),
            valueHasLanguage = maybeValueHasLanguage,
            textValueType = TextValueType.UnformattedText,
            comment = comment,
          ),
        )

      case (None, Some(textValueAsXml), Some(mappingResponse)) =>
        for {
          textWithStandoffTags <- ZIO.attempt(
                                    StandoffTagUtilV2.convertXMLtoStandoffTagV2(
                                      textValueAsXml,
                                      mappingResponse,
                                      acceptStandoffLinksToClientIDs = false,
                                    ),
                                  )

          textType =
            if (mappingResponse.mappingIri == OntologyConstants.KnoraBase.StandardMapping) {
              TextValueType.FormattedText
            } else {
              TextValueType.CustomFormattedText(InternalIri(mappingResponse.mappingIri))
            }
          text <- RouteUtilZ.toSparqlEncodedString(textWithStandoffTags.text, "Text value contains invalid characters")
        } yield TextValueContentV2(
          ontologySchema = ApiV2Complex,
          maybeValueHasString = Some(text),
          textValueType = textType,
          valueHasLanguage = maybeValueHasLanguage,
          standoff = textWithStandoffTags.standoffTagV2,
          mappingIri = Some(mappingResponse.mappingIri),
          mapping = Some(mappingResponse.mapping),
          comment = comment,
        )

      case _ =>
        ZIO.fail(
          BadRequestException(
            s"Invalid combination of knora-api:valueHasString, knora-api:textValueAsXml, and/or knora-api:textValueHasMapping",
          ),
        )
    }

  private def objectSparqlStringOption(r: Resource, property: String) = for {
    str <- r.objectStringOption(property)
    iri <- str match
             case Some(s) => Right(Iri.toSparqlEncodedString(s))
             case None    => Right(None)
  } yield iri

  def from(r: Resource): ZIO[MessageRelay, IRI, TextValueContentV2] = for {
    messageRelay          <- ZIO.service[MessageRelay]
    maybeValueAsString    <- ZIO.fromEither(objectSparqlStringOption(r, ValueAsString))
    maybeValueHasLanguage <- ZIO.fromEither(objectSparqlStringOption(r, TextValueHasLanguage))
    maybeTextValueAsXml   <- ZIO.fromEither(r.objectStringOption(TextValueAsXml))
    comment               <- ZIO.fromEither(objectCommentOption(r))
    mappingIriOption      <- ZIO.fromEither(r.objectUriOption(TextValueHasMapping))
    maybeMappingResponse <- ZIO
                              .foreach(mappingIriOption) { mappingIri =>
                                messageRelay.ask[GetMappingResponseV2](GetMappingRequestV2(mappingIri))
                              }
                              .mapError(_.getMessage)
    textValue <-
      getTextValue(maybeValueAsString, maybeTextValueAsXml, maybeValueHasLanguage, maybeMappingResponse, comment)
        .mapError(_.getMessage)

  } yield textValue
}

/**
 * Represents a Knora integer value.
 *
 * @param valueHasInteger the integer value.
 * @param comment         a comment on this [[IntegerValueContentV2]], if any.
 */
case class IntegerValueContentV2(ontologySchema: OntologySchema, valueHasInteger: Int, comment: Option[String] = None)
    extends ValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.IntValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override lazy val valueHasString: String = valueHasInteger.toString

  override def toOntologySchema(targetSchema: OntologySchema): IntegerValueContentV2 =
    copy(ontologySchema = targetSchema)

  override def toJsonLDValue(
    targetSchema: ApiV2Schema,
    projectADM: Project,
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
  ): JsonLDValue =
    targetSchema match {
      case ApiV2Simple => JsonLDInt(valueHasInteger)

      case ApiV2Complex =>
        JsonLDObject(Map(IntValueAsInt -> JsonLDInt(valueHasInteger)))

    }

  override def unescape: ValueContentV2 =
    copy(comment = comment.map(commentStr => Iri.fromSparqlEncodedString(commentStr)))

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean =
    that match {
      case thatIntegerValue: IntegerValueContentV2 => valueHasInteger == thatIntegerValue.valueHasInteger
      case _                                       => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean =
    currentVersion match {
      case thatIntegerValue: IntegerValueContentV2 =>
        valueHasInteger == thatIntegerValue.valueHasInteger &&
        comment == thatIntegerValue.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
}

/**
 * Constructs [[IntegerValueContentV2]] objects based on JSON-LD input.
 */
object IntegerValueContentV2 {
  def from(r: Resource): Either[String, IntegerValueContentV2] =
    for {
      intValue <- r.objectInt(IntValueAsInt)
      comment  <- objectCommentOption(r)
    } yield IntegerValueContentV2(ApiV2Complex, intValue, comment)
}

/**
 * import org.knora.webapi.slice.common.ResourceOps.*
 * Represents a Knora decimal value.
 *
 * @param valueHasDecimal the decimal value.
 * @param comment         a comment on this [[DecimalValueContentV2]], if any.
 */
case class DecimalValueContentV2(
  ontologySchema: OntologySchema,
  valueHasDecimal: BigDecimal,
  comment: Option[String] = None,
) extends ValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.DecimalValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override lazy val valueHasString: String = valueHasDecimal.toString

  override def toOntologySchema(targetSchema: OntologySchema): DecimalValueContentV2 =
    copy(ontologySchema = targetSchema)

  override def toJsonLDValue(
    targetSchema: ApiV2Schema,
    projectADM: Project,
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
  ): JsonLDValue = {
    val decimalValueAsJsonLDObject = JsonLDUtil.datatypeValueToJsonLDObject(
      value = valueHasDecimal.toString,
      datatype = OntologyConstants.Xsd.Decimal.toSmartIri,
    )

    targetSchema match {
      case ApiV2Simple => decimalValueAsJsonLDObject

      case ApiV2Complex =>
        JsonLDObject(Map(DecimalValueAsDecimal -> decimalValueAsJsonLDObject))
    }
  }

  override def unescape: ValueContentV2 =
    copy(comment = comment.map(commentStr => Iri.fromSparqlEncodedString(commentStr)))

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean =
    that match {
      case thatDecimalValue: DecimalValueContentV2 => valueHasDecimal == thatDecimalValue.valueHasDecimal
      case _                                       => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean =
    currentVersion match {
      case thatDecimalValue: DecimalValueContentV2 =>
        valueHasDecimal == thatDecimalValue.valueHasDecimal &&
        comment == thatDecimalValue.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
}

/**
 * Constructs [[DecimalValueContentV2]] objects based on JSON-LD input.
 */
object DecimalValueContentV2 {
  def from(r: Resource): Either[String, DecimalValueContentV2] =
    for {
      decimalValue <- r.objectBigDecimal(DecimalValueAsDecimal)
      comment      <- objectCommentOption(r)
    } yield DecimalValueContentV2(ApiV2Complex, decimalValue, comment)
}

/**
 * Represents a Boolean value.
 *
 * @param valueHasBoolean the Boolean value.
 * @param comment         a comment on this [[BooleanValueContentV2]], if any.
 */
case class BooleanValueContentV2(
  ontologySchema: OntologySchema,
  valueHasBoolean: Boolean,
  comment: Option[String] = None,
) extends ValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.BooleanValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override lazy val valueHasString: String = valueHasBoolean.toString

  override def toOntologySchema(targetSchema: OntologySchema): BooleanValueContentV2 =
    copy(ontologySchema = targetSchema)

  override def toJsonLDValue(
    targetSchema: ApiV2Schema,
    projectADM: Project,
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
  ): JsonLDValue =
    targetSchema match {
      case ApiV2Simple => JsonLDBoolean(valueHasBoolean)

      case ApiV2Complex =>
        JsonLDObject(Map(BooleanValueAsBoolean -> JsonLDBoolean(valueHasBoolean)))
    }

  override def unescape: ValueContentV2 =
    copy(comment = comment.map(commentStr => Iri.fromSparqlEncodedString(commentStr)))

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean =
    // Always returns true, because it doesn't make sense to have two instances of the same boolean property.
    true

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean =
    currentVersion match {
      case thatBooleanValue: BooleanValueContentV2 =>
        valueHasBoolean == thatBooleanValue.valueHasBoolean &&
        comment == thatBooleanValue.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
}

/**
 * Constructs [[BooleanValueContentV2]] objects based on JSON-LD input.
 */
object BooleanValueContentV2 {
  def from(r: Resource): Either[String, BooleanValueContentV2] = for {
    bool    <- r.objectBoolean(BooleanValueAsBoolean)
    comment <- objectCommentOption(r)
  } yield BooleanValueContentV2(ApiV2Complex, bool, comment)
}

/**
 * Represents a Knora geometry value (a 2D-shape).
 *
 * @param valueHasGeometry JSON representing a 2D geometrical shape.
 * @param comment          a comment on this [[GeomValueContentV2]], if any.
 */
case class GeomValueContentV2(ontologySchema: OntologySchema, valueHasGeometry: String, comment: Option[String] = None)
    extends ValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.GeomValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override def valueHasString: String = valueHasGeometry

  override def toOntologySchema(targetSchema: OntologySchema): GeomValueContentV2 = copy(ontologySchema = targetSchema)

  override def toJsonLDValue(
    targetSchema: ApiV2Schema,
    projectADM: Project,
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
  ): JsonLDValue =
    targetSchema match {
      case ApiV2Simple =>
        JsonLDUtil.datatypeValueToJsonLDObject(
          value = valueHasGeometry,
          datatype = OntologyConstants.KnoraApiV2Simple.Geom.toSmartIri,
        )

      case ApiV2Complex =>
        JsonLDObject(Map(GeometryValueAsGeometry -> JsonLDString(valueHasGeometry)))
    }

  override def unescape: ValueContentV2 =
    copy(
      valueHasGeometry = Iri.fromSparqlEncodedString(valueHasGeometry),
      comment = comment.map(commentStr => Iri.fromSparqlEncodedString(commentStr)),
    )

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean =
    that match {
      case thatGeomValue: GeomValueContentV2 => valueHasGeometry == thatGeomValue.valueHasGeometry
      case _                                 => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean =
    currentVersion match {
      case thatGeomValue: GeomValueContentV2 =>
        valueHasGeometry == thatGeomValue.valueHasGeometry &&
        comment == thatGeomValue.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
}

/**
 * Constructs [[GeomValueContentV2]] objects based on JSON-LD input.
 */
object GeomValueContentV2 {
  def from(r: Resource): Either[String, GeomValueContentV2] = for {
    geomStr <- r.objectString(GeometryValueAsGeometry)
    geom    <- ValuesValidator.validateGeometryString(geomStr).toRight(s"Invalid geometry string: $geomStr")
    comment <- objectCommentOption(r)
  } yield GeomValueContentV2(ApiV2Complex, geom, comment)
}

/**
 * Represents a Knora time interval value.
 *
 * @param valueHasIntervalStart the start of the time interval.
 * @param valueHasIntervalEnd   the end of the time interval.
 * @param comment               a comment on this [[IntervalValueContentV2]], if any.
 */
case class IntervalValueContentV2(
  ontologySchema: OntologySchema,
  valueHasIntervalStart: BigDecimal,
  valueHasIntervalEnd: BigDecimal,
  comment: Option[String] = None,
) extends ValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.IntervalValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override lazy val valueHasString: String = s"$valueHasIntervalStart - $valueHasIntervalEnd"

  override def toOntologySchema(targetSchema: OntologySchema): IntervalValueContentV2 =
    copy(ontologySchema = targetSchema)

  override def toJsonLDValue(
    targetSchema: ApiV2Schema,
    projectADM: Project,
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
  ): JsonLDValue =
    targetSchema match {
      case ApiV2Simple =>
        JsonLDUtil.datatypeValueToJsonLDObject(
          value = valueHasString,
          datatype = OntologyConstants.KnoraApiV2Simple.Interval.toSmartIri,
        )

      case ApiV2Complex =>
        JsonLDObject(
          Map(
            IntervalValueHasStart ->
              JsonLDUtil.datatypeValueToJsonLDObject(
                value = valueHasIntervalStart.toString,
                datatype = OntologyConstants.Xsd.Decimal.toSmartIri,
              ),
            IntervalValueHasEnd ->
              JsonLDUtil.datatypeValueToJsonLDObject(
                value = valueHasIntervalEnd.toString,
                datatype = OntologyConstants.Xsd.Decimal.toSmartIri,
              ),
          ),
        )
    }

  override def unescape: ValueContentV2 =
    copy(comment = comment.map(commentStr => Iri.fromSparqlEncodedString(commentStr)))

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean =
    that match {
      case thatIntervalValueContent: IntervalValueContentV2 =>
        valueHasIntervalStart == thatIntervalValueContent.valueHasIntervalStart &&
        valueHasIntervalEnd == thatIntervalValueContent.valueHasIntervalEnd

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean =
    currentVersion match {
      case thatIntervalValueContent: IntervalValueContentV2 =>
        valueHasIntervalStart == thatIntervalValueContent.valueHasIntervalStart &&
        valueHasIntervalEnd == thatIntervalValueContent.valueHasIntervalEnd &&
        comment == thatIntervalValueContent.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
}

/**
 * Constructs [[IntervalValueContentV2]] objects based on JSON-LD input.
 */
object IntervalValueContentV2 {
  def from(r: Resource): Either[String, IntervalValueContentV2] = for {
    intervalValueHasStart <- r.objectBigDecimal(IntervalValueHasStart)
    intervalValueHasEnd   <- r.objectBigDecimal(IntervalValueHasEnd)
    comment               <- objectCommentOption(r)
  } yield IntervalValueContentV2(ApiV2Complex, intervalValueHasStart, intervalValueHasEnd, comment)

}

/**
 * Represents a Knora timestamp value.
 *
 * @param valueHasTimeStamp the timestamp.
 * @param comment           a comment on this [[TimeValueContentV2]], if any.
 */
case class TimeValueContentV2(
  ontologySchema: OntologySchema,
  valueHasTimeStamp: Instant,
  comment: Option[String] = None,
) extends ValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.TimeValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override lazy val valueHasString: String = s"$valueHasTimeStamp"

  override def toOntologySchema(targetSchema: OntologySchema): TimeValueContentV2 = copy(ontologySchema = targetSchema)

  override def toJsonLDValue(
    targetSchema: ApiV2Schema,
    projectADM: Project,
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
  ): JsonLDValue =
    targetSchema match {
      case ApiV2Simple =>
        JsonLDUtil.datatypeValueToJsonLDObject(
          value = valueHasTimeStamp.toString,
          datatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
        )

      case ApiV2Complex =>
        JsonLDObject(
          Map(
            TimeValueAsTimeStamp ->
              JsonLDUtil.datatypeValueToJsonLDObject(
                value = valueHasTimeStamp.toString,
                datatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
              ),
          ),
        )
    }

  override def unescape: ValueContentV2 =
    copy(comment = comment.map(commentStr => Iri.fromSparqlEncodedString(commentStr)))

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean =
    that match {
      case thatTimeValueContent: TimeValueContentV2 =>
        valueHasTimeStamp == thatTimeValueContent.valueHasTimeStamp

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean =
    currentVersion match {
      case thatTimeValueContent: TimeValueContentV2 =>
        valueHasTimeStamp == thatTimeValueContent.valueHasTimeStamp &&
        comment == thatTimeValueContent.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
}

/**
 * Constructs [[TimeValueContentV2]] objects based on JSON-LD input.
 */
object TimeValueContentV2 {
  def from(r: Resource): Either[String, TimeValueContentV2] = for {
    timeStamp <- r.objectInstant(TimeValueAsTimeStamp)
    comment   <- objectCommentOption(r)
  } yield TimeValueContentV2(ApiV2Complex, timeStamp, comment)
}

/**
 * Represents a value pointing to a Knora hierarchical list node.
 *
 * @param valueHasListNode the IRI of the hierarchical list node pointed to.
 * @param listNodeLabel    the label of the hierarchical list node pointed to.
 * @param comment          a comment on this [[HierarchicalListValueContentV2]], if any.
 */
case class HierarchicalListValueContentV2(
  ontologySchema: OntologySchema,
  valueHasListNode: IRI,
  listNodeLabel: Option[String],
  comment: Option[String],
) extends ValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.ListValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override def valueHasString: String = valueHasListNode

  override def toOntologySchema(targetSchema: OntologySchema): HierarchicalListValueContentV2 =
    copy(ontologySchema = targetSchema)

  override def toJsonLDValue(
    targetSchema: ApiV2Schema,
    projectADM: Project,
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
  ): JsonLDValue =
    targetSchema match {
      case ApiV2Simple =>
        listNodeLabel match {
          case Some(labelStr) =>
            JsonLDUtil.datatypeValueToJsonLDObject(
              value = labelStr,
              datatype = OntologyConstants.KnoraApiV2Simple.ListNode.toSmartIri,
            )
          case None =>
            throw AssertionException("Can't convert list value to simple schema because it has no list node label")

        }

      case ApiV2Complex =>
        JsonLDObject(
          Map(
            ListValueAsListNode -> JsonLDUtil.iriToJsonLDObject(valueHasListNode),
          ),
        )
    }

  override def unescape: ValueContentV2 =
    copy(
      listNodeLabel = listNodeLabel.map(labelStr => Iri.fromSparqlEncodedString(labelStr)),
      comment = comment.map(commentStr => Iri.fromSparqlEncodedString(commentStr)),
    )

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean =
    that match {
      case thatListContent: HierarchicalListValueContentV2 => valueHasListNode == thatListContent.valueHasListNode
      case _                                               => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean =
    currentVersion match {
      case thatListContent: HierarchicalListValueContentV2 =>
        valueHasListNode == thatListContent.valueHasListNode &&
        comment == thatListContent.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
}

/**
 * Constructs [[HierarchicalListValueContentV2]] objects based on JSON-LD input.
 */
object HierarchicalListValueContentV2 {
  def from(r: Resource, converter: IriConverter): IO[String, HierarchicalListValueContentV2] = for {
    comment  <- ZIO.fromEither(objectCommentOption(r))
    listNode <- ZIO.fromEither(r.objectUri(ListValueAsListNode))
    _ <- ZIO
           .fail(s"List node IRI <$listNode> is not a Knora data IRI")
           .unlessZIO(converter.isKnoraDataIri(listNode).mapError(_.getMessage))
  } yield HierarchicalListValueContentV2(ApiV2Complex, listNode, None, comment)
}

/**
 * Represents a Knora color value.
 *
 * @param valueHasColor a hexadecimal string containing the RGB color value
 * @param comment       a comment on this [[ColorValueContentV2]], if any.
 */
case class ColorValueContentV2(ontologySchema: OntologySchema, valueHasColor: String, comment: Option[String] = None)
    extends ValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.ColorValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override def valueHasString: String = valueHasColor

  override def toOntologySchema(targetSchema: OntologySchema): ColorValueContentV2 = copy(ontologySchema = targetSchema)

  override def toJsonLDValue(
    targetSchema: ApiV2Schema,
    projectADM: Project,
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
  ): JsonLDValue =
    targetSchema match {
      case ApiV2Simple =>
        JsonLDUtil.datatypeValueToJsonLDObject(
          value = valueHasColor,
          datatype = OntologyConstants.KnoraApiV2Simple.Color.toSmartIri,
        )

      case ApiV2Complex =>
        JsonLDObject(Map(ColorValueAsColor -> JsonLDString(valueHasColor)))
    }

  override def unescape: ValueContentV2 =
    copy(
      valueHasColor = Iri.fromSparqlEncodedString(valueHasColor),
      comment = comment.map(commentStr => Iri.fromSparqlEncodedString(commentStr)),
    )

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean =
    that match {
      case thatColorContent: ColorValueContentV2 => valueHasColor == thatColorContent.valueHasColor
      case _                                     => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean =
    currentVersion match {
      case thatColorContent: ColorValueContentV2 =>
        valueHasColor == thatColorContent.valueHasColor &&
        comment == thatColorContent.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
}

/**
 * Constructs [[ColorValueContentV2]] objects based on JSON-LD input.
 */
object ColorValueContentV2 {
  def from(r: Resource): Either[IRI, ColorValueContentV2] = for {
    color   <- r.objectString(ColorValueAsColor)
    comment <- objectCommentOption(r)
  } yield ColorValueContentV2(ApiV2Complex, color, comment)
}

/**
 * Represents a Knora URI value.
 *
 * @param valueHasUri the URI value.
 * @param comment     a comment on this [[UriValueContentV2]], if any.
 */
case class UriValueContentV2(ontologySchema: OntologySchema, valueHasUri: String, comment: Option[String] = None)
    extends ValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.UriValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override def valueHasString: String = valueHasUri

  override def toOntologySchema(targetSchema: OntologySchema): UriValueContentV2 = copy(ontologySchema = targetSchema)

  override def toJsonLDValue(
    targetSchema: ApiV2Schema,
    projectADM: Project,
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
  ): JsonLDValue = {
    val uriAsJsonLDObject = JsonLDUtil.datatypeValueToJsonLDObject(
      value = valueHasUri,
      datatype = OntologyConstants.Xsd.Uri.toSmartIri,
    )

    targetSchema match {
      case ApiV2Simple => uriAsJsonLDObject

      case ApiV2Complex =>
        JsonLDObject(Map(UriValueAsUri -> uriAsJsonLDObject))
    }
  }

  override def unescape: ValueContentV2 =
    copy(
      valueHasUri = Iri.fromSparqlEncodedString(valueHasUri),
      comment = comment.map(commentStr => Iri.fromSparqlEncodedString(commentStr)),
    )

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean =
    that match {
      case thatUriContent: UriValueContentV2 => valueHasUri == thatUriContent.valueHasUri
      case _                                 => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean =
    currentVersion match {
      case thatUriContent: UriValueContentV2 =>
        valueHasUri == thatUriContent.valueHasUri &&
        comment == thatUriContent.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
}

/**
 * Constructs [[UriValueContentV2]] objects based on JSON-LD input.
 */
object UriValueContentV2 {
  def from(r: Resource): Either[String, UriValueContentV2] = for {
    uri     <- r.objectDataType(UriValueAsUri, OntologyConstants.Xsd.Uri)
    comment <- objectCommentOption(r)
  } yield UriValueContentV2(ApiV2Complex, uri, comment)
}

/**
 * Represents a Knora geoname value.
 *
 * @param valueHasGeonameCode the geoname code.
 * @param comment             a comment on this [[GeonameValueContentV2]], if any.
 */
case class GeonameValueContentV2(
  ontologySchema: OntologySchema,
  valueHasGeonameCode: String,
  comment: Option[String] = None,
) extends ValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.GeonameValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override def valueHasString: String = valueHasGeonameCode

  override def toOntologySchema(targetSchema: OntologySchema): GeonameValueContentV2 =
    copy(ontologySchema = targetSchema)

  override def toJsonLDValue(
    targetSchema: ApiV2Schema,
    projectADM: Project,
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
  ): JsonLDValue =
    targetSchema match {
      case ApiV2Simple =>
        JsonLDUtil.datatypeValueToJsonLDObject(
          value = valueHasGeonameCode,
          datatype = OntologyConstants.KnoraApiV2Simple.Geoname.toSmartIri,
        )

      case ApiV2Complex =>
        JsonLDObject(
          Map(GeonameValueAsGeonameCode -> JsonLDString(valueHasGeonameCode)),
        )
    }

  override def unescape: ValueContentV2 =
    copy(
      valueHasGeonameCode = Iri.fromSparqlEncodedString(valueHasGeonameCode),
      comment = comment.map(commentStr => Iri.fromSparqlEncodedString(commentStr)),
    )

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean =
    that match {
      case thatGeonameContent: GeonameValueContentV2 => valueHasGeonameCode == thatGeonameContent.valueHasGeonameCode
      case _                                         => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean =
    currentVersion match {
      case thatGeonameContent: GeonameValueContentV2 =>
        valueHasGeonameCode == thatGeonameContent.valueHasGeonameCode &&
        comment == thatGeonameContent.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
}

/**
 * Constructs [[GeonameValueContentV2]] objects based on JSON-LD input.
 */
object GeonameValueContentV2 {
  def from(r: Resource): Either[String, GeonameValueContentV2] = for {
    geonameCode <- r.objectString(GeonameValueAsGeonameCode)
    comment     <- objectCommentOption(r)
  } yield GeonameValueContentV2(ApiV2Complex, geonameCode, comment)
}

/**
 * Represents the basic metadata stored about any file value.
 */
case class FileValueV2(
  internalFilename: String,
  internalMimeType: String,
  originalFilename: Option[String] = None,
  originalMimeType: Option[String] = None,
  copyrightHolder: Option[CopyrightHolder] = None,
  authorship: Option[List[Authorship]] = None,
  licenseIri: Option[LicenseIri] = None,
)
object FileValueV2 {

  def makeNew(r: Resource, info: FileInfo): Either[String, FileValueV2] = {
    val meta = info.metadata
    for {
      copyrightHolder <- r.objectStringOption(HasCopyrightHolder, CopyrightHolder.from)
      authorship      <- r.objectStringListOption(HasAuthorship, Authorship.from)
      licenseIri      <- r.objectUriOption(HasLicense, LicenseIri.from)
    } yield FileValueV2(
      info.filename,
      meta.internalMimeType,
      meta.originalFilename,
      meta.originalMimeType,
      copyrightHolder,
      authorship,
      licenseIri,
    )
  }
}

/**
 * A trait for case classes representing different types of file values.
 */
sealed trait FileValueContentV2 extends ValueContentV2 {

  /**
   * The basic metadata about the file value.
   */
  def fileValue: FileValueV2

  def toJsonLDValueInSimpleSchema(fileUrl: String): JsonLDObject = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    JsonLDUtil.datatypeValueToJsonLDObject(
      value = fileUrl,
      datatype = OntologyConstants.KnoraApiV2Simple.File.toSmartIri,
    )
  }

  def toJsonLDObjectMapInComplexSchema(fileUrl: String): Map[IRI, JsonLDValue] = {
    def mkJsonLdString: StringValue => JsonLDString           = sv => JsonLDString(sv.value)
    def mkJsonLdStringArray: List[StringValue] => JsonLDArray = values => JsonLDArray(values.map(mkJsonLdString))
    def mkJsLdId: StringValue => JsonLDObject                 = str => JsonLDObject(Map("@id" -> JsonLDString(str.value)))
    val knownValues: Map[IRI, JsonLDValue] = Map(
      FileValueHasFilename -> JsonLDString(fileValue.internalFilename),
      FileValueAsUrl -> JsonLDUtil.datatypeValueToJsonLDObject(
        value = fileUrl,
        datatype = OntologyConstants.Xsd.Uri.toSmartIri,
      ),
    )

    val copyrightHolder = fileValue.copyrightHolder.map(mkJsonLdString).map((HasCopyrightHolder, _))
    val authorship      = fileValue.authorship.map(mkJsonLdStringArray).map((HasAuthorship, _))
    val licenseIri      = fileValue.licenseIri.map(mkJsLdId).map((HasLicense, _))
    knownValues ++ copyrightHolder ++ authorship ++ licenseIri
  }
}

/**
 * Represents image file metadata.
 *
 * @param fileValue the basic metadata about the file value.
 * @param dimX      the width of the image in pixels.
 * @param dimY      the height of the image in pixels.
 * @param comment   a comment on this `StillImageFileValueContentV2`, if any.
 */
case class StillImageFileValueContentV2(
  ontologySchema: OntologySchema,
  fileValue: FileValueV2,
  dimX: Int,
  dimY: Int,
  comment: Option[String] = None,
) extends FileValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.StillImageFileValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override def valueHasString: String = fileValue.internalFilename

  override def toOntologySchema(targetSchema: OntologySchema): StillImageFileValueContentV2 =
    copy(ontologySchema = targetSchema)

  def makeFileUrl(projectADM: Project, url: String): String =
    s"$url/${projectADM.shortcode}/${fileValue.internalFilename}/full/$dimX,$dimY/0/default.jpg"

  override def toJsonLDValue(
    targetSchema: ApiV2Schema,
    projectADM: Project,
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
  ): JsonLDValue = {
    val fileUrl: String = makeFileUrl(projectADM, appConfig.sipi.externalBaseUrl)

    targetSchema match {
      case ApiV2Simple => toJsonLDValueInSimpleSchema(fileUrl)

      case ApiV2Complex =>
        JsonLDObject(
          toJsonLDObjectMapInComplexSchema(fileUrl) ++ Map(
            StillImageFileValueHasDimX -> JsonLDInt(dimX),
            StillImageFileValueHasDimY -> JsonLDInt(dimY),
            StillImageFileValueHasIIIFBaseUrl -> JsonLDUtil
              .datatypeValueToJsonLDObject(
                value = s"${appConfig.sipi.externalBaseUrl}/${projectADM.shortcode}",
                datatype = OntologyConstants.Xsd.Uri.toSmartIri,
              ),
          ),
        )
    }
  }

  override def unescape: ValueContentV2 =
    copy(comment = comment.map(commentStr => Iri.fromSparqlEncodedString(commentStr)))

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean =
    that match {
      case thatStillImage: StillImageFileValueContentV2 =>
        fileValue == thatStillImage.fileValue &&
        dimX == thatStillImage.dimX &&
        dimY == thatStillImage.dimY

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean =
    currentVersion match {
      case thatStillImage: StillImageFileValueContentV2 =>
        wouldDuplicateOtherValue(thatStillImage) && comment == thatStillImage.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }

}

/**
 * Constructs [[StillImageFileValueContentV2]] objects based on JSON-LD input.
 */
object StillImageFileValueContentV2 {
  def from(r: Resource, fileInfo: FileInfo): Either[String, StillImageFileValueContentV2] = for {
    comment   <- objectCommentOption(r)
    meta       = fileInfo.metadata
    fileValue <- FileValueV2.makeNew(r, fileInfo)
  } yield StillImageFileValueContentV2(
    ApiV2Complex,
    fileValue,
    meta.width.getOrElse(0),
    meta.height.getOrElse(0),
    comment,
  )
}

/**
 * Represents the external image file metadata.
 *
 * @param fileValue the basic metadata about the file value.
 * @param comment   a comment on this `StillImageFileValueContentV2`, if any.
 */
case class StillImageExternalFileValueContentV2(
  ontologySchema: OntologySchema,
  fileValue: FileValueV2,
  externalUrl: IiifImageRequestUrl,
  comment: Option[IRI] = None,
) extends FileValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.StillImageExternalFileValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override def valueHasString: String = fileValue.internalFilename

  override def toOntologySchema(targetSchema: OntologySchema): StillImageExternalFileValueContentV2 =
    copy(ontologySchema = targetSchema)

  def makeFileUrl: String = externalUrl.value.toString

  override def toJsonLDValue(
    targetSchema: ApiV2Schema,
    projectADM: Project,
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
  ): JsonLDValue =
    targetSchema match {
      case ApiV2Simple => toJsonLDValueInSimpleSchema(makeFileUrl)

      case ApiV2Complex =>
        JsonLDObject(
          toJsonLDObjectMapInComplexSchema(makeFileUrl) ++ Map(
            StillImageFileValueHasIIIFBaseUrl -> JsonLDUtil.makeUriObject {
              val uri = externalUrl.value.toURI
              if (uri.getPort == -1) URI.create(s"${uri.getScheme}://${uri.getHost}").toURL
              else URI.create(s"${uri.getScheme}://${uri.getHost}:${uri.getPort}").toURL
            },
            StillImageFileValueHasExternalUrl -> JsonLDUtil.makeUriObject(externalUrl.value),
          ),
        )
    }

  override def unescape: ValueContentV2 =
    copy(comment = comment.map(commentStr => Iri.fromSparqlEncodedString(commentStr)))

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean =
    that match {
      case thatStillImage: StillImageExternalFileValueContentV2 =>
        externalUrl == thatStillImage.externalUrl
      case _: StillImageFileValueContentV2 => false

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean =
    currentVersion match {
      case thatStillImage: StillImageExternalFileValueContentV2 =>
        wouldDuplicateOtherValue(thatStillImage) && comment == thatStillImage.comment
      case _: StillImageFileValueContentV2 => false

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }

}

/**
 * Constructs [[StillImageFileValueContentV2]] objects based on JSON-LD input.
 */
object StillImageExternalFileValueContentV2 {
  private val fakeInfo = FileInfo(
    "internalFilename",
    FileMetadataSipiResponse(
      Some("originalFilename"),
      Some("originalMimeType"),
      "internalMimeType",
      None,
      None,
      None,
      None,
      None,
    ),
  )
  def from(r: Resource): Either[String, StillImageExternalFileValueContentV2] = for {
    externalUrlStr <- r.objectString(StillImageFileValueHasExternalUrl)
    iifUrl         <- IiifImageRequestUrl.from(externalUrlStr)
    comment        <- objectCommentOption(r)
    fileValue      <- FileValueV2.makeNew(r, fakeInfo)
  } yield StillImageExternalFileValueContentV2(ApiV2Complex, fileValue, iifUrl, comment)
}

/**
 * Represents document file metadata.
 *
 * @param fileValue the basic metadata about the file value.
 * @param pageCount the number of pages in the document.
 * @param dimX      the width of the document in pixels.
 * @param dimY      the height of the document in pixels.
 * @param comment   a comment on this `DocumentFileValueContentV2`, if any.
 */
case class DocumentFileValueContentV2(
  ontologySchema: OntologySchema,
  fileValue: FileValueV2,
  pageCount: Option[Int],
  dimX: Option[Int],
  dimY: Option[Int],
  comment: Option[String] = None,
) extends FileValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.DocumentFileValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override def valueHasString: String = fileValue.internalFilename

  override def toOntologySchema(targetSchema: OntologySchema): DocumentFileValueContentV2 =
    copy(ontologySchema = targetSchema)

  override def toJsonLDValue(
    targetSchema: ApiV2Schema,
    projectADM: Project,
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
  ): JsonLDValue = {
    val fileUrl: String =
      s"${appConfig.sipi.externalBaseUrl}/${projectADM.shortcode}/${fileValue.internalFilename}/file"

    targetSchema match {
      case ApiV2Simple => toJsonLDValueInSimpleSchema(fileUrl)

      case ApiV2Complex =>
        JsonLDObject(
          toJsonLDObjectMapInComplexSchema(
            fileUrl,
          ),
        )
    }
  }

  override def unescape: ValueContentV2 =
    copy(comment = comment.map(commentStr => Iri.fromSparqlEncodedString(commentStr)))

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean =
    that match {
      case thatDocumentFile: DocumentFileValueContentV2 =>
        fileValue == thatDocumentFile.fileValue

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean =
    currentVersion match {
      case thatDocumentFile: DocumentFileValueContentV2 =>
        wouldDuplicateOtherValue(thatDocumentFile) && comment == thatDocumentFile.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
}

/**
 * Represents archive file metadata.
 *
 * @param fileValue the basic metadata about the file value.
 * @param comment   a comment on this `ArchiveFileValueContentV2`, if any.
 */
case class ArchiveFileValueContentV2(
  ontologySchema: OntologySchema,
  fileValue: FileValueV2,
  comment: Option[String] = None,
) extends FileValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.ArchiveFileValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override def valueHasString: String = fileValue.internalFilename

  override def toOntologySchema(targetSchema: OntologySchema): ArchiveFileValueContentV2 =
    copy(ontologySchema = targetSchema)

  override def toJsonLDValue(
    targetSchema: ApiV2Schema,
    projectADM: Project,
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
  ): JsonLDValue = {
    val fileUrl: String =
      s"${appConfig.sipi.externalBaseUrl}/${projectADM.shortcode}/${fileValue.internalFilename}/file"

    targetSchema match {
      case ApiV2Simple => toJsonLDValueInSimpleSchema(fileUrl)
      case ApiV2Complex =>
        JsonLDObject(
          toJsonLDObjectMapInComplexSchema(
            fileUrl,
          ),
        )
    }
  }

  override def unescape: ValueContentV2 =
    copy(comment = comment.map(commentStr => Iri.fromSparqlEncodedString(commentStr)))

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean =
    that match {
      case thatArchiveFile: ArchiveFileValueContentV2 =>
        fileValue == thatArchiveFile.fileValue

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean =
    currentVersion match {
      case thatArchiveFile: ArchiveFileValueContentV2 =>
        wouldDuplicateOtherValue(thatArchiveFile) && comment == thatArchiveFile.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
}

/**
 * Constructs [[DocumentFileValueContentV2]] objects based on JSON-LD input.
 */
object DocumentFileValueContentV2 {
  def from(r: Resource, info: FileInfo): Either[String, DocumentFileValueContentV2] = for {
    comment   <- objectCommentOption(r)
    fileValue <- FileValueV2.makeNew(r, info)
    meta       = info.metadata
  } yield DocumentFileValueContentV2(ApiV2Complex, fileValue, meta.numpages, meta.width, meta.height, comment)
}

/**
 * Constructs [[ArchiveFileValueContentV2]] objects based on JSON-LD input.
 */
object ArchiveFileValueContentV2 {
  def from(r: Resource, info: FileInfo): Either[String, ArchiveFileValueContentV2] = for {
    comment   <- objectCommentOption(r)
    fileValue <- FileValueV2.makeNew(r, info)
  } yield ArchiveFileValueContentV2(ApiV2Complex, fileValue, comment)
}

/**
 * Represents text file metadata.
 *
 * @param fileValue the basic metadata about the file value.
 * @param comment   a comment on this [[TextFileValueContentV2]], if any.
 */
case class TextFileValueContentV2(
  ontologySchema: OntologySchema,
  fileValue: FileValueV2,
  comment: Option[String] = None,
) extends FileValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.TextFileValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override def valueHasString: String = fileValue.internalFilename

  override def toOntologySchema(targetSchema: OntologySchema): TextFileValueContentV2 =
    copy(ontologySchema = targetSchema)

  override def toJsonLDValue(
    targetSchema: ApiV2Schema,
    projectADM: Project,
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
  ): JsonLDValue = {
    val fileUrl: String =
      s"${appConfig.sipi.externalBaseUrl}/${projectADM.shortcode}/${fileValue.internalFilename}/file"

    targetSchema match {
      case ApiV2Simple => toJsonLDValueInSimpleSchema(fileUrl)

      case ApiV2Complex =>
        JsonLDObject(toJsonLDObjectMapInComplexSchema(fileUrl))
    }
  }

  override def unescape: ValueContentV2 =
    copy(comment = comment.map(commentStr => Iri.fromSparqlEncodedString(commentStr)))

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean =
    that match {
      case thatTextFile: TextFileValueContentV2 =>
        fileValue == thatTextFile.fileValue

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean =
    currentVersion match {
      case thatTextFile: TextFileValueContentV2 =>
        fileValue == thatTextFile.fileValue &&
        comment == thatTextFile.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
}

/**
 * Constructs [[TextFileValueContentV2]] objects based on JSON-LD input.
 */
object TextFileValueContentV2 {
  def from(r: Resource, info: FileInfo): Either[String, TextFileValueContentV2] = for {
    comment   <- objectCommentOption(r)
    fileValue <- FileValueV2.makeNew(r, info)
  } yield TextFileValueContentV2(ApiV2Complex, fileValue, comment)
}

/**
 * Represents audio file metadata.
 *
 * @param fileValue the basic metadata about the file value.
 * @param comment a comment on this [[AudioFileValueContentV2]], if any.
 */
case class AudioFileValueContentV2(
  ontologySchema: OntologySchema,
  fileValue: FileValueV2,
  comment: Option[String] = None,
) extends FileValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.AudioFileValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override def valueHasString: String = fileValue.internalFilename

  override def toOntologySchema(targetSchema: OntologySchema): AudioFileValueContentV2 =
    copy(ontologySchema = targetSchema)

  override def toJsonLDValue(
    targetSchema: ApiV2Schema,
    projectADM: Project,
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
  ): JsonLDValue = {
    val fileUrl: String =
      s"${appConfig.sipi.externalBaseUrl}/${projectADM.shortcode}/${fileValue.internalFilename}/file"

    targetSchema match {
      case ApiV2Simple => toJsonLDValueInSimpleSchema(fileUrl)

      case ApiV2Complex =>
        JsonLDObject(toJsonLDObjectMapInComplexSchema(fileUrl))
    }
  }

  override def unescape: ValueContentV2 =
    copy(comment = comment.map(commentStr => Iri.fromSparqlEncodedString(commentStr)))

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean =
    that match {
      case thatAudioFile: AudioFileValueContentV2 =>
        fileValue == thatAudioFile.fileValue

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean =
    currentVersion match {
      case thatAudioFile: AudioFileValueContentV2 =>
        fileValue == thatAudioFile.fileValue &&
        comment == thatAudioFile.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
}

/**
 * Constructs [[AudioFileValueContentV2]] objects based on JSON-LD input.
 */
object AudioFileValueContentV2 {
  def from(r: Resource, info: FileInfo): Either[String, AudioFileValueContentV2] = for {
    comment   <- objectCommentOption(r)
    fileValue <- FileValueV2.makeNew(r, info)
  } yield AudioFileValueContentV2(ApiV2Complex, fileValue, comment)
}

/**
 * Represents video file metadata.
 *
 * @param fileValue the basic metadata about the file value.
 * @param comment a comment on this [[MovingImageFileValueContentV2]], if any.
 */
case class MovingImageFileValueContentV2(
  ontologySchema: OntologySchema,
  fileValue: FileValueV2,
  comment: Option[String] = None,
) extends FileValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.MovingImageFileValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override def valueHasString: String = fileValue.internalFilename

  override def toOntologySchema(targetSchema: OntologySchema): MovingImageFileValueContentV2 =
    copy(ontologySchema = targetSchema)

  override def toJsonLDValue(
    targetSchema: ApiV2Schema,
    projectADM: Project,
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
  ): JsonLDValue = {
    val fileUrl: String =
      s"${appConfig.sipi.externalBaseUrl}/${projectADM.shortcode}/${fileValue.internalFilename}/file"

    targetSchema match {
      case ApiV2Simple => toJsonLDValueInSimpleSchema(fileUrl)

      case ApiV2Complex =>
        JsonLDObject(
          toJsonLDObjectMapInComplexSchema(fileUrl),
        )
    }
  }

  override def unescape: ValueContentV2 =
    copy(comment = comment.map(commentStr => Iri.fromSparqlEncodedString(commentStr)))

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean =
    that match {
      case thatVideoFile: MovingImageFileValueContentV2 =>
        fileValue == thatVideoFile.fileValue

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean =
    currentVersion match {
      case thatVideoFile: MovingImageFileValueContentV2 =>
        fileValue == thatVideoFile.fileValue &&
        comment == thatVideoFile.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
}

/**
 * Constructs [[MovingImageFileValueContentV2]] objects based on JSON-LD input.
 */
object MovingImageFileValueContentV2 {
  def from(r: Resource, info: FileInfo): Either[String, MovingImageFileValueContentV2] = for {
    comment   <- objectCommentOption(r)
    fileValue <- FileValueV2.makeNew(r, info)
  } yield MovingImageFileValueContentV2(ApiV2Complex, fileValue, comment)
}

/**
 * Represents a Knora link value.
 *
 * @param referredResourceIri    the IRI of resource that this link value refers to (either the source
 *                               of an incoming link, or the target of an outgoing link).
 * @param referredResourceExists `true` if the referred resource already exists, `false` if it is being created in the
 *                               same transaction.
 * @param isIncomingLink         indicates if it is an incoming link.
 * @param nestedResource         information about the nested resource, if given.
 * @param comment                a comment on the link.
 */
case class LinkValueContentV2(
  ontologySchema: OntologySchema,
  referredResourceIri: IRI,
  referredResourceExists: Boolean = true,
  isIncomingLink: Boolean = false,
  nestedResource: Option[ReadResourceV2] = None,
  comment: Option[String] = None,
) extends ValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.LinkValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override def valueHasString: String = referredResourceIri

  override def toOntologySchema(targetSchema: OntologySchema): LinkValueContentV2 = {
    val convertedNestedResource = nestedResource.map { nested =>
      val targetApiSchema: ApiV2Schema = targetSchema match {
        case apiSchema: ApiV2Schema => apiSchema
        case _                      => throw AssertionException(s"Can't convert a nested resource to $targetSchema")
      }

      nested.toOntologySchema(targetApiSchema)
    }

    copy(
      ontologySchema = targetSchema,
      nestedResource = convertedNestedResource,
    )
  }

  override def toJsonLDValue(
    targetSchema: ApiV2Schema,
    projectADM: Project,
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
  ): JsonLDValue =
    targetSchema match {
      case ApiV2Simple => JsonLDUtil.iriToJsonLDObject(referredResourceIri)

      case ApiV2Complex =>
        // check if the referred resource has to be included in the JSON response
        val objectMap: Map[IRI, JsonLDValue] = nestedResource match {
          case Some(targetResource: ReadResourceV2) =>
            // include the nested resource in the response
            val referredResourceAsJsonLDValue: JsonLDObject = targetResource.toJsonLD(
              targetSchema = targetSchema,
              appConfig = appConfig,
              schemaOptions = schemaOptions,
            )

            // check whether the nested resource is the target or the source of the link
            if (!isIncomingLink) {
              Map(LinkValueHasTarget -> referredResourceAsJsonLDValue)
            } else {
              Map(LinkValueHasSource -> referredResourceAsJsonLDValue)
            }
          case None =>
            // check whether it is an outgoing or incoming link
            if (!isIncomingLink) {
              Map(
                LinkValueHasTargetIri -> JsonLDUtil.iriToJsonLDObject(
                  referredResourceIri,
                ),
              )
            } else {
              Map(
                LinkValueHasSourceIri -> JsonLDUtil.iriToJsonLDObject(
                  referredResourceIri,
                ),
              )
            }
        }

        JsonLDObject(objectMap)
    }

  override def unescape: ValueContentV2 =
    copy(comment = comment.map(commentStr => Iri.fromSparqlEncodedString(commentStr)))

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean =
    that match {
      case thatLinkValue: LinkValueContentV2 =>
        referredResourceIri == thatLinkValue.referredResourceIri &&
        isIncomingLink == thatLinkValue.isIncomingLink

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
    }

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean =
    currentVersion match {
      case thatLinkValue: LinkValueContentV2 =>
        referredResourceIri == thatLinkValue.referredResourceIri &&
        isIncomingLink == thatLinkValue.isIncomingLink &&
        comment == thatLinkValue.comment

      case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
    }
}

/**
 * Constructs [[LinkValueContentV2]] objects based on JSON-LD input.
 */
object LinkValueContentV2 {
  def from(r: Resource, converter: IriConverter): IO[String, LinkValueContentV2] =
    for {
      targetIri <- ZIO.fromEither(r.objectUri(LinkValueHasTargetIri))
      comment   <- ZIO.fromEither(objectCommentOption(r))
      _ <- ZIO
             .fail(s"Link target IRI <$targetIri> is not a Knora data IRI")
             .unlessZIO(converter.isKnoraDataIri(targetIri).mapError(_.getMessage))
    } yield LinkValueContentV2(ApiV2Complex, referredResourceIri = targetIri, comment = comment)
}

/**
 * Generic representation of a deleted value.
 *
 * @param ontologySchema the ontology schema
 * @param comment        optional comment
 */
case class DeletedValueContentV2(ontologySchema: OntologySchema, comment: Option[String] = None)
    extends ValueContentV2 {
  override def valueType: SmartIri = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    OntologyConstants.KnoraBase.DeletedValue.toSmartIri.toOntologySchema(ontologySchema)
  }

  override def toOntologySchema(targetSchema: OntologySchema): DeletedValueContentV2 =
    copy(ontologySchema = targetSchema)

  override def toJsonLDValue(
    targetSchema: ApiV2Schema,
    projectADM: Project,
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
  ): JsonLDValue = JsonLDObject(Map(OntologyConstants.KnoraBase.DeletedValue -> JsonLDString("DeletedValue")))

  override def unescape: ValueContentV2 =
    copy(comment = comment.map(commentStr => Iri.fromSparqlEncodedString(commentStr)))

  override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean = false

  override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean = false

  override def valueHasString: String = "Deleted Value"
}
