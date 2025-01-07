/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import com.typesafe.scalalogging.LazyLogging
import zio.IO
import zio.Task
import zio.URLayer
import zio.ZIO
import zio.ZLayer
import zio.prelude.Validation

import java.time.Instant
import java.util.UUID
import scala.collection.immutable

import dsp.constants.SalsahGui
import dsp.errors.*
import org.knora.webapi.*
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageHandler
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.*
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants.Rdfs
import org.knora.webapi.messages.store.triplestoremessages.SmartIriLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.twirl.queries.sparql
import org.knora.webapi.messages.util.ErrorHandlingMap
import org.knora.webapi.messages.v2.responder.CanDoResponseV2
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.ontologymessages.*
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality.KnoraCardinalityInfo
import org.knora.webapi.responders.IriLocker
import org.knora.webapi.responders.IriService
import org.knora.webapi.responders.Responder
import org.knora.webapi.responders.v2.ontology.CardinalityHandler
import org.knora.webapi.responders.v2.ontology.OntologyCacheHelpers
import org.knora.webapi.responders.v2.ontology.OntologyHelpers
import org.knora.webapi.responders.v2.ontology.OntologyTriplestoreHelpers
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.ontology.domain.model.Cardinality
import org.knora.webapi.slice.ontology.domain.service.CardinalityService
import org.knora.webapi.slice.ontology.domain.service.ChangeCardinalityCheckResult.CanSetCardinalityCheckResult
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.slice.ontology.repo.service.OntologyCache.ONTOLOGY_CACHE_LOCK_IRI
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

/**
 * Responds to requests dealing with ontologies.
 *
 * The API v2 ontology responder reads ontologies from two sources:
 *
 * - The triplestore.
 * - The constant knora-api v2 ontologies that are defined in Scala rather than in the triplestore, [[KnoraBaseToApiV2SimpleTransformationRules]] and [[KnoraBaseToApiV2ComplexTransformationRules]].
 *
 * It maintains an in-memory cache of all ontology data. This cache can be refreshed by using [[OntologyCache.loadOntologies]].
 *
 * Read requests to the ontology responder may contain internal or external IRIs as needed. Response messages from the
 * ontology responder will contain internal IRIs and definitions, unless a constant API v2 ontology was requested,
 * in which case the response will be in the requested API v2 schema.
 *
 * In API v2, the ontology responder can also create and update ontologies. Update requests must contain
 * [[ApiV2Complex]] IRIs and definitions.
 *
 * The API v1 ontology responder, which is read-only, delegates most of its work to this responder.
 */
