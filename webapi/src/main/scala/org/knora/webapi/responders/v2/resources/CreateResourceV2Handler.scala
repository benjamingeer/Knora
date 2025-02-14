/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2.resources

import com.typesafe.scalalogging.LazyLogging
import zio.*

import java.time.Instant
import scala.language.postfixOps

import dsp.errors.*
import dsp.valueobjects.UuidUtil
import org.knora.webapi.*
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.*
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionType
import org.knora.webapi.messages.admin.responder.permissionsmessages.ResourceCreateOperation
import org.knora.webapi.messages.util.*
import org.knora.webapi.messages.util.PermissionUtilADM.AGreaterThanB
import org.knora.webapi.messages.util.PermissionUtilADM.PermissionComparisonResult
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.messages.v2.responder.ontologymessages.EntityInfoGetRequestV2
import org.knora.webapi.messages.v2.responder.ontologymessages.EntityInfoGetResponseV2
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality.*
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadClassInfoV2
import org.knora.webapi.messages.v2.responder.resourcemessages.*
import org.knora.webapi.messages.v2.responder.standoffmessages.*
import org.knora.webapi.messages.v2.responder.valuemessages.*
import org.knora.webapi.responders.IriLocker
import org.knora.webapi.responders.IriService
import org.knora.webapi.responders.admin.PermissionsResponder
import org.knora.webapi.responders.v2.*
import org.knora.webapi.slice.admin.api.model.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo
import org.knora.webapi.slice.admin.domain.service.KnoraUserRepo
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.ontology.domain.model.Cardinality.ExactlyOne
import org.knora.webapi.slice.ontology.domain.model.Cardinality.ZeroOrOne
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.ontology.domain.service.OntologyService
import org.knora.webapi.slice.ontology.domain.service.OntologyServiceLive
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.slice.resources.repo.model.FormattedTextValueType
import org.knora.webapi.slice.resources.repo.model.ResourceReadyToCreate
import org.knora.webapi.slice.resources.repo.model.StandoffAttribute
import org.knora.webapi.slice.resources.repo.model.StandoffAttributeValue
import org.knora.webapi.slice.resources.repo.model.StandoffLinkValueInfo
import org.knora.webapi.slice.resources.repo.model.StandoffTagInfo
import org.knora.webapi.slice.resources.repo.model.TypeSpecificValueInfo
import org.knora.webapi.slice.resources.repo.model.TypeSpecificValueInfo.*
import org.knora.webapi.slice.resources.repo.model.ValueInfo
import org.knora.webapi.slice.resources.repo.service.ResourcesRepo
import org.knora.webapi.util.ZioHelper

