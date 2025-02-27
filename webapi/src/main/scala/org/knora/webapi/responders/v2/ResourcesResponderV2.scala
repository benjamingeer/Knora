/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import com.typesafe.scalalogging.LazyLogging
import zio.*
import zio.Task
import zio.ZIO

import java.time.Instant
import java.util.UUID

import dsp.errors.*
import dsp.valueobjects.Iri
import dsp.valueobjects.UuidUtil
import org.knora.webapi.*
import org.knora.webapi.SchemaRendering.apiV2SchemaWithOption
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageHandler
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.*
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.store.sipimessages.SipiGetTextFileRequest
import org.knora.webapi.messages.store.sipimessages.SipiGetTextFileResponse
import org.knora.webapi.messages.twirl.queries.sparql
import org.knora.webapi.messages.util.*
import org.knora.webapi.messages.util.ConstructResponseUtilV2.MappingAndXSLTransformation
import org.knora.webapi.messages.util.rdf.*
import org.knora.webapi.messages.util.search.gravsearch.GravsearchParser
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.resourcemessages.*
import org.knora.webapi.messages.v2.responder.standoffmessages.GetMappingRequestV2
import org.knora.webapi.messages.v2.responder.standoffmessages.GetMappingResponseV2
import org.knora.webapi.messages.v2.responder.standoffmessages.GetXSLTransformationRequestV2
import org.knora.webapi.messages.v2.responder.standoffmessages.GetXSLTransformationResponseV2
import org.knora.webapi.messages.v2.responder.valuemessages.*
import org.knora.webapi.responders.IriLocker
import org.knora.webapi.responders.IriService
import org.knora.webapi.responders.Responder
import org.knora.webapi.responders.admin.PermissionsResponder
import org.knora.webapi.responders.v2.resources.CreateResourceV2Handler
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.LegalInfoService
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.ontology.domain.service.OntologyService
import org.knora.webapi.slice.resources.api.model.GraphDirection
import org.knora.webapi.slice.resources.api.model.VersionDate
import org.knora.webapi.slice.resources.repo.service.ResourcesRepo
import org.knora.webapi.store.iiif.errors.SipiException
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update
import org.knora.webapi.util.FileUtil

trait GetResources {
  def getResourcesV2(
    resourceIris: Seq[IRI],
    propertyIri: Option[SmartIri] = None,
    valueUuid: Option[UUID] = None,
    versionDate: Option[Instant] = None,
    withDeleted: Boolean = true,
    showDeletedValues: Boolean = false,
    targetSchema: ApiV2Schema,
    schemaOptions: Set[Rendering],
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2]

  def getResourcePreviewV2(
    resourceIris: Seq[IRI],
    withDeleted: Boolean = true,
    targetSchema: ApiV2Schema,
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2]
}