final case class OntologyResponderV2(
  appConfig: AppConfig,
  cardinalityHandler: CardinalityHandler,
  cardinalityService: CardinalityService,
  iriService: IriService,
  ontologyCache: OntologyCache,
  ontologyCacheHelpers: OntologyCacheHelpers,
  ontologyTriplestoreHelpers: OntologyTriplestoreHelpers,
  ontologyRepo: OntologyRepo,
  knoraProjectService: KnoraProjectService,
  triplestoreService: TriplestoreService,
)(implicit val stringFormatter: StringFormatter)
    extends MessageHandler
    with LazyLogging {

  override def isResponsibleFor(message: ResponderRequest): Boolean = message.isInstanceOf[OntologiesResponderRequestV2]

  override def handle(msg: ResponderRequest): Task[Any] = msg match {
    case EntityInfoGetRequestV2(classIris, propertyIris, requestingUser) =>
      getEntityInfoResponseV2(classIris, propertyIris, requestingUser)
    case StandoffEntityInfoGetRequestV2(standoffClassIris, standoffPropertyIris) =>
      getStandoffEntityInfoResponseV2(standoffClassIris, standoffPropertyIris)
    case StandoffClassesWithDataTypeGetRequestV2(_) =>
      getStandoffStandoffClassesWithDataTypeV2
    case StandoffAllPropertyEntitiesGetRequestV2(_) => getAllStandoffPropertyEntitiesV2
    case CheckSubClassRequestV2(subClassIri, superClassIri, _) =>
      checkSubClassV2(subClassIri, superClassIri)
    case SubClassesGetRequestV2(resourceClassIri, requestingUser) =>
      getSubClassesV2(resourceClassIri, requestingUser)
    case OntologyEntitiesGetRequestV2(ontologyIri, allLanguages, requestingUser) =>
      getOntologyEntitiesV2(ontologyIri, allLanguages, requestingUser)
    case ClassesGetRequestV2(resourceClassIris, allLanguages, requestingUser) =>
      ontologyCacheHelpers.getClassDefinitionsFromOntologyV2(resourceClassIris, allLanguages, requestingUser)
    case PropertiesGetRequestV2(propertyIris, allLanguages, requestingUser) =>
      getPropertyDefinitionsFromOntologyV2(propertyIris, allLanguages, requestingUser)
    case OntologyMetadataGetByProjectRequestV2(projectIris) =>
      getOntologyMetadataForProjectsV2(projectIris)
    case OntologyMetadataGetByIriRequestV2(ontologyIris) =>
      getOntologyMetadataByIriV2(ontologyIris)
    case createOntologyRequest: CreateOntologyRequestV2 => createOntology(createOntologyRequest)
    case changeOntologyMetadataRequest: ChangeOntologyMetadataRequestV2 =>
      changeOntologyMetadata(changeOntologyMetadataRequest)
    case deleteOntologyCommentRequest: DeleteOntologyCommentRequestV2 =>
      deleteOntologyComment(deleteOntologyCommentRequest)
    case createClassRequest: CreateClassRequestV2 => createClass(createClassRequest)
    case changeClassLabelsOrCommentsRequest: ChangeClassLabelsOrCommentsRequestV2 =>
      changeClassLabelsOrComments(changeClassLabelsOrCommentsRequest)
    case addCardinalitiesToClassRequest: AddCardinalitiesToClassRequestV2 =>
      addCardinalitiesToClass(addCardinalitiesToClassRequest)
    case r: ReplaceClassCardinalitiesRequestV2 => replaceClassCardinalities(r)
    case canDeleteCardinalitiesFromClassRequestV2: CanDeleteCardinalitiesFromClassRequestV2 =>
      canDeleteCardinalitiesFromClass(canDeleteCardinalitiesFromClassRequestV2)
    case deleteCardinalitiesFromClassRequest: DeleteCardinalitiesFromClassRequestV2 =>
      deleteCardinalitiesFromClass(deleteCardinalitiesFromClassRequest)
    case changeGuiOrderRequest: ChangeGuiOrderRequestV2 => changeGuiOrder(changeGuiOrderRequest)
    case canDeleteClassRequest: CanDeleteClassRequestV2 => canDeleteClass(canDeleteClassRequest)
    case deleteClassRequest: DeleteClassRequestV2       => deleteClass(deleteClassRequest)
    case createPropertyRequest: CreatePropertyRequestV2 => createProperty(createPropertyRequest)
    case changePropertyLabelsOrCommentsRequest: ChangePropertyLabelsOrCommentsRequestV2 =>
      changePropertyLabelsOrComments(changePropertyLabelsOrCommentsRequest)
    case deletePropertyCommentRequest: DeletePropertyCommentRequestV2 =>
      deletePropertyComment(deletePropertyCommentRequest)
    case deleteClassCommentRequest: DeleteClassCommentRequestV2 =>
      deleteClassComment(deleteClassCommentRequest)
    case changePropertyGuiElementRequest: ChangePropertyGuiElementRequest =>
      changePropertyGuiElement(changePropertyGuiElementRequest)
    case canDeletePropertyRequest: CanDeletePropertyRequestV2 => canDeleteProperty(canDeletePropertyRequest)
    case deletePropertyRequest: DeletePropertyRequestV2       => deleteProperty(deletePropertyRequest)
    case canDeleteOntologyRequest: CanDeleteOntologyRequestV2 => canDeleteOntology(canDeleteOntologyRequest)
    case deleteOntologyRequest: DeleteOntologyRequestV2       => deleteOntology(deleteOntologyRequest)
    case other                                                => Responder.handleUnexpectedMessage(other, this.getClass.getName)
  }

  /**
   * Given a list of resource IRIs and a list of property IRIs (ontology entities), returns an [[EntityInfoGetResponseV2]] describing both resource and property entities.
   *
   * @param classIris      the IRIs of the resource entities to be queried.
   * @param propertyIris   the IRIs of the property entities to be queried.
   * @param requestingUser the user making the request.
   * @return an [[EntityInfoGetResponseV2]].
   */
  private def getEntityInfoResponseV2(
    classIris: Set[SmartIri] = Set.empty[SmartIri],
    propertyIris: Set[SmartIri],
    requestingUser: User,
  ): Task[EntityInfoGetResponseV2] =
    ontologyCacheHelpers.getEntityInfoResponseV2(classIris, propertyIris, requestingUser)

  /**
   * Given a list of standoff class IRIs and a list of property IRIs (ontology entities), returns an [[StandoffEntityInfoGetResponseV2]] describing both resource and property entities.
   *
   * @param standoffClassIris    the IRIs of the resource entities to be queried.
   * @param standoffPropertyIris the IRIs of the property entities to be queried.
   * @return a [[StandoffEntityInfoGetResponseV2]].
   */
  private def getStandoffEntityInfoResponseV2(
    standoffClassIris: Set[SmartIri],
    standoffPropertyIris: Set[SmartIri],
  ): Task[StandoffEntityInfoGetResponseV2] =
    for {
      cacheData <- ontologyCache.getCacheData

      entitiesInWrongSchema =
        (standoffClassIris ++ standoffPropertyIris).filter(_.getOntologySchema.contains(ApiV2Simple))

      _ <- ZIO.fail {
             NotFoundException(
               s"Some requested standoff classes were not found: ${entitiesInWrongSchema.mkString(", ")}",
             )
           }.when(entitiesInWrongSchema.nonEmpty)

      classIrisForCache    = standoffClassIris.map(_.toOntologySchema(InternalSchema))
      propertyIrisForCache = standoffPropertyIris.map(_.toOntologySchema(InternalSchema))

      classOntologies =
        cacheData.ontologies.view.filterKeys(classIrisForCache.map(_.getOntologyFromEntity)).values
      propertyOntologies =
        cacheData.ontologies.view.filterKeys(propertyIrisForCache.map(_.getOntologyFromEntity)).values

      classDefsAvailable = classOntologies.flatMap { ontology =>
                             ontology.classes.filter { case (classIri, classDef) =>
                               classDef.isStandoffClass && standoffClassIris.contains(classIri)
                             }
                           }.toMap

      propertyDefsAvailable =
        propertyOntologies.flatMap { ontology =>
          ontology.properties.filter { case (propertyIri, _) =>
            standoffPropertyIris.contains(propertyIri) && cacheData.containsStandoffProperty(propertyIri)
          }
        }.toMap

      missingClassDefs    = classIrisForCache -- classDefsAvailable.keySet
      missingPropertyDefs = propertyIrisForCache -- propertyDefsAvailable.keySet

      _ <- ZIO.fail {
             NotFoundException(s"Some requested standoff classes were not found: ${missingClassDefs.mkString(", ")}")
           }.when(missingClassDefs.nonEmpty)

      _ <- ZIO.fail {
             NotFoundException(
               s"Some requested standoff properties were not found: ${missingPropertyDefs.mkString(", ")}",
             )
           }.when(missingPropertyDefs.nonEmpty)

      response =
        StandoffEntityInfoGetResponseV2(
          standoffClassInfoMap = new ErrorHandlingMap(classDefsAvailable, key => s"Resource class $key not found"),
          standoffPropertyInfoMap = new ErrorHandlingMap(propertyDefsAvailable, key => s"Property $key not found"),
        )
    } yield response

  /**
   * Gets information about all standoff classes that are a subclass of a data type standoff class.
   *
   * @return a [[StandoffClassesWithDataTypeGetResponseV2]]
   */
  private def getStandoffStandoffClassesWithDataTypeV2: Task[StandoffClassesWithDataTypeGetResponseV2] =
    for {
      cacheData <- ontologyCache.getCacheData
    } yield StandoffClassesWithDataTypeGetResponseV2(
      standoffClassInfoMap = cacheData.ontologies.values.flatMap { ontology =>
        ontology.classes.filter { case (_, classDef) =>
          classDef.isStandoffClass && classDef.standoffDataType.isDefined
        }
      }.toMap,
    )

  /**
   * Gets all standoff property entities.
   *
   * @return a [[StandoffAllPropertyEntitiesGetResponseV2]].
   */
  private def getAllStandoffPropertyEntitiesV2: Task[StandoffAllPropertyEntitiesGetResponseV2] =
    ontologyCache.getCacheData
      .map(_.getAllStandoffPropertyEntities)
      .map(StandoffAllPropertyEntitiesGetResponseV2.apply)

  /**
   * Checks whether a certain Knora resource or value class is a subclass of another class.
   *
   * @param subClassIri   the IRI of the resource or value class whose subclassOf relations have to be checked.
   * @param superClassIri the IRI of the resource or value class to check for (whether it is a a super class of `subClassIri` or not).
   * @return a [[CheckSubClassResponseV2]].
   */
  private def checkSubClassV2(subClassIri: SmartIri, superClassIri: SmartIri): Task[CheckSubClassResponseV2] =
    for {
      cacheData <- ontologyCache.getCacheData
      isSubClass <- ZIO
                      .fromOption(cacheData.getSuperClassesOf(subClassIri))
                      .map(_.contains(superClassIri))
                      .orElseFail(BadRequestException(s"Class $subClassIri not found"))
    } yield CheckSubClassResponseV2(isSubClass)

  /**
   * Gets the IRIs of the subclasses of a class.
   *
   * @param classIri the IRI of the class whose subclasses should be returned.
   * @return a [[SubClassesGetResponseV2]].
   */
  private def getSubClassesV2(classIri: SmartIri, requestingUser: User): Task[SubClassesGetResponseV2] =
    for {
      cacheData <- ontologyCache.getCacheData
      subClasses <-
        ZIO.foreach(cacheData.getSubClassesOf(classIri).get.toVector.sorted) { subClassIri =>
          val labelValueMaybe = cacheData
            .ontologies(subClassIri.getOntologyFromEntity)
            .classes(subClassIri)
            .entityInfoContent
            .getPredicateStringLiteralObject(
              OntologyConstants.Rdfs.Label.toSmartIri,
              Some(requestingUser.lang, appConfig.fallbackLanguage),
            )
          ZIO
            .fromOption(labelValueMaybe)
            .orElseFail(InconsistentRepositoryDataException(s"Resource class $subClassIri has no rdfs:label"))
            .map(SubClassInfoV2(subClassIri, _))
        }
    } yield SubClassesGetResponseV2(subClasses)

  /**
   * Gets the metadata describing the ontologies that belong to selected projects, or to all projects.
   *
   * @param projectIris    the IRIs of the projects selected, or an empty set if all projects are selected.
   * @return a [[ReadOntologyMetadataV2]].
   */
  def getOntologyMetadataForProjectsV2(projectIris: Set[SmartIri]): Task[ReadOntologyMetadataV2] = {
    val returnAllOntologies = projectIris.isEmpty
    for {
      allOntologies <- ontologyCache.getCacheData.map(_.ontologies.values.map(_.ontologyMetadata).toSet)
      ontologies =
        if (returnAllOntologies) { allOntologies }
        else { allOntologies.filter(ontology => projectIris.contains(ontology.projectIri.orNull)) }
    } yield ReadOntologyMetadataV2(ontologies)
  }

  /**
   * Gets the metadata describing the specified ontologies, or all ontologies.
   *
   * @param ontologyIris   the IRIs of the ontologies selected, or an empty set if all ontologies are selected.
   * @return a [[ReadOntologyMetadataV2]].
   */
  private def getOntologyMetadataByIriV2(ontologyIris: Set[SmartIri]): Task[ReadOntologyMetadataV2] =
    for {
      cacheData          <- ontologyCache.getCacheData
      returnAllOntologies = ontologyIris.isEmpty
      ontologyMetadata <-
        if (returnAllOntologies) { ZIO.succeed(cacheData.ontologies.values.map(_.ontologyMetadata).toSet) }
        else {
          val ontologyIrisForCache = ontologyIris.map(_.toOntologySchema(InternalSchema))
          val missingOntologies    = ontologyIrisForCache -- cacheData.ontologies.keySet
          if (missingOntologies.nonEmpty) {
            val msg = s"One or more requested ontologies were not found: ${missingOntologies.mkString(", ")}"
            ZIO.fail(BadRequestException(msg))
          } else {
            ZIO.succeed(
              cacheData.ontologies.view
                .filterKeys(ontologyIrisForCache)
                .values
                .map(ontology => ontology.ontologyMetadata)
                .toSet,
            )
          }
        }
    } yield ReadOntologyMetadataV2(ontologyMetadata)

  /**
   * Requests the entities defined in the given ontology.
   *
   * @param ontologyIri    the IRI (internal or external) of the ontology to be queried.
   * @param requestingUser the user making the request.
   * @return a [[ReadOntologyV2]].
   */
  private def getOntologyEntitiesV2(
    ontologyIri: SmartIri,
    allLanguages: Boolean,
    requestingUser: User,
  ): Task[ReadOntologyV2] =
    for {
      cacheData <- ontologyCache.getCacheData

      _ <- ZIO.when(ontologyIri.getOntologyName == "standoff" && ontologyIri.getOntologySchema.contains(ApiV2Simple))(
             ZIO.fail(BadRequestException(s"The standoff ontology is not available in the API v2 simple schema")),
           )

      ontology <- ZIO
                    .fromOption(cacheData.ontologies.get(ontologyIri.toOntologySchema(InternalSchema)))
                    .orElseFail(NotFoundException(s"Ontology not found: $ontologyIri"))

      // Are we returning data in the user's preferred language, or in all available languages?
      userLang =
        if (!allLanguages) {
          // Just the user's preferred language.
          Some(requestingUser.lang)
        } else {
          // All available languages.
          None
        }
    } yield ontology.copy(
      userLang = userLang,
    )

  /**
   * Requests information about properties in a single ontology.
   *
   * @param propertyIris   the IRIs (internal or external) of the properties to query for.
   * @param requestingUser the user making the request.
   * @return a [[ReadOntologyV2]].
   */
  private def getPropertyDefinitionsFromOntologyV2(
    propertyIris: Set[SmartIri],
    allLanguages: Boolean,
    requestingUser: User,
  ): Task[ReadOntologyV2] =
    for {
      cacheData <- ontologyCache.getCacheData

      ontologyIris = propertyIris.map(_.getOntologyFromEntity)

      _ <-
        ZIO.when(ontologyIris.size != 1)(ZIO.fail(BadRequestException(s"Only one ontology may be queried per request")))

      propertyInfoResponse <- getEntityInfoResponseV2(propertyIris = propertyIris, requestingUser = requestingUser)
      internalOntologyIri   = ontologyIris.head.toOntologySchema(InternalSchema)

      // Are we returning data in the user's preferred language, or in all available languages?
      userLang =
        if (!allLanguages) {
          // Just the user's preferred language.
          Some(requestingUser.lang)
        } else {
          // All available languages.
          None
        }
    } yield ReadOntologyV2(
      ontologyMetadata = cacheData.ontologies(internalOntologyIri).ontologyMetadata,
      properties = propertyInfoResponse.propertyInfoMap,
      userLang = userLang,
    )

  /**
   * Creates a new, empty ontology.
   *
   * @param createOntologyRequest the request message.
   * @return a [[SuccessResponseV2]].
   */
  def createOntology(createOntologyRequest: CreateOntologyRequestV2): Task[ReadOntologyMetadataV2] = {
    def makeTaskFuture(internalOntologyIri: SmartIri): Task[ReadOntologyMetadataV2] =
      for {
        // Make sure the ontology doesn't already exist.
        existingOntologyMetadata <- ontologyTriplestoreHelpers.loadOntologyMetadata(internalOntologyIri)

        _ <- ZIO.when(existingOntologyMetadata.nonEmpty) {
               val msg =
                 s"Ontology ${internalOntologyIri.toOntologySchema(ApiV2Complex)} cannot be created, because it already exists"
               ZIO.fail(BadRequestException(msg))
             }

        // If this is a shared ontology, make sure it's in the default shared ontologies project.
        _ <-
          ZIO.when(
            createOntologyRequest.isShared && createOntologyRequest.projectIri.toString != OntologyConstants.KnoraAdmin.DefaultSharedOntologiesProject,
          ) {
            val msg =
              s"Shared ontologies must be created in project <${OntologyConstants.KnoraAdmin.DefaultSharedOntologiesProject}>"
            ZIO.fail(BadRequestException(msg))
          }

        // If it's in the default shared ontologies project, make sure it's a shared ontology.
        _ <-
          ZIO.when(
            createOntologyRequest.projectIri.toString == OntologyConstants.KnoraAdmin.DefaultSharedOntologiesProject && !createOntologyRequest.isShared,
          ) {
            val msg =
              s"Ontologies created in project <${OntologyConstants.KnoraAdmin.DefaultSharedOntologiesProject}> must be shared"
            ZIO.fail(BadRequestException(msg))
          }

        // Create the ontology.

        currentTime: Instant = Instant.now

        createOntologySparql = sparql.v2.txt
                                 .createOntology(
                                   ontologyNamedGraphIri = internalOntologyIri,
                                   ontologyIri = internalOntologyIri,
                                   projectIri = createOntologyRequest.projectIri,
                                   isShared = createOntologyRequest.isShared,
                                   ontologyLabel = createOntologyRequest.label,
                                   ontologyComment = createOntologyRequest.comment,
                                   currentTime = currentTime,
                                 )
        _ <- triplestoreService.query(Update(createOntologySparql))

        // Check that the update was successful. To do this, we have to undo the SPARQL-escaping of the input.

        unescapedNewMetadata = OntologyMetadataV2(
                                 ontologyIri = internalOntologyIri,
                                 projectIri = Some(createOntologyRequest.projectIri),
                                 label = Some(createOntologyRequest.label),
                                 comment = createOntologyRequest.comment,
                                 lastModificationDate = Some(currentTime),
                               ).unescape

        maybeLoadedOntologyMetadata <- ontologyTriplestoreHelpers.loadOntologyMetadata(internalOntologyIri)

        _ <- maybeLoadedOntologyMetadata match {
               case Some(loadedOntologyMetadata) =>
                 ZIO.unless(loadedOntologyMetadata == unescapedNewMetadata)(ZIO.fail(UpdateNotPerformedException()))
               case None => ZIO.fail(UpdateNotPerformedException())
             }

        _ <- // Update the ontology cache with the unescaped metadata.
          ontologyCache.cacheUpdatedOntologyWithoutUpdatingMaps(
            internalOntologyIri,
            ReadOntologyV2(ontologyMetadata = unescapedNewMetadata),
          )

      } yield ReadOntologyMetadataV2(ontologies = Set(unescapedNewMetadata))

    for {
      requestingUser <- ZIO.succeed(createOntologyRequest.requestingUser)
      projectIri      = createOntologyRequest.projectIri

      // check if the requesting user is allowed to create an ontology
      _ <-
        ZIO.when(
          !(requestingUser.permissions.isProjectAdmin(
            projectIri.toString,
          ) || requestingUser.permissions.isSystemAdmin || requestingUser.isSystemUser),
        ) {
          val msg =
            s"A new ontology in the project ${createOntologyRequest.projectIri} can only be created by an admin of that project, or by a system admin."
          ZIO.fail(ForbiddenException(msg))
        }

      // Check that the ontology name is valid.
      validOntologyName <-
        ZIO
          .fromOption(ValuesValidator.validateProjectSpecificOntologyName(createOntologyRequest.ontologyName))
          .orElseFail(
            BadRequestException(s"Invalid project-specific ontology name: ${createOntologyRequest.ontologyName}"),
          )

      // Make the internal ontology IRI.
      projectId <- ZIO.fromEither(ProjectIri.from(projectIri.toString)).mapError(e => BadRequestException(e))
      project <-
        knoraProjectService.findById(projectId).someOrFail(BadRequestException(s"Project not found: $projectIri"))
      internalOntologyIri = stringFormatter.makeProjectSpecificInternalOntologyIri(
                              validOntologyName,
                              createOntologyRequest.isShared,
                              project.shortcode.value,
                            )

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      createOntologyRequest.apiRequestID,
                      ONTOLOGY_CACHE_LOCK_IRI,
                      makeTaskFuture(internalOntologyIri),
                    )
    } yield taskResult
  }

  /**
   * Changes ontology metadata.
   *
   * @param changeOntologyMetadataRequest the request to change the metadata.
   * @return a [[ReadOntologyMetadataV2]] containing the new metadata.
   */
  private def changeOntologyMetadata(
    changeOntologyMetadataRequest: ChangeOntologyMetadataRequestV2,
  ): Task[ReadOntologyMetadataV2] = {
    def makeTaskFuture(internalOntologyIri: SmartIri): Task[ReadOntologyMetadataV2] =
      for {
        cacheData <- ontologyCache.getCacheData

        // Check that the user has permission to update the ontology.
        projectIri <-
          ontologyCacheHelpers.checkPermissionsForOntologyUpdate(
            internalOntologyIri,
            changeOntologyMetadataRequest.requestingUser,
          )

        // Check that the ontology exists and has not been updated by another user since the client last read its metadata.
        _ <- ontologyTriplestoreHelpers.checkOntologyLastModificationDateBeforeUpdate(
               internalOntologyIri,
               changeOntologyMetadataRequest.lastModificationDate,
             )

        // get the metadata of the ontology.
        oldMetadata = cacheData.ontologies(internalOntologyIri).ontologyMetadata
        // Was there a comment in the ontology metadata?
        ontologyHasComment = oldMetadata.comment.nonEmpty

        // Update the metadata.

        currentTime: Instant = Instant.now

        updateSparql = sparql.v2.txt.changeOntologyMetadata(
                         ontologyNamedGraphIri = internalOntologyIri,
                         ontologyIri = internalOntologyIri,
                         newLabel = changeOntologyMetadataRequest.label,
                         hasOldComment = ontologyHasComment,
                         deleteOldComment = ontologyHasComment && changeOntologyMetadataRequest.comment.nonEmpty,
                         newComment = changeOntologyMetadataRequest.comment,
                         lastModificationDate = changeOntologyMetadataRequest.lastModificationDate,
                         currentTime = currentTime,
                       )
        _ <- triplestoreService.query(Update(updateSparql))

        // Check that the update was successful. To do this, we have to undo the SPARQL-escaping of the input.

        // Is there any new label given?
        label =
          if (changeOntologyMetadataRequest.label.isEmpty) {
            // No. Consider the old label for checking the update.
            oldMetadata.label
          } else {
            // Yes. Consider the new label for checking the update.
            changeOntologyMetadataRequest.label
          }

        // Is there any new comment given?
        comment =
          if (changeOntologyMetadataRequest.comment.isEmpty) {
            // No. Consider the old comment for checking the update.
            oldMetadata.comment
          } else {
            // Yes. Consider the new comment for checking the update.
            changeOntologyMetadataRequest.comment
          }

        unescapedNewMetadata = OntologyMetadataV2(
                                 ontologyIri = internalOntologyIri,
                                 projectIri = Some(projectIri),
                                 label = label,
                                 comment = comment,
                                 lastModificationDate = Some(currentTime),
                               ).unescape

        maybeLoadedOntologyMetadata <- ontologyTriplestoreHelpers.loadOntologyMetadata(internalOntologyIri)

        _ <- maybeLoadedOntologyMetadata match {
               case Some(loadedOntologyMetadata) =>
                 ZIO.unless(loadedOntologyMetadata == unescapedNewMetadata)(ZIO.fail(UpdateNotPerformedException()))
               case None => ZIO.fail(UpdateNotPerformedException())
             }

        // Update the ontology cache with the unescaped metadata.
        updatedOntology = cacheData
                            .ontologies(internalOntologyIri)
                            .copy(ontologyMetadata = unescapedNewMetadata)
        _ <- ontologyCache.cacheUpdatedOntologyWithoutUpdatingMaps(
               internalOntologyIri,
               updatedOntology,
             )

      } yield ReadOntologyMetadataV2(ontologies = Set(unescapedNewMetadata))

    for {
      _                  <- OntologyHelpers.checkExternalOntologyIriForUpdate(changeOntologyMetadataRequest.ontologyIri)
      internalOntologyIri = changeOntologyMetadataRequest.ontologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      changeOntologyMetadataRequest.apiRequestID,
                      ONTOLOGY_CACHE_LOCK_IRI,
                      makeTaskFuture(internalOntologyIri = internalOntologyIri),
                    )
    } yield taskResult
  }

  def deleteOntologyComment(
    deleteOntologyCommentRequestV2: DeleteOntologyCommentRequestV2,
  ): Task[ReadOntologyMetadataV2] = {
    def makeTaskFuture(internalOntologyIri: SmartIri): Task[ReadOntologyMetadataV2] =
      for {
        cacheData <- ontologyCache.getCacheData

        // Check that the user has permission to update the ontology.
        projectIri <- ontologyCacheHelpers.checkPermissionsForOntologyUpdate(
                        internalOntologyIri,
                        deleteOntologyCommentRequestV2.requestingUser,
                      )

        // Check that the ontology exists and has not been updated by another user since the client last read its metadata.
        _ <- ontologyTriplestoreHelpers.checkOntologyLastModificationDateBeforeUpdate(
               internalOntologyIri,
               deleteOntologyCommentRequestV2.lastModificationDate,
             )

        // get the metadata of the ontology.
        oldMetadata = cacheData.ontologies(internalOntologyIri).ontologyMetadata
        // Was there a comment in the ontology metadata?
        ontologyHasComment = oldMetadata.comment.nonEmpty

        // Update the metadata.

        currentTime: Instant = Instant.now

        updateSparql = sparql.v2.txt.changeOntologyMetadata(
                         ontologyNamedGraphIri = internalOntologyIri,
                         ontologyIri = internalOntologyIri,
                         newLabel = None,
                         hasOldComment = ontologyHasComment,
                         deleteOldComment = true,
                         newComment = None,
                         lastModificationDate = deleteOntologyCommentRequestV2.lastModificationDate,
                         currentTime = currentTime,
                       )
        _ <- triplestoreService.query(Update(updateSparql))

        // Check that the update was successful.

        unescapedNewMetadata = OntologyMetadataV2(
                                 ontologyIri = internalOntologyIri,
                                 projectIri = Some(projectIri),
                                 label = oldMetadata.label,
                                 comment = None,
                                 lastModificationDate = Some(currentTime),
                               ).unescape

        maybeLoadedOntologyMetadata <- ontologyTriplestoreHelpers.loadOntologyMetadata(internalOntologyIri)

        _ <- maybeLoadedOntologyMetadata match {
               case Some(loadedOntologyMetadata) =>
                 ZIO.unless(loadedOntologyMetadata == unescapedNewMetadata)(ZIO.fail(UpdateNotPerformedException()))
               case None => ZIO.fail(UpdateNotPerformedException())
             }

        // Update the ontology cache with the unescaped metadata.

        updatedOntology = cacheData
                            .ontologies(internalOntologyIri)
                            .copy(ontologyMetadata = unescapedNewMetadata)
        _ <- ontologyCache.cacheUpdatedOntologyWithoutUpdatingMaps(internalOntologyIri, updatedOntology)
      } yield ReadOntologyMetadataV2(ontologies = Set(unescapedNewMetadata))

    for {
      _                  <- OntologyHelpers.checkExternalOntologyIriForUpdate(deleteOntologyCommentRequestV2.ontologyIri)
      internalOntologyIri = deleteOntologyCommentRequestV2.ontologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      deleteOntologyCommentRequestV2.apiRequestID,
                      ONTOLOGY_CACHE_LOCK_IRI,
                      makeTaskFuture(internalOntologyIri = internalOntologyIri),
                    )
    } yield taskResult
  }

  /**
   * Creates a class in an existing ontology.
   *
   * @param createClassRequest the request to create the class.
   * @return a [[ReadOntologyV2]] in the internal schema, the containing the definition of the new class.
   */
  def createClass(createClassRequest: CreateClassRequestV2): Task[ReadOntologyV2] = {
    def makeTaskFuture(internalClassIri: SmartIri, internalOntologyIri: SmartIri): Task[ReadOntologyV2] = {
      val predicates = createClassRequest.classInfoContent.predicates.values
      for {
        _ <- Validation
               .validate(
                 PredicateInfoV2.checkRequiredStringLiteralWithLanguageTag(Rdfs.Label, predicates),
                 PredicateInfoV2.checkOptionalStringLiteralWithLanguageTag(Rdfs.Comment, predicates),
               )
               .mapError(BadRequestException.apply)
               .toZIO
        cacheData                           <- ontologyCache.getCacheData
        internalClassDef: ClassInfoContentV2 = createClassRequest.classInfoContent.toOntologySchema(InternalSchema)

        // Check that the ontology exists and has not been updated by another user since the client last read it.
        _ <- ontologyTriplestoreHelpers.checkOntologyLastModificationDateBeforeUpdate(
               internalOntologyIri,
               createClassRequest.lastModificationDate,
             )
        // Check that the class's rdf:type is owl:Class.

        rdfType <- ZIO
                     .fromOption(internalClassDef.getIriObject(OntologyConstants.Rdf.Type.toSmartIri))
                     .orElseFail(BadRequestException(s"No rdf:type specified"))
        _ <- ZIO.when(rdfType != OntologyConstants.Owl.Class.toSmartIri) {
               ZIO.fail(BadRequestException(s"Invalid rdf:type for property: $rdfType"))
             }

        ontology = cacheData.ontologies(internalOntologyIri)

        // Check that the class doesn't exist yet.
        _ <- ZIO.when(ontology.classes.contains(internalClassIri)) {
               ZIO.fail(BadRequestException(s"Class ${createClassRequest.classInfoContent.classIri} already exists"))
             }

        // Check that the class's IRI isn't already used for something else.
        _ <-
          ZIO.when(ontology.properties.contains(internalClassIri) || ontology.individuals.contains(internalClassIri)) {
            ZIO.fail(BadRequestException(s"IRI ${createClassRequest.classInfoContent.classIri} is already used"))
          }

        // Check that the base classes that have Knora IRIs are defined as Knora resource classes.
        missingBaseClasses =
          internalClassDef.subClassOf
            .filter(_.isKnoraInternalEntityIri)
            .filter(baseClassIri => !OntologyHelpers.isKnoraInternalResourceClass(baseClassIri, cacheData))
        _ <- ZIO.when(missingBaseClasses.nonEmpty) {
               val msg = s"One or more specified base classes are invalid: ${missingBaseClasses.mkString(", ")}"
               ZIO.fail(BadRequestException(msg))
             }

        // Check for rdfs:subClassOf cycles.

        allBaseClassIrisWithoutSelf: Set[SmartIri] = internalClassDef.subClassOf.flatMap { baseClassIri =>
                                                       cacheData
                                                         .getSuperClassesOf(baseClassIri)
                                                         .getOrElse(Set.empty[SmartIri])
                                                         .toSet
                                                     }

        _ <- ZIO.when(allBaseClassIrisWithoutSelf.contains(internalClassIri)) {
               val msg = s"Class ${createClassRequest.classInfoContent.classIri} would have a cyclical rdfs:subClassOf"
               ZIO.fail(BadRequestException(msg))
             }

        // Check that the class is a subclass of knora-base:Resource.
        allBaseClassIris: Seq[SmartIri] = internalClassIri +: allBaseClassIrisWithoutSelf.toSeq
        _ <- ZIO.when(!allBaseClassIris.contains(OntologyConstants.KnoraBase.Resource.toSmartIri)) {
               val msg =
                 s"Class ${createClassRequest.classInfoContent.classIri} would not be a subclass of knora-api:Resource"
               ZIO.fail(BadRequestException(msg))
             }

        // Check that the cardinalities are valid, and add any inherited cardinalities.
        cardinalitiesCheckResult <- OntologyHelpers
                                      .checkCardinalitiesBeforeAddingAndIfNecessaryAddLinkValueProperties(
                                        internalClassDef,
                                        allBaseClassIris.toSet,
                                        cacheData,
                                      )
                                      .toZIO
        (internalClassDefWithLinkValueProps, cardinalitiesForClassWithInheritance) = cardinalitiesCheckResult

        // Check that the class definition doesn't refer to any non-shared ontologies in other projects.
        _ <- ZIO.attempt(
               OntologyCache.checkOntologyReferencesInClassDef(
                 cacheData,
                 internalClassDefWithLinkValueProps,
                 (msg: String) => throw BadRequestException(msg),
               ),
             )

        // Prepare to update the ontology cache, undoing the SPARQL-escaping of the input.

        propertyIrisOfAllCardinalitiesForClass = cardinalitiesForClassWithInheritance.keySet

        inheritedCardinalities =
          cardinalitiesForClassWithInheritance.filterNot { case (propertyIri, _) =>
            internalClassDefWithLinkValueProps.directCardinalities.contains(propertyIri)
          }

        unescapedClassDefWithLinkValueProps = internalClassDefWithLinkValueProps.unescape

        readClassInfo = ReadClassInfoV2(
                          entityInfoContent = unescapedClassDefWithLinkValueProps,
                          allBaseClasses = allBaseClassIris,
                          isResourceClass = true,
                          canBeInstantiated = true,
                          inheritedCardinalities = inheritedCardinalities,
                          knoraResourceProperties = propertyIrisOfAllCardinalitiesForClass.filter(propertyIri =>
                            OntologyHelpers.isKnoraResourceProperty(propertyIri, cacheData),
                          ),
                          linkProperties = propertyIrisOfAllCardinalitiesForClass.filter(propertyIri =>
                            OntologyHelpers.isLinkProp(propertyIri, cacheData),
                          ),
                          linkValueProperties = propertyIrisOfAllCardinalitiesForClass.filter(propertyIri =>
                            OntologyHelpers.isLinkValueProp(propertyIri, cacheData),
                          ),
                          fileValueProperties = propertyIrisOfAllCardinalitiesForClass.filter(propertyIri =>
                            OntologyHelpers.isFileValueProp(propertyIri, cacheData),
                          ),
                        )

        // Add the SPARQL-escaped class to the triplestore.

        currentTime = Instant.now

        updateSparql = sparql.v2.txt.createClass(
                         ontologyNamedGraphIri = internalOntologyIri,
                         ontologyIri = internalOntologyIri,
                         classDef = internalClassDefWithLinkValueProps,
                         lastModificationDate = createClassRequest.lastModificationDate,
                         currentTime = currentTime,
                       )
        _ <- triplestoreService.query(Update(updateSparql))

        // Check that the ontology's last modification date was updated.
        _ <- ontologyTriplestoreHelpers.checkOntologyLastModificationDateAfterUpdate(internalOntologyIri, currentTime)

        // Check that the data that was saved corresponds to the data that was submitted.
        loadedClassDef <- ontologyTriplestoreHelpers.loadClassDefinition(internalClassIri)

        _ <- ZIO.when(loadedClassDef != unescapedClassDefWithLinkValueProps) {
               val msg =
                 s"Attempted to save class definition $unescapedClassDefWithLinkValueProps, but $loadedClassDef was saved"
               ZIO.fail(InconsistentRepositoryDataException(msg))
             }

        // Update the cache.

        updatedOntology = ontology.copy(
                            ontologyMetadata = ontology.ontologyMetadata.copy(
                              lastModificationDate = Some(currentTime),
                            ),
                            classes = ontology.classes + (internalClassIri -> readClassInfo),
                          )

        _ <- ontologyCache.cacheUpdatedOntologyWithClass(internalOntologyIri, updatedOntology, internalClassIri)

        // Read the data back from the cache.
        response <- ontologyCacheHelpers.getClassDefinitionsFromOntologyV2(
                      classIris = Set(internalClassIri),
                      allLanguages = true,
                      requestingUser = createClassRequest.requestingUser,
                    )
      } yield response
    }

    for {
      requestingUser <- ZIO.succeed(createClassRequest.requestingUser)

      externalClassIri    = createClassRequest.classInfoContent.classIri
      externalOntologyIri = externalClassIri.getOntologyFromEntity

      _ <-
        ontologyCacheHelpers.checkOntologyAndEntityIrisForUpdate(externalOntologyIri, externalClassIri, requestingUser)

      internalClassIri    = externalClassIri.toOntologySchema(InternalSchema)
      internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      createClassRequest.apiRequestID,
                      ONTOLOGY_CACHE_LOCK_IRI,
                      makeTaskFuture(internalClassIri, internalOntologyIri),
                    )
    } yield taskResult
  }

  /**
   * Changes GUI orders in cardinalities in a class definition.
   *
   * @param changeGuiOrderRequest the request message.
   * @return the updated class definition.
   */
  private def changeGuiOrder(changeGuiOrderRequest: ChangeGuiOrderRequestV2): Task[ReadOntologyV2] = {
    def makeTaskFuture(internalClassIri: SmartIri, internalOntologyIri: SmartIri): Task[ReadOntologyV2] = {
      for {
        cacheData                           <- ontologyCache.getCacheData
        internalClassDef: ClassInfoContentV2 = changeGuiOrderRequest.classInfoContent.toOntologySchema(InternalSchema)

        // Check that the ontology exists and has not been updated by another user since the client last read it.
        _ <- ontologyTriplestoreHelpers.checkOntologyLastModificationDateBeforeUpdate(
               internalOntologyIri,
               changeGuiOrderRequest.lastModificationDate,
             )

        // Check that the class's rdf:type is owl:Class.
        rdfType <- ZIO
                     .fromOption(internalClassDef.getIriObject(OntologyConstants.Rdf.Type.toSmartIri))
                     .orElseFail(BadRequestException(s"No rdf:type specified"))
        _ <- ZIO.when(rdfType != OntologyConstants.Owl.Class.toSmartIri) {
               ZIO.fail(BadRequestException(s"Invalid rdf:type for property: $rdfType"))
             }

        // Check that the class exists.
        ontology = cacheData.ontologies(internalOntologyIri)
        currentReadClassInfo <-
          ZIO
            .fromOption(ontology.classes.get(internalClassIri))
            .orElseFail(BadRequestException(s"Class ${changeGuiOrderRequest.classInfoContent.classIri} does not exist"))

        // Check that the properties submitted already have cardinalities.
        wrongProperties: Set[SmartIri] =
          internalClassDef.directCardinalities.keySet -- currentReadClassInfo.entityInfoContent.directCardinalities.keySet
        _ <- ZIO.when(wrongProperties.nonEmpty) {
               val msg =
                 s"One or more submitted properties do not have cardinalities in class ${changeGuiOrderRequest.classInfoContent.classIri}: ${wrongProperties
                     .map(_.toOntologySchema(ApiV2Complex))
                     .mkString(", ")}"
               ZIO.fail(BadRequestException(msg))
             }

        linkValuePropCardinalities = internalClassDef.directCardinalities.filter {
                                       case (propertyIri: SmartIri, _: KnoraCardinalityInfo) =>
                                         val propertyDef = cacheData
                                           .ontologies(propertyIri.getOntologyFromEntity)
                                           .properties(propertyIri)
                                         propertyDef.isLinkProp
                                     }.map {
                                       case (
                                             propertyIri: SmartIri,
                                             cardinalityWithCurrentGuiOrder: KnoraCardinalityInfo,
                                           ) =>
                                         propertyIri.fromLinkPropToLinkValueProp -> cardinalityWithCurrentGuiOrder
                                     }

        internalClassDefWithLinkValueProps = internalClassDef.directCardinalities ++ linkValuePropCardinalities

        // Make an updated class definition.

        newReadClassInfo =
          currentReadClassInfo.copy(
            entityInfoContent = currentReadClassInfo.entityInfoContent.copy(
              directCardinalities = currentReadClassInfo.entityInfoContent.directCardinalities.map {
                case (propertyIri: SmartIri, cardinalityWithCurrentGuiOrder: KnoraCardinalityInfo) =>
                  internalClassDefWithLinkValueProps.get(propertyIri) match {
                    case Some(cardinalityWithNewGuiOrder) =>
                      propertyIri -> cardinalityWithCurrentGuiOrder.copy(guiOrder = cardinalityWithNewGuiOrder.guiOrder)

                    case None => propertyIri -> cardinalityWithCurrentGuiOrder
                  }
              },
            ),
          )

        // Replace the cardinalities in the class definition in the triplestore.

        currentTime: Instant = Instant.now

        updateSparql = sparql.v2.txt.replaceClassCardinalities(
                         ontologyNamedGraphIri = internalOntologyIri,
                         ontologyIri = internalOntologyIri,
                         classIri = internalClassIri,
                         newCardinalities = newReadClassInfo.entityInfoContent.directCardinalities,
                         lastModificationDate = changeGuiOrderRequest.lastModificationDate,
                         currentTime = currentTime,
                       )
        _ <- triplestoreService.query(Update(updateSparql))

        // Check that the ontology's last modification date was updated.
        _ <- ontologyTriplestoreHelpers.checkOntologyLastModificationDateAfterUpdate(internalOntologyIri, currentTime)

        // Check that the data that was saved corresponds to the data that was submitted.
        loadedClassDef <- ontologyTriplestoreHelpers.loadClassDefinition(internalClassIri)

        _ <- ZIO.when(loadedClassDef != newReadClassInfo.entityInfoContent) {
               val msg =
                 s"Attempted to save class definition ${newReadClassInfo.entityInfoContent}, but $loadedClassDef was saved"
               ZIO.fail(InconsistentRepositoryDataException(msg))
             }

        // Update the cache.

        updatedOntology = ontology.copy(
                            ontologyMetadata = ontology.ontologyMetadata.copy(
                              lastModificationDate = Some(currentTime),
                            ),
                            classes = ontology.classes + (internalClassIri -> newReadClassInfo),
                          )
        // Update subclasses and write the cache.
        _ <- ontologyCache.cacheUpdatedOntologyWithClass(internalOntologyIri, updatedOntology, internalClassIri)

        // Read the data back from the cache.
        response <- ontologyCacheHelpers.getClassDefinitionsFromOntologyV2(
                      classIris = Set(internalClassIri),
                      allLanguages = true,
                      requestingUser = changeGuiOrderRequest.requestingUser,
                    )

      } yield response
    }

    for {
      requestingUser <- ZIO.succeed(changeGuiOrderRequest.requestingUser)

      externalClassIri    = changeGuiOrderRequest.classInfoContent.classIri
      externalOntologyIri = externalClassIri.getOntologyFromEntity

      _ <-
        ontologyCacheHelpers.checkOntologyAndEntityIrisForUpdate(externalOntologyIri, externalClassIri, requestingUser)

      internalClassIri    = externalClassIri.toOntologySchema(InternalSchema)
      internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      changeGuiOrderRequest.apiRequestID,
                      ONTOLOGY_CACHE_LOCK_IRI,
                      makeTaskFuture(internalClassIri, internalOntologyIri),
                    )
    } yield taskResult
  }

  /**
   * Adds cardinalities to an existing class definition.
   *
   * @param addCardinalitiesRequest the request to add the cardinalities.
   * @return a [[ReadOntologyV2]] in the internal schema, containing the new class definition.
   */
  private def addCardinalitiesToClass(
    addCardinalitiesRequest: AddCardinalitiesToClassRequestV2,
  ): Task[ReadOntologyV2] = {
    def makeTaskFuture(internalClassIri: SmartIri, internalOntologyIri: SmartIri): Task[ReadOntologyV2] = {
      for {
        cacheData                           <- ontologyCache.getCacheData
        internalClassDef: ClassInfoContentV2 = addCardinalitiesRequest.classInfoContent.toOntologySchema(InternalSchema)

        // Check that the ontology exists and has not been updated by another user since the client last read it.
        _ <- ontologyTriplestoreHelpers.checkOntologyLastModificationDateBeforeUpdate(
               internalOntologyIri,
               addCardinalitiesRequest.lastModificationDate,
             )

        // Check that the class's rdf:type is owl:Class.
        rdfType <- ZIO
                     .fromOption(internalClassDef.getIriObject(OntologyConstants.Rdf.Type.toSmartIri))
                     .orElseFail(BadRequestException(s"No rdf:type specified"))
        _ <- ZIO.when(rdfType != OntologyConstants.Owl.Class.toSmartIri) {
               ZIO.fail(BadRequestException(s"Invalid rdf:type for property: $rdfType"))
             }

        // Check that cardinalities were submitted.
        _ <- ZIO.when(internalClassDef.directCardinalities.isEmpty) {
               ZIO.fail(BadRequestException("No cardinalities specified"))
             }

        // Check that the class exists, that it's a Knora resource class, and that the submitted cardinalities aren't for properties that already have cardinalities
        // directly defined on the class.
        ontology = cacheData.ontologies(internalOntologyIri)
        existingReadClassInfo <-
          ZIO
            .fromOption(ontology.classes.get(internalClassIri))
            .orElseFail(
              BadRequestException(s"Class ${addCardinalitiesRequest.classInfoContent.classIri} does not exist"),
            )

        existingClassDef: ClassInfoContentV2 = existingReadClassInfo.entityInfoContent

        redundantCardinalities = existingClassDef.directCardinalities.keySet
                                   .intersect(internalClassDef.directCardinalities.keySet)

        _ <- ZIO.when(redundantCardinalities.nonEmpty) {
               val msg =
                 s"The cardinalities of ${addCardinalitiesRequest.classInfoContent.classIri} already include the following property or properties: ${redundantCardinalities
                     .mkString(", ")}"
               ZIO.fail(BadRequestException(msg))
             }

        // Is there any property with minCardinality>0 or Cardinality=1?
        hasCardinality: Option[(SmartIri, KnoraCardinalityInfo)] =
          addCardinalitiesRequest.classInfoContent.directCardinalities.find {
            case (_, constraint: KnoraCardinalityInfo) => constraint.cardinality.min > 0
          }

        _ <- hasCardinality match {
               case Some((propIri: SmartIri, cardinality: KnoraCardinalityInfo)) =>
                 ZIO
                   .fail(
                     BadRequestException(
                       s"Cardinality ${cardinality.toString} for $propIri cannot be added to class ${addCardinalitiesRequest.classInfoContent.classIri}, because it is used in data",
                     ),
                   )
                   .whenZIO(iriService.isClassUsedInData(internalClassIri))
               case None => ZIO.unit
             }

        // Make an updated class definition.
        newInternalClassDef = existingClassDef.copy(
                                directCardinalities =
                                  existingClassDef.directCardinalities ++ internalClassDef.directCardinalities,
                              )

        // Check that the new cardinalities are valid, and add any inherited cardinalities.

        allBaseClassIrisWithoutInternal = newInternalClassDef.subClassOf.toSeq.flatMap { baseClassIri =>
                                            cacheData
                                              .getSuperClassesOf(baseClassIri)
                                              .getOrElse(
                                                Seq.empty[SmartIri],
                                              )
                                          }

        allBaseClassIris = internalClassIri +: allBaseClassIrisWithoutInternal
        existingLinkPropsToKeep: Set[SmartIri] =
          existingReadClassInfo.entityInfoContent.directCardinalities.keySet
            .flatMap(p => cacheData.ontologies(p.getOntologyFromEntity).properties.get(p))
            .filter(_.isLinkProp)
            .map(_.entityInfoContent.propertyIri)

        cardinalityCheckResult <- OntologyHelpers
                                    .checkCardinalitiesBeforeAddingAndIfNecessaryAddLinkValueProperties(
                                      internalClassDef = newInternalClassDef,
                                      allBaseClassIris = allBaseClassIris.toSet,
                                      cacheData = cacheData,
                                      existingLinkPropsToKeep = existingLinkPropsToKeep,
                                    )
                                    .toZIO
        (newInternalClassDefWithLinkValueProps, cardinalitiesForClassWithInheritance) = cardinalityCheckResult

        // Check that the class definition doesn't refer to any non-shared ontologies in other projects.
        _ <- ZIO.attempt(
               OntologyCache.checkOntologyReferencesInClassDef(
                 cacheData,
                 newInternalClassDefWithLinkValueProps,
                 (msg: String) => throw BadRequestException(msg),
               ),
             )

        // Prepare to update the ontology cache. (No need to deal with SPARQL-escaping here, because there
        // isn't any text to escape in cardinalities.)

        propertyIrisOfAllCardinalities = cardinalitiesForClassWithInheritance.keySet

        inheritedCardinalities =
          cardinalitiesForClassWithInheritance.filterNot { case (propertyIri, _) =>
            newInternalClassDefWithLinkValueProps.directCardinalities.contains(propertyIri)
          }

        readClassInfo = ReadClassInfoV2(
                          entityInfoContent = newInternalClassDefWithLinkValueProps,
                          allBaseClasses = allBaseClassIris,
                          isResourceClass = true,
                          canBeInstantiated = true,
                          inheritedCardinalities = inheritedCardinalities,
                          knoraResourceProperties = propertyIrisOfAllCardinalities.filter(
                            OntologyHelpers.isKnoraResourceProperty(_, cacheData),
                          ),
                          linkProperties =
                            propertyIrisOfAllCardinalities.filter(OntologyHelpers.isLinkProp(_, cacheData)),
                          linkValueProperties =
                            propertyIrisOfAllCardinalities.filter(OntologyHelpers.isLinkValueProp(_, cacheData)),
                          fileValueProperties =
                            propertyIrisOfAllCardinalities.filter(OntologyHelpers.isFileValueProp(_, cacheData)),
                        )

        // Add the cardinalities to the class definition in the triplestore.

        currentTime = Instant.now
        cardinalitiesToAdd =
          newInternalClassDefWithLinkValueProps.directCardinalities -- existingClassDef.directCardinalities.keySet

        updateSparql = sparql.v2.txt.addCardinalitiesToClass(
                         ontologyNamedGraphIri = internalOntologyIri,
                         ontologyIri = internalOntologyIri,
                         classIri = internalClassIri,
                         cardinalitiesToAdd = cardinalitiesToAdd,
                         lastModificationDate = addCardinalitiesRequest.lastModificationDate,
                         currentTime = currentTime,
                       )
        _ <- triplestoreService.query(Update(updateSparql))

        // Check that the ontology's last modification date was updated.
        _ <- ontologyTriplestoreHelpers.checkOntologyLastModificationDateAfterUpdate(internalOntologyIri, currentTime)

        // Check that the data that was saved corresponds to the data that was submitted.
        loadedClassDef <- ontologyTriplestoreHelpers.loadClassDefinition(internalClassIri)

        _ <- ZIO.when(loadedClassDef != newInternalClassDefWithLinkValueProps) {
               val msg =
                 s"Attempted to save class definition $newInternalClassDefWithLinkValueProps, but $loadedClassDef was saved"
               ZIO.fail(InconsistentRepositoryDataException(msg))
             }

        // Update subclasses and write the cache.

        updatedOntology = ontology.copy(
                            ontologyMetadata = ontology.ontologyMetadata.copy(
                              lastModificationDate = Some(currentTime),
                            ),
                            classes = ontology.classes + (internalClassIri -> readClassInfo),
                          )

        _ <- ontologyCache.cacheUpdatedOntologyWithClass(internalOntologyIri, updatedOntology, internalClassIri)

        // Read the data back from the cache.
        response <- ontologyCacheHelpers.getClassDefinitionsFromOntologyV2(
                      classIris = Set(internalClassIri),
                      allLanguages = true,
                      requestingUser = addCardinalitiesRequest.requestingUser,
                    )
      } yield response
    }

    for {
      requestingUser <- ZIO.succeed(addCardinalitiesRequest.requestingUser)

      externalClassIri    = addCardinalitiesRequest.classInfoContent.classIri
      externalOntologyIri = externalClassIri.getOntologyFromEntity

      _ <-
        ontologyCacheHelpers.checkOntologyAndEntityIrisForUpdate(externalOntologyIri, externalClassIri, requestingUser)

      internalClassIri    = externalClassIri.toOntologySchema(InternalSchema)
      internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      addCardinalitiesRequest.apiRequestID,
                      ONTOLOGY_CACHE_LOCK_IRI,
                      makeTaskFuture(internalClassIri, internalOntologyIri),
                    )
    } yield taskResult
  }

  /**
   * Replace cardinalities of a particular class.
   *
   * Fails if any of the new cardinalities is not consistent with the ontology or if persistent data is not compatible.
   *
   * @param request the [[ReplaceClassCardinalitiesRequestV2]] defining the cardinalities.
   * @return a [[ReadOntologyV2]] in the internal schema, containing the new class definition.
   */
  def replaceClassCardinalities(request: ReplaceClassCardinalitiesRequestV2): Task[ReadOntologyV2] = {
    val task = for {
      newModel   <- makeUpdatedClassModel(request)
      validModel <- checkLastModificationDateAndCanCardinalitiesBeSet(request, newModel)
      response   <- replaceClassCardinalitiesInPersistence(request, validModel)
    } yield response
    val classIriExternal    = request.classInfoContent.classIri
    val ontologyIriExternal = classIriExternal.getOntologyFromEntity
    for {
      _ <- ontologyCacheHelpers.checkOntologyAndEntityIrisForUpdate(
             ontologyIriExternal,
             classIriExternal,
             request.requestingUser,
           )
      response <- IriLocker.runWithIriLock(request.apiRequestID, ONTOLOGY_CACHE_LOCK_IRI, task)
    } yield response
  }

  // Make an updated class definition.
  // Check that the new cardinalities are valid, and don't add any inherited cardinalities.
  // Check that the class definition doesn't refer to any non-shared ontologies in other projects.
  private def makeUpdatedClassModel(request: ReplaceClassCardinalitiesRequestV2): Task[ReadClassInfoV2] =
    for {
      newClassInfo       <- checkRdfTypeOfClassIsClass(request.classInfoContent.toOntologySchema(InternalSchema))
      classIriExternal    = newClassInfo.classIri
      classIri            = classIriExternal.toOntologySchema(InternalSchema)
      ontologyIriExternal = classIri.getOntologyFromEntity
      cacheData          <- ontologyCache.getCacheData
      oldClassInfo <-
        ontologyRepo
          .findClassBy(classIri.toInternalIri)
          .flatMap(ZIO.fromOption(_))
          .mapBoth(_ => BadRequestException(s"Class $ontologyIriExternal does not exist"), _.entityInfoContent)

      // Check that the new cardinalities are valid, and don't add any inherited cardinalities.
      newInternalClassDef = oldClassInfo.copy(directCardinalities = newClassInfo.directCardinalities)
      allBaseClassIrisWithoutInternal = newInternalClassDef.subClassOf.toSeq.flatMap { baseClassIri =>
                                          cacheData
                                            .getSuperClassesOf(baseClassIri)
                                            .getOrElse(
                                              Seq.empty[SmartIri],
                                            )
                                        }

      allBaseClassIris = classIri +: allBaseClassIrisWithoutInternal

      cardinalityCheckResult <- OntologyHelpers
                                  .checkCardinalitiesBeforeAddingAndIfNecessaryAddLinkValueProperties(
                                    newInternalClassDef,
                                    allBaseClassIris.toSet,
                                    cacheData,
                                  )
                                  .toZIO
      (newInternalClassDefWithLinkValueProps, cardinalitiesForClassWithInheritance) = cardinalityCheckResult

      // Check that the class definition doesn't refer to any non-shared ontologies in other projects.
      _ <- ZIO.attempt(
             OntologyCache.checkOntologyReferencesInClassDef(
               cacheData,
               newInternalClassDefWithLinkValueProps,
               (msg: String) => throw BadRequestException(msg),
             ),
           )

      // Build the model
      inheritedCardinalities = cardinalitiesForClassWithInheritance.filterNot { case (propertyIri, _) =>
                                 newInternalClassDefWithLinkValueProps.directCardinalities.contains(propertyIri)
                               }
      propertyIrisOfAllCardinalitiesForClass = cardinalitiesForClassWithInheritance.keySet
      knoraResourceProperties =
        propertyIrisOfAllCardinalitiesForClass.filter(OntologyHelpers.isKnoraResourceProperty(_, cacheData))
      linkProperties      = propertyIrisOfAllCardinalitiesForClass.filter(OntologyHelpers.isLinkProp(_, cacheData))
      linkValueProperties = propertyIrisOfAllCardinalitiesForClass.filter(OntologyHelpers.isLinkValueProp(_, cacheData))
      fileValueProperties = propertyIrisOfAllCardinalitiesForClass.filter(OntologyHelpers.isFileValueProp(_, cacheData))
    } yield ReadClassInfoV2(
      entityInfoContent = newInternalClassDefWithLinkValueProps,
      allBaseClasses = allBaseClassIris,
      isResourceClass = true,
      canBeInstantiated = true,
      inheritedCardinalities = inheritedCardinalities,
      knoraResourceProperties = knoraResourceProperties,
      linkProperties = linkProperties,
      linkValueProperties = linkValueProperties,
      fileValueProperties = fileValueProperties,
    )

  private def checkRdfTypeOfClassIsClass(classInfo: ClassInfoContentV2): Task[ClassInfoContentV2] =
    for {
      rdfType <- ZIO
                   .fromOption(classInfo.getIriObject(OntologyConstants.Rdf.Type.toSmartIri))
                   .orElseFail(BadRequestException(s"No rdf:type specified"))
      _ <- ZIO.when(rdfType != OntologyConstants.Owl.Class.toSmartIri) {
             ZIO.fail(BadRequestException(s"Invalid rdf:type of property: $rdfType."))
           }
    } yield classInfo

  private def checkLastModificationDateAndCanCardinalitiesBeSet(
    request: ReplaceClassCardinalitiesRequestV2,
    newModel: ReadClassInfoV2,
  ): Task[ReadClassInfoV2] = {
    val classIriExternal     = request.classInfoContent.classIri
    val classIri             = classIriExternal.toOntologySchema(InternalSchema)
    val ontologyIri          = classIri.getOntologyFromEntity
    val lastModificationDate = request.lastModificationDate
    for {
      _ <- ontologyTriplestoreHelpers.checkOntologyLastModificationDateBeforeUpdate(ontologyIri, lastModificationDate)
      _ <- checkCanCardinalitiesBeSet(newModel.entityInfoContent).mapError(f => BadRequestException(f.mkString(" ")))
    } yield newModel
  }

  private def checkCanCardinalitiesBeSet(
    newModel: ClassInfoContentV2,
  ): IO[List[CanSetCardinalityCheckResult.Failure], Unit] = {
    val classIri = newModel.classIri.toInternalIri
    val cardinalitiesToCheck: List[(InternalIri, Cardinality)] =
      newModel.directCardinalities.toList.map { case (p, c) => (p.toInternalIri, c.cardinality) }
    for {
      resultsForEachCardinalityChecked <-
        ZIO.foreach(cardinalitiesToCheck) { case (p, c) => cardinalityService.canSetCardinality(classIri, p, c) }.orDie
      errors =
        resultsForEachCardinalityChecked.foldLeft(List.empty)(
          (
            fails: List[CanSetCardinalityCheckResult.Failure],
            nextResult: Either[List[CanSetCardinalityCheckResult.Failure], CanSetCardinalityCheckResult.Success.type],
          ) => nextResult.fold(fails ::: _, _ => fails),
        )
      aggregatedResultMsg <- errors match {
                               case _ if errors.isEmpty => ZIO.unit
                               case errors              => ZIO.fail(errors)
                             }
    } yield aggregatedResultMsg
  }

  private def replaceClassCardinalitiesInPersistence(
    request: ReplaceClassCardinalitiesRequestV2,
    newReadClassInfo: ReadClassInfoV2,
  ): Task[ReadOntologyV2] = {
    val timeOfUpdate = Instant.now()
    val classIri     = request.classInfoContent.classIri.toOntologySchema(InternalSchema)
    for {
      _ <- replaceClassCardinalitiesInTripleStore(request, newReadClassInfo, timeOfUpdate)
      _ <- replaceClassCardinalitiesInOntologyCache(request, newReadClassInfo, timeOfUpdate)
      // Return the response with the new data from the cache
      response <- ontologyCacheHelpers.getClassDefinitionsFromOntologyV2(
                    classIris = Set(classIri),
                    allLanguages = true,
                    requestingUser = request.requestingUser,
                  )
    } yield response
  }

  private def replaceClassCardinalitiesInTripleStore(
    request: ReplaceClassCardinalitiesRequestV2,
    newReadClassInfo: ReadClassInfoV2,
    timeOfUpdate: Instant,
  ): Task[Unit] = {
    val classIri    = request.classInfoContent.classIri.toOntologySchema(InternalSchema)
    val ontologyIri = classIri.getOntologyFromEntity
    val updateSparql = sparql.v2.txt.replaceClassCardinalities(
      ontologyNamedGraphIri = ontologyIri,
      ontologyIri = ontologyIri,
      classIri = classIri,
      newCardinalities = newReadClassInfo.entityInfoContent.directCardinalities,
      lastModificationDate = request.lastModificationDate,
      currentTime = timeOfUpdate,
    )
    for {
      _              <- triplestoreService.query(Update(updateSparql))
      _              <- ontologyTriplestoreHelpers.checkOntologyLastModificationDateAfterUpdate(ontologyIri, timeOfUpdate)
      loadedClassDef <- ontologyTriplestoreHelpers.loadClassDefinition(classIri)
      _ <- ZIO.when(loadedClassDef != newReadClassInfo.entityInfoContent) {
             val msg =
               s"Attempted to save class definition ${newReadClassInfo.entityInfoContent}, but $loadedClassDef was saved instead."
             ZIO.fail(InconsistentRepositoryDataException(msg))
           }
    } yield ()
  }

  private def replaceClassCardinalitiesInOntologyCache(
    request: ReplaceClassCardinalitiesRequestV2,
    newReadClassInfo: ReadClassInfoV2,
    timeOfUpdate: Instant,
  ): Task[Unit] = {
    val classIriExternal    = request.classInfoContent.classIri
    val classIri            = classIriExternal.toOntologySchema(InternalSchema)
    val ontologyIriExternal = classIriExternal.getOntologyFromEntity
    val ontologyIri         = classIri.getOntologyFromEntity
    for {
      ontology <- ontologyRepo
                    .findById(ontologyIri.toInternalIri)
                    .flatMap(ZIO.fromOption(_))
                    .orElseFail(BadRequestException(s"Ontology $ontologyIriExternal does not exist."))
      updatedOntologyMetaData = ontology.ontologyMetadata.copy(lastModificationDate = Some(timeOfUpdate))
      updatedOntologyClasses  = ontology.classes + (classIri -> newReadClassInfo)
      updatedOntology         = ontology.copy(ontologyMetadata = updatedOntologyMetaData, classes = updatedOntologyClasses)
      _                      <- ontologyCache.cacheUpdatedOntologyWithClass(ontologyIri, updatedOntology, classIri)
    } yield ()
  }

  /**
   * FIXME(DSP-1856): Only works if a single cardinality is supplied.
   * Checks if cardinalities can be removed from a class.
   *
   * @param canDeleteCardinalitiesFromClassRequest the request to remove cardinalities.
   * @return a [[CanDoResponseV2]] indicating whether a class's cardinalities can be deleted.
   */
  private def canDeleteCardinalitiesFromClass(
    canDeleteCardinalitiesFromClassRequest: CanDeleteCardinalitiesFromClassRequestV2,
  ): Task[CanDoResponseV2] =
    for {
      requestingUser <- ZIO.succeed(canDeleteCardinalitiesFromClassRequest.requestingUser)

      externalClassIri    = canDeleteCardinalitiesFromClassRequest.classInfoContent.classIri
      externalOntologyIri = externalClassIri.getOntologyFromEntity

      _ <-
        ontologyCacheHelpers.checkOntologyAndEntityIrisForUpdate(externalOntologyIri, externalClassIri, requestingUser)

      internalClassIri    = externalClassIri.toOntologySchema(InternalSchema)
      internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      canDeleteCardinalitiesFromClassRequest.apiRequestID,
                      ONTOLOGY_CACHE_LOCK_IRI,
                      cardinalityHandler.canDeleteCardinalitiesFromClass(
                        deleteCardinalitiesFromClassRequest = canDeleteCardinalitiesFromClassRequest,
                        internalClassIri = internalClassIri,
                        internalOntologyIri = internalOntologyIri,
                      ),
                    )
    } yield taskResult

  /**
   * FIXME(DSP-1856): Only works if a single cardinality is supplied.
   * Removes cardinalities (from class to properties) from class if properties are not used inside data.
   *
   * @param deleteCardinalitiesFromClassRequest the request to remove cardinalities.
   * @return a [[ReadOntologyV2]] in the internal schema, containing the new class definition.
   */
  private def deleteCardinalitiesFromClass(
    deleteCardinalitiesFromClassRequest: DeleteCardinalitiesFromClassRequestV2,
  ): Task[ReadOntologyV2] =
    for {
      requestingUser <- ZIO.succeed(deleteCardinalitiesFromClassRequest.requestingUser)

      externalClassIri    = deleteCardinalitiesFromClassRequest.classInfoContent.classIri
      externalOntologyIri = externalClassIri.getOntologyFromEntity

      _ <-
        ontologyCacheHelpers.checkOntologyAndEntityIrisForUpdate(externalOntologyIri, externalClassIri, requestingUser)

      internalClassIri    = externalClassIri.toOntologySchema(InternalSchema)
      internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      deleteCardinalitiesFromClassRequest.apiRequestID,
                      ONTOLOGY_CACHE_LOCK_IRI,
                      cardinalityHandler.deleteCardinalitiesFromClass(
                        deleteCardinalitiesFromClassRequest = deleteCardinalitiesFromClassRequest,
                        internalClassIri = internalClassIri,
                        internalOntologyIri = internalOntologyIri,
                      ),
                    )
    } yield taskResult

  /**
   * Checks whether a class can be deleted.
   *
   * @param canDeleteClassRequest the request message.
   * @return a [[CanDoResponseV2]].
   */
  private def canDeleteClass(canDeleteClassRequest: CanDeleteClassRequestV2): Task[CanDoResponseV2] = {
    val internalClassIri: SmartIri    = canDeleteClassRequest.classIri.toOntologySchema(InternalSchema)
    val internalOntologyIri: SmartIri = internalClassIri.getOntologyFromEntity

    for {
      cacheData <- ontologyCache.getCacheData

      ontology <-
        ZIO
          .fromOption(cacheData.ontologies.get(internalOntologyIri))
          .orElseFail(
            BadRequestException(s"Ontology ${canDeleteClassRequest.classIri.getOntologyFromEntity} does not exist"),
          )

      _ <- ZIO.when(!ontology.classes.contains(internalClassIri)) {
             ZIO.fail(BadRequestException(s"Class ${canDeleteClassRequest.classIri} does not exist"))
           }

      userCanUpdateOntology <-
        ontologyCacheHelpers.canUserUpdateOntology(internalOntologyIri, canDeleteClassRequest.requestingUser)
      classIsUsed <- iriService.isEntityUsed(internalClassIri)
    } yield CanDoResponseV2.of(userCanUpdateOntology && !classIsUsed)
  }

  /**
   * Deletes a class.
   *
   * @param deleteClassRequest the request to delete the class.
   * @return a [[SuccessResponseV2]].
   */
  private def deleteClass(deleteClassRequest: DeleteClassRequestV2): Task[ReadOntologyMetadataV2] = {
    def makeTaskFuture(internalClassIri: SmartIri, internalOntologyIri: SmartIri): Task[ReadOntologyMetadataV2] =
      for {
        cacheData <- ontologyCache.getCacheData

        // Check that the ontology exists and has not been updated by another user since the client last read it.
        _ <- ontologyTriplestoreHelpers.checkOntologyLastModificationDateBeforeUpdate(
               internalOntologyIri,
               deleteClassRequest.lastModificationDate,
             )

        // Check that the class exists.

        ontology = cacheData.ontologies(internalOntologyIri)

        _ <- ZIO.when(!ontology.classes.contains(internalClassIri)) {
               ZIO.fail(BadRequestException(s"Class ${deleteClassRequest.classIri} does not exist"))
             }

        // Check that the class isn't used in data or ontologies.
        _ <- ZIO
               .whenZIO(iriService.isEntityUsed(internalClassIri)) {
                 val msg =
                   s"Class ${deleteClassRequest.classIri} cannot be deleted, because it is used in data or ontologies"
                 ZIO.fail(BadRequestException(msg))
               }

        // Delete the class from the triplestore.

        currentTime: Instant = Instant.now

        updateSparql = sparql.v2.txt.deleteClass(
                         ontologyNamedGraphIri = internalOntologyIri,
                         ontologyIri = internalOntologyIri,
                         classIri = internalClassIri,
                         lastModificationDate = deleteClassRequest.lastModificationDate,
                         currentTime = currentTime,
                       )
        _ <- triplestoreService.query(Update(updateSparql))

        // Check that the ontology's last modification date was updated.
        _ <- ontologyTriplestoreHelpers.checkOntologyLastModificationDateAfterUpdate(internalOntologyIri, currentTime)

        // Update the cache.

        updatedOntology = ontology.copy(
                            ontologyMetadata = ontology.ontologyMetadata.copy(
                              lastModificationDate = Some(currentTime),
                            ),
                            classes = ontology.classes - internalClassIri,
                          )

        _ <- ontologyCache.cacheUpdatedOntology(internalOntologyIri, updatedOntology)

      } yield ReadOntologyMetadataV2(Set(updatedOntology.ontologyMetadata))

    for {
      requestingUser <- ZIO.succeed(deleteClassRequest.requestingUser)

      externalClassIri    = deleteClassRequest.classIri
      externalOntologyIri = externalClassIri.getOntologyFromEntity

      _ <-
        ontologyCacheHelpers.checkOntologyAndEntityIrisForUpdate(externalOntologyIri, externalClassIri, requestingUser)

      internalClassIri    = externalClassIri.toOntologySchema(InternalSchema)
      internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      deleteClassRequest.apiRequestID,
                      ONTOLOGY_CACHE_LOCK_IRI,
                      makeTaskFuture(internalClassIri, internalOntologyIri),
                    )
    } yield taskResult
  }

  /**
   * Checks whether a property can be deleted.
   *
   * @param canDeletePropertyRequest the request message.
   * @return a [[CanDoResponseV2]] indicating whether the property can be deleted.
   */
  private def canDeleteProperty(canDeletePropertyRequest: CanDeletePropertyRequestV2): Task[CanDoResponseV2] = {
    val internalPropertyIri = canDeletePropertyRequest.propertyIri.toOntologySchema(InternalSchema)
    val internalOntologyIri = internalPropertyIri.getOntologyFromEntity

    for {
      cacheData <- ontologyCache.getCacheData

      ontology <- ZIO
                    .fromOption(cacheData.ontologies.get(internalOntologyIri))
                    .orElseFail {
                      val msg = s"Ontology ${canDeletePropertyRequest.propertyIri.getOntologyFromEntity} does not exist"
                      BadRequestException(msg)
                    }

      propertyDef <-
        ZIO
          .fromOption(ontology.properties.get(internalPropertyIri))
          .orElseFail(BadRequestException(s"Property ${canDeletePropertyRequest.propertyIri} does not exist"))

      _ <- ZIO.when(propertyDef.isLinkValueProp) {
             val msg =
               s"A link value property cannot be deleted directly; check the corresponding link property instead"
             ZIO.fail(BadRequestException(msg))
           }

      userCanUpdateOntology <-
        ontologyCacheHelpers.canUserUpdateOntology(internalOntologyIri, canDeletePropertyRequest.requestingUser)
      propertyIsUsed <- iriService.isEntityUsed(internalPropertyIri)
    } yield CanDoResponseV2.of(userCanUpdateOntology && !propertyIsUsed)
  }

  /**
   * Deletes a property. If the property is a link property, the corresponding link value property is also deleted.
   *
   * @param deletePropertyRequest the request to delete the property.
   * @return a [[ReadOntologyMetadataV2]].
   */
  private def deleteProperty(deletePropertyRequest: DeletePropertyRequestV2): Task[ReadOntologyMetadataV2] = {
    def makeTaskFuture(internalPropertyIri: SmartIri, internalOntologyIri: SmartIri): Task[ReadOntologyMetadataV2] =
      for {
        cacheData <- ontologyCache.getCacheData

        // Check that the ontology exists and has not been updated by another user since the client last read it.
        _ <- ontologyTriplestoreHelpers.checkOntologyLastModificationDateBeforeUpdate(
               internalOntologyIri,
               deletePropertyRequest.lastModificationDate,
             )

        // Check that the property exists.

        ontology = cacheData.ontologies(internalOntologyIri)
        propertyDef <-
          ZIO
            .fromOption(ontology.properties.get(internalPropertyIri))
            .orElseFail(BadRequestException(s"Property ${deletePropertyRequest.propertyIri} does not exist"))

        _ <- ZIO.when(propertyDef.isLinkValueProp) {
               val msg =
                 s"A link value property cannot be deleted directly; delete the corresponding link property instead"
               ZIO.fail(BadRequestException(msg))
             }

        maybeInternalLinkValuePropertyIri: Option[SmartIri] =
          if (propertyDef.isLinkProp) {
            Some(internalPropertyIri.fromLinkPropToLinkValueProp)
          } else {
            None
          }

        // Check that the property isn't used in data or ontologies.

        _ <-
          ZIO
            .fail(
              BadRequestException(
                s"Property ${deletePropertyRequest.propertyIri} cannot be deleted, because it is used in data or ontologies",
              ),
            )
            .whenZIO(iriService.isEntityUsed(internalPropertyIri))

        _ <- maybeInternalLinkValuePropertyIri match {
               case Some(internalLinkValuePropertyIri) =>
                 ZIO
                   .fail(
                     BadRequestException(
                       s"Property ${deletePropertyRequest.propertyIri} cannot be deleted, because the corresponding link value property, ${internalLinkValuePropertyIri
                           .toOntologySchema(ApiV2Complex)}, is used in data or ontologies",
                     ),
                   )
                   .whenZIO(iriService.isEntityUsed(internalLinkValuePropertyIri))
               case None => ZIO.succeed(())
             }

        // Delete the property from the triplestore.

        currentTime: Instant = Instant.now

        updateSparql = sparql.v2.txt.deleteProperty(
                         ontologyNamedGraphIri = internalOntologyIri,
                         ontologyIri = internalOntologyIri,
                         propertyIri = internalPropertyIri,
                         maybeLinkValuePropertyIri = maybeInternalLinkValuePropertyIri,
                         lastModificationDate = deletePropertyRequest.lastModificationDate,
                         currentTime = currentTime,
                       )
        _ <- triplestoreService.query(Update(updateSparql))

        // Check that the ontology's last modification date was updated.
        _ <- ontologyTriplestoreHelpers.checkOntologyLastModificationDateAfterUpdate(internalOntologyIri, currentTime)

        // Update the cache.

        propertiesToRemoveFromCache = Set(internalPropertyIri) ++ maybeInternalLinkValuePropertyIri

        updatedOntology =
          ontology.copy(
            ontologyMetadata = ontology.ontologyMetadata.copy(
              lastModificationDate = Some(currentTime),
            ),
            properties = ontology.properties -- propertiesToRemoveFromCache,
          )

        _ <- ontologyCache.cacheUpdatedOntology(internalOntologyIri, updatedOntology)

      } yield ReadOntologyMetadataV2(Set(updatedOntology.ontologyMetadata))

    for {
      requestingUser <- ZIO.succeed(deletePropertyRequest.requestingUser)

      externalPropertyIri = deletePropertyRequest.propertyIri
      externalOntologyIri = externalPropertyIri.getOntologyFromEntity

      _ <- ontologyCacheHelpers.checkOntologyAndEntityIrisForUpdate(
             externalOntologyIri,
             externalPropertyIri,
             requestingUser,
           )

      internalPropertyIri = externalPropertyIri.toOntologySchema(InternalSchema)
      internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      deletePropertyRequest.apiRequestID,
                      ONTOLOGY_CACHE_LOCK_IRI,
                      makeTaskFuture(internalPropertyIri, internalOntologyIri),
                    )
    } yield taskResult
  }

  /**
   * Checks whether an ontology can be deleted.
   *
   * @param canDeleteOntologyRequest the request message.
   * @return a [[CanDoResponseV2]] indicating whether an ontology can be deleted.
   */
  private def canDeleteOntology(canDeleteOntologyRequest: CanDeleteOntologyRequestV2): Task[CanDoResponseV2] = {
    val internalOntologyIri: SmartIri = canDeleteOntologyRequest.ontologyIri.toOntologySchema(InternalSchema)

    for {
      cacheData <- ontologyCache.getCacheData

      ontology <- ZIO.fromOption(cacheData.ontologies.get(internalOntologyIri)).orElseFail {
                    val msg = s"Ontology ${canDeleteOntologyRequest.ontologyIri.getOntologyFromEntity} does not exist"
                    BadRequestException(msg)
                  }

      userCanUpdateOntology <-
        ontologyCacheHelpers.canUserUpdateOntology(internalOntologyIri, canDeleteOntologyRequest.requestingUser)
      subjectsUsingOntology <- ontologyTriplestoreHelpers.getSubjectsUsingOntology(ontology)
    } yield CanDoResponseV2.of(userCanUpdateOntology && subjectsUsingOntology.isEmpty)
  }

  private def deleteOntology(deleteOntologyRequest: DeleteOntologyRequestV2): Task[SuccessResponseV2] = {
    def deleteTask(internalOntologyIri: SmartIri): Task[SuccessResponseV2] =
      for {
        cacheData <- ontologyCache.getCacheData

        // Check that the user has permission to update the ontology.
        _ <-
          ontologyCacheHelpers.checkPermissionsForOntologyUpdate(
            internalOntologyIri,
            deleteOntologyRequest.requestingUser,
          )

        // Check that the ontology exists and has not been updated by another user since the client last read it.
        _ <- ontologyTriplestoreHelpers.checkOntologyLastModificationDateBeforeUpdate(
               internalOntologyIri,
               deleteOntologyRequest.lastModificationDate,
             )

        // Check that none of the entities in the ontology are used in data or in other ontologies.

        ontology               = cacheData.ontologies(internalOntologyIri)
        subjectsUsingOntology <- ontologyTriplestoreHelpers.getSubjectsUsingOntology(ontology)

        _ <- ZIO.when(subjectsUsingOntology.nonEmpty) {
               val sortedSubjects = subjectsUsingOntology.map(s => "<" + s + ">").toVector.sorted.mkString(", ")
               val msg =
                 s"Ontology ${internalOntologyIri.toOntologySchema(ApiV2Complex)} cannot be deleted, because of subjects that refer to it: $sortedSubjects"
               ZIO.fail(BadRequestException(msg))
             }

        // Delete everything in the ontology's named graph.
        _ <- triplestoreService.query(Update(sparql.v2.txt.deleteOntology(internalOntologyIri)))
        // Remove the ontology from the cache.
        _ <- ontologyCache.deleteOntology(internalOntologyIri)

        // Check that the ontology has been deleted.
        maybeOntologyMetadata <- ontologyTriplestoreHelpers.loadOntologyMetadata(internalOntologyIri)

        _ <- ZIO.when(maybeOntologyMetadata.nonEmpty) {
               val msg =
                 s"Ontology ${internalOntologyIri.toOntologySchema(ApiV2Complex)} was not deleted. Please report this as a possible bug."
               ZIO.fail(UpdateNotPerformedException(msg))
             }

      } yield SuccessResponseV2(s"Ontology ${internalOntologyIri.toOntologySchema(ApiV2Complex)} has been deleted")

    for {
      _                  <- OntologyHelpers.checkExternalOntologyIriForUpdate(deleteOntologyRequest.ontologyIri)
      internalOntologyIri = deleteOntologyRequest.ontologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      deleteOntologyRequest.apiRequestID,
                      ONTOLOGY_CACHE_LOCK_IRI,
                      deleteTask(internalOntologyIri),
                    )
    } yield taskResult
  }

  /**
   * Creates a property in an existing ontology.
   *
   * @param createPropertyRequest the request to create the property.
   * @return a [[ReadOntologyV2]] in the internal schema, the containing the definition of the new property.
   */
  def createProperty(req: CreatePropertyRequestV2): Task[ReadOntologyV2] =
    createProperty(req.propertyInfoContent, req.lastModificationDate, req.apiRequestID, req.requestingUser)

  def createProperty(
    propertyInfoContent: PropertyInfoContentV2,
    lastModificationDate: Instant,
    apiRequestID: UUID,
    requestingUser: User,
  ): Task[ReadOntologyV2] = {
    def makeTaskFuture(internalPropertyIri: SmartIri, internalOntologyIri: SmartIri): Task[ReadOntologyV2] = {
      val predicates = propertyInfoContent.predicates.values
      for {
        _ <- Validation
               .validate(
                 PredicateInfoV2.checkRequiredStringLiteralWithLanguageTag(Rdfs.Label, predicates),
                 PredicateInfoV2.checkOptionalStringLiteralWithLanguageTag(Rdfs.Comment, predicates),
               )
               .mapError(BadRequestException(_))
               .toZIO
        cacheData          <- ontologyCache.getCacheData
        internalPropertyDef = propertyInfoContent.toOntologySchema(InternalSchema)

        // Check that the ontology exists and has not been updated by another user since the client last read it.
        _ <- ontologyTriplestoreHelpers.checkOntologyLastModificationDateBeforeUpdate(
               internalOntologyIri,
               lastModificationDate,
             )

        // Check that the property's rdf:type is owl:ObjectProperty.
        rdfType <- ZIO
                     .fromOption(internalPropertyDef.getIriObject(OntologyConstants.Rdf.Type.toSmartIri))
                     .orElseFail(BadRequestException(s"No rdf:type specified"))
        _ <- ZIO.when(rdfType != OntologyConstants.Owl.ObjectProperty.toSmartIri) {
               ZIO.fail(BadRequestException(s"Invalid rdf:type for property: $rdfType"))
             }

        // Check that the property doesn't exist yet.
        ontology = cacheData.ontologies(internalOntologyIri)
        _ <- ZIO.when(ontology.properties.contains(internalPropertyIri)) {
               val msg = s"Property ${propertyInfoContent.propertyIri} already exists"
               ZIO.fail(BadRequestException(msg))
             }

        // Check that the property's IRI isn't already used for something else.
        _ <- ZIO.when(
               ontology.classes.contains(internalPropertyIri) || ontology.individuals.contains(internalPropertyIri),
             ) {
               ZIO.fail(
                 BadRequestException(s"IRI ${propertyInfoContent.propertyIri} is already used"),
               )
             }

        // Check that the base properties that have Knora IRIs are defined as Knora resource properties.

        knoraSuperProperties = internalPropertyDef.subPropertyOf.filter(_.isKnoraInternalEntityIri)
        invalidSuperProperties = knoraSuperProperties.filterNot(baseProperty =>
                                   OntologyHelpers.isKnoraResourceProperty(
                                     baseProperty,
                                     cacheData,
                                   ) && baseProperty.toString != OntologyConstants.KnoraBase.ResourceProperty,
                                 )

        _ <- ZIO.when(invalidSuperProperties.nonEmpty) {
               val msg = s"One or more specified base properties are invalid: ${invalidSuperProperties.mkString(", ")}"
               ZIO.fail(BadRequestException(msg))
             }

        // Check for rdfs:subPropertyOf cycles.

        allKnoraSuperPropertyIrisWithoutSelf: Set[SmartIri] = knoraSuperProperties.flatMap { superPropertyIri =>
                                                                cacheData
                                                                  .getSuperPropertiesOf(superPropertyIri)
                                                                  .getOrElse(
                                                                    Set.empty[SmartIri],
                                                                  )
                                                              }

        _ <- ZIO.when(allKnoraSuperPropertyIrisWithoutSelf.contains(internalPropertyIri)) {
               val msg =
                 s"Property ${propertyInfoContent.propertyIri} would have a cyclical rdfs:subPropertyOf"
               ZIO.fail(BadRequestException(msg))
             }

        // Check the property is a subproperty of knora-base:hasValue or knora-base:hasLinkTo, but not both.

        allKnoraSuperPropertyIris: Set[SmartIri] = allKnoraSuperPropertyIrisWithoutSelf + internalPropertyIri

        isValueProp     = allKnoraSuperPropertyIris.contains(OntologyConstants.KnoraBase.HasValue.toSmartIri)
        isLinkProp      = allKnoraSuperPropertyIris.contains(OntologyConstants.KnoraBase.HasLinkTo.toSmartIri)
        isLinkValueProp = allKnoraSuperPropertyIris.contains(OntologyConstants.KnoraBase.HasLinkToValue.toSmartIri)
        isFileValueProp = allKnoraSuperPropertyIris.contains(OntologyConstants.KnoraBase.HasFileValue.toSmartIri)

        _ <- ZIO.when(!isValueProp && !isLinkProp) {
               val msg =
                 s"Property ${propertyInfoContent.propertyIri} would not be a subproperty of knora-api:hasValue or knora-api:hasLinkTo"
               ZIO.fail(BadRequestException(msg))
             }

        _ <- ZIO.when(isValueProp && isLinkProp) {
               val msg =
                 s"Property ${propertyInfoContent.propertyIri} would be a subproperty of both knora-api:hasValue and knora-api:hasLinkTo"
               ZIO.fail(BadRequestException(msg))
             }

        // Don't allow new file value properties to be created.
        _ <- ZIO.when(isFileValueProp)(ZIO.fail(BadRequestException("New file value properties cannot be created")))

        // Don't allow new link value properties to be created directly, because we do that automatically when creating a link property.
        _ <- ZIO.when(isLinkValueProp) {
               val msg = "New link value properties cannot be created directly. Create a link property instead."
               ZIO.fail(BadRequestException(msg))
             }

        // If we're creating a link property, make the definition of the corresponding link value property.
        maybeLinkValuePropertyDef <-
          if (isLinkProp) {
            val linkValuePropertyDef = OntologyHelpers.linkPropertyDefToLinkValuePropertyDef(internalPropertyDef)

            if (ontology.properties.contains(linkValuePropertyDef.propertyIri)) {
              ZIO.fail(BadRequestException(s"Link value property ${linkValuePropertyDef.propertyIri} already exists"))
            } else {
              ZIO.some(linkValuePropertyDef)
            }
          } else { ZIO.none }

        // Check that the subject class constraint, if provided, designates a Knora resource class that exists.
        maybeSubjectClassConstraint <-
          ZIO.attempt(
            internalPropertyDef.predicates
              .get(OntologyConstants.KnoraBase.SubjectClassConstraint.toSmartIri)
              .map(_.requireIriObject(throw BadRequestException("Invalid knora-api:subjectType"))),
          )
        _ <- ZIO.foreachDiscard(maybeSubjectClassConstraint) { subjectClassConstraint =>
               ZIO.unless(OntologyHelpers.isKnoraInternalResourceClass(subjectClassConstraint, cacheData)) {
                 val msg = s"Invalid subject class constraint: ${subjectClassConstraint.toOntologySchema(ApiV2Complex)}"
                 ZIO.fail(BadRequestException(msg))
               }
             }

        // Check that the object class constraint designates an appropriate class that exists.

        objectClassConstraint <-
          ZIO
            .fromOption(internalPropertyDef.getIriObject(OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri))
            .orElseFail(BadRequestException(s"No knora-api:objectType specified"))

        // If this is a value property, ensure its object class constraint is not LinkValue or a file value class.
        _ <-
          ZIO.when(
            !isLinkProp &&
              (objectClassConstraint.toString == OntologyConstants.KnoraBase.LinkValue
                || OntologyConstants.KnoraBase.FileValueClasses.contains(objectClassConstraint.toString)),
          ) {
            val msg =
              s"Invalid object class constraint for value property: ${objectClassConstraint.toOntologySchema(ApiV2Complex)}"
            ZIO.fail(BadRequestException(msg))
          }

        // Check that the subject class, if provided, is a subclass of the subject classes of the base properties.
        _ <- ZIO.attempt(maybeSubjectClassConstraint match {
               case Some(subjectClassConstraint) =>
                 OntologyCache.checkPropertyConstraint(
                   cacheData = cacheData,
                   internalPropertyIri = internalPropertyIri,
                   constraintPredicateIri = OntologyConstants.KnoraBase.SubjectClassConstraint.toSmartIri,
                   constraintValueToBeChecked = subjectClassConstraint,
                   allSuperPropertyIris = allKnoraSuperPropertyIris,
                   errorSchema = ApiV2Complex,
                   errorFun = { (msg: String) => throw BadRequestException(msg) },
                 )

               case None => ()
             })

        // Check that the object class is a subclass of the object classes of the base properties.

        _ <- ZIO.attempt(
               OntologyCache.checkPropertyConstraint(
                 cacheData = cacheData,
                 internalPropertyIri = internalPropertyIri,
                 constraintPredicateIri = OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri,
                 constraintValueToBeChecked = objectClassConstraint,
                 allSuperPropertyIris = allKnoraSuperPropertyIris,
                 errorSchema = ApiV2Complex,
                 errorFun = { (msg: String) => throw BadRequestException(msg) },
               ),
             )

        // Check that the property definition doesn't refer to any non-shared ontologies in other projects.
        _ <- ZIO.attempt(
               OntologyCache.checkOntologyReferencesInPropertyDef(
                 ontologyCacheData = cacheData,
                 propertyDef = internalPropertyDef,
                 errorFun = { (msg: String) => throw BadRequestException(msg) },
               ),
             )

        // Add the property (and the link value property if needed) to the triplestore.

        currentTime: Instant = Instant.now

        updateSparql = sparql.v2.txt.createProperty(
                         ontologyNamedGraphIri = internalOntologyIri,
                         ontologyIri = internalOntologyIri,
                         propertyDef = internalPropertyDef,
                         maybeLinkValuePropertyDef = maybeLinkValuePropertyDef,
                         lastModificationDate = lastModificationDate,
                         currentTime = currentTime,
                       )
        _ <- triplestoreService.query(Update(updateSparql))

        // Check that the ontology's last modification date was updated.
        _ <- ontologyTriplestoreHelpers.checkOntologyLastModificationDateAfterUpdate(internalOntologyIri, currentTime)

        // Check that the data that was saved corresponds to the data that was submitted. To make this comparison,
        // we have to undo the SPARQL-escaping of the input.

        loadedPropertyDef <- ontologyTriplestoreHelpers.loadPropertyDefinition(internalPropertyIri)

        unescapedInputPropertyDef = internalPropertyDef.unescape

        _ <- ZIO.when(loadedPropertyDef != unescapedInputPropertyDef) {
               val msg =
                 s"Attempted to save property definition $unescapedInputPropertyDef, but $loadedPropertyDef was saved"
               ZIO.fail(InconsistentRepositoryDataException(msg))
             }

        maybeLoadedLinkValuePropertyDefFuture: Option[Task[PropertyInfoContentV2]] =
          maybeLinkValuePropertyDef.map { linkValuePropertyDef =>
            ontologyTriplestoreHelpers.loadPropertyDefinition(linkValuePropertyDef.propertyIri)
          }

        maybeLoadedLinkValuePropertyDef <-
          ZIO.collectAll(maybeLoadedLinkValuePropertyDefFuture)
        maybeUnescapedNewLinkValuePropertyDef = maybeLinkValuePropertyDef.map(_.unescape)

        _ <- (maybeLoadedLinkValuePropertyDef, maybeUnescapedNewLinkValuePropertyDef) match {
               case (Some(loadedLinkValuePropertyDef), Some(unescapedNewLinkPropertyDef)) =>
                 ZIO.unless(loadedLinkValuePropertyDef == unescapedNewLinkPropertyDef) {
                   val msg =
                     s"Attempted to save link value property definition $unescapedNewLinkPropertyDef, but $loadedLinkValuePropertyDef was saved"
                   ZIO.fail(InconsistentRepositoryDataException(msg))
                 }
               case _ => ZIO.unit
             }

        // Update the ontology cache, using the unescaped definition(s).

        readPropertyInfo = ReadPropertyInfoV2(
                             entityInfoContent = unescapedInputPropertyDef,
                             isEditable = true,
                             isResourceProp = true,
                             isLinkProp = isLinkProp,
                           )

        maybeLinkValuePropertyCacheEntry =
          maybeUnescapedNewLinkValuePropertyDef.map { unescapedNewLinkPropertyDef =>
            unescapedNewLinkPropertyDef.propertyIri -> ReadPropertyInfoV2(
              entityInfoContent = unescapedNewLinkPropertyDef,
              isEditable = true,
              isResourceProp = true,
              isLinkValueProp = true,
            )
          }

        updatedOntologyMetadata = ontology.ontologyMetadata.copy(
                                    lastModificationDate = Some(currentTime),
                                  )

        updatedOntology =
          ontology.copy(
            ontologyMetadata = updatedOntologyMetadata,
            properties =
              ontology.properties ++ maybeLinkValuePropertyCacheEntry + (internalPropertyIri -> readPropertyInfo),
          )

        _ <- ontologyCache.cacheUpdatedOntology(internalOntologyIri, updatedOntology)

        // Read the data back from the cache.
        response <- getPropertyDefinitionsFromOntologyV2(
                      propertyIris = Set(internalPropertyIri),
                      allLanguages = true,
                      requestingUser = requestingUser,
                    )
      } yield response
    }

    for {
      requestingUser <- ZIO.succeed(requestingUser)

      externalPropertyIri = propertyInfoContent.propertyIri
      externalOntologyIri = externalPropertyIri.getOntologyFromEntity

      _ <- ontologyCacheHelpers.checkOntologyAndEntityIrisForUpdate(
             externalOntologyIri,
             externalPropertyIri,
             requestingUser,
           )

      internalPropertyIri = externalPropertyIri.toOntologySchema(InternalSchema)
      internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      apiRequestID,
                      ONTOLOGY_CACHE_LOCK_IRI,
                      makeTaskFuture(internalPropertyIri, internalOntologyIri),
                    )
    } yield taskResult
  }

  /**
   * Changes the values of `salsah-gui:guiElement` and `salsah-gui:guiAttribute` in a property definition.
   *
   * @param changePropertyGuiElementRequest the request to change the property's GUI element and GUI attribute.
   * @return a [[ReadOntologyV2]] containing the modified property definition.
   */
  private def changePropertyGuiElement(
    changePropertyGuiElementRequest: ChangePropertyGuiElementRequest,
  ): Task[ReadOntologyV2] = {
    def makeTaskFuture(internalPropertyIri: SmartIri, internalOntologyIri: SmartIri): Task[ReadOntologyV2] = for {
      cacheData <- ontologyCache.getCacheData

      ontology = cacheData.ontologies(internalOntologyIri)

      currentReadPropertyInfo <-
        ZIO
          .fromOption(ontology.properties.get(internalPropertyIri))
          .orElseFail(NotFoundException(s"Property ${changePropertyGuiElementRequest.propertyIri} not found"))

      // Check that the ontology exists and has not been updated by another user since the client last read it.
      _ <- ontologyTriplestoreHelpers.checkOntologyLastModificationDateBeforeUpdate(
             internalOntologyIri,
             changePropertyGuiElementRequest.lastModificationDate,
           )

      // If this is a link property, also change the GUI element and attribute of the corresponding link value property.
      maybeCurrentLinkValueReadPropertyInfo <-
        if (currentReadPropertyInfo.isLinkProp) {
          val linkValuePropertyIri = internalPropertyIri.fromLinkPropToLinkValueProp
          ZIO
            .fromOption(ontology.properties.get(linkValuePropertyIri))
            .orElseFail(InconsistentRepositoryDataException(s"Link value property $linkValuePropertyIri not found"))
            .map(Some(_))
        } else {
          ZIO.none
        }

      // Do the update.
      currentTime: Instant = Instant.now

      newGuiElementIri =
        changePropertyGuiElementRequest.newGuiObject.guiElement.map(guiElement => guiElement.value.toSmartIri)

      newGuiAttributeIris =
        changePropertyGuiElementRequest.newGuiObject.guiAttributes.map(guiAttribute => guiAttribute.value)

      updateSparql = sparql.v2.txt.changePropertyGuiElement(
                       ontologyNamedGraphIri = internalOntologyIri,
                       ontologyIri = internalOntologyIri,
                       propertyIri = internalPropertyIri,
                       maybeLinkValuePropertyIri =
                         maybeCurrentLinkValueReadPropertyInfo.map(_.entityInfoContent.propertyIri),
                       maybeNewGuiElement = newGuiElementIri,
                       newGuiAttributes = newGuiAttributeIris,
                       lastModificationDate = changePropertyGuiElementRequest.lastModificationDate,
                       currentTime = currentTime,
                     )
      _ <- triplestoreService.query(Update(updateSparql))

      // Check that the ontology's last modification date was updated.

      _ <- ontologyTriplestoreHelpers.checkOntologyLastModificationDateAfterUpdate(internalOntologyIri, currentTime)

      // Check that the data that was saved corresponds to the data that was submitted. To make this comparison,
      // we have to undo the SPARQL-escaping of the input.

      loadedPropertyDef <- ontologyTriplestoreHelpers.loadPropertyDefinition(internalPropertyIri)

      maybeNewGuiElementPredicate =
        newGuiElementIri.map { (guiElement: SmartIri) =>
          SalsahGui.GuiElementProp.toSmartIri -> PredicateInfoV2(
            predicateIri = SalsahGui.GuiElementProp.toSmartIri,
            objects = Seq(SmartIriLiteralV2(guiElement)),
          )
        }

      maybeUnescapedNewGuiAttributePredicate =
        if (newGuiAttributeIris.nonEmpty) {
          Some(
            SalsahGui.GuiAttribute.toSmartIri -> PredicateInfoV2(
              predicateIri = SalsahGui.GuiAttribute.toSmartIri,
              objects = newGuiAttributeIris.map(StringLiteralV2.from(_, None)).toSeq,
            ),
          )
        } else {
          None
        }

      unescapedNewPropertyDef = currentReadPropertyInfo.entityInfoContent.copy(
                                  predicates = currentReadPropertyInfo.entityInfoContent.predicates -
                                    SalsahGui.GuiElementProp.toSmartIri -
                                    SalsahGui.GuiAttribute.toSmartIri ++
                                    maybeNewGuiElementPredicate ++
                                    maybeUnescapedNewGuiAttributePredicate,
                                )

      _ <- ZIO.when(loadedPropertyDef != unescapedNewPropertyDef) {
             val msg =
               s"Attempted to save property definition $unescapedNewPropertyDef, but $loadedPropertyDef was saved"
             ZIO.fail(InconsistentRepositoryDataException(msg))
           }

      maybeLoadedLinkValuePropertyDefFuture =
        maybeCurrentLinkValueReadPropertyInfo.map { linkValueReadPropertyInfo =>
          ontologyTriplestoreHelpers.loadPropertyDefinition(linkValueReadPropertyInfo.entityInfoContent.propertyIri)
        }

      maybeLoadedLinkValuePropertyDef <- ZIO.collectAll(maybeLoadedLinkValuePropertyDefFuture)

      maybeUnescapedNewLinkValuePropertyDef <-
        maybeLoadedLinkValuePropertyDef.map { loadedLinkValuePropertyDef =>
          val unescapedNewLinkPropertyDef = maybeCurrentLinkValueReadPropertyInfo.get.entityInfoContent.copy(
            predicates = maybeCurrentLinkValueReadPropertyInfo.get.entityInfoContent.predicates -
              SalsahGui.GuiElementProp.toSmartIri -
              SalsahGui.GuiAttribute.toSmartIri ++
              maybeNewGuiElementPredicate ++
              maybeUnescapedNewGuiAttributePredicate,
          )

          if (loadedLinkValuePropertyDef != unescapedNewLinkPropertyDef) {
            val msg =
              s"Attempted to save link value property definition $unescapedNewLinkPropertyDef, but $loadedLinkValuePropertyDef was saved"
            ZIO.fail(InconsistentRepositoryDataException(msg))
          } else {
            ZIO.some(unescapedNewLinkPropertyDef)
          }
        }.getOrElse(ZIO.none)

      // Update the ontology cache, using the unescaped definition(s).

      newReadPropertyInfo = ReadPropertyInfoV2(
                              entityInfoContent = unescapedNewPropertyDef,
                              isEditable = true,
                              isResourceProp = true,
                              isLinkProp = currentReadPropertyInfo.isLinkProp,
                            )

      maybeLinkValuePropertyCacheEntry =
        maybeUnescapedNewLinkValuePropertyDef.map { unescapedNewLinkPropertyDef =>
          unescapedNewLinkPropertyDef.propertyIri -> ReadPropertyInfoV2(
            entityInfoContent = unescapedNewLinkPropertyDef,
            isEditable = true,
            isResourceProp = true,
            isLinkValueProp = true,
          )
        }

      updatedOntologyMetadata = ontology.ontologyMetadata.copy(
                                  lastModificationDate = Some(currentTime),
                                )

      updatedOntology =
        ontology.copy(
          ontologyMetadata = updatedOntologyMetadata,
          properties =
            ontology.properties ++ maybeLinkValuePropertyCacheEntry + (internalPropertyIri -> newReadPropertyInfo),
        )

      _ <- ontologyCache.cacheUpdatedOntology(internalOntologyIri, updatedOntology)

      // Read the data back from the cache.

      response <- getPropertyDefinitionsFromOntologyV2(
                    propertyIris = Set(internalPropertyIri),
                    allLanguages = true,
                    requestingUser = changePropertyGuiElementRequest.requestingUser,
                  )
    } yield response

    for {
      requestingUser <- ZIO.succeed(changePropertyGuiElementRequest.requestingUser)

      externalPropertyIri = changePropertyGuiElementRequest.propertyIri.value.toSmartIri
      externalOntologyIri = externalPropertyIri.getOntologyFromEntity

      _ <- ontologyCacheHelpers.checkOntologyAndEntityIrisForUpdate(
             externalOntologyIri,
             externalPropertyIri,
             requestingUser,
           )

      internalPropertyIri = externalPropertyIri.toOntologySchema(InternalSchema)
      internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      changePropertyGuiElementRequest.apiRequestID,
                      ONTOLOGY_CACHE_LOCK_IRI,
                      makeTaskFuture(internalPropertyIri, internalOntologyIri),
                    )
    } yield taskResult
  }

  /**
   * Changes the values of `rdfs:label` or `rdfs:comment` in a property definition.
   *
   * @param changePropertyLabelsOrCommentsRequest the request to change the property's labels or comments.
   * @return a [[ReadOntologyV2]] containing the modified property definition.
   */
  private def changePropertyLabelsOrComments(
    changePropertyLabelsOrCommentsRequest: ChangePropertyLabelsOrCommentsRequestV2,
  ): Task[ReadOntologyV2] = {
    def makeTaskFuture(internalPropertyIri: SmartIri, internalOntologyIri: SmartIri): Task[ReadOntologyV2] = {
      for {
        cacheData <- ontologyCache.getCacheData

        ontology = cacheData.ontologies(internalOntologyIri)

        currentReadPropertyInfo <-
          ZIO
            .fromOption(ontology.properties.get(internalPropertyIri))
            .orElseFail(NotFoundException(s"Property ${changePropertyLabelsOrCommentsRequest.propertyIri} not found"))

        // Check that the ontology exists and has not been updated by another user since the client last read it.
        _ <- ontologyTriplestoreHelpers.checkOntologyLastModificationDateBeforeUpdate(
               internalOntologyIri,
               changePropertyLabelsOrCommentsRequest.lastModificationDate,
             )

        // If this is a link property, also change the labels/comments of the corresponding link value property.

        maybeCurrentLinkValueReadPropertyInfo <-
          if (currentReadPropertyInfo.isLinkProp) {
            val linkValuePropertyIri = internalPropertyIri.fromLinkPropToLinkValueProp
            ZIO
              .fromOption(ontology.properties.get(linkValuePropertyIri))
              .orElseFail(InconsistentRepositoryDataException(s"Link value property $linkValuePropertyIri not found"))
              .map(Some(_))
          } else {
            ZIO.none
          }

        // Do the update.
        currentTime: Instant = Instant.now

        updateSparql = sparql.v2.txt.changePropertyLabelsOrComments(
                         ontologyNamedGraphIri = internalOntologyIri,
                         ontologyIri = internalOntologyIri,
                         propertyIri = internalPropertyIri,
                         maybeLinkValuePropertyIri =
                           maybeCurrentLinkValueReadPropertyInfo.map(_.entityInfoContent.propertyIri),
                         predicateToUpdate = changePropertyLabelsOrCommentsRequest.predicateToUpdate,
                         newObjects = changePropertyLabelsOrCommentsRequest.newObjects,
                         lastModificationDate = changePropertyLabelsOrCommentsRequest.lastModificationDate,
                         currentTime = currentTime,
                       )
        _ <- triplestoreService.query(Update(updateSparql))

        // Check that the ontology's last modification date was updated.
        _ <- ontologyTriplestoreHelpers.checkOntologyLastModificationDateAfterUpdate(internalOntologyIri, currentTime)

        // Check that the data that was saved corresponds to the data that was submitted. To make this comparison,
        // we have to undo the SPARQL-escaping of the input.

        loadedPropertyDef <- ontologyTriplestoreHelpers.loadPropertyDefinition(internalPropertyIri)

        unescapedNewLabelOrCommentPredicate: PredicateInfoV2 =
          PredicateInfoV2(
            predicateIri = changePropertyLabelsOrCommentsRequest.predicateToUpdate,
            objects = changePropertyLabelsOrCommentsRequest.newObjects,
          ).unescape

        unescapedNewPropertyDef: PropertyInfoContentV2 =
          currentReadPropertyInfo.entityInfoContent.copy(
            predicates =
              currentReadPropertyInfo.entityInfoContent.predicates + (changePropertyLabelsOrCommentsRequest.predicateToUpdate -> unescapedNewLabelOrCommentPredicate),
          )

        _ <- ZIO.when(loadedPropertyDef != unescapedNewPropertyDef) {
               val msg =
                 s"Attempted to save property definition $unescapedNewPropertyDef, but $loadedPropertyDef was saved"
               ZIO.fail(InconsistentRepositoryDataException(msg))
             }

        maybeLoadedLinkValuePropertyDefFuture: Option[Task[PropertyInfoContentV2]] =
          maybeCurrentLinkValueReadPropertyInfo.map { linkValueReadPropertyInfo =>
            ontologyTriplestoreHelpers.loadPropertyDefinition(linkValueReadPropertyInfo.entityInfoContent.propertyIri)
          }

        maybeLoadedLinkValuePropertyDef <- ZIO.collectAll(maybeLoadedLinkValuePropertyDefFuture)

        maybeUnescapedNewLinkValuePropertyDef <-
          maybeLoadedLinkValuePropertyDef.map { loadedLinkValuePropertyDef =>
            val unescapedNewLinkPropertyDef = maybeCurrentLinkValueReadPropertyInfo.get.entityInfoContent.copy(
              predicates =
                maybeCurrentLinkValueReadPropertyInfo.get.entityInfoContent.predicates + (changePropertyLabelsOrCommentsRequest.predicateToUpdate -> unescapedNewLabelOrCommentPredicate),
            )

            if (loadedLinkValuePropertyDef != unescapedNewLinkPropertyDef) {
              ZIO.fail(
                InconsistentRepositoryDataException(
                  s"Attempted to save link value property definition $unescapedNewLinkPropertyDef, but $loadedLinkValuePropertyDef was saved",
                ),
              )
            } else {

              ZIO.succeed(Some(unescapedNewLinkPropertyDef))
            }
          }.getOrElse(ZIO.none)

        // Update the ontology cache, using the unescaped definition(s).

        newReadPropertyInfo = ReadPropertyInfoV2(
                                entityInfoContent = unescapedNewPropertyDef,
                                isEditable = true,
                                isResourceProp = true,
                                isLinkProp = currentReadPropertyInfo.isLinkProp,
                              )

        maybeLinkValuePropertyCacheEntry =
          maybeUnescapedNewLinkValuePropertyDef.map { unescapedNewLinkPropertyDef =>
            unescapedNewLinkPropertyDef.propertyIri -> ReadPropertyInfoV2(
              entityInfoContent = unescapedNewLinkPropertyDef,
              isEditable = true,
              isResourceProp = true,
              isLinkValueProp = true,
            )
          }

        updatedOntologyMetadata = ontology.ontologyMetadata.copy(
                                    lastModificationDate = Some(currentTime),
                                  )

        updatedOntology =
          ontology.copy(
            ontologyMetadata = updatedOntologyMetadata,
            properties =
              ontology.properties ++ maybeLinkValuePropertyCacheEntry + (internalPropertyIri -> newReadPropertyInfo),
          )

        _ <- ontologyCache.cacheUpdatedOntologyWithoutUpdatingMaps(internalOntologyIri, updatedOntology)

        // Read the data back from the cache.

        response <- getPropertyDefinitionsFromOntologyV2(
                      propertyIris = Set(internalPropertyIri),
                      allLanguages = true,
                      requestingUser = changePropertyLabelsOrCommentsRequest.requestingUser,
                    )
      } yield response
    }

    for {
      requestingUser <- ZIO.succeed(changePropertyLabelsOrCommentsRequest.requestingUser)

      externalPropertyIri = changePropertyLabelsOrCommentsRequest.propertyIri
      externalOntologyIri = externalPropertyIri.getOntologyFromEntity

      _ <- ontologyCacheHelpers.checkOntologyAndEntityIrisForUpdate(
             externalOntologyIri,
             externalPropertyIri,
             requestingUser,
           )

      internalPropertyIri = externalPropertyIri.toOntologySchema(InternalSchema)
      internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      changePropertyLabelsOrCommentsRequest.apiRequestID,
                      ONTOLOGY_CACHE_LOCK_IRI,
                      makeTaskFuture(internalPropertyIri, internalOntologyIri),
                    )
    } yield taskResult
  }

  /**
   * Changes the values of `rdfs:label` or `rdfs:comment` in a class definition.
   *
   * @param changeClassLabelsOrCommentsRequest the request to change the class's labels or comments.
   * @return a [[ReadOntologyV2]] containing the modified class definition.
   */
  private def changeClassLabelsOrComments(
    changeClassLabelsOrCommentsRequest: ChangeClassLabelsOrCommentsRequestV2,
  ): Task[ReadOntologyV2] = {
    def makeTaskFuture(internalClassIri: SmartIri, internalOntologyIri: SmartIri): Task[ReadOntologyV2] =
      for {
        cacheData <- ontologyCache.getCacheData

        ontology = cacheData.ontologies(internalOntologyIri)
        currentReadClassInfo <-
          ZIO
            .fromOption(ontology.classes.get(internalClassIri))
            .orElseFail(NotFoundException(s"Class ${changeClassLabelsOrCommentsRequest.classIri} not found"))

        // Check that the ontology exists and has not been updated by another user since the client last read it.
        _ <- ontologyTriplestoreHelpers.checkOntologyLastModificationDateBeforeUpdate(
               internalOntologyIri,
               changeClassLabelsOrCommentsRequest.lastModificationDate,
             )

        // Do the update.

        currentTime: Instant = Instant.now

        updateSparql = sparql.v2.txt.changeClassLabelsOrComments(
                         ontologyNamedGraphIri = internalOntologyIri,
                         ontologyIri = internalOntologyIri,
                         classIri = internalClassIri,
                         predicateToUpdate = changeClassLabelsOrCommentsRequest.predicateToUpdate,
                         newObjects = changeClassLabelsOrCommentsRequest.newObjects,
                         lastModificationDate = changeClassLabelsOrCommentsRequest.lastModificationDate,
                         currentTime = currentTime,
                       )
        _ <- triplestoreService.query(Update(updateSparql))

        // Check that the ontology's last modification date was updated.
        _ <- ontologyTriplestoreHelpers.checkOntologyLastModificationDateAfterUpdate(internalOntologyIri, currentTime)

        // Check that the data that was saved corresponds to the data that was submitted. To make this comparison,
        // we have to undo the SPARQL-escaping of the input.
        loadedClassDef <- ontologyTriplestoreHelpers.loadClassDefinition(internalClassIri)

        unescapedNewLabelOrCommentPredicate = PredicateInfoV2(
                                                predicateIri = changeClassLabelsOrCommentsRequest.predicateToUpdate,
                                                objects = changeClassLabelsOrCommentsRequest.newObjects,
                                              ).unescape

        unescapedNewClassDef: ClassInfoContentV2 =
          currentReadClassInfo.entityInfoContent.copy(
            predicates =
              currentReadClassInfo.entityInfoContent.predicates + (changeClassLabelsOrCommentsRequest.predicateToUpdate -> unescapedNewLabelOrCommentPredicate),
          )

        _ <- ZIO.when(loadedClassDef != unescapedNewClassDef) {
               val msg = s"Attempted to save class definition $unescapedNewClassDef, but $loadedClassDef was saved"
               ZIO.fail(InconsistentRepositoryDataException(msg))
             }

        // Update the ontology cache, using the unescaped definition(s).
        newReadClassInfo = currentReadClassInfo.copy(entityInfoContent = unescapedNewClassDef)

        updatedOntology = ontology.copy(
                            ontologyMetadata = ontology.ontologyMetadata.copy(
                              lastModificationDate = Some(currentTime),
                            ),
                            classes = ontology.classes + (internalClassIri -> newReadClassInfo),
                          )

        _ <- ontologyCache.cacheUpdatedOntologyWithoutUpdatingMaps(internalOntologyIri, updatedOntology)

        // Read the data back from the cache.

        response <- ontologyCacheHelpers.getClassDefinitionsFromOntologyV2(
                      classIris = Set(internalClassIri),
                      allLanguages = true,
                      requestingUser = changeClassLabelsOrCommentsRequest.requestingUser,
                    )
      } yield response

    for {
      requestingUser <- ZIO.succeed(changeClassLabelsOrCommentsRequest.requestingUser)

      externalClassIri    = changeClassLabelsOrCommentsRequest.classIri
      externalOntologyIri = externalClassIri.getOntologyFromEntity

      _ <-
        ontologyCacheHelpers.checkOntologyAndEntityIrisForUpdate(externalOntologyIri, externalClassIri, requestingUser)

      internalClassIri    = externalClassIri.toOntologySchema(InternalSchema)
      internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      changeClassLabelsOrCommentsRequest.apiRequestID,
                      ONTOLOGY_CACHE_LOCK_IRI,
                      makeTaskFuture(internalClassIri, internalOntologyIri),
                    )
    } yield taskResult
  }

  /**
   * Delete the `rdfs:comment` in a property definition.
   *
   * @param deletePropertyCommentRequest the request to delete the property's comment
   * @return a [[ReadOntologyV2]] containing the modified property definition.
   */
  private def deletePropertyComment(
    deletePropertyCommentRequest: DeletePropertyCommentRequestV2,
  ): Task[ReadOntologyV2] = {
    def makeTaskFuture(
      internalPropertyIri: SmartIri,
      internalOntologyIri: SmartIri,
      ontology: ReadOntologyV2,
      propertyToUpdate: ReadPropertyInfoV2,
    ) =
      for {

        // Check that the ontology exists and has not been updated by another user since the client last read its metadata.
        _ <- ontologyTriplestoreHelpers.checkOntologyLastModificationDateBeforeUpdate(
               internalOntologyIri,
               deletePropertyCommentRequest.lastModificationDate,
             )

        // If this is a link property, also delete the comment of the corresponding link value property.
        maybeLinkValueOfPropertyToUpdate <-
          if (propertyToUpdate.isLinkProp) {
            val linkValuePropertyIri = internalPropertyIri.fromLinkPropToLinkValueProp
            ZIO
              .fromOption(ontology.properties.get(linkValuePropertyIri))
              .orElseFail {
                val msg = s"Link value property $linkValuePropertyIri not found"
                InconsistentRepositoryDataException(msg)
              }
              .map(Some(_))
          } else {
            ZIO.none
          }

        maybeLinkValueOfPropertyToUpdateIri =
          if (propertyToUpdate.isLinkProp) { Some(internalPropertyIri.fromLinkPropToLinkValueProp) }
          else { None }

        currentTime = Instant.now

        // Delete the comment
        updateSparql = sparql.v2.txt.deletePropertyComment(
                         ontologyNamedGraphIri = internalOntologyIri,
                         ontologyIri = internalOntologyIri,
                         propertyIri = internalPropertyIri,
                         maybeLinkValuePropertyIri = maybeLinkValueOfPropertyToUpdateIri,
                         lastModificationDate = deletePropertyCommentRequest.lastModificationDate,
                         currentTime = currentTime,
                       )
        _ <- triplestoreService.query(Update(updateSparql))

        // Check that the ontology's last modification date was updated.
        _ <- ontologyTriplestoreHelpers.checkOntologyLastModificationDateAfterUpdate(internalOntologyIri, currentTime)

        // Check that the update was successful.
        loadedPropertyDef <- ontologyTriplestoreHelpers.loadPropertyDefinition(internalPropertyIri)

        propertyDefWithoutComment: PropertyInfoContentV2 =
          propertyToUpdate.entityInfoContent.copy(
            predicates = propertyToUpdate.entityInfoContent.predicates.-(
              OntologyConstants.Rdfs.Comment.toSmartIri,
            ), // the "-" deletes the entry with the comment
          )

        _ <- ZIO.when(loadedPropertyDef != propertyDefWithoutComment) {
               val msg =
                 s"Attempted to save property definition $propertyDefWithoutComment, but $loadedPropertyDef was saved"
               ZIO.fail(InconsistentRepositoryDataException(msg))
             }

        maybeLoadedLinkValuePropertyDefFuture: Option[Task[PropertyInfoContentV2]] =
          maybeLinkValueOfPropertyToUpdate.map { (linkValueReadPropertyInfo: ReadPropertyInfoV2) =>
            ontologyTriplestoreHelpers.loadPropertyDefinition(linkValueReadPropertyInfo.entityInfoContent.propertyIri)
          }

        maybeLoadedLinkValuePropertyDef <- ZIO.collectAll(maybeLoadedLinkValuePropertyDefFuture)

        maybeNewLinkValuePropertyDef <-
          maybeLoadedLinkValuePropertyDef.map { (loadedLinkValuePropertyDef: PropertyInfoContentV2) =>
            val newLinkPropertyDef: PropertyInfoContentV2 =
              maybeLinkValueOfPropertyToUpdate.get.entityInfoContent.copy(
                predicates = maybeLinkValueOfPropertyToUpdate.get.entityInfoContent.predicates
                  .-(OntologyConstants.Rdfs.Comment.toSmartIri),
              )
            if (loadedLinkValuePropertyDef != newLinkPropertyDef) {
              val msg =
                s"Attempted to save link value property definition $newLinkPropertyDef, but $loadedLinkValuePropertyDef was saved"
              ZIO.fail(InconsistentRepositoryDataException(msg))
            } else {
              ZIO.some(newLinkPropertyDef)
            }
          }.getOrElse(ZIO.none)

        // Update the ontology cache using the new property definition.
        newReadPropertyInfo = ReadPropertyInfoV2(
                                entityInfoContent = loadedPropertyDef,
                                isEditable = true,
                                isResourceProp = true,
                                isLinkProp = propertyToUpdate.isLinkProp,
                              )

        maybeLinkValuePropertyCacheEntry =
          maybeNewLinkValuePropertyDef.map { (newLinkPropertyDef: PropertyInfoContentV2) =>
            newLinkPropertyDef.propertyIri -> ReadPropertyInfoV2(
              entityInfoContent = newLinkPropertyDef,
              isEditable = true,
              isResourceProp = true,
              isLinkValueProp = true,
            )
          }

        updatedOntologyMetadata = ontology.ontologyMetadata.copy(lastModificationDate = Some(currentTime))

        updatedOntology: ReadOntologyV2 =
          ontology.copy(
            ontologyMetadata = updatedOntologyMetadata,
            properties =
              ontology.properties ++ maybeLinkValuePropertyCacheEntry + (internalPropertyIri -> newReadPropertyInfo),
          )

        _ <- ontologyCache.cacheUpdatedOntologyWithoutUpdatingMaps(internalOntologyIri, updatedOntology)

        // Read the data back from the cache.

        response <- getPropertyDefinitionsFromOntologyV2(
                      propertyIris = Set(internalPropertyIri),
                      allLanguages = true,
                      requestingUser = deletePropertyCommentRequest.requestingUser,
                    )

      } yield response

    for {
      requestingUser <- ZIO.succeed(deletePropertyCommentRequest.requestingUser)

      externalPropertyIri: SmartIri = deletePropertyCommentRequest.propertyIri
      externalOntologyIri: SmartIri = externalPropertyIri.getOntologyFromEntity

      _ <- ontologyCacheHelpers.checkOntologyAndEntityIrisForUpdate(
             externalOntologyIri,
             externalPropertyIri,
             requestingUser,
           )

      internalPropertyIri: SmartIri = externalPropertyIri.toOntologySchema(InternalSchema)
      internalOntologyIri: SmartIri = externalOntologyIri.toOntologySchema(InternalSchema)

      cacheData <- ontologyCache.getCacheData

      ontology: ReadOntologyV2 = cacheData.ontologies(internalOntologyIri)

      propertyToUpdate <-
        ZIO
          .fromOption(ontology.properties.get(internalPropertyIri))
          .orElseFail(NotFoundException(s"Property ${deletePropertyCommentRequest.propertyIri} not found"))

      hasComment: Boolean =
        propertyToUpdate.entityInfoContent.predicates.contains(OntologyConstants.Rdfs.Comment.toSmartIri)

      taskResult <-
        if (hasComment) for {
          // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
          taskResult <- IriLocker.runWithIriLock(
                          deletePropertyCommentRequest.apiRequestID,
                          ONTOLOGY_CACHE_LOCK_IRI,
                          makeTaskFuture(
                            internalPropertyIri = internalPropertyIri,
                            internalOntologyIri = internalOntologyIri,
                            ontology = ontology,
                            propertyToUpdate = propertyToUpdate,
                          ),
                        )
        } yield taskResult
        else {
          // not change anything if property has no comment
          getPropertyDefinitionsFromOntologyV2(
            propertyIris = Set(internalPropertyIri),
            allLanguages = true,
            requestingUser = deletePropertyCommentRequest.requestingUser,
          )
        }
    } yield taskResult
  }

  /**
   * Delete the `rdfs:comment` in a class definition.
   *
   * @param deleteClassCommentRequest the request to delete the class' comment
   * @return a [[ReadOntologyV2]] containing the modified class definition.
   */
  private def deleteClassComment(
    deleteClassCommentRequest: DeleteClassCommentRequestV2,
  ): Task[ReadOntologyV2] = {
    def makeTaskFuture(
      internalClassIri: SmartIri,
      internalOntologyIri: SmartIri,
      ontology: ReadOntologyV2,
      classToUpdate: ReadClassInfoV2,
    ) =
      for {

        // Check that the ontology exists and has not been updated by another user since the client last read its metadata.
        _ <- ontologyTriplestoreHelpers.checkOntologyLastModificationDateBeforeUpdate(
               internalOntologyIri,
               deleteClassCommentRequest.lastModificationDate,
             )

        currentTime: Instant = Instant.now

        // Delete the comment
        updateSparql = sparql.v2.txt
                         .deleteClassComment(
                           ontologyNamedGraphIri = internalOntologyIri,
                           ontologyIri = internalOntologyIri,
                           classIri = internalClassIri,
                           lastModificationDate = deleteClassCommentRequest.lastModificationDate,
                           currentTime = currentTime,
                         )
        _ <- triplestoreService.query(Update(updateSparql))

        // Check that the ontology's last modification date was updated.
        _ <- ontologyTriplestoreHelpers.checkOntologyLastModificationDateAfterUpdate(internalOntologyIri, currentTime)

        // Check that the update was successful.
        loadedClassDef <- ontologyTriplestoreHelpers.loadClassDefinition(internalClassIri)

        classDefWithoutComment: ClassInfoContentV2 =
          classToUpdate.entityInfoContent.copy(
            predicates = classToUpdate.entityInfoContent.predicates.-(
              OntologyConstants.Rdfs.Comment.toSmartIri,
            ), // the "-" deletes the entry with the comment
          )

        _ <- ZIO.when(loadedClassDef != classDefWithoutComment) {
               val msg = s"Attempted to save class definition $classDefWithoutComment, but $loadedClassDef was saved"
               ZIO.fail(InconsistentRepositoryDataException(msg))
             }

        // Update the ontology cache using the new class definition.
        newReadClassInfo        = classToUpdate.copy(entityInfoContent = classDefWithoutComment)
        updatedOntologyMetadata = ontology.ontologyMetadata.copy(lastModificationDate = Some(currentTime))

        updatedOntology: ReadOntologyV2 =
          ontology.copy(
            ontologyMetadata = updatedOntologyMetadata,
            classes = ontology.classes + (internalClassIri -> newReadClassInfo),
          )

        _ <- ontologyCache.cacheUpdatedOntologyWithoutUpdatingMaps(internalOntologyIri, updatedOntology)

        // Read the data back from the cache.
        response <- ontologyCacheHelpers.getClassDefinitionsFromOntologyV2(
                      classIris = Set(internalClassIri),
                      allLanguages = true,
                      requestingUser = deleteClassCommentRequest.requestingUser,
                    )

      } yield response

    for {
      requestingUser <- ZIO.succeed(deleteClassCommentRequest.requestingUser)

      externalClassIri: SmartIri    = deleteClassCommentRequest.classIri
      externalOntologyIri: SmartIri = externalClassIri.getOntologyFromEntity

      _ <-
        ontologyCacheHelpers.checkOntologyAndEntityIrisForUpdate(externalOntologyIri, externalClassIri, requestingUser)

      internalClassIri: SmartIri    = externalClassIri.toOntologySchema(InternalSchema)
      internalOntologyIri: SmartIri = externalOntologyIri.toOntologySchema(InternalSchema)

      cacheData <- ontologyCache.getCacheData

      ontology: ReadOntologyV2 = cacheData.ontologies(internalOntologyIri)

      classToUpdate <- ZIO
                         .fromOption(ontology.classes.get(internalClassIri))
                         .orElseFail(NotFoundException(s"Class ${deleteClassCommentRequest.classIri} not found"))

      hasComment: Boolean = classToUpdate.entityInfoContent.predicates.contains(
                              OntologyConstants.Rdfs.Comment.toSmartIri,
                            )

      taskResult <-
        if (hasComment) for {
          // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
          taskResult <- IriLocker.runWithIriLock(
                          deleteClassCommentRequest.apiRequestID,
                          ONTOLOGY_CACHE_LOCK_IRI,
                          makeTaskFuture(
                            internalClassIri = internalClassIri,
                            internalOntologyIri = internalOntologyIri,
                            ontology = ontology,
                            classToUpdate = classToUpdate,
                          ),
                        )
        } yield taskResult
        else {
          // not change anything if class has no comment
          ontologyCacheHelpers.getClassDefinitionsFromOntologyV2(
            classIris = Set(internalClassIri),
            allLanguages = true,
            requestingUser = deleteClassCommentRequest.requestingUser,
          )
        }
    } yield taskResult
  }
}

object OntologyResponderV2 {
  val layer: URLayer[
    AppConfig & CardinalityHandler & CardinalityService & IriService & KnoraProjectService & MessageRelay &
      OntologyCache & OntologyTriplestoreHelpers & OntologyCacheHelpers & OntologyRepo & StringFormatter &
      TriplestoreService,
    OntologyResponderV2,
  ] = ZLayer.fromZIO {
    for {
      ac       <- ZIO.service[AppConfig]
      ch       <- ZIO.service[CardinalityHandler]
      cs       <- ZIO.service[CardinalityService]
      is       <- ZIO.service[IriService]
      kr       <- ZIO.service[KnoraProjectService]
      oc       <- ZIO.service[OntologyCache]
      oth      <- ZIO.service[OntologyTriplestoreHelpers]
      och      <- ZIO.service[OntologyCacheHelpers]
      or       <- ZIO.service[OntologyRepo]
      sf       <- ZIO.service[StringFormatter]
      ts       <- ZIO.service[TriplestoreService]
      responder = OntologyResponderV2(ac, ch, cs, is, oc, och, oth, or, kr, ts)(sf)
      _        <- ZIO.serviceWithZIO[MessageRelay](_.subscribe(responder))
    } yield responder
  }
}