final case class CreateResourceV2Handler(
  appConfig: AppConfig,
  iriService: IriService,
  messageRelay: MessageRelay,
  resourcesRepo: ResourcesRepo,
  constructResponseUtilV2: ConstructResponseUtilV2,
  standoffTagUtilV2: StandoffTagUtilV2,
  resourceUtilV2: ResourceUtilV2,
  permissionUtilADM: PermissionUtilADM,
  searchResponderV2: SearchResponderV2,
  getResources: GetResources,
  ontologyRepo: OntologyRepo,
  permissionsResponder: PermissionsResponder,
  ontologyService: OntologyService,
)(implicit val stringFormatter: StringFormatter)
    extends LazyLogging {

  /**
   * Creates a new resource.
   *
   * @param createResourceRequestV2 the request to create the resource.
   * @return a [[ReadResourcesSequenceV2]] containing a preview of the resource.
   */
  def apply(createResourceRequestV2: CreateResourceRequestV2): Task[ReadResourcesSequenceV2] =
    triplestoreUpdate(createResourceRequestV2)

  private def triplestoreUpdate(
    createResourceRequestV2: CreateResourceRequestV2,
  ): Task[ReadResourcesSequenceV2] =
    for {
      _         <- ensureNotAnonymousUser(createResourceRequestV2.requestingUser)
      _         <- ensureClassBelongsToProjectOntology(createResourceRequestV2)
      projectIri = createResourceRequestV2.createResource.projectADM.id
      _         <- ensureUserHasPermission(createResourceRequestV2, projectIri)

      resourceIri <-
        iriService.checkOrCreateEntityIri(
          createResourceRequestV2.createResource.resourceIri,
          stringFormatter.makeRandomResourceIri(createResourceRequestV2.createResource.projectADM.shortcode),
        )
      taskResult <- IriLocker.runWithIriLock(
                      createResourceRequestV2.apiRequestID,
                      resourceIri,
                      makeTask(createResourceRequestV2, resourceIri),
                    )
    } yield taskResult

  private def ensureNotAnonymousUser(user: User): Task[Unit] =
    ZIO
      .when(user.isAnonymousUser)(ZIO.fail(ForbiddenException("Anonymous users aren't allowed to create resources")))
      .ignore

  private def ensureClassBelongsToProjectOntology(createResourceRequestV2: CreateResourceRequestV2): Task[Unit] = for {
    projectIri <- ZIO.succeed(createResourceRequestV2.createResource.projectADM.id)
    isSystemOrSharedProject =
      projectIri == KnoraProjectRepo.builtIn.SystemProject.id ||
        projectIri == OntologyConstants.KnoraAdmin.DefaultSharedOntologiesProject
    _ <- ZIO.when(isSystemOrSharedProject)(
           ZIO.fail(BadRequestException(s"Resources cannot be created in project <$projectIri>")),
         )

    resourceClassOntologyIri =
      createResourceRequestV2.createResource.resourceClassIri.getOntologyFromEntity.toInternalIri
    resourceClassProjectIri <-
      ontologyService
        .getProjectIriForOntologyIri(resourceClassOntologyIri)
        .someOrFail(BadRequestException(s"Ontology $resourceClassOntologyIri not found"))

    _ <-
      ZIO
        .fail(
          BadRequestException(
            s"Cannot create a resource in project <$projectIri> with resource class <${createResourceRequestV2.createResource.resourceClassIri}>, which is defined in a non-shared ontology in another project",
          ),
        )
        .unless(
          projectIri.value == resourceClassProjectIri || OntologyServiceLive.isBuiltInOrSharedOntology(
            resourceClassOntologyIri,
          ),
        )
  } yield ()

  private def ensureUserHasPermission(createResourceRequestV2: CreateResourceRequestV2, projectIri: ProjectIri) = for {
    internalResourceClassIri <-
      ZIO.succeed(createResourceRequestV2.createResource.resourceClassIri.toOntologySchema(InternalSchema))
    _ <-
      ZIO
        .fail(
          ForbiddenException(
            s"User ${createResourceRequestV2.requestingUser.username} does not have permission to create a resource of class <${createResourceRequestV2.createResource.resourceClassIri}> in project <$projectIri>",
          ),
        )
        .unless(
          createResourceRequestV2.requestingUser.permissions
            .hasPermissionFor(ResourceCreateOperation(internalResourceClassIri.toString), projectIri.value),
        )
  } yield ()

  private def makeTask(
    createResourceRequestV2: CreateResourceRequestV2,
    resourceIri: IRI,
  ): Task[ReadResourcesSequenceV2] =
    for {
      _ <- // check if resourceIri already exists holding a lock on the IRI
        ZIO
          .fail(DuplicateValueException(s"Resource IRI: '$resourceIri' already exists."))
          .whenZIO(iriService.checkIriExists(resourceIri))
      project = createResourceRequestV2.createResource.projectADM

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
      defaultResourcePermissions <- permissionsResponder.newResourceDefaultObjectAccessPermissions(
                                      createResourceRequestV2.createResource.projectADM.id,
                                      internalCreateResource.resourceClassIri,
                                      createResourceRequestV2.requestingUser,
                                    )

      // Get the default permissions of each property used.
      defaultPropertyPermissionsMap <- permissionsResponder.newValueDefaultObjectAccessPermissions(
                                         createResourceRequestV2.createResource.projectADM.id,
                                         internalCreateResource.resourceClassIri,
                                         internalCreateResource.values.keySet,
                                         createResourceRequestV2.requestingUser,
                                       )

      // Do the remaining pre-update checks and make a ResourceReadyToCreate describing the SPARQL
      // for creating the resource.
      resourceReadyToCreate <-
        generateResourceReadyToCreate(
          resourceIri = resourceIri,
          internalCreateResource = internalCreateResource,
          linkTargetClasses = linkTargetClasses,
          entityInfo = allEntityInfo,
          clientResourceIDs = Map.empty[IRI, String],
          defaultResourcePermissions = defaultResourcePermissions.permissionLiteral,
          defaultPropertyPermissions = defaultPropertyPermissionsMap.map((k, v) => (k, v.permissionLiteral)),
          creationDate = internalCreateResource.creationDate.getOrElse(Instant.now),
          requestingUser = createResourceRequestV2.requestingUser,
        )

      dataNamedGraph = ProjectService.projectDataNamedGraphV2(createResourceRequestV2.createResource.projectADM)

      _ <- resourcesRepo.createNewResource(
             dataGraphIri = dataNamedGraph,
             resource = resourceReadyToCreate,
             projectIri = InternalIri(createResourceRequestV2.createResource.projectADM.id.value),
             userIri = InternalIri(createResourceRequestV2.requestingUser.id),
           )

      // Verify that the resource was created.
      previewOfCreatedResource <- verifyResource(
                                    resourceIri = resourceReadyToCreate.resourceIri.value,
                                    requestingUser = createResourceRequestV2.requestingUser,
                                  )
    } yield previewOfCreatedResource

  /**
   * Generates a [[ResourceReadyToCreate]] describing SPARQL for creating a resource and its values.
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
      requiredProps = knoraPropertyCardinalities.filter { case (_, cardinality) =>
                        cardinality.isRequired
                      }.keySet -- resourceClassInfo.linkProperties
      propsWithoutValues =
        internalCreateResource.values.collect { case (key, values) if values.nonEmpty => key }.toSet
      missingProps = requiredProps -- propsWithoutValues
      _ <- ZIO.fail {
             val externalResourceClassIri =
               s"<${internalCreateResource.resourceClassIri.toOntologySchema(ApiV2Complex)}>"
             val externalMissingPropIris =
               missingProps.map(iri => s"<${iri.toOntologySchema(ApiV2Complex)}>").mkString(", ")
             val msg =
               s"${resourceIDForErrorMsg}Values were not submitted for the following property or properties, which are required by resource class $externalResourceClassIri: $externalMissingPropIris"
             OntologyConstraintException(msg)
           }.when(missingProps.nonEmpty)

      // Check that each submitted value is consistent with the knora-base:objectClassConstraint of the property that is supposed to
      // point to it.
      _ <-
        ZIO.foreachDiscard(internalCreateResource.values) {
          case (iri: SmartIri, values: Seq[CreateValueInNewResourceV2]) =>
            CheckObjectClassConstraints(
              iri,
              values,
              linkTargetClasses,
              entityInfo,
              clientResourceIDs,
              resourceIDForErrorMsg,
              ontologyRepo,
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
                     !(requestingUser.permissions.isProjectAdmin(internalCreateResource.projectADM.id.value) &&
                       !requestingUser.permissions.isSystemAdmin)
                   } {
                     // Make sure they don't give themselves higher permissions than they would get from the default permissions.
                     val permissionComparisonResult: PermissionComparisonResult =
                       PermissionUtilADM.comparePermissionsADM(
                         internalCreateResource.projectADM.id.value,
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

      linkUpdates <- generateInsertSparqlForStandoffLinksInMultipleValues(
                       resourceIri = resourceIri,
                       values = valuesWithValidatedPermissions.values.flatten,
                     )

      valuesWithIndex = valuesWithValidatedPermissions.flatMap {
                          case (propertyIri: SmartIri, valuesToCreate: Seq[GenerateSparqlForValueInNewResourceV2]) =>
                            valuesToCreate.zipWithIndex.map {
                              case (valueToCreate: GenerateSparqlForValueInNewResourceV2, valueHasOrder: Int) =>
                                (propertyIri, valueToCreate, valueHasOrder)
                            }
                        }.toList

      newValueInfos <-
        ZIO.foreach(valuesWithIndex) { case (propertyIri, valueToCreate, valueHasOrder) =>
          for {
            newValueUUID <-
              ValuesResponderV2.makeNewValueUUID(valueToCreate.customValueIri, valueToCreate.customValueUUID)
            newValueIri <-
              iriService.checkOrCreateEntityIri(
                valueToCreate.customValueIri,
                StringFormatter.makeValueIri(resourceIri, newValueUUID),
              )

            // Make a creation date for the value. If a custom creation date is given for a value, consider that otherwise
            // use resource creation date for the value.
            valueCreationDate: Instant = valueToCreate.customValueCreationDate.getOrElse(creationDate)

            valueInfo <-
              valueToCreate.valueContent match
                case DateValueContentV2(
                      _,
                      valueHasStartJDN,
                      valueHasEndJDN,
                      valueHasStartPrecision,
                      valueHasEndPrecision,
                      valueHasCalendar,
                      _,
                    ) =>
                  ZIO.succeed(
                    DateValueInfo(
                      valueHasStartJDN = valueHasStartJDN,
                      valueHasEndJDN = valueHasEndJDN,
                      valueHasStartPrecision = valueHasStartPrecision,
                      valueHasEndPrecision = valueHasEndPrecision,
                      valueHasCalendar = valueHasCalendar,
                    ),
                  )
                case TextValueContentV2(_, _, _, valueHasLanguage, _, None, _, _, _) =>
                  ZIO.succeed(UnformattedTextValueInfo(valueHasLanguage))
                case tv @ TextValueContentV2(_, _, textType, valueHasLanguage, _, Some(mappingIri), _, _, _) =>
                  val standoffTags = generateStandoffInfo(tv, newValueIri)
                  generateFormattedTextValueInfo(
                    standoffTags,
                    tv.computedMaxStandoffStartIndex,
                    textType,
                    valueHasLanguage,
                    mappingIri,
                  )
                case IntegerValueContentV2(_, valueHasInteger, _) =>
                  ZIO.succeed(IntegerValueInfo(valueHasInteger))
                case DecimalValueContentV2(_, valueHasDecimal, _) =>
                  ZIO.succeed(DecimalValueInfo(valueHasDecimal))
                case BooleanValueContentV2(_, valueHasBoolean, _) =>
                  ZIO.succeed(BooleanValueInfo(valueHasBoolean))
                case GeomValueContentV2(_, valueHasGeometry, _) =>
                  ZIO.succeed(GeomValueInfo(valueHasGeometry))
                case IntervalValueContentV2(_, valueHasIntervalStart, valueHasIntervalEnd, _) =>
                  ZIO.succeed(IntervalValueInfo(valueHasIntervalStart, valueHasIntervalEnd))
                case TimeValueContentV2(_, valueHasTimeStamp, _) =>
                  ZIO.succeed(TimeValueInfo(valueHasTimeStamp))
                case HierarchicalListValueContentV2(_, valueHasListNode, _, _) =>
                  ZIO.succeed(HierarchicalListValueInfo(InternalIri(valueHasListNode)))
                case ColorValueContentV2(_, valueHasColor, _) =>
                  ZIO.succeed(ColorValueInfo(valueHasColor))
                case UriValueContentV2(_, valueHasUri, _) =>
                  ZIO.succeed(UriValueInfo(valueHasUri))
                case GeonameValueContentV2(_, valueHasGeonameCode, _) =>
                  ZIO.succeed(GeonameValueInfo(valueHasGeonameCode))
                case StillImageFileValueContentV2(_, fileValue, dimX, dimY, _) =>
                  ZIO.succeed(StillImageFileValueInfo(fileValue, dimX, dimY))
                case StillImageExternalFileValueContentV2(_, fileValue, externalUrl, _) =>
                  ZIO.succeed(StillImageExternalFileValueInfo(fileValue, externalUrl.value.toString))
                case DocumentFileValueContentV2(_, fileValue, pageCount, dimX, dimY, _) =>
                  ZIO.succeed(DocumentFileValueInfo(fileValue = fileValue, dimX, dimY, pageCount))
                case ArchiveFileValueContentV2(_, fileValue, _) =>
                  ZIO.succeed(OtherFileValueInfo(fileValue))
                case TextFileValueContentV2(_, fileValue, _) =>
                  ZIO.succeed(OtherFileValueInfo(fileValue))
                case AudioFileValueContentV2(_, fileValue, _) =>
                  ZIO.succeed(OtherFileValueInfo(fileValue))
                case MovingImageFileValueContentV2(_, fileValue, _) =>
                  ZIO.succeed(OtherFileValueInfo(fileValue))
                case LinkValueContentV2(
                      _,
                      referredResourceIri,
                      referredResourceExists,
                      isIncomingLink,
                      nestedResource,
                      _,
                    ) =>
                  ZIO.succeed(LinkValueInfo(InternalIri(referredResourceIri)))
                case _: DeletedValueContentV2 => ZIO.fail(BadRequestException("Deleted values cannot be created"))

          } yield ValueInfo(
            resourceIri = InternalIri(resourceIri),
            propertyIri = InternalIri(propertyIri.toIri),
            value = valueInfo,
            valueIri = InternalIri(newValueIri),
            valueTypeIri = InternalIri(valueToCreate.valueContent.valueType.toString()),
            valueUUID = newValueUUID,
            creator = InternalIri(requestingUser.id),
            permissions = valueToCreate.permissions,
            creationDate = valueCreationDate,
            valueHasOrder = valueHasOrder,
            valueHasString = valueToCreate.valueContent.unescape.valueHasString,
            comment = valueToCreate.valueContent.comment,
          )
        }
    } yield ResourceReadyToCreate(
      resourceIri = InternalIri(resourceIri),
      resourceClassIri = InternalIri(internalCreateResource.resourceClassIri.toString),
      resourceLabel = internalCreateResource.label,
      creationDate = creationDate,
      permissions = resourcePermissions,
      valueInfos = newValueInfos,
      standoffLinks = linkUpdates,
    )
  }

  private def generateStandoffInfo(tv: TextValueContentV2, newValueIri: IRI): Seq[StandoffTagInfo] =
    tv
      .prepareForSparqlInsert(newValueIri)
      .map(standoffTag =>
        val attributes = standoffTag.standoffNode.attributes.map { attr =>
          val v = attr match
            case StandoffTagIriAttributeV2(_, value, _) =>
              StandoffAttributeValue.IriAttribute(InternalIri(value))
            case StandoffTagUriAttributeV2(_, value) => StandoffAttributeValue.UriAttribute(value)
            case StandoffTagInternalReferenceAttributeV2(_, value) =>
              StandoffAttributeValue.InternalReferenceAttribute(InternalIri(value))
            case StandoffTagStringAttributeV2(_, value)  => StandoffAttributeValue.StringAttribute(value)
            case StandoffTagIntegerAttributeV2(_, value) => StandoffAttributeValue.IntegerAttribute(value)
            case StandoffTagDecimalAttributeV2(_, value) => StandoffAttributeValue.DecimalAttribute(value)
            case StandoffTagBooleanAttributeV2(_, value) => StandoffAttributeValue.BooleanAttribute(value)
            case StandoffTagTimeAttributeV2(_, value)    => StandoffAttributeValue.TimeAttribute(value)
          StandoffAttribute(InternalIri(attr.standoffPropertyIri.toString()), v)
        }
        StandoffTagInfo(
          standoffTagClassIri = InternalIri(standoffTag.standoffNode.standoffTagClassIri.toString()),
          standoffTagInstanceIri = InternalIri(standoffTag.standoffTagInstanceIri),
          startParentIri = standoffTag.startParentIri.map(InternalIri.apply),
          endParentIri = standoffTag.endParentIri.map(InternalIri.apply),
          uuid = standoffTag.standoffNode.uuid,
          originalXMLID = standoffTag.standoffNode.originalXMLID,
          startIndex = standoffTag.standoffNode.startIndex,
          endIndex = standoffTag.standoffNode.endIndex,
          startPosition = standoffTag.standoffNode.startPosition,
          endPosition = standoffTag.standoffNode.endPosition,
          attributes = attributes,
        ),
      )

  private def generateFormattedTextValueInfo(
    standoffInfo: Seq[StandoffTagInfo],
    maxStandoffStartIndex: Option[Int],
    textType: TextValueType,
    valueHasLanguage: Option[String],
    mappingIri: String,
  ): IO[StandoffInternalException, TypeSpecificValueInfo] =
    ZIO
      .whenCase(textType) {
        case TextValueType.FormattedText => ZIO.succeed(FormattedTextValueType.StandardMapping)
        case TextValueType.CustomFormattedText(mappingIri) =>
          ZIO.succeed(FormattedTextValueType.CustomMapping(mappingIri))
      }
      .someOrFail(StandoffInternalException("Text type does not match mapping information"))
      .flatMap { textType =>
        ZIO
          .fromOption(maxStandoffStartIndex)
          .orElseFail(StandoffInternalException("Max standoff start index not computed"))
          .map(standoffStartIndex =>
            FormattedTextValueInfo(
              valueHasLanguage,
              InternalIri(mappingIri),
              standoffStartIndex,
              standoffInfo,
              textType,
            ),
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
                           .isProjectAdmin(project.id.value) || requestingUser.permissions.isSystemAdmin),
                       ) {

                         // No. Make sure they don't give themselves higher permissions than they would get from the default permissions.

                         val permissionComparisonResult: PermissionComparisonResult =
                           PermissionUtilADM.comparePermissionsADM(
                             entityProject = project.id.value,
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
   * Checks that a resource was created.
   *
   * @param resourceIri    the IRI of the resource that should have been created.
   * @param requestingUser the user that attempted to create the resource.
   * @return a preview of the resource that was created.
   */
  private def verifyResource(
    resourceIri: IRI,
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2] =
    getResources
      .getResourcesV2(
        resourceIris = Seq(resourceIri),
        requestingUser = requestingUser,
        targetSchema = ApiV2Complex,
        schemaOptions = SchemaOptions.ForStandoffWithTextValues,
      )
      .mapError { case _: NotFoundException =>
        UpdateNotPerformedException(
          s"Resource <$resourceIri> was not created. Please report this as a possible bug.",
        )
      }

  private def generateInsertSparqlForStandoffLinksInMultipleValues(
    resourceIri: IRI,
    values: Iterable[GenerateSparqlForValueInNewResourceV2],
  ): Task[Seq[StandoffLinkValueInfo]] = {
    // To create LinkValues for the standoff links in the values to be created, we need to compute
    // the initial reference count of each LinkValue. This is equal to the number of TextValues in the resource
    // that have standoff links to a particular target resource.

    // First, get the standoff link targets from all the text values to be created.
    val standoffLinkTargetsPerTextValue: Vector[Set[IRI]] =
      values.foldLeft(Vector.empty[Set[IRI]]) {
        case (standoffLinkTargetsAcc: Vector[Set[IRI]], createValueV2: GenerateSparqlForValueInNewResourceV2) =>
          createValueV2.valueContent match {
            case textValueContentV2: TextValueContentV2
                if textValueContentV2.standoffLinkTagTargetResourceIris.nonEmpty =>
              standoffLinkTargetsAcc :+ textValueContentV2.standoffLinkTagTargetResourceIris

            case _ => standoffLinkTargetsAcc
          }
      }

    if (standoffLinkTargetsPerTextValue.nonEmpty) {
      // Combine those resource references into a single list, so if there are n text values with a link to
      // some IRI, the list will contain that IRI n times.
      val allStandoffLinkTargets: Vector[IRI] = standoffLinkTargetsPerTextValue.flatten

      // Now we need to count the number of times each IRI occurs in allStandoffLinkTargets. To do this, first
      // use groupBy(identity). The groupBy method takes a function that returns a key for each item in the
      // collection, and makes a Map in which items with the same key are grouped together. The identity
      // function just returns its argument. So groupBy(identity) makes a Map[IRI, Vector[IRI]] in which each
      // IRI points to a sequence of the same IRI repeated as many times as it occurred in allStandoffLinkTargets.
      val allStandoffLinkTargetsGrouped: Map[IRI, Vector[IRI]] = allStandoffLinkTargets.groupBy(identity)

      // Replace each Vector[IRI] with its size. That's the number of text values containing
      // standoff links to that IRI.
      val initialReferenceCounts: Map[IRI, Int] = allStandoffLinkTargetsGrouped.view.mapValues(_.size).toMap

      // For each standoff link target IRI, construct a SparqlTemplateLinkUpdate to create a hasStandoffLinkTo property
      // and one LinkValue with its initial reference count.
      val standoffLinkUpdatesFutures: Seq[Task[StandoffLinkValueInfo]] = initialReferenceCounts.toSeq.map {
        case (targetIri, initialReferenceCount) =>
          for {
            newValueIri <- makeUnusedValueIri(resourceIri)
          } yield StandoffLinkValueInfo(
            linkPropertyIri = InternalIri(OntologyConstants.KnoraBase.HasStandoffLinkTo),
            newLinkValueIri = InternalIri(newValueIri),
            linkTargetIri = InternalIri(targetIri),
            newReferenceCount = initialReferenceCount,
            newLinkValueCreator = InternalIri(KnoraUserRepo.builtIn.SystemUser.id.value),
            newLinkValuePermissions = standoffLinkValuePermissions,
            valueUuid = UuidUtil.makeRandomBase64EncodedUuid,
          )
      }
      ZIO.collectAll(standoffLinkUpdatesFutures)
    } else {
      ZIO.succeed(Seq.empty[StandoffLinkValueInfo])
    }
  }

  /**
   * A convenience method for generating an unused random value IRI.
   *
   * @param resourceIri the IRI of the containing resource.
   * @return the new value IRI.
   */
  private def makeUnusedValueIri(resourceIri: IRI): Task[IRI] =
    iriService.makeUnusedIri(stringFormatter.makeRandomValueIri(resourceIri))

  /**
   * The permissions that are granted by every `knora-base:LinkValue` describing a standoff link.
   */
  private lazy val standoffLinkValuePermissions: String = {
    val permissions: Set[PermissionADM] = Set(
      PermissionADM.from(Permission.ObjectAccess.ChangeRights, KnoraUserRepo.builtIn.SystemUser.id.value),
      PermissionADM.from(Permission.ObjectAccess.View, KnoraGroupRepo.builtIn.UnknownUser.id.value),
    )

    PermissionUtilADM.formatPermissionADMs(permissions, PermissionType.OAP)
  }

}