final case class ResourcesResponderV2(
  appConfig: AppConfig,
  iriService: IriService,
  messageRelay: MessageRelay,
  triplestore: TriplestoreService,
  constructResponseUtilV2: ConstructResponseUtilV2,
  standoffTagUtilV2: StandoffTagUtilV2,
  resourceUtilV2: ResourceUtilV2,
  permissionUtilADM: PermissionUtilADM,
  knoraProjectService: KnoraProjectService,
  searchResponderV2: SearchResponderV2,
  ontologyRepo: OntologyRepo,
  permissionsResponder: PermissionsResponder,
  ontologyService: OntologyService,
  resourcesRepo: ResourcesRepo,
  legalInfoService: LegalInfoService,
)(implicit val stringFormatter: StringFormatter)
    extends MessageHandler
    with LazyLogging
    with GetResources {

  private val createHandler = CreateResourceV2Handler(
    appConfig,
    iriService,
    messageRelay,
    resourcesRepo,
    constructResponseUtilV2,
    standoffTagUtilV2,
    resourceUtilV2,
    permissionUtilADM,
    searchResponderV2,
    this,
    ontologyRepo,
    permissionsResponder: PermissionsResponder,
    ontologyService,
    legalInfoService,
  )

  override def isResponsibleFor(message: ResponderRequest): Boolean =
    message.isInstanceOf[ResourcesResponderRequestV2]

  override def handle(msg: ResponderRequest): Task[Any] = msg match {
    case ResourcesGetRequestV2(
          resIris,
          propertyIri,
          valueUuid,
          versionDate,
          withDeleted,
          targetSchema,
          schemaOptions,
          requestingUser,
        ) =>
      getResourcesV2(
        resIris,
        propertyIri,
        valueUuid,
        versionDate,
        withDeleted,
        showDeletedValues = false,
        targetSchema,
        schemaOptions,
        requestingUser,
      )
    case ResourcesPreviewGetRequestV2(
          resIris,
          withDeletedResource,
          targetSchema,
          requestingUser,
        ) =>
      getResourcePreviewV2(resIris, withDeletedResource, targetSchema, requestingUser)
    case ResourceTEIGetRequestV2(
          resIri,
          textProperty,
          mappingIri,
          gravsearchTemplateIri,
          headerXSLTIri,
          requestingUser,
        ) =>
      getResourceAsTeiV2(
        resIri,
        textProperty,
        mappingIri,
        gravsearchTemplateIri,
        headerXSLTIri,
        requestingUser,
      )

    case createResourceRequestV2: CreateResourceRequestV2 =>
      createHandler(createResourceRequestV2)

    case updateResourceMetadataRequestV2: UpdateResourceMetadataRequestV2 =>
      updateResourceMetadataV2(updateResourceMetadataRequestV2)

    case deleteOrEraseResourceRequestV2: DeleteOrEraseResourceRequestV2 =>
      deleteOrEraseResourceV2(deleteOrEraseResourceRequestV2)

    case resourceHistoryRequest: ResourceVersionHistoryGetRequestV2 =>
      getResourceHistoryV2(resourceHistoryRequest)

    case other =>
      Responder.handleUnexpectedMessage(other, this.getClass.getName)
  }

  def createResource(createResource: CreateResourceRequestV2): Task[ReadResourcesSequenceV2] =
    createHandler(createResource)

  /**
   * If resource has already been modified, make sure that its lastModificationDate is given in the request body.
   * It is a conflict if the resource has been modified since the client last read it.
   * It is also conflict if the resource has a lastModificationDate but it was not provided by the client.
   *
   * @param resource The resource to be updated.
   * @param providedLastModificationDate The lastModificationDate provided by the client.
   * @return Fails with an [[EditConflictException]] if there is a conflict.
   */
  private def ensureNoConflictingChange(
    resource: ReadResourceV2,
    providedLastModificationDate: Option[Instant],
  ): IO[EditConflictException, Unit] = {
    val existingLastModificationDate = resource.lastModificationDate
    val isConflict = (
      for {
        existingDate <- existingLastModificationDate
        providedDate <- providedLastModificationDate
      } yield existingDate != providedDate
    ).getOrElse(existingLastModificationDate.nonEmpty)
    ZIO
      .fail(
        EditConflictException(
          s"Resource ${resource.resourceIri} has been modified since you last read it. Its lastModificationDate " +
            s"${existingLastModificationDate.map(_.toString).getOrElse("")} must be included in the request body.",
        ),
      )
      .when(isConflict && !appConfig.features.disableLastModificationDateCheck)
      .unit
  }

  /**
   * Updates a resources metadata.
   *
   * @param updateResourceMetadataRequestV2 the update request.
   * @return a [[UpdateResourceMetadataResponseV2]].
   */
  private def updateResourceMetadataV2(
    updateResourceMetadataRequestV2: UpdateResourceMetadataRequestV2,
  ): Task[UpdateResourceMetadataResponseV2] = {
    def makeTaskFuture: Task[UpdateResourceMetadataResponseV2] =
      for {
        // Get the metadata of the resource to be updated.
        resourcesSeq <- getResourcePreviewV2(
                          resourceIris = Seq(updateResourceMetadataRequestV2.resourceIri),
                          targetSchema = ApiV2Complex,
                          requestingUser = updateResourceMetadataRequestV2.requestingUser,
                        )

        resource: ReadResourceV2 = resourcesSeq.toResource(updateResourceMetadataRequestV2.resourceIri)
        internalResourceClassIri = updateResourceMetadataRequestV2.resourceClassIri.toOntologySchema(InternalSchema)

        // Make sure that the resource's class is what the client thinks it is.
        _ <- ZIO.when(resource.resourceClassIri != internalResourceClassIri) {
               val msg =
                 s"Resource <${resource.resourceIri}> is not a member of class <${updateResourceMetadataRequestV2.resourceClassIri}>"
               ZIO.fail(BadRequestException(msg))
             }

        _ <- ensureNoConflictingChange(resource, updateResourceMetadataRequestV2.maybeLastModificationDate)

        // Check that the user has permission to modify the resource.
        _ <- resourceUtilV2.checkResourcePermission(
               resource,
               Permission.ObjectAccess.Modify,
               updateResourceMetadataRequestV2.requestingUser,
             )

        // Get the IRI of the named graph in which the resource is stored.
        dataNamedGraph: IRI = ProjectService.projectDataNamedGraphV2(resource.projectADM).value

        newModificationDate <-
          updateResourceMetadataRequestV2.maybeNewModificationDate match {
            case Some(submittedNewModificationDate) =>
              if (resource.lastModificationDate.exists(_.isAfter(submittedNewModificationDate))) {
                val msg =
                  s"Submitted knora-api:newModificationDate is before the resource's current knora-api:lastModificationDate"
                ZIO.fail(BadRequestException(msg))
              } else {
                ZIO.succeed(submittedNewModificationDate)
              }
            case None => ZIO.succeed(Instant.now)
          }

        // Generate SPARQL for updating the resource.
        sparqlUpdate = sparql.v2.txt.changeResourceMetadata(
                         dataNamedGraph = dataNamedGraph,
                         resourceIri = updateResourceMetadataRequestV2.resourceIri,
                         resourceClassIri = internalResourceClassIri,
                         maybeLastModificationDate = updateResourceMetadataRequestV2.maybeLastModificationDate,
                         newModificationDate = newModificationDate,
                         maybeLabel = updateResourceMetadataRequestV2.maybeLabel,
                         maybePermissions = updateResourceMetadataRequestV2.maybePermissions,
                       )
        // Do the update.
        _ <- triplestore.query(Update(sparqlUpdate))

        // Verify that the resource was updated correctly.

        updatedResourcesSeq <-
          getResourcePreviewV2(
            resourceIris = Seq(updateResourceMetadataRequestV2.resourceIri),
            targetSchema = ApiV2Complex,
            requestingUser = updateResourceMetadataRequestV2.requestingUser,
          )

        _ <- ZIO.when(updatedResourcesSeq.resources.size != 1) {
               ZIO.fail(AssertionException(s"Expected one resource, got ${resourcesSeq.resources.size}"))
             }

        updatedResource: ReadResourceV2 = updatedResourcesSeq.resources.head

        _ <- ZIO.when(!updatedResource.lastModificationDate.contains(newModificationDate)) {
               val msg =
                 s"Updated resource has last modification date ${updatedResource.lastModificationDate}, expected $newModificationDate"
               ZIO.fail(UpdateNotPerformedException(msg))
             }

        _ <- updateResourceMetadataRequestV2.maybeLabel match {
               case Some(newLabel) if !updatedResource.label.contains(Iri.fromSparqlEncodedString(newLabel)) =>
                 ZIO.fail(UpdateNotPerformedException())
               case _ => ZIO.unit
             }

        _ <- updateResourceMetadataRequestV2.maybePermissions match {
               case Some(newPermissions)
                   if PermissionUtilADM.parsePermissions(updatedResource.permissions) != PermissionUtilADM
                     .parsePermissions(newPermissions) =>
                 ZIO.fail(UpdateNotPerformedException())
               case _ => ZIO.unit
             }

      } yield UpdateResourceMetadataResponseV2(
        resourceIri = updateResourceMetadataRequestV2.resourceIri,
        resourceClassIri = updateResourceMetadataRequestV2.resourceClassIri,
        maybeLabel = updateResourceMetadataRequestV2.maybeLabel,
        maybePermissions = updateResourceMetadataRequestV2.maybePermissions,
        lastModificationDate = newModificationDate,
      )

    for {
      // Do the remaining pre-update checks and the update while holding an update lock on the resource.
      taskResult <- IriLocker.runWithIriLock(
                      updateResourceMetadataRequestV2.apiRequestID,
                      updateResourceMetadataRequestV2.resourceIri,
                      makeTaskFuture,
                    )
    } yield taskResult
  }

  /**
   * Either marks a resource as deleted or erases it from the triplestore, depending on the value of `erase`
   * in the request message.
   *
   * @param deleteOrEraseResourceV2 the request message.
   */
  private def deleteOrEraseResourceV2(
    deleteOrEraseResourceV2: DeleteOrEraseResourceRequestV2,
  ): Task[SuccessResponseV2] =
    if (deleteOrEraseResourceV2.erase) {
      eraseResourceV2(deleteOrEraseResourceV2)
    } else {
      markResourceAsDeletedV2(deleteOrEraseResourceV2)
    }

  /**
   * Marks a resource as deleted.
   *
   * @param deleteResourceV2 the request message.
   */
  private def markResourceAsDeletedV2(deleteResourceV2: DeleteOrEraseResourceRequestV2): Task[SuccessResponseV2] = {
    def deleteTask(): Task[SuccessResponseV2] =
      for {
        // Get the metadata of the resource to be updated.
        resourcesSeq <- getResourcePreviewV2(
                          resourceIris = Seq(deleteResourceV2.resourceIri),
                          targetSchema = ApiV2Complex,
                          requestingUser = deleteResourceV2.requestingUser,
                        )

        resource: ReadResourceV2 = resourcesSeq.toResource(deleteResourceV2.resourceIri)
        internalResourceClassIri = deleteResourceV2.resourceClassIri.toOntologySchema(InternalSchema)

        // Make sure that the resource's class is what the client thinks it is.
        _ <- ZIO.when(resource.resourceClassIri != internalResourceClassIri) {
               val msg =
                 s"Resource <${resource.resourceIri}> is not a member of class <${deleteResourceV2.resourceClassIri}>"
               ZIO.fail(BadRequestException(msg))
             }

        _ <- ensureNoConflictingChange(resource, deleteResourceV2.maybeLastModificationDate)

        // If a custom delete date was provided, make sure it's later than the resource's most recent timestamp.
        _ <- ZIO.when(
               deleteResourceV2.maybeDeleteDate.exists(
                 !_.isAfter(resource.lastModificationDate.getOrElse(resource.creationDate)),
               ),
             ) {
               val msg =
                 s"A custom delete date must be later than the date when the resource was created or last modified"
               ZIO.fail(BadRequestException(msg))
             }

        // Check that the user has permission to mark the resource as deleted.
        _ <- resourceUtilV2.checkResourcePermission(
               resource,
               Permission.ObjectAccess.Delete,
               deleteResourceV2.requestingUser,
             )

        // Get the IRI of the named graph in which the resource is stored.
        dataNamedGraph = ProjectService.projectDataNamedGraphV2(resource.projectADM).value

        // Generate SPARQL for marking the resource as deleted.
        sparqlUpdate = sparql.v2.txt.deleteResource(
                         dataNamedGraph = dataNamedGraph,
                         resourceIri = deleteResourceV2.resourceIri,
                         maybeDeleteComment = deleteResourceV2.maybeDeleteComment,
                         currentTime = deleteResourceV2.maybeDeleteDate.getOrElse(Instant.now),
                         requestingUser = deleteResourceV2.requestingUser.id,
                       )
        // Do the update.
        _ <- triplestore.query(Update(sparqlUpdate))

        // Verify that the resource was deleted correctly.
        sparqlSelectResponse <-
          triplestore.query(Select(sparql.v2.txt.checkResourceDeletion(deleteResourceV2.resourceIri)))

        rows = sparqlSelectResponse.results.bindings

        _ <-
          ZIO.when(
            rows.isEmpty || !ValuesValidator.optionStringToBoolean(rows.head.rowMap.get("isDeleted"), fallback = false),
          ) {
            val msg =
              s"Resource <${deleteResourceV2.resourceIri}> was not marked as deleted. Please report this as a possible bug."
            ZIO.fail(UpdateNotPerformedException(msg))
          }
      } yield SuccessResponseV2("Resource marked as deleted")

    ZIO.when(deleteResourceV2.erase)(ZIO.fail(AssertionException(s"Request message has erase == true"))) *>
      IriLocker.runWithIriLock(deleteResourceV2.apiRequestID, deleteResourceV2.resourceIri, deleteTask())
  }

  /**
   * Erases a resource from the triplestore.
   *
   * @param eraseResourceV2 the request message.
   */
  private def eraseResourceV2(eraseResourceV2: DeleteOrEraseResourceRequestV2): Task[SuccessResponseV2] = {
    def eraseTask: Task[SuccessResponseV2] =
      for {
        // Get the metadata of the resource to be updated.
        resourcesSeq <- getResourcePreviewV2(
                          resourceIris = Seq(eraseResourceV2.resourceIri),
                          targetSchema = ApiV2Complex,
                          requestingUser = eraseResourceV2.requestingUser,
                        )

        resource: ReadResourceV2 = resourcesSeq.toResource(eraseResourceV2.resourceIri)

        // Ensure that the requesting user is a system admin, or an admin of this project.
        _ <- ZIO.when(
               !(eraseResourceV2.requestingUser.permissions.isProjectAdmin(resource.projectADM.id.value) ||
                 eraseResourceV2.requestingUser.permissions.isSystemAdmin),
             ) {
               ZIO.fail(ForbiddenException(s"Only a system admin or project admin can erase a resource"))
             }

        internalResourceClassIri = eraseResourceV2.resourceClassIri.toOntologySchema(InternalSchema)

        // Make sure that the resource's class is what the client thinks it is.
        _ <- ZIO.when(resource.resourceClassIri != internalResourceClassIri) {
               val msg =
                 s"Resource <${resource.resourceIri}> is not a member of class <${eraseResourceV2.resourceClassIri}>"
               ZIO.fail(BadRequestException(msg))
             }

        _ <- ensureNoConflictingChange(resource, eraseResourceV2.maybeLastModificationDate)

        // Check that the resource is not referred to by any other resources. We ignore rdf:subject (so we
        // can erase the resource's own links) and rdf:object (in case there is a deleted link value that
        // refers to it). Any such deleted link values will be erased along with the resource. If there
        // is a non-deleted link to the resource, the direct link (rather than the link value) will case
        // isEntityUsed() to fail with an exception.

        resourceSmartIri = eraseResourceV2.resourceIri.toSmartIri

        _ <- ZIO
               .whenZIO(iriService.isEntityUsed(resourceSmartIri, ignoreRdfSubjectAndObject = true)) {
                 val msg =
                   s"Resource ${eraseResourceV2.resourceIri} cannot be erased, because it is referred to by another resource"
                 ZIO.fail(BadRequestException(msg))
               }

        // Get the IRI of the named graph from which the resource will be erased.
        dataNamedGraph = ProjectService.projectDataNamedGraphV2(resource.projectADM).value

        // Do the update.
        _ <- triplestore.query(Update(sparql.v2.txt.eraseResource(dataNamedGraph, eraseResourceV2.resourceIri)))

        _ <- // Verify that the resource was erased correctly.
          ZIO
            .fail(
              UpdateNotPerformedException(
                s"Resource <${eraseResourceV2.resourceIri}> was not erased. Please report this as a possible bug.",
              ),
            )
            .whenZIO(iriService.checkIriExists(resourceSmartIri.toString))
      } yield SuccessResponseV2("Resource erased")

    for {
      _          <- ZIO.when(!eraseResourceV2.erase)(ZIO.fail(AssertionException(s"Request message has erase == false")))
      taskResult <- IriLocker.runWithIriLock(eraseResourceV2.apiRequestID, eraseResourceV2.resourceIri, eraseTask)
    } yield taskResult
  }

  /**
   * Gets the requested resources from the triplestore.
   *
   * @param resourceIris         the Iris of the requested resources.
   * @param preview              `true` if a preview of the resource is requested.
   * @param withDeleted          if defined, indicates if the deleted resources and values should be returned or not.
   * @param propertyIri          if defined, requests only the values of the specified explicit property.
   * @param valueUuid            if defined, requests only the value with the specified UUID.
   * @param versionDate          if defined, requests the state of the resources at the specified time in the past.
   *                             Cannot be used in conjunction with `preview`.
   * @param queryStandoff        `true` if standoff should be queried.
   *
   * @return a map of resource IRIs to RDF data.
   */
  private def getResourcesFromTriplestore(
    resourceIris: Seq[IRI],
    preview: Boolean,
    withDeleted: Boolean,
    propertyIri: Option[SmartIri] = None,
    valueUuid: Option[UUID] = None,
    versionDate: Option[Instant] = None,
    queryStandoff: Boolean,
    requestingUser: User,
  ): Task[ConstructResponseUtilV2.MainResourcesAndValueRdfData] = {
    val query =
      Construct(
        sparql.v2.txt
          .getResourcePropertiesAndValues(
            resourceIris = resourceIris.distinct,
            preview = preview,
            withDeleted = withDeleted,
            maybePropertyIri = propertyIri,
            maybeValueUuid = valueUuid,
            maybeVersionDate = versionDate,
            queryAllNonStandoff = true,
            queryStandoff = queryStandoff,
          ),
      )

    triplestore
      .query(query)
      .flatMap(_.asExtended)
      .map(constructResponseUtilV2.splitMainResourcesAndValueRdfData(_, requestingUser))
  }

  /**
   * Get one or several resources and return them as a sequence.
   *
   * @param resourceIris         the IRIs of the resources to be queried.
   * @param propertyIri          if defined, requests only the values of the specified explicit property.
   * @param valueUuid            if defined, requests only the value with the specified UUID.
   * @param versionDate          if defined, requests the state of the resources at the specified time in the past.
   * @param withDeleted          if defined, indicates if the deleted resource and values should be returned or not.
   * @param targetSchema         the target API schema.
   * @param schemaOptions        the schema options submitted with the request.
   *
   * @param requestingUser       the user making the request.
   * @return a [[ReadResourcesSequenceV2]].
   */
  def getResourcesV2(
    resourceIris: Seq[IRI],
    propertyIri: Option[SmartIri] = None,
    valueUuid: Option[UUID] = None,
    versionDate: Option[Instant] = None,
    withDeleted: Boolean = true,
    showDeletedValues: Boolean = false,
    targetSchema: ApiV2Schema,
    schemaOptions: Set[Rendering],
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2] = {
    // eliminate duplicate Iris
    val resourceIrisDistinct: Seq[IRI] = resourceIris.distinct

    // Find out whether to query standoff along with text values. This boolean value will be passed to
    // ConstructResponseUtilV2.makeTextValueContentV2.
    val queryStandoff: Boolean =
      SchemaOptions.queryStandoffWithTextValues(targetSchema = targetSchema, schemaOptions = schemaOptions)

    for {

      mainResourcesAndValueRdfData <-
        getResourcesFromTriplestore(
          resourceIris = resourceIris,
          preview = false,
          withDeleted = withDeleted,
          propertyIri = propertyIri,
          valueUuid = valueUuid,
          versionDate = versionDate,
          queryStandoff = queryStandoff,
          requestingUser = requestingUser,
        )
      mappingsAsMap <-
        if (queryStandoff) {
          constructResponseUtilV2.getMappingsFromQueryResultsSeparated(
            mainResourcesAndValueRdfData.resources,
            requestingUser,
          )
        } else {
          ZIO.succeed(Map.empty[IRI, MappingAndXSLTransformation])
        }

      apiResponse <-
        constructResponseUtilV2.createApiResponse(
          mainResourcesAndValueRdfData = mainResourcesAndValueRdfData,
          orderByResourceIri = resourceIrisDistinct,
          pageSizeBeforeFiltering = resourceIris.size, // doesn't matter because we're not doing paging
          mappings = mappingsAsMap,
          queryStandoff = queryStandoff,
          versionDate = versionDate,
          calculateMayHaveMoreResults = false,
          targetSchema = targetSchema,
          requestingUser = requestingUser,
        )

      _ <- apiResponse.checkResourceIris(resourceIris.toSet, apiResponse)

      _ <- valueUuid match {
             case Some(definedValueUuid) =>
               ZIO.unless(
                 apiResponse.resources.exists(_.values.values.exists(_.exists(_.valueHasUUID == definedValueUuid))),
               ) {
                 val msg =
                   s"Value with UUID ${UuidUtil.base64Encode(definedValueUuid)} not found (maybe you do not have permission to see it)"
                 ZIO.fail(NotFoundException(msg))
               }
             case None => ZIO.unit
           }

      // Check if resources are deleted, if so, replace them with DeletedResource
      deletedResourcesReplaced =
        apiResponse.resources.map { case resource =>
          resource.deletionInfo match {
            // Resource deleted -> return DeletedResource instead
            case Some(_) => resource.asDeletedResource()
            // Resource not deleted -> return resource
            case None =>
              // deleted values should be shown -> resource can be returned
              if (showDeletedValues) resource
              // deleted Values should not be shown -> replace them with generic DeletedValue
              else resource.withDeletedValues()
          }
        }
      responseWithDeletedResourcesReplaced = apiResponse.copy(resources = deletedResourcesReplaced)
    } yield responseWithDeletedResourcesReplaced
  }

  /**
   * Get the preview of a resource.
   *
   * @param resourceIris         the resource to query for.
   * @param withDeleted          indicates if the deleted resource should be returned or not.
   *
   * @param requestingUser       the the user making the request.
   * @return a [[ReadResourcesSequenceV2]].
   */
  def getResourcePreviewV2(
    resourceIris: Seq[IRI],
    withDeleted: Boolean = true,
    targetSchema: ApiV2Schema,
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2] = {

    // eliminate duplicate Iris
    val resourceIrisDistinct: Seq[IRI] = resourceIris.distinct

    for {
      mainResourcesAndValueRdfData <- getResourcesFromTriplestore(
                                        resourceIris = resourceIris,
                                        preview = true,
                                        withDeleted = withDeleted,
                                        queryStandoff =
                                          false, // This has no effect, because we are not querying values.
                                        requestingUser = requestingUser,
                                      )

      apiResponse <- constructResponseUtilV2.createApiResponse(
                       mainResourcesAndValueRdfData = mainResourcesAndValueRdfData,
                       orderByResourceIri = resourceIrisDistinct,
                       pageSizeBeforeFiltering = resourceIris.size, // doesn't matter because we're not doing paging
                       mappings = Map.empty[IRI, MappingAndXSLTransformation],
                       queryStandoff = false,
                       versionDate = None,
                       calculateMayHaveMoreResults = false,
                       targetSchema = targetSchema,
                       requestingUser = requestingUser,
                     )

      _ <- apiResponse.checkResourceIris(resourceIris.toSet, apiResponse)

      // Check if resources are deleted, if so, replace them with DeletedResource
      responseWithDeletedResourcesReplaced = apiResponse.resources match {
                                               case resourceList =>
                                                 if (resourceList.nonEmpty) {
                                                   val resourceListWithDeletedResourcesReplaced = resourceList.map {
                                                     resource =>
                                                       resource.deletionInfo match {
                                                         case Some(_) => resource.asDeletedResource()
                                                         case None    => resource.withDeletedValues()
                                                       }
                                                   }
                                                   apiResponse.copy(resources =
                                                     resourceListWithDeletedResourcesReplaced,
                                                   )
                                                 } else {
                                                   apiResponse
                                                 }
                                             }

    } yield responseWithDeletedResourcesReplaced
  }

  /**
   * Obtains a Gravsearch template from Sipi.
   *
   * @param gravsearchTemplateIri the Iri of the resource representing the Gravsearch template.
   *
   * @param requestingUser        the user making the request.
   * @return the Gravsearch template.
   */
  private def getGravsearchTemplate(
    gravsearchTemplateIri: IRI,
    requestingUser: User,
  ): Task[String] = {

    val gravsearchUrlTask = for {
      resources <- getResourcesV2(
                     resourceIris = Vector(gravsearchTemplateIri),
                     targetSchema = ApiV2Complex,
                     schemaOptions = Set(MarkupRendering.Standoff),
                     requestingUser = requestingUser,
                   )

      resource: ReadResourceV2 = resources.toResource(gravsearchTemplateIri)

      _ <- ZIO.when(resource.resourceClassIri.toString != OntologyConstants.KnoraBase.TextRepresentation) {
             val msg = s"Resource $gravsearchTemplateIri is not a Gravsearch template (text file expected)"
             ZIO.fail(BadRequestException(msg))
           }

      valueAndContent <-
        resource.values.get(OntologyConstants.KnoraBase.HasTextFileValue.toSmartIri) match {
          case Some(singleValue :: Nil) =>
            singleValue match {
              case value: ReadValueV2 =>
                value.valueContent match {
                  case textRepr: TextFileValueContentV2 => ZIO.succeed((value.valueIri, textRepr))
                  case _ =>
                    val msg =
                      s"Resource $gravsearchTemplateIri is supposed to have exactly one value of type ${OntologyConstants.KnoraBase.TextFileValue}"
                    ZIO.fail(InconsistentRepositoryDataException(msg))
                }
            }

          case _ =>
            val msg = s"Resource $gravsearchTemplateIri has no property ${OntologyConstants.KnoraBase.HasTextFileValue}"
            ZIO.fail(InconsistentRepositoryDataException(msg))
        }
      (fileValueIri, gravsearchFileValueContent) = valueAndContent

      // check if gravsearchFileValueContent represents a text file
      _ <- ZIO.when(gravsearchFileValueContent.fileValue.internalMimeType != "text/plain") {
             val msg =
               s"Expected $fileValueIri to be a text file referring to a Gravsearch template, but it has MIME type ${gravsearchFileValueContent.fileValue.internalMimeType}"
             ZIO.fail(BadRequestException(msg))
           }

      gravsearchUrl: String =
        s"${appConfig.sipi.internalBaseUrl}/${resource.projectADM.shortcode}/${gravsearchFileValueContent.fileValue.internalFilename}/file"
    } yield gravsearchUrl

    val recoveredGravsearchUrlTask = gravsearchUrlTask.mapError { case notFound: NotFoundException =>
      BadRequestException(s"Gravsearch template $gravsearchTemplateIri not found: ${notFound.message}")
    }

    for {
      gravsearchTemplateUrl <- recoveredGravsearchUrlTask
      response <- messageRelay
                    .ask[SipiGetTextFileResponse](
                      SipiGetTextFileRequest(
                        fileUrl = gravsearchTemplateUrl,
                        requestingUser = KnoraSystemInstances.Users.SystemUser,
                        senderName = this.getClass.getName,
                      ),
                    )
      gravsearchTemplate: String = response.content

    } yield gravsearchTemplate

  }

  /**
   * Returns a resource as TEI/XML.
   * This makes only sense for resources that have a text value containing standoff that is to be converted to the TEI body.
   *
   * @param resourceIri           the Iri of the resource to be converted to a TEI document (header and body).
   * @param textProperty          the Iri of the property (text value with standoff) to be converted to the body of the TEI document.
   * @param mappingIri            the Iri of the mapping to be used to convert standoff to XML, if a custom mapping is provided. The mapping is expected to contain an XSL transformation.
   * @param gravsearchTemplateIri the Iri of the Gravsearch template to query for the metadata for the TEI header. The resource Iri is expected to be represented by the placeholder '$resourceIri' in a BIND.
   * @param headerXSLTIri         the Iri of the XSL template to convert the metadata properties to the TEI header.
   *
   * @param requestingUser        the user making the request.
   * @return a [[ResourceTEIGetResponseV2]].
   */
  private def getResourceAsTeiV2(
    resourceIri: IRI,
    textProperty: SmartIri,
    mappingIri: Option[IRI],
    gravsearchTemplateIri: Option[IRI],
    headerXSLTIri: Option[String],
    requestingUser: User,
  ): Task[ResourceTEIGetResponseV2] = {

    /**
     * Extract the text value to be converted to TEI/XML.
     *
     * @param readResource the resource which is expected to hold the text value.
     * @return a [[TextValueContentV2]] representing the text value to be converted to TEI/XML.
     */
    def getTextValueFromReadResource(readResource: ReadResourceV2): Task[TextValueContentV2] =
      readResource.values.get(textProperty) match {
        case Some(valObjs: Seq[ReadValueV2]) if valObjs.size == 1 =>
          // make sure that the property has one instance and that it is of type TextValue and that is has standoff (markup)
          valObjs.head.valueContent match {
            case textValWithStandoff: TextValueContentV2 if textValWithStandoff.standoff.nonEmpty =>
              ZIO.succeed(textValWithStandoff)
            case _ =>
              val msg = s"$textProperty to be of type ${OntologyConstants.KnoraBase.TextValue} with standoff (markup)"
              ZIO.fail(BadRequestException(msg))
          }
        case _ => ZIO.fail(BadRequestException(s"$textProperty is expected to occur once on $resourceIri"))
      }

    /**
     * Given a resource's values, convert the date values to Gregorian.
     *
     * @param values the values to be processed.
     * @return the resource's values with date values converted to Gregorian.
     */
    def convertDateToGregorian(values: Map[SmartIri, Seq[ReadValueV2]]): Map[SmartIri, Seq[ReadValueV2]] =
      values.map { case (propIri: SmartIri, valueObjs: Seq[ReadValueV2]) =>
        propIri -> valueObjs.map {
          case valueObj @ (readNonLinkValueV2: ReadOtherValueV2) =>
            readNonLinkValueV2.valueContent match {
              case dateContent: DateValueContentV2 =>
                // date value
                readNonLinkValueV2.copy(
                  valueContent = dateContent.copy(
                    // act as if this was a Gregorian date
                    valueHasCalendar = CalendarNameGregorian,
                  ),
                )
              case _ => valueObj
            }

          case readLinkValueV2: ReadLinkValueV2 if readLinkValueV2.valueContent.nestedResource.nonEmpty =>
            // recursively process the values of the nested resource

            val linkContent = readLinkValueV2.valueContent

            readLinkValueV2.copy(
              valueContent = linkContent.copy(
                nestedResource = Some(
                  linkContent.nestedResource.get.copy(
                    // recursive call
                    values = convertDateToGregorian(linkContent.nestedResource.get.values),
                  ),
                ),
              ),
            )

          case valueObj => valueObj
        }
      }

    for {

      // get the requested resource
      resource <-
        if (gravsearchTemplateIri.nonEmpty) {
          for {
            templateIri <-
              ZIO.fromOption(gravsearchTemplateIri).orDieWith(_ => new Exception("cannot happen, checked nonEmpty"))
            // check that there is an XSLT to create the TEI header
            _ <- ZIO.when(headerXSLTIri.isEmpty) {
                   val msg = s"When a Gravsearch template Iri is provided, also a header XSLT Iri has to be provided."
                   ZIO.fail(BadRequestException(msg))
                 }
            // get the template
            query <- getGravsearchTemplate(templateIri, requestingUser)
                       .map(_.replace("$resourceIri", resourceIri))
                       .mapAttempt(GravsearchParser.parseQuery)

            resource <- searchResponderV2
                          .gravsearchV2(query, apiV2SchemaWithOption(MarkupRendering.Xml), requestingUser)
                          .mapAttempt(_.toResource(resourceIri))
          } yield resource

        } else {
          // no Gravsearch template is provided

          for {
            // check that there is no XSLT for the header since there is no Gravsearch template
            _ <- ZIO.when(headerXSLTIri.nonEmpty) {
                   val msg =
                     s"When no Gravsearch template Iri is provided, no header XSLT Iri is expected to be provided either."
                   ZIO.fail(BadRequestException(msg))
                 }

            // get requested resource
            resource <- getResourcesV2(
                          resourceIris = Vector(resourceIri),
                          targetSchema = ApiV2Complex,
                          schemaOptions = SchemaOptions.ForStandoffWithTextValues,
                          requestingUser = requestingUser,
                        ).mapAttempt(_.toResource(resourceIri))
          } yield resource
        }

      // get the value object representing the text value that is to be mapped to the body of the TEI document
      bodyTextValue <- getTextValueFromReadResource(resource)

      // the ext value is expected to have standoff markup
      _ <-
        ZIO.when(bodyTextValue.standoff.isEmpty)(
          ZIO.fail(BadRequestException(s"Property $textProperty of $resourceIri is expected to have standoff markup")),
        )

      // get all the metadata but the text property for the TEI header
      headerResource = resource.copy(values = convertDateToGregorian(resource.values - textProperty))

      // get the XSL transformation for the TEI header
      headerXSLT <- headerXSLTIri match {
                      case Some(headerIri) =>
                        messageRelay
                          .ask[GetXSLTransformationResponseV2](GetXSLTransformationRequestV2(headerIri, requestingUser))
                          .mapBoth(
                            { case e: NotFoundException =>
                              SipiException(s"TEI header XSL transformation <$headerIri> not found: ${e.message}")
                            },
                            resp => Some(resp.xslt),
                          )
                      case _ => ZIO.none
                    }

      // get the Iri of the mapping to convert standoff markup to TEI/XML
      mappingToBeApplied = mappingIri.getOrElse(OntologyConstants.KnoraBase.TEIMapping)

      // get mapping to convert standoff markup to TEI/XML
      teiMapping <- messageRelay.ask[GetMappingResponseV2](GetMappingRequestV2(mappingToBeApplied))

      // get XSLT from mapping for the TEI body
      bodyXslt <- teiMapping.mappingIri match {
                    case OntologyConstants.KnoraBase.TEIMapping =>
                      // standard standoff to TEI conversion

                      // use standard XSLT (built-in)
                      val teiXSLTFile: String = FileUtil.readTextResource("standoffToTEI.xsl")

                      // return the file's content
                      ZIO.attempt(teiXSLTFile)

                    case otherMapping =>
                      teiMapping.mapping.defaultXSLTransformation match {
                        // custom standoff to TEI conversion

                        case Some(xslTransformationIri) =>
                          // get XSLT for the TEI body.
                          messageRelay
                            .ask[GetXSLTransformationResponseV2](
                              GetXSLTransformationRequestV2(xslTransformationIri, requestingUser),
                            )
                            .mapBoth(
                              { case notFound: NotFoundException =>
                                val msg =
                                  s"Default XSL transformation <${teiMapping.mapping.defaultXSLTransformation.get}> not found for mapping <${teiMapping.mappingIri}>: ${notFound.message}"
                                SipiException(msg)
                              },
                              _.xslt,
                            )
                        case None =>
                          val msg = s"Default XSL Transformation expected for mapping $otherMapping"
                          ZIO.fail(BadRequestException(msg))
                      }
                  }

      tei = ResourceTEIGetResponseV2(
              header = TEIHeader(
                headerInfo = headerResource,
                headerXSLT = headerXSLT,
                appConfig = appConfig,
              ),
              body = TEIBody(
                bodyInfo = bodyTextValue,
                bodyXSLT = bodyXslt,
                teiMapping = teiMapping.mapping,
              ),
            )

    } yield tei
  }

  /**
   * Gets a graph of resources that are reachable via links to or from a given resource.
   *
   * @return a [[GraphDataGetResponseV2]] representing the requested graph.
   */
  def getGraphDataResponseV2(
    resourceIri: IRI,
    depth: Int,
    dir: GraphDirection,
    excludeProperty: Option[SmartIri],
    requestingUser: User,
  ): Task[GraphDataGetResponseV2] = {
    val excludePropertyInternal = excludeProperty.map(_.toOntologySchema(InternalSchema))

    /**
     * The internal representation of a node returned by a SPARQL query generated by the `getGraphData` template.
     *
     * @param nodeIri         the IRI of the node.
     * @param nodeClass       the IRI of the node's class.
     * @param nodeLabel       the node's label.
     * @param nodeCreator     the node's creator.
     * @param nodeProject     the node's project.
     * @param nodePermissions the node's permissions.
     */
    case class QueryResultNode(
      nodeIri: IRI,
      nodeClass: SmartIri,
      nodeLabel: String,
      nodeCreator: IRI,
      nodeProject: IRI,
      nodePermissions: String,
    )

    /**
     * The internal representation of an edge returned by a SPARQL query generated by the `getGraphData` template.
     *
     * @param linkValueIri         the IRI of the link value.
     * @param sourceNodeIri        the IRI of the source node.
     * @param targetNodeIri        the IRI of the target node.
     * @param linkProp             the IRI of the link property.
     * @param linkValueCreator     the link value's creator.
     * @param sourceNodeProject    the project of the source node.
     * @param linkValuePermissions the link value's permissions.
     */
    case class QueryResultEdge(
      linkValueIri: IRI,
      sourceNodeIri: IRI,
      targetNodeIri: IRI,
      linkProp: SmartIri,
      linkValueCreator: IRI,
      sourceNodeProject: IRI,
      linkValuePermissions: String,
    )

    /**
     * Represents results returned by a SPARQL query generated by the `getGraphData` template.
     *
     * @param nodes the nodes that were returned by the query.
     * @param edges the edges that were returned by the query.
     */
    case class GraphQueryResults(
      nodes: Set[QueryResultNode] = Set.empty[QueryResultNode],
      edges: Set[QueryResultEdge] = Set.empty[QueryResultEdge],
    )

    /**
     * Recursively queries outbound or inbound links from/to a resource.
     *
     * @param startNode      the node to use as the starting point of the query. The user is assumed to have permission
     *                       to see this node.
     * @param outbound       `true` to get outbound links, `false` to get inbound links.
     * @param depth          the maximum depth of the query.
     * @param traversedEdges edges that have already been traversed.
     * @return a [[GraphQueryResults]].
     */
    def traverseGraph(
      startNode: QueryResultNode,
      outbound: Boolean,
      depth: Int,
      traversedEdges: Set[QueryResultEdge] = Set.empty[QueryResultEdge],
    ): Task[GraphQueryResults] = ZIO.fail(AssertionException("Depth must be at least 1")).when(depth < 1) *> {
      for {
        // Get the direct links from/to the start node.
        response <- triplestore.query(
                      Select(
                        sparql.v2.txt
                          .getGraphData(
                            startNode.nodeIri,
                            false,
                            excludePropertyInternal,
                            outbound,
                            appConfig.v2.graphRoute.maxGraphBreadth,
                          ),
                      ),
                    )
        rows: Seq[VariableResultsRow] = response.results.bindings

        // Did we get any results?
        recursiveResults <-
          if (rows.isEmpty) {
            // No. Return nothing.
            ZIO.attempt(GraphQueryResults())
          } else {
            // Yes. Get the nodes from the query results.
            val otherNodes: Seq[QueryResultNode] = rows.map { (row: VariableResultsRow) =>
              val rowMap: Map[String, String] = row.rowMap

              QueryResultNode(
                nodeIri = rowMap("node"),
                nodeClass = rowMap("nodeClass").toSmartIri,
                nodeLabel = rowMap("nodeLabel"),
                nodeCreator = rowMap("nodeCreator"),
                nodeProject = rowMap("nodeProject"),
                nodePermissions = rowMap("nodePermissions"),
              )
            }.filter { (node: QueryResultNode) =>
              // Filter out the nodes that the user doesn't have permission to see.
              PermissionUtilADM
                .getUserPermissionADM(
                  entityCreator = node.nodeCreator,
                  entityProject = node.nodeProject,
                  entityPermissionLiteral = node.nodePermissions,
                  requestingUser = requestingUser,
                )
                .nonEmpty
            }

            // Collect the IRIs of the nodes that the user has permission to see, including the start node.
            val visibleNodeIris: Set[IRI] = otherNodes.map(_.nodeIri).toSet + startNode.nodeIri

            // Get the edges from the query results.
            val edges: Set[QueryResultEdge] = rows.map { (row: VariableResultsRow) =>
              val rowMap: Map[String, String] = row.rowMap
              val nodeIri: IRI                = rowMap("node")

              // The SPARQL query takes a start node and returns the other node in the edge.
              //
              // If we're querying outbound edges, the start node is the source node, and the other
              // node is the target node.
              //
              // If we're querying inbound edges, the start node is the target node, and the other
              // node is the source node.

              QueryResultEdge(
                linkValueIri = rowMap("linkValue"),
                sourceNodeIri = if (outbound) startNode.nodeIri else nodeIri,
                targetNodeIri = if (outbound) nodeIri else startNode.nodeIri,
                linkProp = rowMap("linkProp").toSmartIri,
                linkValueCreator = rowMap("linkValueCreator"),
                sourceNodeProject = if (outbound) startNode.nodeProject else rowMap("nodeProject"),
                linkValuePermissions = rowMap("linkValuePermissions"),
              )
            }.filter { (edge: QueryResultEdge) =>
              // Filter out the edges that the user doesn't have permission to see. To see an edge,
              // the user must have some permission on the link value and on the source and target
              // nodes.
              val hasPermission: Boolean =
                visibleNodeIris.contains(edge.sourceNodeIri) && visibleNodeIris.contains(edge.targetNodeIri) &&
                  PermissionUtilADM
                    .getUserPermissionADM(
                      entityCreator = edge.linkValueCreator,
                      entityProject = edge.sourceNodeProject,
                      entityPermissionLiteral = edge.linkValuePermissions,
                      requestingUser = requestingUser,
                    )
                    .nonEmpty

              // Filter out edges we've already traversed.
              val isRedundant: Boolean = traversedEdges.contains(edge)
              hasPermission && !isRedundant
            }.toSet

            // Include only nodes that are reachable via edges that we're going to traverse (i.e. the user
            // has permission to see those edges, and we haven't already traversed them).
            val visibleNodeIrisFromEdges: Set[IRI] = edges.map(_.sourceNodeIri) ++ edges.map(_.targetNodeIri)
            val filteredOtherNodes: Seq[QueryResultNode] =
              otherNodes.filter(node => visibleNodeIrisFromEdges.contains(node.nodeIri))

            // Make a GraphQueryResults containing the resulting nodes and edges, including the start
            // node.
            val results = GraphQueryResults(nodes = filteredOtherNodes.toSet + startNode, edges = edges)

            // Have we reached the maximum depth?
            if (depth == 1) {
              // Yes. Just return the results we have.
              ZIO.attempt(results)
            } else {
              // No. Recursively get results for each of the nodes we found.

              val traversedEdgesForRecursion: Set[QueryResultEdge] = traversedEdges ++ edges

              val lowerResultFutures: Seq[Task[GraphQueryResults]] = filteredOtherNodes.map { node =>
                traverseGraph(
                  startNode = node,
                  outbound = outbound,
                  depth = depth - 1,
                  traversedEdges = traversedEdgesForRecursion,
                )
              }

              val lowerResultsFuture: Task[Seq[GraphQueryResults]] = ZIO.collectAll(lowerResultFutures)

              // Return those results plus the ones we found.

              lowerResultsFuture.map { (lowerResultsSeq: Seq[GraphQueryResults]) =>
                lowerResultsSeq.foldLeft(results) { case (acc: GraphQueryResults, lowerResults: GraphQueryResults) =>
                  GraphQueryResults(
                    nodes = acc.nodes ++ lowerResults.nodes,
                    edges = acc.edges ++ lowerResults.edges,
                  )
                }
              }
            }
          }
      } yield recursiveResults
    }

    // Get the start node.
    val query =
      sparql.v2.txt
        .getGraphData(
          startNodeIri = resourceIri,
          maybeExcludeLinkProperty = excludePropertyInternal,
          startNodeOnly = true,
          outbound = true,
          limit = appConfig.v2.graphRoute.maxGraphBreadth,
        )

    for {
      response                     <- triplestore.query(Select(query))
      rows: Seq[VariableResultsRow] = response.results.bindings

      _ <- ZIO.when(rows.isEmpty) {
             val msg = s"Resource <$resourceIri> not found (it may have been deleted)"
             ZIO.fail(NotFoundException(msg))
           }

      firstRowMap = rows.head.rowMap

      startNode: QueryResultNode = QueryResultNode(
                                     nodeIri = firstRowMap("node"),
                                     nodeClass = firstRowMap("nodeClass").toSmartIri,
                                     nodeLabel = firstRowMap("nodeLabel"),
                                     nodeCreator = firstRowMap("nodeCreator"),
                                     nodeProject = firstRowMap("nodeProject"),
                                     nodePermissions = firstRowMap("nodePermissions"),
                                   )

      // Make sure the user has permission to see the start node.
      _ <- ZIO.when(
             PermissionUtilADM
               .getUserPermissionADM(
                 entityCreator = startNode.nodeCreator,
                 entityProject = startNode.nodeProject,
                 entityPermissionLiteral = startNode.nodePermissions,
                 requestingUser = requestingUser,
               )
               .isEmpty,
           ) {
             val msg =
               s"User ${requestingUser.email} does not have permission to view resource <$resourceIri>"
             ZIO.fail(ForbiddenException(msg))
           }

      // Recursively get the graph containing outbound links.
      outboundQueryResults <-
        if (dir.outbound) {
          traverseGraph(
            startNode = startNode,
            outbound = true,
            depth = depth,
          )
        } else {
          ZIO.succeed(GraphQueryResults())
        }

      // Recursively get the graph containing inbound links.
      inboundQueryResults <-
        if (dir.inbound) {
          traverseGraph(
            startNode = startNode,
            outbound = false,
            depth = depth,
          )
        } else {
          ZIO.succeed(GraphQueryResults())
        }

      // Combine the outbound and inbound graphs into a single graph.
      nodes = outboundQueryResults.nodes ++ inboundQueryResults.nodes + startNode
      edges = outboundQueryResults.edges ++ inboundQueryResults.edges

      // Convert each node to a GraphNodeV2 for the API response message.
      resultNodes: Vector[GraphNodeV2] = nodes.map { (node: QueryResultNode) =>
                                           GraphNodeV2(
                                             resourceIri = node.nodeIri,
                                             resourceClassIri = node.nodeClass,
                                             resourceLabel = node.nodeLabel,
                                           )
                                         }.toVector

      // Convert each edge to a GraphEdgeV2 for the API response message.
      resultEdges: Vector[GraphEdgeV2] = edges.map { (edge: QueryResultEdge) =>
                                           GraphEdgeV2(
                                             source = edge.sourceNodeIri,
                                             propertyIri = edge.linkProp,
                                             target = edge.targetNodeIri,
                                           )
                                         }.toVector

    } yield GraphDataGetResponseV2(
      nodes = resultNodes,
      edges = resultEdges,
      ontologySchema = InternalSchema,
    )
  }

  def getResourceHistory(
    resourceIri: String,
    startDate: Option[VersionDate],
    endDate: Option[VersionDate],
    requestingUser: User,
  ): Task[ResourceVersionHistoryResponseV2] =
    getResourceHistoryV2(
      ResourceVersionHistoryGetRequestV2(
        resourceIri,
        withDeletedResource = false,
        startDate.map(_.value),
        endDate.map(_.value),
        requestingUser,
      ),
    )

  /**
   * Returns the version history of a resource.
   *
   * @param resourceHistoryRequest the version history request.
   * @return the resource's version history.
   */
  private def getResourceHistoryV2(
    resourceHistoryRequest: ResourceVersionHistoryGetRequestV2,
  ): Task[ResourceVersionHistoryResponseV2] =
    for {
      // Get the resource preview, to make sure the user has permission to see the resource, and to get
      // its creation date.
      resourcePreviewResponse <- getResourcePreviewV2(
                                   resourceIris = Seq(resourceHistoryRequest.resourceIri),
                                   withDeleted = resourceHistoryRequest.withDeletedResource,
                                   targetSchema = ApiV2Complex,
                                   requestingUser = KnoraSystemInstances.Users.SystemUser,
                                 )

      resourcePreview: ReadResourceV2 = resourcePreviewResponse.toResource(resourceHistoryRequest.resourceIri)

      // Get the version history of the resource's values.

      historyRequestSparql = sparql.v2.txt
                               .getResourceValueVersionHistory(
                                 withDeletedResource = resourceHistoryRequest.withDeletedResource,
                                 resourceIri = resourceHistoryRequest.resourceIri,
                                 maybeStartDate = resourceHistoryRequest.startDate,
                                 maybeEndDate = resourceHistoryRequest.endDate,
                               )
      valueHistoryResponse <- triplestore.query(Select(historyRequestSparql))

      valueHistoryEntries <-
        ZIO.foreach(valueHistoryResponse.results.bindings) { row =>
          val versionDateStr = row.rowMap("versionDate")
          val author         = row.rowMap("author")
          ZIO
            .fromOption(ValuesValidator.xsdDateTimeStampToInstant(versionDateStr))
            .mapBoth(
              _ => InconsistentRepositoryDataException(s"Could not parse version date: $versionDateStr"),
              versionDate => ResourceHistoryEntry(versionDate, author),
            )
        }

      // Figure out whether to add the resource's creation to the history.

      // Is there a requested start date that's after the resource's creation date?
      historyEntriesWithResourceCreation: Seq[ResourceHistoryEntry] =
        if (resourceHistoryRequest.startDate.exists(_.isAfter(resourcePreview.creationDate))) {
          // Yes. No need to add the resource's creation.
          valueHistoryEntries
        } else {
          // No. Does the value history contain the resource creation date?
          if (valueHistoryEntries.nonEmpty && valueHistoryEntries.last.versionDate == resourcePreview.creationDate) {
            // Yes. No need to add the resource's creation.
            valueHistoryEntries
          } else {
            // No. Add a history entry for it.
            valueHistoryEntries :+ ResourceHistoryEntry(
              versionDate = resourcePreview.creationDate,
              author = resourcePreview.attachedToUser,
            )
          }
        }
    } yield ResourceVersionHistoryResponseV2(
      historyEntriesWithResourceCreation,
    )

  def getIiifManifestV2(resourceIri: IRI, requestingUser: User): Task[ResourceIIIFManifestGetResponseV2] =
    // The implementation here is experimental. If we had a way of streaming the canvas URLs to the IIIF viewer,
    // it would be better to write the Gravsearch query differently, so that ?representation was the main resource.
    // Then the Gravsearch query could return pages of results.
    //
    // The manifest generated here also needs to be tested with a IIIF viewer. It's not clear what some of the IRIs
    // in the manifest should be.

    for {
      // Make a Gravsearch query from a template.
      gravsearchQueryForIncomingLinks <- ZIO.attempt(
                                           org.knora.webapi.messages.twirl.queries.gravsearch.txt
                                             .getIncomingImageLinks(resourceIri)
                                             .toString(),
                                         )

      // Run the query.

      parsedGravsearchQuery <- ZIO.succeed(GravsearchParser.parseQuery(gravsearchQueryForIncomingLinks))
      searchResponse <- searchResponderV2.gravsearchV2(
                          parsedGravsearchQuery,
                          apiV2SchemaWithOption(MarkupRendering.Standoff),
                          requestingUser,
                        )

      resource      = searchResponse.toResource(resourceIri)
      incomingLinks = resource.values.getOrElse(OntologyConstants.KnoraBase.HasIncomingLinkValue.toSmartIri, Seq.empty)

      representations: Seq[ReadResourceV2] = incomingLinks.collect { case readLinkValueV2: ReadLinkValueV2 =>
                                               readLinkValueV2.valueContent.nestedResource
                                             }.flatten
      items <- toItems(representations)
    } yield ResourceIIIFManifestGetResponseV2(
      JsonLDDocument(
        body = JsonLDObject(
          Map(
            JsonLDKeywords.CONTEXT -> JsonLDString("http://iiif.io/api/presentation/3/context.json"),
            "id"                   -> JsonLDString(s"$resourceIri/manifest"), // Is this IRI OK?
            "type"                 -> JsonLDString("Manifest"),
            "label"                -> JsonLDObject(Map("en" -> JsonLDArray(Seq(JsonLDString(resource.label))))),
            "behavior"             -> JsonLDArray(Seq(JsonLDString("paged"))),
            "items"                -> items,
          ),
        ),
        keepStructure = true,
      ),
    )

  private def toItems(representations: Seq[ReadResourceV2]): Task[JsonLDArray] =
    ZIO
      .foreach(representations) { (representation: ReadResourceV2) =>
        for {
          imageValue <-
            ZIO
              .fromOption(
                representation.values
                  .get(OntologyConstants.KnoraBase.HasStillImageFileValue.toSmartIri)
                  .flatMap(_.headOption),
              )
              .orElseFail {
                val msg = s"Representation ${representation.resourceIri} has no still image file value"
                InconsistentRepositoryDataException(msg)
              }

          imageValueContent <-
            imageValue.valueContent match {
              case s: StillImageFileValueContentV2 => ZIO.succeed(s)
              case _                               => ZIO.fail(AssertionException("Expected a StillImageFileValueContentV2"))
            }

          fileUrl = imageValueContent.makeFileUrl(representation.projectADM, appConfig.sipi.externalBaseUrl)
        } yield JsonLDObject(
          Map(
            "id"     -> JsonLDString(s"${representation.resourceIri}/canvas"),
            "type"   -> JsonLDString("Canvas"),
            "label"  -> JsonLDObject(Map("en" -> JsonLDArray(Seq(JsonLDString(representation.label))))),
            "height" -> JsonLDInt(imageValueContent.dimY),
            "width"  -> JsonLDInt(imageValueContent.dimX),
            "items" -> JsonLDArray(
              Seq(
                JsonLDObject(
                  Map(
                    "id"   -> JsonLDString(s"${imageValue.valueIri}/image"),
                    "type" -> JsonLDString("AnnotationPage"),
                    "items" -> JsonLDArray(
                      Seq(
                        JsonLDObject(
                          Map(
                            "id"         -> JsonLDString(imageValue.valueIri),
                            "type"       -> JsonLDString("Annotation"),
                            "motivation" -> JsonLDString("painting"),
                            "body" -> JsonLDObject(
                              Map(
                                "id"     -> JsonLDString(fileUrl),
                                "type"   -> JsonLDString("Image"),
                                "format" -> JsonLDString("image/jpeg"),
                                "height" -> JsonLDInt(imageValueContent.dimY),
                                "width"  -> JsonLDInt(imageValueContent.dimX),
                                "service" -> JsonLDArray(
                                  Seq(
                                    JsonLDObject(
                                      Map(
                                        "id"      -> JsonLDString(appConfig.sipi.externalBaseUrl),
                                        "type"    -> JsonLDString("ImageService3"),
                                        "profile" -> JsonLDString("level1"),
                                      ),
                                    ),
                                  ),
                                ),
                              ),
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        )
      }
      .map(JsonLDArray.apply)

  /**
   * Returns all events describing the history of a resource ordered by version date.
   *
   * @param resourceIri the request for events describing history of a resource.
   * @param requestingUser the user making the request.
   * @return the events extracted from full representation of a resource at each time point in its history ordered by version date.
   */
  def getResourceHistoryEvents(
    resourceIri: IRI,
    requestingUser: User,
  ): Task[ResourceAndValueVersionHistoryResponseV2] =
    for {
      resourceHistory <- getResourceHistoryV2(
                           ResourceVersionHistoryGetRequestV2(
                             resourceIri = resourceIri,
                             withDeletedResource = true,
                             requestingUser = requestingUser,
                           ),
                         )
      resourceFullHist <- extractEventsFromHistory(resourceIri, resourceHistory.history, requestingUser)
    } yield ResourceAndValueVersionHistoryResponseV2(resourceFullHist.sortBy(_.versionDate))

  /**
   * Returns events representing the history of all resources and values belonging to a project ordered by date.
   *
   * @param projectId the history events of a project.
   * @param requestingUser the user making the request.
   *
   * @return the all history events of resources of a project ordered by version date.
   */
  def getProjectResourceHistoryEvents(
    projectId: ProjectIri,
    requestingUser: User,
  ): Task[ResourceAndValueVersionHistoryResponseV2] =
    for {
      _ <- knoraProjectService.findById(projectId).someOrFail(NotFoundException(s"Project $projectId not found"))

      // Do a SELECT prequery to get the IRIs of the resources that belong to the project.
      prequery              = sparql.v2.txt.getAllResourcesInProjectPrequery(projectId.value)
      sparqlSelectResponse <- triplestore.query(Select(prequery))
      mainResourceIris      = sparqlSelectResponse.getColOrThrow("resource")
      // For each resource IRI return history events
      historyOfResourcesAsSeqOfFutures: Seq[Task[Seq[ResourceAndValueHistoryEvent]]] =
        mainResourceIris.map { resourceIri =>
          for {
            resourceHistory <-
              getResourceHistoryV2(
                ResourceVersionHistoryGetRequestV2(
                  resourceIri = resourceIri,
                  withDeletedResource = true,
                  requestingUser = requestingUser,
                ),
              )
            resourceFullHist <- extractEventsFromHistory(resourceIri, resourceHistory.history, requestingUser)
          } yield resourceFullHist
        }

      projectHistory                                         <- ZIO.collectAll(historyOfResourcesAsSeqOfFutures)
      sortedProjectHistory: Seq[ResourceAndValueHistoryEvent] = projectHistory.flatten.sortBy(_.versionDate)

    } yield ResourceAndValueVersionHistoryResponseV2(historyEvents = sortedProjectHistory)

  /**
   * Extract events from full representations of resource in each point of its history.
   *
   * @param resourceIri     the IRI of the resource.
   * @param resourceHistory the full representations of the resource in each point in its history.
   *
   * @param requestingUser             the user making the request.
   * @return the full history of resource as sequence of [[ResourceAndValueHistoryEvent]].
   */
  private def extractEventsFromHistory(
    resourceIri: IRI,
    resourceHistory: Seq[ResourceHistoryEntry],
    requestingUser: User,
  ): Task[Seq[ResourceAndValueHistoryEvent]] =
    for {
      resourceHist <- ZIO.succeed(resourceHistory.reverse)
      // Collect the full representations of the resource for each version date
      histories: Seq[Task[(ResourceHistoryEntry, ReadResourceV2)]] = resourceHist.map { hist =>
                                                                       getResourceAtGivenTime(
                                                                         resourceIri = resourceIri,
                                                                         versionHist = hist,
                                                                         requestingUser = requestingUser,
                                                                       )
                                                                     }

      fullReps <- ZIO.collectAll(histories)

      // Create an event for the resource at creation time
      (creationTimeHist, resourceAtCreation) = fullReps.head
      resourceCreationEvent: Seq[ResourceAndValueHistoryEvent] = getResourceCreationEvent(
                                                                   resourceAtCreation,
                                                                   creationTimeHist,
                                                                 )

      // If there is a version history for deletion of the event, create a delete resource event for it.
      (deletionRep, resourceAtValueChanges) =
        fullReps.tail.partition { case (resHist, resource) =>
          resource
            .asInstanceOf[ReadResourceV2]
            .deletionInfo
            .exists(deletionInfo => deletionInfo.deleteDate == resHist.versionDate)
        }
      resourceDeleteEvent = getResourceDeletionEvents(deletionRep)

      // For each value version, form an event
      valuesEvents <-
        ZIO
          .foreach(resourceAtValueChanges) { case (versionHist, readResource) =>
            getValueEvents(readResource, versionHist, fullReps)
          }
          .map(_.flatten)

      // Get the update resource metadata event, if there is any.
      resourceMetadataUpdateEvent: Seq[ResourceAndValueHistoryEvent] = getResourceMetadataUpdateEvent(
                                                                         fullReps.last,
                                                                         valuesEvents,
                                                                         resourceDeleteEvent,
                                                                       )

    } yield resourceCreationEvent ++ resourceDeleteEvent ++ valuesEvents ++ resourceMetadataUpdateEvent

  /**
   * Returns the full representation of a resource at a given date.
   *
   * @param resourceIri                the IRI of the resource.
   * @param versionHist                the history info of the version; i.e. versionDate and author.
   *
   * @param requestingUser             the user making the request.
   * @return the full representation of the resource at the given version date.
   */
  private def getResourceAtGivenTime(
    resourceIri: IRI,
    versionHist: ResourceHistoryEntry,
    requestingUser: User,
  ): Task[(ResourceHistoryEntry, ReadResourceV2)] =
    for {
      resourceFullRepAtCreationTime <- getResourcesV2(
                                         resourceIris = Seq(resourceIri),
                                         versionDate = Some(versionHist.versionDate),
                                         showDeletedValues = true,
                                         targetSchema = ApiV2Complex,
                                         schemaOptions = Set.empty[Rendering],
                                         requestingUser = requestingUser,
                                       )
      resourceAtCreationTime: ReadResourceV2 = resourceFullRepAtCreationTime.resources.head
    } yield versionHist -> resourceAtCreationTime

  /**
   * Returns a createResource event as [[ResourceAndValueHistoryEvent]] with request body of the form [[ResourceEventBody]].
   *
   * @param resourceAtTimeOfCreation the full representation of the resource at creation date.
   * @param versionInfoAtCreation the history info of the version; i.e. versionDate and author.
   * @return a createResource event.
   */
  private def getResourceCreationEvent(
    resourceAtTimeOfCreation: ReadResourceV2,
    versionInfoAtCreation: ResourceHistoryEntry,
  ): Seq[ResourceAndValueHistoryEvent] = {

    val requestBody: ResourceEventBody = ResourceEventBody(
      resourceIri = resourceAtTimeOfCreation.resourceIri,
      resourceClassIri = resourceAtTimeOfCreation.resourceClassIri,
      label = Some(resourceAtTimeOfCreation.label),
      values = resourceAtTimeOfCreation.values.view
        .mapValues(readValues => readValues.map(readValue => readValue.valueContent))
        .toMap,
      projectADM = resourceAtTimeOfCreation.projectADM,
      permissions = Some(resourceAtTimeOfCreation.permissions),
      creationDate = Some(resourceAtTimeOfCreation.creationDate),
    )

    Seq(
      ResourceAndValueHistoryEvent(
        eventType = ResourceAndValueEventsUtil.CREATE_RESOURCE_EVENT,
        versionDate = versionInfoAtCreation.versionDate,
        author = versionInfoAtCreation.author,
        eventBody = requestBody,
      ),
    )
  }

  /**
   * Returns resourceDeletion events as Seq[[ResourceAndValueHistoryEvent]] with request body of the form [[ResourceEventBody]].
   *
   * @param resourceDeletionInfo A sequence of resource deletion info containing version history of deletion and
   *                             the full representation of resource at time of deletion.
   * @return a seq of deleteResource events.
   */
  private def getResourceDeletionEvents(
    resourceDeletionInfo: Seq[(ResourceHistoryEntry, ReadResourceV2)],
  ): Seq[ResourceAndValueHistoryEvent] =
    resourceDeletionInfo.map { case (delHist, fullRepresentation) =>
      val requestBody: ResourceEventBody = ResourceEventBody(
        resourceIri = fullRepresentation.resourceIri,
        resourceClassIri = fullRepresentation.resourceClassIri,
        projectADM = fullRepresentation.projectADM,
        lastModificationDate = fullRepresentation.lastModificationDate,
        deletionInfo = fullRepresentation.deletionInfo,
      )
      ResourceAndValueHistoryEvent(
        eventType = ResourceAndValueEventsUtil.DELETE_RESOURCE_EVENT,
        versionDate = delHist.versionDate,
        author = delHist.author,
        eventBody = requestBody,
      )
    }

  /**
   * Returns a value event as [[ResourceAndValueHistoryEvent]] with body of the form [[ValueEventBody]].
   *
   * @param resourceAtGivenTime the full representation of the resource at the given time.
   * @param versionHist the history info of the version; i.e. versionDate and author.
   * @param allResourceVersions all full representations of resource for each version date in its history.
   * @return a create/update/delete value event.
   */
  private def getValueEvents(
    resourceAtGivenTime: ReadResourceV2,
    versionHist: ResourceHistoryEntry,
    allResourceVersions: Seq[(ResourceHistoryEntry, ReadResourceV2)],
  ): Task[Seq[ResourceAndValueHistoryEvent]] = {

    /** returns the values of the resource which have the given version date. */
    def findValuesWithGivenVersionDate(values: Map[SmartIri, Seq[ReadValueV2]]): Map[SmartIri, ReadValueV2] =
      values.foldLeft(Map.empty[SmartIri, ReadValueV2]) { case (acc, (propIri, readValue)) =>
        val valuesWithGivenVersion: Seq[ReadValueV2] =
          readValue.filter(readValue =>
            readValue.valueCreationDate == versionHist.versionDate || readValue.deletionInfo.exists(deleteInfo =>
              deleteInfo.deleteDate == versionHist.versionDate,
            ),
          )
        if (valuesWithGivenVersion.nonEmpty) {
          acc + (propIri -> valuesWithGivenVersion.head)
        } else { acc }
      }

    ZIO.foldLeft(findValuesWithGivenVersionDate(resourceAtGivenTime.values))(List.empty[ResourceAndValueHistoryEvent]) {
      (acc, next) =>
        val (propIri, readValue) = next

        // Is the given date a deletion date?
        if (readValue.deletionInfo.exists(deletionInfo => deletionInfo.deleteDate == versionHist.versionDate)) {
          // Yes. Return a deleteValue event
          val deleteValueRequestBody = ValueEventBody(
            resourceIri = resourceAtGivenTime.resourceIri,
            resourceClassIri = resourceAtGivenTime.resourceClassIri,
            projectADM = resourceAtGivenTime.projectADM,
            propertyIri = propIri,
            valueIri = readValue.valueIri,
            valueTypeIri = readValue.valueContent.valueType,
            deletionInfo = readValue.deletionInfo,
            previousValueIri = readValue.previousValueIri,
          )
          ZIO.succeed(
            acc appended ResourceAndValueHistoryEvent(
              eventType = ResourceAndValueEventsUtil.DELETE_VALUE_EVENT,
              versionDate = versionHist.versionDate,
              author = versionHist.author,
              eventBody = deleteValueRequestBody,
            ),
          )
        } else {
          // No. Is the given date a creation date, i.e. value does not have a previous version?
          if (readValue.previousValueIri.isEmpty) {
            // Yes. return a createValue event with its request body
            val createValueRequestBody = ValueEventBody(
              resourceIri = resourceAtGivenTime.resourceIri,
              resourceClassIri = resourceAtGivenTime.resourceClassIri,
              projectADM = resourceAtGivenTime.projectADM,
              propertyIri = propIri,
              valueIri = readValue.valueIri,
              valueTypeIri = readValue.valueContent.valueType,
              valueContent = Some(readValue.valueContent),
              valueUUID = Some(readValue.valueHasUUID),
              valueCreationDate = Some(readValue.valueCreationDate),
              permissions = Some(readValue.permissions),
              valueComment = readValue.valueContent.comment,
            )
            ZIO.succeed(
              acc appended ResourceAndValueHistoryEvent(
                eventType = ResourceAndValueEventsUtil.CREATE_VALUE_EVENT,
                versionDate = versionHist.versionDate,
                author = versionHist.author,
                eventBody = createValueRequestBody,
              ),
            )
          } else {
            // No. return updateValue event
            getValueUpdateEventType(propIri, readValue, allResourceVersions, resourceAtGivenTime).map {
              case (updateEventType, updateEventRequestBody) =>
                acc appended ResourceAndValueHistoryEvent(
                  eventType = updateEventType,
                  versionDate = versionHist.versionDate,
                  author = versionHist.author,
                  eventBody = updateEventRequestBody,
                )
            }
          }
        }
    }
  }

  /**
   * Since update value operation can be used to update value content or value permissions, using the previous versions
   * of the value, it determines the type of the update and returns eventType: updateValuePermission/updateValueContent
   * together with the request body necessary to do the update.
   *
   * @param propertyIri the IRI of the property.
   * @param currentVersionOfValue the current value version.
   * @param allResourceVersions all versions of resource.
   * @param resourceAtGivenTime the full representation of the resource at time of value update.
   * @return (eventType, update event request body)
   */
  private def getValueUpdateEventType(
    propertyIri: SmartIri,
    currentVersionOfValue: ReadValueV2,
    allResourceVersions: Seq[(ResourceHistoryEntry, ReadResourceV2)],
    resourceAtGivenTime: ReadResourceV2,
  ): Task[(String, ValueEventBody)] = for {
    previousValueIri <-
      ZIO
        .fromOption(currentVersionOfValue.previousValueIri)
        .orElseFail(BadRequestException("No previous value IRI found for the value, Please report this as a bug."))

    // find the version of resource which has a value with previousValueIri
    versionDateAndPreviousVersion <-
      ZIO
        .fromOption(allResourceVersions.find { case (_, resource) =>
          resource.values.get(propertyIri).exists(_.exists(_.valueIri == previousValueIri))
        })
        .orElseFail(NotFoundException(s"Could not find the previous value of ${currentVersionOfValue.valueIri}"))
    (previousVersionDate, previousVersionOfResource) = versionDateAndPreviousVersion

    // check that the version date of the previousValue is before the version date of the current value.
    _ <- ZIO.when(previousVersionDate.versionDate.isAfter(currentVersionOfValue.valueCreationDate)) {
           val msg = s"Previous version of the value ${currentVersionOfValue.valueIri} that has previousValueIRI " +
             s"$previousValueIri has a date after the current value."
           ZIO.fail(ForbiddenException(msg))
         }

    // get the previous value
    previousValue <- ZIO
                       .fromOption(previousVersionOfResource.values(propertyIri).find(_.valueIri == previousValueIri))
                       .orDieWith(_ => new Exception("cannot happen as the previous value must exist"))

  } yield // Is the content of previous version of value the same as content of the current version?
    if (previousValue.valueContent == currentVersionOfValue.valueContent) {
      // Yes. Permission must have been updated; return a permission update event.
      val updateValuePermissionsRequestBody = ValueEventBody(
        resourceIri = resourceAtGivenTime.resourceIri,
        resourceClassIri = resourceAtGivenTime.resourceClassIri,
        projectADM = resourceAtGivenTime.projectADM,
        propertyIri = propertyIri,
        valueIri = currentVersionOfValue.valueIri,
        valueTypeIri = currentVersionOfValue.valueContent.valueType,
        permissions = Some(currentVersionOfValue.permissions),
        valueComment = currentVersionOfValue.valueContent.comment,
      )
      (ResourceAndValueEventsUtil.UPDATE_VALUE_PERMISSION_EVENT, updateValuePermissionsRequestBody)
    } else {
      // No. Content must have been updated; return a content update event.
      val updateValueContentRequestBody = ValueEventBody(
        resourceIri = resourceAtGivenTime.resourceIri,
        resourceClassIri = resourceAtGivenTime.resourceClassIri,
        projectADM = resourceAtGivenTime.projectADM,
        propertyIri = propertyIri,
        valueIri = currentVersionOfValue.valueIri,
        valueTypeIri = currentVersionOfValue.valueContent.valueType,
        valueContent = Some(currentVersionOfValue.valueContent),
        valueUUID = Some(currentVersionOfValue.valueHasUUID),
        valueCreationDate = Some(currentVersionOfValue.valueCreationDate),
        valueComment = currentVersionOfValue.valueContent.comment,
        previousValueIri = currentVersionOfValue.previousValueIri,
      )
      (ResourceAndValueEventsUtil.UPDATE_VALUE_CONTENT_EVENT, updateValueContentRequestBody)
    }

  /**
   * Returns an updateResourceMetadata event as [[ResourceAndValueHistoryEvent]] with request body of the form
   * [[ResourceMetadataEventBody]] with information necessary to make update metadata of resource request with a
   * given modification date.
   *
   * @param latestVersionOfResource the full representation of the resource.
   * @param valueEvents             the events describing value operations.
   * @param resourceDeleteEvents    the events describing resource deletion operations.
   * @return an updateResourceMetadata event.
   */
  private def getResourceMetadataUpdateEvent(
    latestVersionOfResource: (ResourceHistoryEntry, ReadResourceV2),
    valueEvents: Seq[ResourceAndValueHistoryEvent],
    resourceDeleteEvents: Seq[ResourceAndValueHistoryEvent],
  ): Seq[ResourceAndValueHistoryEvent] = {
    val readResource: ReadResourceV2 = latestVersionOfResource._2
    val author: IRI                  = latestVersionOfResource._1.author
    // Is lastModificationDate of resource None
    readResource.lastModificationDate match {
      // Yes. Do nothing.
      case None => Seq.empty[ResourceAndValueHistoryEvent]
      // No. Either a value or the resource metadata must have been modified.
      case Some(modDate) =>
        val deletionEventWithSameDate = resourceDeleteEvents.find(event => event.versionDate == modDate)
        // Is the lastModificationDate of the resource the same as its deletion date?
        val updateMetadataEvent = if (deletionEventWithSameDate.isDefined) {
          // Yes. Do noting.
          Seq.empty[ResourceAndValueHistoryEvent]
          // No. Is there any value event?
        } else if (valueEvents.isEmpty) {
          // No. After creation of the resource its metadata must have been updated, use creation date as the lastModification date of the event.
          val requestBody = ResourceMetadataEventBody(
            resourceIri = readResource.resourceIri,
            resourceClassIri = readResource.resourceClassIri,
            lastModificationDate = readResource.creationDate,
            newModificationDate = modDate,
          )
          val event = ResourceAndValueHistoryEvent(
            eventType = ResourceAndValueEventsUtil.UPDATE_RESOURCE_METADATA_EVENT,
            versionDate = modDate,
            author = author,
            eventBody = requestBody,
          )
          Seq(event)
        } else {
          // Yes. Sort the value events by version date.
          val sortedEvents = valueEvents.sortBy(_.versionDate)
          // Is there any value event with version date equal to lastModificationDate of the resource?
          val modDateExists = valueEvents.find(event => event.versionDate == modDate)
          modDateExists match {
            // Yes. The last modification date of the resource reflects the modification of a value. Return nothing.
            case Some(_) => Seq.empty[ResourceAndValueHistoryEvent]
            // No. The last modification date of the resource reflects update of a resource's metadata. Return an updateMetadataEvent
            case None =>
              // Find the event with version date before resource's last modification date.
              val eventsBeforeModDate = sortedEvents.filter(event => event.versionDate.isBefore(modDate))
              // Is there any value with versionDate before this date?
              val oldModDate = if (eventsBeforeModDate.nonEmpty) {
                // Yes. assign the versionDate of the last value event as lastModificationDate for request.
                eventsBeforeModDate.last.versionDate

              } else {
                // No. The metadata of the resource must have been updated after the value operations, use the version date
                // of the last value event as the lastModificationDate
                sortedEvents.last.versionDate
              }
              val requestBody = ResourceMetadataEventBody(
                resourceIri = readResource.resourceIri,
                resourceClassIri = readResource.resourceClassIri,
                lastModificationDate = oldModDate,
                newModificationDate = modDate,
              )
              val event = ResourceAndValueHistoryEvent(
                eventType = ResourceAndValueEventsUtil.UPDATE_RESOURCE_METADATA_EVENT,
                versionDate = modDate,
                author = author,
                eventBody = requestBody,
              )
              Seq(event)
          }
        }
        updateMetadataEvent
    }
  }
}

object ResourcesResponderV2 {
  val layer = ZLayer.fromZIO {
    for {
      config <- ZIO.service[AppConfig]
      iriS   <- ZIO.service[IriService]
      mr     <- ZIO.service[MessageRelay]
      ts     <- ZIO.service[TriplestoreService]
      cu     <- ZIO.service[ConstructResponseUtilV2]
      su     <- ZIO.service[StandoffTagUtilV2]
      ru     <- ZIO.service[ResourceUtilV2]
      pu     <- ZIO.service[PermissionUtilADM]
      kps    <- ZIO.service[KnoraProjectService]
      sr     <- ZIO.service[SearchResponderV2]
      or     <- ZIO.service[OntologyRepo]
      sf     <- ZIO.service[StringFormatter]
      pr     <- ZIO.service[PermissionsResponder]
      os     <- ZIO.service[OntologyService]
      rr     <- ZIO.service[ResourcesRepo]
      li     <- ZIO.service[LegalInfoService]
      handler <-
        mr.subscribe(ResourcesResponderV2(config, iriS, mr, ts, cu, su, ru, pu, kps, sr, or, pr, os, rr, li)(sf))
    } yield handler
  }
}
