/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common
import org.apache.jena.rdf.model.*
import org.apache.jena.vocabulary.RDF
import org.apache.jena.vocabulary.RDFS
import zio.*
import zio.ZIO
import zio.ZLayer

import java.time.Instant
import java.util.UUID
import scala.collection.immutable.Seq
import scala.jdk.CollectionConverters.*
import scala.language.implicitConversions

import org.knora.webapi.IRI
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex as KA
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex.*
import org.knora.webapi.messages.OntologyConstants.Rdfs
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.v2.responder.resourcemessages.CreateResourceRequestV2
import org.knora.webapi.messages.v2.responder.resourcemessages.CreateResourceV2
import org.knora.webapi.messages.v2.responder.resourcemessages.CreateValueInNewResourceV2
import org.knora.webapi.messages.v2.responder.resourcemessages.DeleteOrEraseResourceRequestV2
import org.knora.webapi.messages.v2.responder.resourcemessages.UpdateResourceMetadataRequestV2
import org.knora.webapi.messages.v2.responder.standoffmessages.CreateMappingRequestMetadataV2
import org.knora.webapi.messages.v2.responder.valuemessages.*
import org.knora.webapi.messages.v2.responder.valuemessages.ValueContentV2.FileInfo
import org.knora.webapi.slice.admin.api.model.Project
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.admin.domain.service.UserService
import org.knora.webapi.slice.common.KnoraIris.*
import org.knora.webapi.slice.common.KnoraIris.ResourceIri as KResourceIri
import org.knora.webapi.slice.common.jena.JenaConversions.given
import org.knora.webapi.slice.common.jena.ModelOps
import org.knora.webapi.slice.common.jena.ModelOps.*
import org.knora.webapi.slice.common.jena.ResourceOps.*
import org.knora.webapi.slice.common.jena.StatementOps.*
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.store.iiif.api.SipiService

