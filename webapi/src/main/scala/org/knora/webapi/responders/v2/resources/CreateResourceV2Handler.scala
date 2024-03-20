/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2.resources

import com.typesafe.scalalogging.LazyLogging
import zio.*

import java.time.Instant

import dsp.errors.*
import dsp.valueobjects.Iri
import org.knora.webapi.*
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.*
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.admin.responder.permissionsmessages.DefaultObjectAccessPermissionsStringForResourceClassGetADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.DefaultObjectAccessPermissionsStringResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.ResourceCreateOperation
import org.knora.webapi.messages.admin.responder.projectsmessages.*
import org.knora.webapi.messages.twirl.SparqlTemplateResourceToCreate
import org.knora.webapi.messages.twirl.queries.sparql
import org.knora.webapi.messages.util.*
import org.knora.webapi.messages.util.PermissionUtilADM.AGreaterThanB
import org.knora.webapi.messages.util.PermissionUtilADM.PermissionComparisonResult
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.messages.v2.responder.ontologymessages.*
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality.*
import org.knora.webapi.messages.v2.responder.resourcemessages.*
import org.knora.webapi.messages.v2.responder.resourcemessages.CreateResourceRequestV2.AssetIngestState
import org.knora.webapi.messages.v2.responder.valuemessages.*
import org.knora.webapi.responders.IriLocker
import org.knora.webapi.responders.IriService
import org.knora.webapi.responders.v2.*
import org.knora.webapi.responders.v2.resources.CheckObjectClassConstraints
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.ontology.domain.model.Cardinality.AtLeastOne
import org.knora.webapi.slice.ontology.domain.model.Cardinality.ExactlyOne
import org.knora.webapi.slice.ontology.domain.model.Cardinality.ZeroOrOne
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update
import org.knora.webapi.util.ZioHelper