final case class ApiComplexV2JsonLdRequestParser(
  converter: IriConverter,
  messageRelay: MessageRelay,
  sipiService: SipiService,
  projectService: ProjectService,
  userService: UserService,
) {

  /**
   * Every value or resource request MUST only contain a single root rdf resource.
   * The root resource MUST have a rdf:type property that specifies the Knora resource class.
   * The root resource MAY be an uri resource or a blank node resource.
   * The RootResource provides some common methods to access the properties of the root resource.
   */
  private case class RootResource(
    resource: Resource,
    resourceIri: Option[ResourceIri],
    resourceClassIri: ResourceClassIri,
  ) {
    def resourceIriOrFail: IO[String, ResourceIri] =
      ZIO.fromOption(resourceIri).orElseFail("The resource IRI is required")
    def resourceClassSmartIri: SmartIri = resourceClassIri.smartIri

    // accessor methods for various properties of the root resource
    def creationDateOption: IO[String, Option[Instant]]  = ZIO.fromEither(resource.objectInstantOption(CreationDate))
    def deleteCommentOption: IO[String, Option[String]]  = ZIO.fromEither(resource.objectStringOption(DeleteComment))
    def deleteDateOption: IO[String, Option[Instant]]    = ZIO.fromEither(resource.objectInstantOption(DeleteDate))
    def hasPermissionsOption: IO[String, Option[String]] = ZIO.fromEither(resource.objectStringOption(HasPermissions))
    def lastModificationDateOption: IO[String, Option[Instant]] =
      ZIO.fromEither(resource.objectInstantOption(LastModificationDate))
    def newModificationDateOption: IO[String, Option[Instant]] =
      ZIO.fromEither(resource.objectInstantOption(NewModificationDate))
    def rdfsLabelOption: IO[String, Option[String]] = ZIO.fromEither(resource.objectStringOption(Rdfs.Label))
  }

  private object RootResource {
    def fromJsonLd(str: String): ZIO[Scope, String, RootResource] =
      for {
        model    <- ModelOps.fromJsonLd(str)
        resource <- ZIO.fromEither(model.singleRootResource)
        resourceIriOption <-
          ZIO
            .foreach(resource.uri)(
              converter.asSmartIri(_).mapError(_.getMessage).flatMap(iri => ZIO.fromEither(KResourceIri.from(iri))),
            )
        resourceClassIri <- resourceClassIri(resource)
      } yield RootResource(resource, resourceIriOption, resourceClassIri)

    private def resourceClassIri(r: Resource): IO[String, ResourceClassIri] = ZIO
      .fromOption(r.rdfsType)
      .orElseFail("No root resource class IRI found")
      .flatMap(converter.asResourceClassIriApiV2Complex)
  }

  def updateResourceMetadataRequestV2(
    str: String,
    requestingUser: User,
    uuid: UUID,
  ): IO[String, UpdateResourceMetadataRequestV2] = ZIO.scoped {
    for {
      r                    <- RootResource.fromJsonLd(str)
      resourceIri          <- r.resourceIriOrFail
      label                <- r.rdfsLabelOption
      permissions          <- r.hasPermissionsOption
      lastModificationDate <- r.lastModificationDateOption
      newModificationDate  <- r.newModificationDateOption
      _ <- ZIO
             .fail("No updated resource metadata provided")
             .when(label.isEmpty && permissions.isEmpty && newModificationDate.isEmpty)
    } yield UpdateResourceMetadataRequestV2(
      resourceIri.smartIri.toString,
      r.resourceClassSmartIri,
      lastModificationDate,
      label,
      permissions,
      newModificationDate,
      requestingUser,
      uuid,
    )
  }

  def deleteOrEraseResourceRequestV2(
    str: String,
    requestingUser: User,
    uuid: UUID,
  ): IO[String, DeleteOrEraseResourceRequestV2] = ZIO.scoped {
    for {
      r                    <- RootResource.fromJsonLd(str)
      resourceIri          <- r.resourceIriOrFail
      deleteComment        <- r.deleteCommentOption
      deleteDate           <- r.deleteDateOption
      lastModificationDate <- r.lastModificationDateOption
    } yield DeleteOrEraseResourceRequestV2(
      resourceIri.smartIri.toString,
      r.resourceClassSmartIri,
      deleteComment,
      deleteDate,
      lastModificationDate,
      false,
      requestingUser,
      uuid,
    )
  }

  def deleteValueV2FromJsonLd(str: String): IO[String, DeleteValueV2] = ZIO.scoped {
    for {
      r                  <- RootResource.fromJsonLd(str)
      resourceIri        <- r.resourceIriOrFail
      v                  <- ValueResource.from(r)
      valueIri           <- v.valueIriOrFail
      valueDeleteDate    <- v.deleteDateOption
      valueDeleteComment <- v.deleteCommentOption
    } yield DeleteValueV2(
      resourceIri.smartIri.toString,
      r.resourceClassSmartIri,
      v.propertySmartIri,
      valueIri.smartIri.toString,
      v.valueType,
      valueDeleteComment,
      valueDeleteDate,
    )
  }

  private case class ValueResource(
    r: Resource,
    valueIri: Option[ValueIri],
    propertyIri: PropertyIri,
    valueType: SmartIri,
  ) {
    def valueIriOrFail: IO[String, ValueIri] = ZIO.fromOption(valueIri).orElseFail("The value IRI is required")
    def propertySmartIri: SmartIri           = propertyIri.smartIri

    // accessor methods for various properties of the value resource
    def deleteCommentOption: IO[String, Option[String]] = ZIO.fromEither(r.objectStringOption(DeleteComment))
    def deleteDateOption: IO[String, Option[Instant]]   = ZIO.fromEither(r.objectInstantOption(DeleteDate))
    def fileValueHasFilenameOption: IO[String, Option[String]] =
      ZIO.fromEither(r.objectStringOption(FileValueHasFilename))
    def hasPermissionsOption: IO[String, Option[String]]     = ZIO.fromEither(r.objectStringOption(HasPermissions))
    def newValueVersionIriOption: IO[String, Option[String]] = ZIO.fromEither(r.objectUriOption(NewValueVersionIri))
    def valueCreationDateOption: IO[String, Option[Instant]] = ZIO.fromEither(r.objectInstantOption(ValueCreationDate))
    def valueHasUuidOption: IO[String, Option[UUID]]         = ZIO.fromEither(r.objectUuidOption(ValueHasUUID))
  }

  private object ValueResource {
    def from(r: RootResource): IO[String, ValueResource] = valueStatement(r).flatMap(from)

    def from(stmt: Statement): IO[String, ValueResource] =
      for {
        propertyIri <- valuePropertyIri(stmt)
        r            = stmt.getObject.asResource()
        iriOption   <- valueIri(r)
        valueType   <- valueType(r)
      } yield ValueResource(r, iriOption, propertyIri, valueType)

    private def valueStatement(r: RootResource): IO[String, Statement] =
      ZIO
        .succeed(r.resource.listProperties().asScala.filter(_.getPredicate != RDF.`type`).toList)
        .filterOrFail(_.nonEmpty)("No value property found in root resource")
        .filterOrFail(_.size == 1)("Multiple value properties found in root resource")
        .map(_.head)

    private def valueIri(valueResource: Resource): IO[String, Option[ValueIri]] = ZIO
      .fromOption(valueResource.uri)
      .flatMap(converter.asSmartIri(_).mapError(_.getMessage).asSomeError)
      .flatMap(iri => ZIO.fromEither(ValueIri.from(iri)).asSomeError)
      .unsome

    private def valuePropertyIri(valueStatement: Statement) =
      converter
        .asSmartIri(valueStatement.predicateUri)
        .mapError(_.getMessage)
        .flatMap(iri => ZIO.fromEither(PropertyIri.fromApiV2Complex(iri)))

    private def valueType(resource: Resource) = ZIO
      .fromEither(resource.rdfsType.toRight("No rdf:type found for value."))
      .orElseFail(s"No value type found for value.")
      .flatMap(converter.asSmartIri(_).mapError(_.getMessage))

  }

  def createResourceRequestV2(
    str: String,
    requestingUser: User,
    uuid: UUID,
  ): IO[String, CreateResourceRequestV2] = ZIO.scoped {
    for {
      r            <- RootResource.fromJsonLd(str)
      permissions  <- r.hasPermissionsOption
      creationDate <- r.creationDateOption
      label        <- r.rdfsLabelOption.someOrFail("A Resource must have an rdfs:label")
      project      <- attachedToProject(r.resource)
      _ <- ZIO
             .fail("Resource IRI and project IRI must reference the same project")
             .when(r.resourceIri.exists(_.shortcode != project.shortcode))
      attachedToUser <- attachedToUser(r.resource, requestingUser, project.id)
      values         <- extractValues(r.resource, project.shortcode)
    } yield CreateResourceRequestV2(
      CreateResourceV2(
        r.resourceIri.map(_.smartIri),
        r.resourceClassSmartIri,
        label,
        values,
        project,
        permissions,
        creationDate,
      ),
      attachedToUser,
      uuid,
    )
  }

  private def extractValues(
    r: Resource,
    shortcode: Shortcode,
  ): IO[String, Map[SmartIri, Seq[CreateValueInNewResourceV2]]] =
    val filteredProperties = Seq(
      RDF.`type`.toString,
      Rdfs.Label,
      AttachedToProject,
      AttachedToUser,
      HasPermissions,
      CreationDate,
    )
    val valueStatements = r
      .listProperties()
      .asScala
      .filter(p => !filteredProperties.contains(p.getPredicate.toString))
      .toSeq
    ZIO
      .foreach(valueStatements)(valueStatementAsContent(_, shortcode))
      .map(_.groupMap(_._1.smartIri)(_._2))

  private def valueStatementAsContent(
    statement: Statement,
    shortcode: Shortcode,
  ): IO[String, (PropertyIri, CreateValueInNewResourceV2)] =
    for {
      v                       <- ValueResource.from(statement)
      cnt                     <- getValueContent(v, shortcode)
      customValueUuid         <- v.valueHasUuidOption
      customValueCreationDate <- v.valueCreationDateOption
      permissions             <- v.hasPermissionsOption
    } yield (
      v.propertyIri,
      CreateValueInNewResourceV2(cnt, v.valueIri.map(_.smartIri), customValueUuid, customValueCreationDate, permissions),
    )

  def attachedToUser(r: Resource, requestingUser: User, projectIri: ProjectIri): IO[String, User] =
    for {
      userStr <- ZIO.fromEither(r.objectUriOption(AttachedToUser))
      userIri <- ZIO.foreach(userStr)(iri => ZIO.fromEither(UserIri.from(iri)))
      user    <- ZIO.foreach(userIri)(iri => checkUser(requestingUser, iri, projectIri))
    } yield user.getOrElse(requestingUser)

  private def checkUser(requestingUser: User, userIri: UserIri, projectIri: ProjectIri): IO[String, User] =
    requestingUser match {
      case _ if requestingUser.id == userIri.value => ZIO.succeed(requestingUser)
      case _
          if !(requestingUser.permissions.isSystemAdmin ||
            requestingUser.permissions.isProjectAdmin(projectIri.value)) =>
        ZIO.fail(
          s"You are logged in as ${requestingUser.username}, but only a system or project administrator can perform an operation as another user",
        )
      case _ => userService.findUserByIri(userIri).orDie.someOrFail(s"User '${userIri.value}' not found")
    }

  def attachedToProject(r: Resource): IO[String, Project] =
    for {
      projectIri <- ZIO.fromEither(r.objectUri(AttachedToProject)).flatMap(iri => ZIO.fromEither(ProjectIri.from(iri)))
      project    <- projectService.findById(projectIri).orDie.someOrFail(s"Project ${projectIri.value} not found")
    } yield project

  private def newValueVersionIri(r: ValueResource, valueIri: ValueIri): IO[String, Option[ValueIri]] =
    r.newValueVersionIriOption.some
      .flatMap(converter.asSmartIri(_).mapError(_.getMessage).asSomeError)
      .flatMap(iri => ZIO.fromEither(ValueIri.from(iri)).asSomeError)
      .filterOrFail(newV => newV != valueIri)(
        Some(s"The IRI of a new value version cannot be the same as the IRI of the current version"),
      )
      .filterOrFail(newV => newV.sameResourceAs(valueIri))(
        Some(
          s"The project shortcode and resource must be equal for the new value version and the current version",
        ),
      )
      .unsome

  def updateValueV2fromJsonLd(str: String): IO[String, UpdateValueV2] =
    ZIO.scoped {
      for {
        r                  <- RootResource.fromJsonLd(str)
        resourceIri        <- r.resourceIriOrFail
        v                  <- ValueResource.from(r)
        valueIri           <- v.valueIriOrFail
        valueCreationDate  <- v.valueCreationDateOption
        valuePermissions   <- v.hasPermissionsOption
        newValueVersionIri <- newValueVersionIri(v, valueIri)
        valueContent       <- getValueContent(v, resourceIri.shortcode).map(Some(_)).orElse(ZIO.none)
        updateValue <- (valueContent, valuePermissions) match
                         case (Some(valueContentV2), _) =>
                           ZIO.succeed(
                             UpdateValueContentV2(
                               resourceIri.smartIri.toString,
                               r.resourceClassSmartIri,
                               v.propertySmartIri,
                               valueIri.smartIri.toString,
                               valueContentV2,
                               valuePermissions,
                               valueCreationDate,
                               newValueVersionIri.map(_.smartIri),
                             ),
                           )
                         case (_, Some(permissions)) =>
                           ZIO.succeed(
                             UpdateValuePermissionsV2(
                               resourceIri.smartIri.toString,
                               r.resourceClassSmartIri,
                               v.propertySmartIri,
                               valueIri.smartIri.toString,
                               v.valueType,
                               permissions,
                               valueCreationDate,
                               newValueVersionIri.map(_.smartIri),
                             ),
                           )
                         case _ => ZIO.fail("No value content or permissions provided")
      } yield updateValue
    }

  def createValueV2FromJsonLd(str: String): IO[String, CreateValueV2] =
    ZIO.scoped {
      for {
        r                 <- RootResource.fromJsonLd(str)
        resourceIri       <- r.resourceIriOrFail
        v                 <- ValueResource.from(r)
        valueUuid         <- v.valueHasUuidOption
        valueCreationDate <- v.valueCreationDateOption
        valuePermissions  <- v.hasPermissionsOption
        valueContent      <- getValueContent(v, resourceIri.shortcode)
      } yield CreateValueV2(
        resourceIri.smartIri.toString,
        r.resourceClassSmartIri,
        v.propertyIri.smartIri,
        valueContent,
        v.valueIri.map(_.smartIri),
        valueUuid,
        valueCreationDate,
        valuePermissions,
      )
    }

  private def getValueContent(
    v: ValueResource,
    shortcode: Shortcode,
  ): IO[String, ValueContentV2] =
    def withFileInfo[T](fileInfo: Option[FileInfo], f: FileInfo => Either[String, T]): IO[String, T] =
      fileInfo match
        case None       => ZIO.fail("FileInfo is missing")
        case Some(info) => ZIO.fromEither(f(info))
    for {
      maybeFileName <- v.fileValueHasFilenameOption
      valueResource  = v.r
      i <-
        ValueContentV2
          .fileInfoFromExternal(maybeFileName, shortcode)
          .provide(ZLayer.succeed(sipiService))
          .mapError(_.getMessage)
      content <-
        v.valueType.toString match
          case AudioFileValue              => withFileInfo(i, AudioFileValueContentV2.from(valueResource, _))
          case ArchiveFileValue            => withFileInfo(i, ArchiveFileValueContentV2.from(valueResource, _))
          case BooleanValue                => ZIO.fromEither(BooleanValueContentV2.from(valueResource))
          case ColorValue                  => ZIO.fromEither(ColorValueContentV2.from(valueResource))
          case DateValue                   => ZIO.fromEither(DateValueContentV2.from(valueResource))
          case DecimalValue                => ZIO.fromEither(DecimalValueContentV2.from(valueResource))
          case DocumentFileValue           => withFileInfo(i, DocumentFileValueContentV2.from(valueResource, _))
          case GeomValue                   => ZIO.fromEither(GeomValueContentV2.from(valueResource))
          case GeonameValue                => ZIO.fromEither(GeonameValueContentV2.from(valueResource))
          case IntValue                    => ZIO.fromEither(IntegerValueContentV2.from(valueResource))
          case IntervalValue               => ZIO.fromEither(IntervalValueContentV2.from(valueResource))
          case ListValue                   => HierarchicalListValueContentV2.from(valueResource, converter)
          case LinkValue                   => LinkValueContentV2.from(valueResource, converter)
          case MovingImageFileValue        => withFileInfo(i, MovingImageFileValueContentV2.from(valueResource, _))
          case StillImageExternalFileValue => ZIO.fromEither(StillImageExternalFileValueContentV2.from(valueResource))
          case StillImageFileValue         => withFileInfo(i, StillImageFileValueContentV2.from(valueResource, _))
          case TextValue                   => TextValueContentV2.from(valueResource).provide(ZLayer.succeed(messageRelay))
          case TextFileValue               => withFileInfo(i, TextFileValueContentV2.from(valueResource, _))
          case TimeValue                   => ZIO.fromEither(TimeValueContentV2.from(valueResource))
          case UriValue                    => ZIO.fromEither(UriValueContentV2.from(valueResource))
          case unsupported                 => ZIO.fail(s"Unsupported value type: $unsupported")
    } yield content

  def createMappingRequestMetadataV2(jsonlLd: String): IO[String, CreateMappingRequestMetadataV2] =
    def findSingle[A](m: Model, p: Property, mapper: Resource => Property => Either[String, A]): IO[String, A] = {
      m.listSubjectsWithProperty(p).asScala.toList match {
        case Nil      => ZIO.fail(s"No $p found")
        case r :: Nil => ZIO.succeed(r)
        case _        => ZIO.fail(s"Multiple $p found")
      }
    }.map(r => mapper.apply(r)(p)).flatMap(ZIO.fromEither)
    ZIO.scoped {
      for {
        m           <- ModelOps.fromJsonLd(jsonlLd).logError
        label       <- findSingle(m, RDFS.label, _.objectString)
        projectIri  <- findSingle(m, KA.AttachedToProject, r => r.objectUri(_, ProjectIri.from))
        mappingName <- findSingle(m, KA.MappingHasName, _.objectString)
      } yield CreateMappingRequestMetadataV2(label, projectIri, mappingName)
    }
}

object ApiComplexV2JsonLdRequestParser {
  val layer = ZLayer.derive[ApiComplexV2JsonLdRequestParser]
}