final case class CreateResourceV2Handler(
  appConfig: AppConfig,
  iriService: IriService,
  messageRelay: MessageRelay,
  triplestore: TriplestoreService,
  constructResponseUtilV2: ConstructResponseUtilV2,
  standoffTagUtilV2: StandoffTagUtilV2,
  resourceUtilV2: ResourceUtilV2,
  permissionUtilADM: PermissionUtilADM,
  searchResponderV2: SearchResponderV2,
  getResources: GetResources,
  implicit val stringFormatter: StringFormatter,
) extends LazyLogging {

  /**
   * Represents a resource that is ready to be created and whose contents can be verified afterwards.
   *
   * @param sparqlTemplateResourceToCreate a [[SparqlTemplateResourceToCreate]] describing SPARQL for creating
   *                                       the resource.
   * @param values                         the resource's values for verification.
   * @param hasStandoffLink                `true` if the property `knora-base:hasStandoffLinkToValue` was automatically added.
   */
  private case class ResourceReadyToCreate(
    sparqlTemplateResourceToCreate: SparqlTemplateResourceToCreate,
    values: Map[SmartIri, Seq[UnverifiedValueV2]],
    hasStandoffLink: Boolean,
  )

  /**
   * Creates a new resource.
   *
   * @param createResourceRequestV2 the request to create the resource.
   * @return a [[ReadResourcesSequenceV2]] containing a preview of the resource.
   */
  def apply(createResourceRequestV2: CreateResourceRequestV2): Task[ReadResourcesSequenceV2] =
    createResourceRequestV2.ingestState match {
      case AssetIngestState.AssetIngested =>
        triplestoreUpdate(createResourceRequestV2)
      // If the request includes file values, tell Sipi to move the files to permanent storage if the update
      // succeeded, or to delete the temporary files if the update failed.
      case AssetIngestState.AssetInTemp =>
        val fileValues = Seq(createResourceRequestV2.createResource)
          .flatMap(_.flatValues)
          .map(_.valueContent)
          .filter(_.isInstanceOf[FileValueContentV2])
          .map(_.asInstanceOf[FileValueContentV2])
        resourceUtilV2.doSipiPostUpdate(
          triplestoreUpdate(createResourceRequestV2),
          fileValues,
          createResourceRequestV2.requestingUser,
        )
    }

  private def triplestoreUpdate(
    createResourceRequestV2: CreateResourceRequestV2,
  ): Task[ReadResourcesSequenceV2] =
    for {
      // Don't allow anonymous users to create resources.
      _ <- ZIO.when(createResourceRequestV2.requestingUser.isAnonymousUser) {
             ZIO.fail(ForbiddenException("Anonymous users aren't allowed to create resources"))
           }

      // Ensure that the project isn't the system project or the shared ontologies project.
      projectIri = createResourceRequestV2.createResource.projectADM.id
      _ <-
        ZIO.when(
          projectIri == OntologyConstants.KnoraAdmin.SystemProject || projectIri == OntologyConstants.KnoraAdmin.DefaultSharedOntologiesProject,
        )(ZIO.fail(BadRequestException(s"Resources cannot be created in project <$projectIri>")))

      // Ensure that the resource class isn't from a non-shared ontology in another project.

      resourceClassOntologyIri: SmartIri = createResourceRequestV2.createResource.resourceClassIri.getOntologyFromEntity
      readOntologyMetadataV2 <- messageRelay
                                  .ask[ReadOntologyMetadataV2](
                                    OntologyMetadataGetByIriRequestV2(
                                      Set(resourceClassOntologyIri),
                                      createResourceRequestV2.requestingUser,
                                    ),
                                  )
      ontologyMetadata <- ZIO
                            .fromOption(readOntologyMetadataV2.ontologies.headOption)
                            .orElseFail(BadRequestException(s"Ontology $resourceClassOntologyIri not found"))
      ontologyProjectIri <-
        ZIO
          .fromOption(ontologyMetadata.projectIri)
          .mapBoth(
            _ => InconsistentRepositoryDataException(s"Ontology $resourceClassOntologyIri has no project"),
            _.toString(),
          )

      _ <-
        ZIO.when(
          projectIri != ontologyProjectIri && !(ontologyMetadata.ontologyIri.isKnoraBuiltInDefinitionIri || ontologyMetadata.ontologyIri.isKnoraSharedDefinitionIri),
        ) {
          val msg =
            s"Cannot create a resource in project <$projectIri> with resource class <${createResourceRequestV2.createResource.resourceClassIri}>, which is defined in a non-shared ontology in another project"
          ZIO.fail(BadRequestException(msg))
        }

      // Check user's PermissionProfile (part of UserADM) to see if the user has the permission to
      // create a new resource in the given project.

      internalResourceClassIri: SmartIri = createResourceRequestV2.createResource.resourceClassIri
                                             .toOntologySchema(InternalSchema)

      _ <- ZIO.when(
             !createResourceRequestV2.requestingUser.permissions
               .hasPermissionFor(ResourceCreateOperation(internalResourceClassIri.toString), projectIri),
           ) {
             val msg =
               s"User ${createResourceRequestV2.requestingUser.username} does not have permission to create a resource of class <${createResourceRequestV2.createResource.resourceClassIri}> in project <$projectIri>"
             ZIO.fail(ForbiddenException(msg))
           }

      resourceIri <-
        iriService.checkOrCreateEntityIri(
          createResourceRequestV2.createResource.resourceIri,
          stringFormatter.makeRandomResourceIri(createResourceRequestV2.createResource.projectADM.shortcode),
        )

      // Do the remaining pre-update checks and the update while holding an update lock on the resource to be created.
      taskResult <- IriLocker.runWithIriLock(
                      createResourceRequestV2.apiRequestID,
                      resourceIri,
                      makeTask(createResourceRequestV2, resourceIri),
                    )
    } yield taskResult

  private def makeTask(
    createResourceRequestV2: CreateResourceRequestV2,
    resourceIri: IRI,
  ): Task[ReadResourcesSequenceV2] = {
    for {
      _ <- // check if resourceIri already exists holding a lock on the IRI
        ZIO
          .fail(DuplicateValueException(s"Resource IRI: '$resourceIri' already exists."))
          .whenZIO(iriService.checkIriExists(resourceIri))

      // Convert the resource to the internal ontology schema.
      internalCreateResource <- ZIO.attempt(createResourceRequestV2.createResource.toOntologySchema(InternalSchema))

      // Check link targets and list nodes that should exist.
      _ <- checkStandoffLinkTargets(
             values = internalCreateResource.flatValues,
             requestingUser = createResourceRequestV2.requestingUser,
           )

      _ <- checkListNodes(internalCreateResource.flatValues)

      // Get the class IRIs of all the link targets in the request.
      linkTargetClasses <- getLinkTargetClasses(
                             resourceIri: IRI,
                             internalCreateResources = Seq(internalCreateResource),
                             requestingUser = createResourceRequestV2.requestingUser,
                           )

      // Get the definitions of the resource class and its properties, as well as of the classes of all
      // resources that are link targets.
      resourceClassEntityInfoResponse <-
        messageRelay
          .ask[EntityInfoGetResponseV2](
            EntityInfoGetRequestV2(
              classIris = linkTargetClasses.values.toSet + internalCreateResource.resourceClassIri,
              requestingUser = createResourceRequestV2.requestingUser,
            ),
          )

      resourceClassInfo: ReadClassInfoV2 = resourceClassEntityInfoResponse.classInfoMap(
                                             internalCreateResource.resourceClassIri,
                                           )

      propertyEntityInfoResponse <-
        messageRelay
          .ask[EntityInfoGetResponseV2](
            EntityInfoGetRequestV2(
              propertyIris = resourceClassInfo.knoraResourceProperties,
              requestingUser = createResourceRequestV2.requestingUser,
            ),
          )

      allEntityInfo = EntityInfoGetResponseV2(
                        classInfoMap = resourceClassEntityInfoResponse.classInfoMap,
                        propertyInfoMap = propertyEntityInfoResponse.propertyInfoMap,
                      )

      // Get the default permissions of the resource class.

      defaultResourcePermissionsMap <- getResourceClassDefaultPermissions(
                                         projectIri = createResourceRequestV2.createResource.projectADM.id,
                                         resourceClassIris = Set(internalCreateResource.resourceClassIri),
                                         requestingUser = createResourceRequestV2.requestingUser,
                                       )

      defaultResourcePermissions: String = defaultResourcePermissionsMap(internalCreateResource.resourceClassIri)

      // Get the default permissions of each property used.

      defaultPropertyPermissionsMap <- getDefaultPropertyPermissions(
                                         projectIri = createResourceRequestV2.createResource.projectADM.id,
                                         resourceClassProperties = Map(
                                           internalCreateResource.resourceClassIri -> internalCreateResource.values.keySet,
                                         ),
                                         requestingUser = createResourceRequestV2.requestingUser,
                                       )
      defaultPropertyPermissions: Map[SmartIri, String] = defaultPropertyPermissionsMap(
                                                            internalCreateResource.resourceClassIri,
                                                          )

      // Make a versionDate for the resource and its values.
      creationDate: Instant = internalCreateResource.creationDate.getOrElse(Instant.now)

      // Do the remaining pre-update checks and make a ResourceReadyToCreate describing the SPARQL
      // for creating the resource.
      resourceReadyToCreate <- generateResourceReadyToCreate(
                                 resourceIri = resourceIri,
                                 internalCreateResource = internalCreateResource,
                                 linkTargetClasses = linkTargetClasses,
                                 entityInfo = allEntityInfo,
                                 clientResourceIDs = Map.empty[IRI, String],
                                 defaultResourcePermissions = defaultResourcePermissions,
                                 defaultPropertyPermissions = defaultPropertyPermissions,
                                 creationDate = creationDate,
                                 requestingUser = createResourceRequestV2.requestingUser,
                               )

      // Get the IRI of the named graph in which the resource will be created.
      dataNamedGraph =
        ProjectService.projectDataNamedGraphV2(createResourceRequestV2.createResource.projectADM).value

      // Generate SPARQL for creating the resource.
      sparqlUpdate = sparql.v2.txt.createNewResources(
                       dataNamedGraph = dataNamedGraph,
                       resourcesToCreate = Seq(resourceReadyToCreate.sparqlTemplateResourceToCreate),
                       projectIri = createResourceRequestV2.createResource.projectADM.id,
                       creatorIri = createResourceRequestV2.requestingUser.id,
                     )
      // Do the update.
      _ <- triplestore.query(Update(sparqlUpdate))

      // Verify that the resource was created correctly.
      previewOfCreatedResource <- verifyResource(
                                    resourceReadyToCreate = resourceReadyToCreate,
                                    projectIri = createResourceRequestV2.createResource.projectADM.id,
                                    requestingUser = createResourceRequestV2.requestingUser,
                                  )
    } yield previewOfCreatedResource
  }

  /**
   * Generates a [[SparqlTemplateResourceToCreate]] describing SPARQL for creating a resource and its values.
   * This method does pre-update checks that have to be done for each new resource individually, even when
   * multiple resources are being created in a single request.
   *
   * @param internalCreateResource     the resource to be created.
   * @param linkTargetClasses          a map of resources that are link targets to the IRIs of those resources' classes.
   * @param entityInfo                 an [[EntityInfoGetResponseV2]] containing definitions of the class of the resource to
   *                                   be created, as well as the classes that all the link targets
   *                                   belong to.
   * @param clientResourceIDs          a map of IRIs of resources to be created to client IDs for the same resources, if any.
   * @param defaultResourcePermissions the default permissions to be given to the resource, if it does not have custom permissions.
   * @param defaultPropertyPermissions the default permissions to be given to the resource's values, if they do not
   *                                   have custom permissions. This is a map of property IRIs to permission strings.
   * @param creationDate               the versionDate to be attached to the resource and its values.
   *
   * @param requestingUser             the user making the request.
   * @return a [[ResourceReadyToCreate]].
   */
  private def generateResourceReadyToCreate(
    resourceIri: IRI,
    internalCreateResource: CreateResourceV2,
    linkTargetClasses: Map[IRI, SmartIri],
    entityInfo: EntityInfoGetResponseV2,
    clientResourceIDs: Map[IRI, String],
    defaultResourcePermissions: String,
    defaultPropertyPermissions: Map[SmartIri, String],
    creationDate: Instant,
    requestingUser: User,
  ): Task[ResourceReadyToCreate] = {
    val resourceIDForErrorMsg: String =
      clientResourceIDs.get(resourceIri).map(resourceID => s"In resource '$resourceID': ").getOrElse("")

    for {
      // Check that the resource class has a suitable cardinality for each submitted value.
      resourceClassInfo <- ZIO.attempt(entityInfo.classInfoMap(internalCreateResource.resourceClassIri))

      knoraPropertyCardinalities: Map[SmartIri, KnoraCardinalityInfo] =
        resourceClassInfo.allCardinalities.view
          .filterKeys(resourceClassInfo.knoraResourceProperties)
          .toMap

      _ <- ZIO.foreachDiscard(internalCreateResource.values) {
             case (propertyIri: SmartIri, valuesForProperty: Seq[CreateValueInNewResourceV2]) =>
               val internalPropertyIri = propertyIri.toOntologySchema(InternalSchema)
               for {

                 cardinalityInfo <-
                   ZIO
                     .fromOption(knoraPropertyCardinalities.get(internalPropertyIri))
                     .orElseFail(
                       OntologyConstraintException(
                         s"${resourceIDForErrorMsg}Resource class <${internalCreateResource.resourceClassIri
                             .toOntologySchema(ApiV2Complex)}> has no cardinality for property <$propertyIri>",
                       ),
                     )

                 _ <-
                   ZIO.when(
                     (cardinalityInfo.cardinality == ZeroOrOne || cardinalityInfo.cardinality == ExactlyOne) && valuesForProperty.size > 1,
                   ) {
                     ZIO.fail(
                       OntologyConstraintException(
                         s"${resourceIDForErrorMsg}Resource class <${internalCreateResource.resourceClassIri
                             .toOntologySchema(ApiV2Complex)}> does not allow more than one value for property <$propertyIri>",
                       ),
                     )
                   }
               } yield ()
           }

      // Check that no required values are missing.

      requiredProps: Set[SmartIri] = knoraPropertyCardinalities.filter { case (_, cardinalityInfo) =>
                                       cardinalityInfo.cardinality == ExactlyOne || cardinalityInfo.cardinality == AtLeastOne
                                     }.keySet -- resourceClassInfo.linkProperties

      internalPropertyIris: Set[SmartIri] = internalCreateResource.values.keySet

      _ <- ZIO.when(!requiredProps.subsetOf(internalPropertyIris)) {
             val missingProps =
               (requiredProps -- internalPropertyIris)
                 .map(iri => s"<${iri.toOntologySchema(ApiV2Complex)}>")
                 .mkString(", ")
             ZIO.fail(
               OntologyConstraintException(
                 s"${resourceIDForErrorMsg}Values were not submitted for the following property or properties, which are required by resource class <${internalCreateResource.resourceClassIri
                     .toOntologySchema(ApiV2Complex)}>: $missingProps",
               ),
             )
           }

      // Check that each submitted value is consistent with the knora-base:objectClassConstraint of the property that is supposed to
      // point to it.
      _ <- ZIO.foreachDiscard(internalCreateResource.values) {
             case (iri: SmartIri, values: Seq[CreateValueInNewResourceV2]) =>
               CheckObjectClassConstraints(
                 iri,
                 values,
                 linkTargetClasses,
                 entityInfo,
                 clientResourceIDs,
                 resourceIDForErrorMsg,
               )
           }

      // Check that the submitted values do not contain duplicates.
      _ <- checkForDuplicateValues(internalCreateResource.values, resourceIDForErrorMsg)

      // Validate and reformat any custom permissions in the request, and set all permissions to defaults if custom
      // permissions are not provided.

      resourcePermissions <-
        internalCreateResource.permissions match {
          case Some(permissionStr) =>
            for {
              validatedCustomPermissions <- permissionUtilADM.validatePermissions(permissionStr)

              _ <- ZIO.when {
                     !(requestingUser.permissions.isProjectAdmin(internalCreateResource.projectADM.id) &&
                       !requestingUser.permissions.isSystemAdmin)
                   } {
                     // Make sure they don't give themselves higher permissions than they would get from the default permissions.
                     val permissionComparisonResult: PermissionComparisonResult =
                       PermissionUtilADM.comparePermissionsADM(
                         internalCreateResource.projectADM.id,
                         validatedCustomPermissions,
                         defaultResourcePermissions,
                         requestingUser,
                       )
                     ZIO.when(permissionComparisonResult == AGreaterThanB) {
                       val msg =
                         s"${resourceIDForErrorMsg}The specified permissions would give the resource's creator a higher permission on the resource than the default permissions"
                       ZIO.fail(ForbiddenException(msg))
                     }
                   }
            } yield validatedCustomPermissions

          case None => ZIO.succeed(defaultResourcePermissions)
        }

      valuesWithValidatedPermissions <-
        validateAndFormatValuePermissions(
          project = internalCreateResource.projectADM,
          values = internalCreateResource.values,
          defaultPropertyPermissions = defaultPropertyPermissions,
          resourceIDForErrorMsg = resourceIDForErrorMsg,
          requestingUser = requestingUser,
        )

      // Ask the values responder for SPARQL for generating the values.
      sparqlForValuesResponse <-
        messageRelay
          .ask[GenerateSparqlToCreateMultipleValuesResponseV2](
            GenerateSparqlToCreateMultipleValuesRequestV2(
              resourceIri = resourceIri,
              values = valuesWithValidatedPermissions,
              creationDate = creationDate,
              requestingUser = requestingUser,
            ),
          )
    } yield ResourceReadyToCreate(
      sparqlTemplateResourceToCreate = SparqlTemplateResourceToCreate(
        resourceIri = resourceIri,
        permissions = resourcePermissions,
        sparqlForValues = sparqlForValuesResponse.insertSparql,
        resourceClassIri = internalCreateResource.resourceClassIri.toString,
        resourceLabel = internalCreateResource.label,
        resourceCreationDate = creationDate,
      ),
      values = sparqlForValuesResponse.unverifiedValues,
      hasStandoffLink = sparqlForValuesResponse.hasStandoffLink,
    )
  }

  /**
   * Given a sequence of resources to be created, gets the class IRIs of all the resources that are the targets of
   * link values in the new resources, whether these already exist in the triplestore or are among the resources
   * to be created.
   *
   * @param internalCreateResources the resources to be created.
   *
   * @param requestingUser          the user making the request.
   * @return a map of resource IRIs to class IRIs.
   */
  private def getLinkTargetClasses(
    resourceIri: IRI,
    internalCreateResources: Seq[CreateResourceV2],
    requestingUser: User,
  ): Task[Map[IRI, SmartIri]] = {
    // Get the IRIs of the new and existing resources that are targets of links.
    val (existingTargetIris: Set[IRI], newTargets: Set[IRI]) =
      internalCreateResources.flatMap(_.flatValues).foldLeft((Set.empty[IRI], Set.empty[IRI])) {
        case ((accExisting: Set[IRI], accNew: Set[IRI]), valueToCreate: CreateValueInNewResourceV2) =>
          valueToCreate.valueContent match {
            case linkValueContentV2: LinkValueContentV2 =>
              if (linkValueContentV2.referredResourceExists) {
                (accExisting + linkValueContentV2.referredResourceIri, accNew)
              } else {
                (accExisting, accNew + linkValueContentV2.referredResourceIri)
              }

            case _ => (accExisting, accNew)
          }
      }

    // Make a map of the IRIs of new target resources to their class IRIs.
    val classesOfNewTargets: Map[IRI, SmartIri] = internalCreateResources.map { resourceToCreate =>
      resourceIri -> resourceToCreate.resourceClassIri
    }.toMap.view
      .filterKeys(newTargets)
      .toMap

    for {
      // Get information about the existing resources that are targets of links.
      existingTargets <- getResources.getResourcePreviewV2(
                           resourceIris = existingTargetIris.toSeq,
                           targetSchema = ApiV2Complex,
                           requestingUser = requestingUser,
                         )

      // Make a map of the IRIs of existing target resources to their class IRIs.
      classesOfExistingTargets: Map[IRI, SmartIri] =
        existingTargets.resources
          .map(resource => resource.resourceIri -> resource.resourceClassIri)
          .toMap
    } yield classesOfNewTargets ++ classesOfExistingTargets
  }

  /**
   * Checks that values to be created in a new resource do not contain duplicates.
   *
   * @param values                a map of property IRIs to values to be created (in the internal schema).
   * @param resourceIDForErrorMsg something that can be prepended to an error message to specify the client's ID for the
   *                              resource to be created, if any.
   */
  private def checkForDuplicateValues(
    values: Map[SmartIri, Seq[CreateValueInNewResourceV2]],
    resourceIDForErrorMsg: IRI,
  ): Task[Unit] =
    ZIO.foreachDiscard(values) { case (propertyIri: SmartIri, valuesToCreate: Seq[CreateValueInNewResourceV2]) =>
      // Given the values for a property, compute all possible combinations of two of those values.
      ZIO.foreachDiscard(valuesToCreate.combinations(2).toSeq) { valueCombination =>
        // valueCombination must have two elements.

        val firstValue: ValueContentV2  = valueCombination.head.valueContent
        val secondValue: ValueContentV2 = valueCombination(1).valueContent

        ZIO.when(firstValue.wouldDuplicateOtherValue(secondValue)) {
          val msg =
            s"${resourceIDForErrorMsg}Duplicate values for property <${propertyIri.toOntologySchema(ApiV2Complex)}>"
          ZIO.fail(DuplicateValueException(msg))
        }
      }
    }

  /**
   * Given a sequence of values to be created in a new resource, checks the targets of standoff links in text
   * values. For each link, if the target is expected to exist, checks that it exists and that the user has
   * permission to see it.
   *
   * @param values               the values to be checked.
   *
   * @param requestingUser       the user making the request.
   */
  private def checkStandoffLinkTargets(
    values: Iterable[CreateValueInNewResourceV2],
    requestingUser: User,
  ): Task[Unit] = {
    val standoffLinkTargetsThatShouldExist: Set[IRI] = values.foldLeft(Set.empty[IRI]) {
      case (acc: Set[IRI], valueToCreate: CreateValueInNewResourceV2) =>
        valueToCreate.valueContent match {
          case textValueContentV2: TextValueContentV2 =>
            acc ++ textValueContentV2.standoffLinkTagIriAttributes.filter(_.targetExists).map(_.value)
          case _ => acc
        }
    }

    getResources
      .getResourcePreviewV2(
        resourceIris = standoffLinkTargetsThatShouldExist.toSeq,
        targetSchema = ApiV2Complex,
        requestingUser = requestingUser,
      )
      .unit
  }

  /**
   * Given a sequence of values to be created in a new resource, checks the existence of the list nodes referred to
   * in list values.
   *
   * @param values         the values to be checked.
   */
  private def checkListNodes(values: Iterable[CreateValueInNewResourceV2]): Task[Unit] = {
    val listNodesThatShouldExist: Set[IRI] = values.foldLeft(Set.empty[IRI]) {
      case (acc: Set[IRI], valueToCreate: CreateValueInNewResourceV2) =>
        valueToCreate.valueContent match {
          case hierarchicalListValueContentV2: HierarchicalListValueContentV2 =>
            acc + hierarchicalListValueContentV2.valueHasListNode
          case _ => acc
        }
    }

    ZIO
      .collectAll(
        listNodesThatShouldExist.map { listNodeIri =>
          for {
            checkNode <- resourceUtilV2.checkListNodeExistsAndIsRootNode(listNodeIri)
            _ <-
              checkNode match {
                // it doesn't have isRootNode property - it's a child node
                case Right(false) => ZIO.unit
                // it does have isRootNode property - it's a root node
                case Right(true) =>
                  ZIO.fail(BadRequestException(s"<$listNodeIri> is a root node. Root nodes cannot be set as values."))
                // it doesn't exists or isn't valid list
                case Left(_) =>
                  ZIO.fail(NotFoundException(s"<$listNodeIri> does not exist, or is not a ListNode."))
              }
          } yield ()
        }.toSeq,
      )
      .unit
  }

  /**
   * Given a map of property IRIs to values to be created in a new resource, validates and reformats any custom
   * permissions in the values, and sets all value permissions to defaults if custom permissions are not provided.
   *
   * @param project                    the project in which the resource is to be created.
   * @param values                     the values whose permissions are to be validated.
   * @param defaultPropertyPermissions a map of property IRIs to default permissions.
   * @param resourceIDForErrorMsg      a string that can be prepended to an error message to specify the client's
   *                                   ID for the containing resource, if provided.
   * @param requestingUser             the user making the request.
   * @return a map of property IRIs to sequences of [[GenerateSparqlForValueInNewResourceV2]], in which
   *         all permissions have been validated and defined.
   */
  private def validateAndFormatValuePermissions(
    project: Project,
    values: Map[SmartIri, Seq[CreateValueInNewResourceV2]],
    defaultPropertyPermissions: Map[SmartIri, String],
    resourceIDForErrorMsg: String,
    requestingUser: User,
  ): Task[Map[SmartIri, Seq[GenerateSparqlForValueInNewResourceV2]]] = {
    val propertyValuesWithValidatedPermissionsFutures: Map[SmartIri, Seq[Task[GenerateSparqlForValueInNewResourceV2]]] =
      values.map { case (propertyIri: SmartIri, valuesToCreate: Seq[CreateValueInNewResourceV2]) =>
        val validatedPermissionFutures: Seq[Task[GenerateSparqlForValueInNewResourceV2]] = valuesToCreate.map {
          valueToCreate =>
            // Does this value have custom permissions?
            valueToCreate.permissions match {
              case Some(permissionStr: String) =>
                // Yes. Validate and reformat them.
                for {
                  validatedCustomPermissions <- permissionUtilADM.validatePermissions(permissionStr)

                  // Is the requesting user a system admin, or an admin of this project?
                  _ <- ZIO.when(
                         !(requestingUser.permissions
                           .isProjectAdmin(project.id) || requestingUser.permissions.isSystemAdmin),
                       ) {

                         // No. Make sure they don't give themselves higher permissions than they would get from the default permissions.

                         val permissionComparisonResult: PermissionComparisonResult =
                           PermissionUtilADM.comparePermissionsADM(
                             entityProject = project.id,
                             permissionLiteralA = validatedCustomPermissions,
                             permissionLiteralB = defaultPropertyPermissions(propertyIri),
                             requestingUser = requestingUser,
                           )

                         ZIO.when(permissionComparisonResult == AGreaterThanB) {
                           ZIO.fail(
                             ForbiddenException(
                               s"${resourceIDForErrorMsg}The specified value permissions would give a value's creator a higher permission on the value than the default permissions",
                             ),
                           )
                         }
                       }
                } yield GenerateSparqlForValueInNewResourceV2(
                  valueContent = valueToCreate.valueContent,
                  customValueIri = valueToCreate.customValueIri,
                  customValueUUID = valueToCreate.customValueUUID,
                  customValueCreationDate = valueToCreate.customValueCreationDate,
                  permissions = validatedCustomPermissions,
                )

              case None =>
                // No. Use the default permissions.
                ZIO.succeed {
                  GenerateSparqlForValueInNewResourceV2(
                    valueContent = valueToCreate.valueContent,
                    customValueIri = valueToCreate.customValueIri,
                    customValueUUID = valueToCreate.customValueUUID,
                    customValueCreationDate = valueToCreate.customValueCreationDate,
                    permissions = defaultPropertyPermissions(propertyIri),
                  )
                }
            }
        }

        propertyIri -> validatedPermissionFutures
      }

    ZioHelper.sequence(propertyValuesWithValidatedPermissionsFutures.map { case (k, v) => k -> ZIO.collectAll(v) })
  }

  /**
   * Gets the default permissions for resource classs in a project.
   *
   * @param projectIri        the IRI of the project.
   * @param resourceClassIris the internal IRIs of the resource classes.
   * @param requestingUser    the user making the request.
   * @return a map of resource class IRIs to default permission strings.
   */
  private def getResourceClassDefaultPermissions(
    projectIri: IRI,
    resourceClassIris: Set[SmartIri],
    requestingUser: User,
  ): Task[Map[SmartIri, String]] = {
    val permissionsFutures: Map[SmartIri, Task[String]] = resourceClassIris.toSeq.map { resourceClassIri =>
      val requestMessage = DefaultObjectAccessPermissionsStringForResourceClassGetADM(
        projectIri = projectIri,
        resourceClassIri = resourceClassIri.toString,
        targetUser = requestingUser,
        requestingUser = KnoraSystemInstances.Users.SystemUser,
      )

      resourceClassIri ->
        messageRelay
          .ask[DefaultObjectAccessPermissionsStringResponseADM](requestMessage)
          .map(_.permissionLiteral)
    }.toMap

    ZioHelper.sequence(permissionsFutures)
  }

  /**
   * Gets the default permissions for properties in a resource class in a project.
   *
   * @param projectIri              the IRI of the project.
   * @param resourceClassProperties a map of internal resource class IRIs to sets of internal property IRIs.
   * @param requestingUser          the user making the request.
   * @return a map of internal resource class IRIs to maps of property IRIs to default permission strings.
   */
  private def getDefaultPropertyPermissions(
    projectIri: IRI,
    resourceClassProperties: Map[SmartIri, Set[SmartIri]],
    requestingUser: User,
  ): Task[Map[SmartIri, Map[SmartIri, String]]] = {
    val permissionsFutures: Map[SmartIri, Task[Map[SmartIri, String]]] = resourceClassProperties.map {
      case (resourceClassIri, propertyIris) =>
        val propertyPermissionsFutures: Map[SmartIri, Task[String]] = propertyIris.toSeq.map { propertyIri =>
          propertyIri -> resourceUtilV2.getDefaultValuePermissions(
            projectIri = projectIri,
            resourceClassIri = resourceClassIri,
            propertyIri = propertyIri,
            requestingUser = requestingUser,
          )
        }.toMap

        resourceClassIri -> ZioHelper.sequence(propertyPermissionsFutures)
    }

    ZioHelper.sequence(permissionsFutures)
  }

  /**
   * Checks that a resource was created correctly.
   *
   * @param resourceReadyToCreate the resource that should have been created.
   * @param projectIri            the IRI of the project in which the resource should have been created.
   *
   * @param requestingUser        the user that attempted to create the resource.
   * @return a preview of the resource that was created.
   */
  private def verifyResource(
    resourceReadyToCreate: ResourceReadyToCreate,
    projectIri: IRI,
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2] = {
    val resourceIri = resourceReadyToCreate.sparqlTemplateResourceToCreate.resourceIri

    val resourceFuture: Task[ReadResourcesSequenceV2] = for {
      resourcesResponse <- getResources.getResourcesV2(
                             resourceIris = Seq(resourceIri),
                             requestingUser = requestingUser,
                             targetSchema = ApiV2Complex,
                             schemaOptions = SchemaOptions.ForStandoffWithTextValues,
                           )

      resource: ReadResourceV2 = resourcesResponse.toResource(requestedResourceIri = resourceIri)

      _ <- ZIO.when(
             resource.resourceClassIri.toString != resourceReadyToCreate.sparqlTemplateResourceToCreate.resourceClassIri,
           ) {
             ZIO.fail(AssertionException(s"Resource <$resourceIri> was saved, but it has the wrong resource class"))
           }

      _ <- ZIO.when(resource.attachedToUser != requestingUser.id) {
             ZIO.fail(AssertionException(s"Resource <$resourceIri> was saved, but it is attached to the wrong user"))
           }

      _ <- ZIO.when(resource.projectADM.id != projectIri) {
             ZIO.fail(AssertionException(s"Resource <$resourceIri> was saved, but it is attached to the wrong user"))
           }

      _ <- ZIO.when(resource.permissions != resourceReadyToCreate.sparqlTemplateResourceToCreate.permissions) {
             ZIO.fail(AssertionException(s"Resource <$resourceIri> was saved, but it has the wrong permissions"))
           }

      // Undo any escapes in the submitted rdfs:label to compare it with the saved one.
      unescapedLabel: String = Iri.fromSparqlEncodedString(
                                 resourceReadyToCreate.sparqlTemplateResourceToCreate.resourceLabel,
                               )

      _ <- ZIO.when(resource.label != unescapedLabel) {
             ZIO.fail(AssertionException(s"Resource <$resourceIri> was saved, but it has the wrong label"))
           }

      savedPropertyIris: Set[SmartIri] = resource.values.keySet

      // Check that the property knora-base:hasStandoffLinkToValue was automatically added if necessary.
      expectedPropertyIris: Set[SmartIri] =
        resourceReadyToCreate.values.keySet ++ (if (resourceReadyToCreate.hasStandoffLink) {
                                                  Some(OntologyConstants.KnoraBase.HasStandoffLinkToValue.toSmartIri)
                                                } else { None })

      _ <- ZIO.when(savedPropertyIris != expectedPropertyIris) {
             val msg =
               s"Resource <$resourceIri> was saved, but it has the wrong properties: expected (${expectedPropertyIris
                   .map(_.toSparql)
                   .mkString(", ")}), but saved (${savedPropertyIris.map(_.toSparql).mkString(", ")})"
             ZIO.fail(AssertionException(msg))
           }

      // Ignore knora-base:hasStandoffLinkToValue when checking the expected values.
      _ <- ZIO.foreachDiscard(resource.values - OntologyConstants.KnoraBase.HasStandoffLinkToValue.toSmartIri) {
             case (propertyIri: SmartIri, savedValues: Seq[ReadValueV2]) =>
               val expectedValues: Seq[UnverifiedValueV2] = resourceReadyToCreate.values(propertyIri)
               for {
                 _ <- ZIO.when(expectedValues.size != savedValues.size) {
                        ZIO.fail(AssertionException(s"Resource <$resourceIri> was saved, but it has the wrong values"))
                      }

                 _ <- ZIO.foreachDiscard(savedValues.zip(expectedValues)) { case (savedValue, expectedValue) =>
                        ZIO.when(
                          !(expectedValue.valueContent.wouldDuplicateCurrentVersion(savedValue.valueContent) &&
                            savedValue.permissions == expectedValue.permissions &&
                            savedValue.attachedToUser == requestingUser.id),
                        ) {
                          val msg = s"Resource <$resourceIri> was saved, but one or more of its values are not correct"
                          ZIO.fail(AssertionException(msg))
                        }
                      }
               } yield ()
           }
    } yield ReadResourcesSequenceV2(resources = Seq(resource.copy(values = Map.empty)))

    resourceFuture.mapError { case _: NotFoundException =>
      UpdateNotPerformedException(
        s"Resource <$resourceIri> was not created. Please report this as a possible bug.",
      )
    }
  }
}
