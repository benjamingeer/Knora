/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import zio.*

import java.time.Instant
import java.util.UUID

import dsp.errors.*
import dsp.valueobjects.UuidUtil
import org.knora.webapi.*
import org.knora.webapi.SchemaRendering.apiV2SchemaWithOption
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.*
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex as KA
import org.knora.webapi.messages.OntologyConstants.KnoraBase.StillImageExternalFileValue
import org.knora.webapi.messages.OntologyConstants.KnoraBase.StillImageFileValue
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionType
import org.knora.webapi.messages.twirl.SparqlTemplateLinkUpdate
import org.knora.webapi.messages.twirl.queries.sparql
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.messages.util.PermissionUtilADM.*
import org.knora.webapi.messages.util.search.gravsearch.GravsearchParser
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.ontologymessages.*
import org.knora.webapi.messages.v2.responder.resourcemessages.*
import org.knora.webapi.messages.v2.responder.valuemessages.*
import org.knora.webapi.messages.v2.responder.valuemessages.ValueMessagesV2Optics.FileValueContentV2Optics.*
import org.knora.webapi.responders.IriLocker
import org.knora.webapi.responders.IriService
import org.knora.webapi.responders.admin.PermissionsResponder
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.KnoraUserRepo
import org.knora.webapi.slice.admin.domain.service.LegalInfoService
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.common.KnoraIris.ResourceIri
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.ontology.domain.model.Cardinality.AtLeastOne
import org.knora.webapi.slice.ontology.domain.model.Cardinality.ExactlyOne
import org.knora.webapi.slice.ontology.domain.model.Cardinality.ZeroOrOne
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

final case class ValuesResponderV2(
  appConfig: AppConfig,
  iriService: IriService,
  iriConverter: IriConverter,
  projectService: KnoraProjectService,
  messageRelay: MessageRelay,
  permissionUtilADM: PermissionUtilADM,
  resourceUtilV2: ResourceUtilV2,
  searchResponderV2: SearchResponderV2,
  triplestoreService: TriplestoreService,
  permissionsResponder: PermissionsResponder,
  legalInfoService: LegalInfoService,
  ontologyRepo: OntologyRepo,
  auth: AuthorizationRestService,
)(implicit val stringFormatter: StringFormatter) {

  /**
   * Creates a new value in an existing resource.
   *
   * @param valueToCreate the value to be created.
   * @param requestingUser the user making the request.
   * @param apiRequestID the API request ID.
   * @return a [[CreateValueResponseV2]].
   */
  def createValueV2(
    valueToCreate: CreateValueV2,
    requestingUser: User,
    apiRequestID: UUID,
  ): Task[CreateValueResponseV2] = {
    def taskZio: Task[CreateValueResponseV2] = {
      for {
        resourceIri <-
          iriConverter
            .asSmartIri(valueToCreate.resourceIri)
            .flatMap(iri => ZIO.fromEither(ResourceIri.from(iri)).mapError(BadRequestException.apply))
        project <- projectService
                     .findByShortcode(resourceIri.shortcode)
                     .someOrFail(NotFoundException(s"Project not found for resource IRI: $resourceIri"))
        _ <- validateLegalInfo(valueToCreate.valueContent, project.shortcode)

        // Convert the submitted value to the internal schema.
        submittedInternalPropertyIri <-
          ZIO.attempt(valueToCreate.propertyIri.toOntologySchema(InternalSchema))

        submittedInternalValueContent = valueToCreate.valueContent.toOntologySchema(InternalSchema)

        // Get ontology information about the submitted property.
        propertyInfoRequestForSubmittedProperty =
          PropertiesGetRequestV2(
            propertyIris = Set(submittedInternalPropertyIri),
            allLanguages = false,
            requestingUser = requestingUser,
          )

        propertyInfoResponseForSubmittedProperty <-
          messageRelay.ask[ReadOntologyV2](propertyInfoRequestForSubmittedProperty)

        propertyInfoForSubmittedProperty: ReadPropertyInfoV2 =
          propertyInfoResponseForSubmittedProperty.properties(
            submittedInternalPropertyIri,
          )

        // Don't accept link properties.
        _ <- ZIO.when(propertyInfoForSubmittedProperty.isLinkProp)(
               ZIO.fail(
                 BadRequestException(
                   s"Invalid property <${valueToCreate.propertyIri}>. Use a link value property to submit a link.",
                 ),
               ),
             )

        // Don't accept knora-api:hasStandoffLinkToValue.
        _ <- ZIO.when(valueToCreate.propertyIri.toString == OntologyConstants.KnoraApiV2Complex.HasStandoffLinkToValue)(
               ZIO.fail(
                 BadRequestException(
                   s"Values of <${valueToCreate.propertyIri}> cannot be created directly",
                 ),
               ),
             )

        // Make an adjusted version of the submitted property: if it's a link value property, substitute the
        // corresponding link property, whose objects we will need to query. Get ontology information about the
        // adjusted property.
        adjustedInternalPropertyInfo <-
          getAdjustedInternalPropertyInfo(
            submittedPropertyIri = valueToCreate.propertyIri,
            maybeSubmittedValueType = Some(valueToCreate.valueContent.valueType),
            propertyInfoForSubmittedProperty = propertyInfoForSubmittedProperty,
            requestingUser = requestingUser,
          )

        adjustedInternalPropertyIri = adjustedInternalPropertyInfo.entityInfoContent.propertyIri

        // Get the resource's metadata and relevant property objects, using the adjusted property. Do this as the system user,
        // so we can see objects that the user doesn't have permission to see.
        resourceInfo <-
          getResourceWithPropertyValues(
            resourceIri = valueToCreate.resourceIri,
            propertyInfo = adjustedInternalPropertyInfo,
            requestingUser = KnoraSystemInstances.Users.SystemUser,
          )

        // Check that the user has permission to modify the resource.
        _ <- resourceUtilV2.checkResourcePermission(
               resourceInfo = resourceInfo,
               permissionNeeded = Permission.ObjectAccess.Modify,
               requestingUser = requestingUser,
             )

        // Check that the resource has the rdf:type that the client thinks it has.
        _ <- ZIO.when(resourceInfo.resourceClassIri != valueToCreate.resourceClassIri.toOntologySchema(InternalSchema))(
               ZIO.fail(
                 BadRequestException(
                   s"The rdf:type of resource <${valueToCreate.resourceIri}> is not <${valueToCreate.resourceClassIri}>",
                 ),
               ),
             )

        // Get the definition of the resource class.
        classInfoRequest =
          ClassesGetRequestV2(
            classIris = Set(resourceInfo.resourceClassIri),
            allLanguages = false,
            requestingUser = requestingUser,
          )

        classInfoResponse <- messageRelay.ask[ReadOntologyV2](classInfoRequest)

        // Check that the resource class has a cardinality for the submitted property.
        cardinalityInfo <-
          ZIO
            .fromOption(
              for {
                classInfo       <- classInfoResponse.classes.get(resourceInfo.resourceClassIri)
                cardinalityInfo <- classInfo.allCardinalities.get(submittedInternalPropertyIri)
              } yield cardinalityInfo,
            )
            .orElseFail(
              BadRequestException(
                s"Resource <${valueToCreate.resourceIri}> belongs to class <${resourceInfo.resourceClassIri
                    .toOntologySchema(ApiV2Complex)}>, which has no cardinality for property <${valueToCreate.propertyIri}>",
              ),
            )

        // Check that the object of the adjusted property (the value to be created, or the target of the link to be created) will have
        // the correct type for the adjusted property's knora-base:objectClassConstraint.
        _ <- checkPropertyObjectClassConstraint(
               propertyInfo = adjustedInternalPropertyInfo,
               valueContent = submittedInternalValueContent,
               requestingUser = requestingUser,
             )

        _ <- ifIsListValueThenCheckItPointsToListNodeWhichIsNotARootNode(submittedInternalValueContent)

        // Check that the resource class's cardinality for the submitted property allows another value to be added
        // for that property.
        currentValuesForProp: Seq[ReadValueV2] =
          resourceInfo.values.getOrElse(submittedInternalPropertyIri, Seq.empty[ReadValueV2])

        _ <-
          ZIO.when(
            (cardinalityInfo.cardinality == ExactlyOne || cardinalityInfo.cardinality == AtLeastOne) && currentValuesForProp.isEmpty,
          )(
            ZIO.fail(
              InconsistentRepositoryDataException(
                s"Resource class <${resourceInfo.resourceClassIri
                    .toOntologySchema(ApiV2Complex)}> has a cardinality of ${cardinalityInfo.cardinality} on property <${valueToCreate.propertyIri}>, but resource <${valueToCreate.resourceIri}> has no value for that property",
              ),
            ),
          )

        _ <-
          ZIO.when(
            cardinalityInfo.cardinality == ExactlyOne || (cardinalityInfo.cardinality == ZeroOrOne && currentValuesForProp.nonEmpty),
          )(
            ZIO.fail(
              OntologyConstraintException(
                s"Resource class <${resourceInfo.resourceClassIri
                    .toOntologySchema(ApiV2Complex)}> has a cardinality of ${cardinalityInfo.cardinality} on property <${valueToCreate.propertyIri}>, and this does not allow a value to be added for that property to resource <${valueToCreate.resourceIri}>",
              ),
            ),
          )

        // Check that the new value would not duplicate an existing value.
        unescapedSubmittedInternalValueContent = submittedInternalValueContent.unescape

        _ <- ZIO.when(
               currentValuesForProp.exists(currentVal =>
                 unescapedSubmittedInternalValueContent.wouldDuplicateOtherValue(currentVal.valueContent),
               ),
             )(ZIO.fail(DuplicateValueException()))

        // If this is a text value, check that the resources pointed to by any standoff link tags exist
        // and that the user has permission to see them.
        _ <- submittedInternalValueContent match {
               case textValueContent: TextValueContentV2 =>
                 checkResourceIris(
                   targetResourceIris = textValueContent.standoffLinkTagTargetResourceIris,
                   requestingUser = requestingUser,
                 )

               case _ => ZIO.unit
             }

        // Get the default permissions for the new value.
        defaultValuePermissions <- permissionsResponder.newValueDefaultObjectAccessPermissions(
                                     resourceInfo.projectADM.id,
                                     resourceInfo.resourceClassIri,
                                     submittedInternalPropertyIri,
                                     requestingUser,
                                   )

        // Did the user submit permissions for the new value?
        newValuePermissionLiteral <-
          valueToCreate.permissions match {
            case Some(permissions: String) =>
              // Yes. Validate them.
              for {
                validatedCustomPermissions <- permissionUtilADM.validatePermissions(permissions)

                // Is the requesting user a system admin, or an admin of this project?
                userPermissions = requestingUser.permissions
                _ <- ZIO.when(!(userPermissions.isProjectAdmin(requestingUser.id) || userPermissions.isSystemAdmin)) {

                       // No. Make sure they don't give themselves higher permissions than they would get from the default permissions.
                       val permissionComparisonResult: PermissionComparisonResult =
                         PermissionUtilADM.comparePermissionsADM(
                           entityProject = resourceInfo.projectADM.id.value,
                           permissionLiteralA = validatedCustomPermissions,
                           permissionLiteralB = defaultValuePermissions.permissionLiteral,
                           requestingUser = requestingUser,
                         )

                       ZIO.when(permissionComparisonResult == AGreaterThanB)(
                         ZIO.fail(
                           ForbiddenException(
                             s"The specified value permissions would give a value's creator a higher permission on the value than the default permissions",
                           ),
                         ),
                       )
                     }
              } yield validatedCustomPermissions

            case None =>
              // No. Use the default permissions.
              ZIO.succeed(defaultValuePermissions.permissionLiteral)
          }

        dataNamedGraph: IRI = ProjectService.projectDataNamedGraphV2(resourceInfo.projectADM).value

        // Create the new value.
        created <-
          createValueV2AfterChecks(
            dataNamedGraph = dataNamedGraph,
            resourceInfo = resourceInfo,
            propertyIri = adjustedInternalPropertyIri,
            value = submittedInternalValueContent,
            valueIri = valueToCreate.valueIri,
            valueUUID = valueToCreate.valueUUID,
            valueCreationDate = valueToCreate.valueCreationDate,
            valueCreator = requestingUser.id,
            valuePermissions = newValuePermissionLiteral,
          )

      } yield CreateValueResponseV2(
        valueIri = created.newValueIri,
        valueType = created.valueContent.valueType,
        valueUUID = created.newValueUUID,
        valueCreationDate = created.creationDate,
        projectADM = resourceInfo.projectADM,
      )
    }
    for {
      // Don't allow anonymous users to create values.
      _ <- ZIO.when(requestingUser.isAnonymousUser)(
             ZIO.fail(ForbiddenException("Anonymous users aren't allowed to create values")),
           )
      // Do the remaining pre-update checks and the update while holding an update lock on the resource.
      taskResult <- IriLocker.runWithIriLock(apiRequestID, valueToCreate.resourceIri, taskZio)
    } yield taskResult
  }

  private def validateLegalInfo(fvc: ValueContentV2, shortcode: Shortcode): IO[BadRequestException, Unit] =
    fvc match {
      case fvc: FileValueContentV2 =>
        legalInfoService.validateLegalInfo(fvc.fileValue, shortcode).mapError(BadRequestException.apply).unit
      case _ => ZIO.unit
    }

  private def ifIsListValueThenCheckItPointsToListNodeWhichIsNotARootNode(valueContent: ValueContentV2) =
    valueContent match {
      case listValue: HierarchicalListValueContentV2 =>
        resourceUtilV2.checkListNodeExistsAndIsRootNode(listValue.valueHasListNode).flatMap {
          // it doesn't have isRootNode property - it's a child node
          case Right(false) => ZIO.unit
          // it does have isRootNode property - it's a root node
          case Right(true) =>
            val msg = s"<${listValue.valueHasListNode}> is a root node. Root nodes cannot be set as values."
            ZIO.fail(BadRequestException(msg))
          // it doesn't exists or isn't valid list
          case Left(_) =>
            val msg = s"<${listValue.valueHasListNode}> does not exist or is not a ListNode."
            ZIO.fail(NotFoundException(msg))
        }
      case _ => ZIO.unit
    }

  /**
   * Creates a new value (either an ordinary value or a link), using an existing transaction, assuming that
   * pre-update checks have already been done.
   *
   * @param dataNamedGraph    the named graph in which the value is to be created.
   * @param resourceInfo      information about the the resource in which to create the value.
   * @param propertyIri       the IRI of the property that will point from the resource to the value, or, if
   *                          the value is a link value, the IRI of the link property.
   * @param value             the value to create.
   * @param valueIri          the optional custom IRI supplied for the value.
   * @param valueUUID         the optional custom UUID supplied for the value.
   * @param valueCreationDate the optional custom creation date supplied for the value.
   * @param valueCreator      the IRI of the new value's owner.
   * @param valuePermissions  the literal that should be used as the object of the new value's
   *                          `knora-base:hasPermissions` predicate.
   * @return an [[UnverifiedValueV2]].
   */
  private def createValueV2AfterChecks(
    dataNamedGraph: IRI,
    resourceInfo: ReadResourceV2,
    propertyIri: SmartIri,
    value: ValueContentV2,
    valueIri: Option[SmartIri],
    valueUUID: Option[UUID],
    valueCreationDate: Option[Instant],
    valueCreator: IRI,
    valuePermissions: IRI,
  ): ZIO[Any, Throwable, UnverifiedValueV2] =
    value match {
      case linkValueContent: LinkValueContentV2 =>
        createLinkValueV2AfterChecks(
          dataNamedGraph = dataNamedGraph,
          resourceInfo = resourceInfo,
          linkPropertyIri = propertyIri,
          linkValueContent = linkValueContent,
          maybeValueIri = valueIri,
          maybeValueUUID = valueUUID,
          maybeCreationDate = valueCreationDate,
          valueCreator = valueCreator,
          valuePermissions = valuePermissions,
        )

      case ordinaryValueContent =>
        createOrdinaryValueV2AfterChecks(
          dataNamedGraph = dataNamedGraph,
          resourceInfo = resourceInfo,
          propertyIri = propertyIri,
          value = ordinaryValueContent,
          maybeValueIri = valueIri,
          maybeValueUUID = valueUUID,
          maybeValueCreationDate = valueCreationDate,
          valueCreator = valueCreator,
          valuePermissions = valuePermissions,
        )
    }

  /**
   * Creates an ordinary value (i.e. not a link), using an existing transaction, assuming that pre-update checks have already been done.
   *
   * @param resourceInfo           information about the resource in which to create the value.
   * @param propertyIri            the property that should point to the value.
   * @param value                  an [[ValueContentV2]] describing the value.
   * @param maybeValueIri          the optional custom IRI supplied for the value.
   * @param maybeValueUUID         the optional custom UUID supplied for the value.
   * @param maybeValueCreationDate the optional custom creation date supplied for the value.
   * @param valueCreator           the IRI of the new value's owner.
   * @param valuePermissions       the literal that should be used as the object of the new value's `knora-base:hasPermissions` predicate.
   * @return an [[UnverifiedValueV2]].
   */
  private def createOrdinaryValueV2AfterChecks(
    dataNamedGraph: IRI,
    resourceInfo: ReadResourceV2,
    propertyIri: SmartIri,
    value: ValueContentV2,
    maybeValueIri: Option[SmartIri],
    maybeValueUUID: Option[UUID],
    maybeValueCreationDate: Option[Instant],
    valueCreator: IRI,
    valuePermissions: IRI,
  ) =
    for {

      // Make a new value UUID.
      newValueUUID <- ValuesResponderV2.makeNewValueUUID(maybeValueIri, maybeValueUUID)

      // Make an IRI for the new value.
      newValueIri <-
        iriService.checkOrCreateEntityIri(
          maybeValueIri,
          stringFormatter.makeRandomValueIri(resourceInfo.resourceIri, Some(newValueUUID)),
        )

      // Make a creation date for the new value
      creationDate: Instant = maybeValueCreationDate match {
                                case Some(customCreationDate) => customCreationDate
                                case None                     => Instant.now
                              }

      // If we're creating a text value, update direct links and LinkValues for any resource references in standoff.
      standoffLinkUpdates <-
        value match {
          case textValueContent: TextValueContentV2 =>
            // Construct a SparqlTemplateLinkUpdate for each reference that was added.
            val linkUpdateFutures: Seq[Task[SparqlTemplateLinkUpdate]] =
              textValueContent.standoffLinkTagTargetResourceIris.map { targetResourceIri =>
                incrementLinkValue(
                  sourceResourceInfo = resourceInfo,
                  linkPropertyIri = OntologyConstants.KnoraBase.HasStandoffLinkTo.toSmartIri,
                  targetResourceIri = targetResourceIri,
                  valueCreator = KnoraUserRepo.builtIn.SystemUser.id.value,
                  valuePermissions = standoffLinkValuePermissions,
                )
              }.toVector

            ZIO.collectAll(linkUpdateFutures)

          case _ => ZIO.succeed(Vector.empty[SparqlTemplateLinkUpdate])
        }

      // Generate a SPARQL update string.
      sparqlUpdate = sparql.v2.txt.createValue(
                       dataNamedGraph = dataNamedGraph,
                       resourceIri = resourceInfo.resourceIri,
                       propertyIri = propertyIri,
                       newValueIri = newValueIri,
                       newValueUUID = newValueUUID,
                       value = value,
                       linkUpdates = standoffLinkUpdates,
                       valueCreator = valueCreator,
                       valuePermissions = valuePermissions,
                       creationDate = creationDate,
                       stringFormatter = stringFormatter,
                     )

      _ <- triplestoreService.query(Update(sparqlUpdate))
    } yield UnverifiedValueV2(
      newValueIri = newValueIri,
      newValueUUID = newValueUUID,
      valueContent = value.unescape,
      permissions = valuePermissions,
      creationDate = creationDate,
    )

  /**
   * Creates a link, using an existing transaction, assuming that pre-update checks have already been done.
   *
   * @param dataNamedGraph   the named graph in which the link is to be created.
   * @param resourceInfo     information about the the resource in which to create the value.
   * @param linkPropertyIri  the link property.
   * @param linkValueContent a [[LinkValueContentV2]] specifying the target resource.
   * @param maybeValueIri    the optional custom IRI supplied for the value.
   * @param maybeValueUUID   the optional custom UUID supplied for the value.
   * @param valueCreator     the IRI of the new link value's owner.
   * @param valuePermissions the literal that should be used as the object of the new link value's `knora-base:hasPermissions` predicate.
   * @return an [[UnverifiedValueV2]].
   */
  private def createLinkValueV2AfterChecks(
    dataNamedGraph: IRI,
    resourceInfo: ReadResourceV2,
    linkPropertyIri: SmartIri,
    linkValueContent: LinkValueContentV2,
    maybeValueIri: Option[SmartIri],
    maybeValueUUID: Option[UUID],
    maybeCreationDate: Option[Instant],
    valueCreator: IRI,
    valuePermissions: IRI,
  ) =
    // Make a new value UUID.

    for {
      newValueUUID <- ValuesResponderV2.makeNewValueUUID(maybeValueIri, maybeValueUUID)
      sparqlTemplateLinkUpdate <-
        incrementLinkValue(
          sourceResourceInfo = resourceInfo,
          linkPropertyIri = linkPropertyIri,
          targetResourceIri = linkValueContent.referredResourceIri,
          customNewLinkValueIri = maybeValueIri,
          valueCreator = valueCreator,
          valuePermissions = valuePermissions,
        )

      creationDate: Instant =
        maybeCreationDate match {
          case Some(customValueCreationDate) => customValueCreationDate
          case None                          => Instant.now
        }

      // Generate a SPARQL update string.
      sparqlUpdate = sparql.v2.txt.createLink(
                       dataNamedGraph = dataNamedGraph,
                       resourceIri = resourceInfo.resourceIri,
                       linkUpdate = sparqlTemplateLinkUpdate,
                       newValueUUID = newValueUUID,
                       creationDate = creationDate,
                       maybeComment = linkValueContent.comment,
                       stringFormatter = stringFormatter,
                     )

      _ <- triplestoreService.query(Update(sparqlUpdate))
    } yield UnverifiedValueV2(
      newValueIri = sparqlTemplateLinkUpdate.newLinkValueIri,
      newValueUUID = newValueUUID,
      valueContent = linkValueContent.unescape,
      permissions = valuePermissions,
      creationDate = creationDate,
    )

  /**
   * Creates a new version of an existing value.
   *
   * @param updateValue       the value update.
   * @param requestingUser    the user making the request.
   * @param apiRequestId      the ID of the API request.
   * @return a [[UpdateValueResponseV2]].
   */
  def updateValueV2(
    updateValue: UpdateValueV2,
    requestingUser: User,
    apiRequestId: UUID,
  ): Task[UpdateValueResponseV2] =
    ZIO
      .fail(ForbiddenException("Anonymous users aren't allowed to update values"))
      .when(requestingUser.isAnonymousUser) *>
      IriLocker.runWithIriLock(
        apiRequestId,
        updateValue.resourceIri,
        updateValue match {
          case updateContent: UpdateValueContentV2         => updateValueContent(updateContent, requestingUser)
          case updatePermissions: UpdateValuePermissionsV2 => updateValuePermissions(updatePermissions, requestingUser)
        },
      )

  /**
   * Updates the permissions attached to a value.
   *
   * @param updateValue the update request.
   * @return an [[UpdateValueResponseV2]].
   */
  private def updateValuePermissions(updateValue: UpdateValuePermissionsV2, requestingUser: User) =
    for {
      // Do the initial checks, and get information about the resource, the property, and the value.
      resourcePropertyValue <- checkValueAndRetrieveResourceProperties(updateValue, requestingUser)

      resourceInfo: ReadResourceV2 = resourcePropertyValue.resource
      currentValue: ReadValueV2    = resourcePropertyValue.value

      // Validate and reformat the submitted permissions.
      newValuePermissionLiteral <- permissionUtilADM.validatePermissions(updateValue.permissions)

      // Check that the user has Permission.ObjectAccess.ChangeRights on the value, and that the new permissions are
      // different from the current ones.
      currentPermissionsParsed <- ZIO.attempt(PermissionUtilADM.parsePermissions(currentValue.permissions))
      newPermissionsParsed <-
        ZIO.attempt(
          PermissionUtilADM.parsePermissions(
            updateValue.permissions,
            (permissionLiteral: String) => throw AssertionException(s"Invalid permission literal: $permissionLiteral"),
          ),
        )

      _ <- ZIO.when(newPermissionsParsed == currentPermissionsParsed)(
             ZIO.fail(BadRequestException(s"The submitted permissions are the same as the current ones")),
           )

      _ <- resourceUtilV2.checkValuePermission(
             resourceInfo = resourceInfo,
             valueInfo = currentValue,
             permissionNeeded = Permission.ObjectAccess.ChangeRights,
             requestingUser = requestingUser,
           )

      // Do the update.
      dataNamedGraph: IRI = ProjectService.projectDataNamedGraphV2(resourceInfo.projectADM).value
      newValueIri <-
        iriService.checkOrCreateEntityIri(
          updateValue.newValueVersionIri,
          stringFormatter.makeRandomValueIri(resourceInfo.resourceIri),
        )

      currentTime = updateValue.valueCreationDate.getOrElse(Instant.now)

      sparqlUpdate = sparql.v2.txt.changeValuePermissions(
                       dataNamedGraph = dataNamedGraph,
                       resourceIri = resourceInfo.resourceIri,
                       propertyIri = updateValue.propertyIri.toInternalSchema,
                       currentValueIri = currentValue.valueIri,
                       valueTypeIri = currentValue.valueContent.valueType,
                       newValueIri = newValueIri,
                       newPermissions = newValuePermissionLiteral,
                       currentTime = currentTime,
                     )
      _ <- triplestoreService.query(Update(sparqlUpdate))
    } yield UpdateValueResponseV2(
      newValueIri,
      currentValue.valueContent.valueType,
      currentValue.valueHasUUID,
      resourceInfo.projectADM,
    )

  /**
   * Updates the contents of a value.
   *
   * @param updateValue the update request.
   * @return an [[UpdateValueResponseV2]].
   */
  private def updateValueContent(
    updateValue: UpdateValueContentV2,
    requestingUser: User,
  ): Task[UpdateValueResponseV2] = {
    for {
      resourcePropertyValue <- checkValueAndRetrieveResourceProperties(updateValue, requestingUser)
      _                     <- validateLegalInfo(updateValue.valueContent, resourcePropertyValue.resource.projectADM.shortcode)

      resourceInfo: ReadResourceV2                     = resourcePropertyValue.resource
      adjustedInternalPropertyInfo: ReadPropertyInfoV2 = resourcePropertyValue.adjustedInternalPropertyInfo
      currentValue: ReadValueV2                        = resourcePropertyValue.value

      // Did the user submit permissions for the new value?
      newValueVersionPermissionLiteral <-
        updateValue.permissions match {
          case Some(permissions) =>
            // Yes. Validate them.
            permissionUtilADM.validatePermissions(permissions)

          case None =>
            // No. Use the permissions on the current version of the value.
            ZIO.succeed(currentValue.permissions)
        }

      // Check that the user has permission to do the update. If they want to change the permissions
      // on the value, they need Permission.ObjectAccess.ChangeRights, otherwise they need Permission.ObjectAccess.Modify.
      currentPermissionsParsed <- ZIO.attempt(PermissionUtilADM.parsePermissions(currentValue.permissions))
      newPermissionsParsed <-
        ZIO.attempt(
          PermissionUtilADM.parsePermissions(
            newValueVersionPermissionLiteral,
            (permissionLiteral: String) => throw AssertionException(s"Invalid permission literal: $permissionLiteral"),
          ),
        )

      permissionNeeded =
        if (newPermissionsParsed != currentPermissionsParsed) { Permission.ObjectAccess.ChangeRights }
        else { Permission.ObjectAccess.Modify }

      _ <- resourceUtilV2.checkValuePermission(
             resourceInfo = resourceInfo,
             valueInfo = currentValue,
             permissionNeeded = permissionNeeded,
             requestingUser = requestingUser,
           )

      // Convert the submitted value content to the internal schema.
      project                       = resourceInfo.projectADM
      submittedInternalValueContent = updateValue.valueContent.toOntologySchema(InternalSchema)

      // Check that the object of the adjusted property (the value to be created, or the target of the link to be created) will have
      // the correct type for the adjusted property's knora-base:objectClassConstraint.
      _ <- checkPropertyObjectClassConstraint(
             propertyInfo = adjustedInternalPropertyInfo,
             valueContent = submittedInternalValueContent,
             requestingUser = requestingUser,
           )

      _ <- ifIsListValueThenCheckItPointsToListNodeWhichIsNotARootNode(submittedInternalValueContent)

      // Check that the updated value would not duplicate the current value version.
      unescapedSubmittedInternalValueContent = submittedInternalValueContent.unescape

      _ <- ZIO.when(unescapedSubmittedInternalValueContent.wouldDuplicateCurrentVersion(currentValue.valueContent))(
             ZIO.fail(DuplicateValueException("The submitted value is the same as the current version")),
           )

      // Check that the updated value would not duplicate another existing value of the resource.
      currentValuesForProp: Seq[ReadValueV2] =
        resourceInfo.values
          .getOrElse(updateValue.propertyIri.toInternalSchema, Seq.empty[ReadValueV2])
          .filter(_.valueIri != updateValue.valueIri)

      _ <- ZIO.when(
             currentValuesForProp.exists(currentVal =>
               unescapedSubmittedInternalValueContent.wouldDuplicateOtherValue(currentVal.valueContent),
             ),
           )(ZIO.fail(DuplicateValueException()))

      _ <- submittedInternalValueContent match {
             case textValueContent: TextValueContentV2 =>
               // This is a text value. Check that the resources pointed to by any standoff link tags exist
               // and that the user has permission to see them.
               checkResourceIris(
                 textValueContent.standoffLinkTagTargetResourceIris,
                 requestingUser,
               )

             case _: LinkValueContentV2 =>
               // We're updating a link. This means deleting an existing link and creating a new one, so
               // check that the user has permission to modify the resource.
               resourceUtilV2.checkResourcePermission(
                 resourceInfo = resourceInfo,
                 permissionNeeded = Permission.ObjectAccess.Modify,
                 requestingUser = requestingUser,
               )

             case _ => ZIO.unit
           }

      dataNamedGraph: IRI = ProjectService.projectDataNamedGraphV2(resourceInfo.projectADM).value

      // Create the new value version.
      newValueVersion <-
        (currentValue, submittedInternalValueContent) match {
          case (
                currentLinkValue: ReadLinkValueV2,
                newLinkValue: LinkValueContentV2,
              ) =>
            updateLinkValueV2AfterChecks(
              dataNamedGraph = dataNamedGraph,
              resourceInfo = resourceInfo,
              linkPropertyIri = adjustedInternalPropertyInfo.entityInfoContent.propertyIri,
              currentLinkValue = currentLinkValue,
              newLinkValue = newLinkValue,
              valueCreator = requestingUser.id,
              valuePermissions = newValueVersionPermissionLiteral,
              valueCreationDate = updateValue.valueCreationDate,
              newValueVersionIri = updateValue.newValueVersionIri,
              requestingUser = requestingUser,
            )

          case _ =>
            updateOrdinaryValueV2AfterChecks(
              dataNamedGraph = dataNamedGraph,
              resourceInfo = resourceInfo,
              propertyIri = adjustedInternalPropertyInfo.entityInfoContent.propertyIri,
              currentValue = currentValue,
              newValueVersion = submittedInternalValueContent,
              valueCreator = requestingUser.id,
              valuePermissions = newValueVersionPermissionLiteral,
              valueCreationDate = updateValue.valueCreationDate,
              newValueVersionIri = updateValue.newValueVersionIri,
              requestingUser = requestingUser,
            )
        }
    } yield UpdateValueResponseV2(
      valueIri = newValueVersion.newValueIri,
      valueType = newValueVersion.valueContent.valueType,
      valueUUID = newValueVersion.newValueUUID,
      projectADM = resourceInfo.projectADM,
    )
  }

  /**
   * Information about a resource, a submitted property, and a value of the property.
   *
   * @param resource                     the contents of the resource.
   * @param adjustedInternalPropertyInfo the internal definition of the submitted property, adjusted
   *                                     as follows: an adjusted version of the submitted property:
   *                                     if it's a link value property, substitute the
   *                                     corresponding link property.
   * @param value                        the requested value.
   */
  private case class ResourcePropertyValue(
    resource: ReadResourceV2,
    adjustedInternalPropertyInfo: ReadPropertyInfoV2,
    value: ReadValueV2,
  )

  /**
   * Gets information about a resource, a submitted property, and a value of the property, and does
   * some checks to see if the submitted information is correct.
   *
   * @param updateValue the submitted value update to check
   * @return a [[ResourcePropertyValue]].
   */
  private def checkValueAndRetrieveResourceProperties(updateValue: UpdateValueV2, requestingUser: User) =
    for {
      submittedInternalPropertyIri <- ZIO.attempt(updateValue.propertyIri.toInternalSchema)
      submittedInternalValueType   <- ZIO.attempt(updateValue.valueType.toInternalSchema)

      // Get ontology information about the submitted property.
      propertyInfoRequestForSubmittedProperty =
        PropertiesGetRequestV2(
          propertyIris = Set(submittedInternalPropertyIri),
          allLanguages = false,
          requestingUser = requestingUser,
        )

      propertyInfoResponseForSubmittedProperty <-
        messageRelay.ask[ReadOntologyV2](propertyInfoRequestForSubmittedProperty)

      propertyInfoForSubmittedProperty: ReadPropertyInfoV2 =
        propertyInfoResponseForSubmittedProperty.properties(submittedInternalPropertyIri)

      _ <- {
        val msg =
          s"Invalid property <${propertyInfoForSubmittedProperty.entityInfoContent.propertyIri.toComplexSchema}>." +
            s" Use a link value property to submit a link."
        ZIO.fail(BadRequestException(msg)).when(propertyInfoForSubmittedProperty.isLinkProp)
      }

      // Don't accept knora-api:hasStandoffLinkToValue.
      _ <- ZIO.when(
             updateValue.propertyIri.toString == OntologyConstants.KnoraApiV2Complex.HasStandoffLinkToValue,
           )(ZIO.fail(BadRequestException(s"Values of <${updateValue.propertyIri}> cannot be updated directly")))

      // Make an adjusted version of the submitted property: if it's a link value property, substitute the
      // corresponding link property, whose objects we will need to query. Get ontology information about the
      // adjusted property.
      adjustedInternalPropertyInfo <- getAdjustedInternalPropertyInfo(
                                        updateValue.propertyIri,
                                        Some(updateValue.valueType),
                                        propertyInfoForSubmittedProperty,
                                        requestingUser,
                                      )

      // Get the resource's metadata and relevant property objects, using the adjusted property. Do this as the system user,
      // so we can see objects that the user doesn't have permission to see.
      resourceInfo <-
        getResourceWithPropertyValues(
          resourceIri = updateValue.resourceIri,
          propertyInfo = adjustedInternalPropertyInfo,
          requestingUser = KnoraSystemInstances.Users.SystemUser,
        )

      _ <- {
        val msg = s"The rdf:type of resource <${updateValue.resourceIri}> is not <${updateValue.resourceClassIri}>"
        ZIO
          .fail(BadRequestException(msg))
          .when(resourceInfo.resourceClassIri != updateValue.resourceClassIri.toInternalSchema)
      }

      // Check that the resource has the value that the user wants to update, as an object of the submitted property.
      currentValue <-
        ZIO
          .fromOption(for {
            values <- resourceInfo.values.get(submittedInternalPropertyIri)
            curVal <- values.find(_.valueIri == updateValue.valueIri)
          } yield curVal)
          .orElseFail(
            NotFoundException(
              s"Resource <${updateValue.resourceIri}> does not have value <${updateValue.valueIri}> as an object of property <${updateValue.propertyIri}>",
            ),
          )
      isSameType = currentValue.valueContent.valueType == submittedInternalValueType
      isStillImageTypes =
        Set(
          submittedInternalValueType.toInternalIri.value,
          currentValue.valueContent.valueType.toInternalIri.value,
        ).subsetOf(Set(StillImageExternalFileValue, StillImageFileValue))

      _ <-
        ZIO.unless(isSameType || isStillImageTypes)(
          ZIO.fail(
            BadRequestException(
              s"Value <${updateValue.valueIri}> has type <${currentValue.valueContent.valueType.toOntologySchema(ApiV2Complex)}>, but the submitted type was <${updateValue.valueType}>",
            ),
          ),
        )

      // If a custom value creation date was submitted, make sure it's later than the date of the current version.
      _ <- ZIO.when(updateValue.valueCreationDate.exists(!_.isAfter(currentValue.valueCreationDate)))(
             ZIO.fail(
               BadRequestException(
                 "A custom value creation date must be later than the date of the current version",
               ),
             ),
           )
    } yield ResourcePropertyValue(resourceInfo, adjustedInternalPropertyInfo, currentValue)

  /**
   * Changes an ordinary value (i.e. not a link), assuming that pre-update checks have already been done.
   *
   * @param dataNamedGraph     the IRI of the named graph to be updated.
   * @param resourceInfo       information about the resource containing the value.
   * @param propertyIri        the IRI of the property that points to the value.
   * @param currentValue       a [[ReadValueV2]] representing the existing value version.
   * @param newValueVersion    a [[ValueContentV2]] representing the new value version, in the internal schema.
   * @param valueCreator       the IRI of the new value's owner.
   * @param valuePermissions   the literal that should be used as the object of the new value's `knora-base:hasPermissions` predicate.
   * @param valueCreationDate  a custom value creation date.
   * @param newValueVersionIri an optional IRI to be used for the new value version.
   * @param requestingUser     the user making the request.
   * @return an [[UnverifiedValueV2]].
   */
  private def updateOrdinaryValueV2AfterChecks(
    dataNamedGraph: IRI,
    resourceInfo: ReadResourceV2,
    propertyIri: SmartIri,
    currentValue: ReadValueV2,
    newValueVersion: ValueContentV2,
    valueCreator: IRI,
    valuePermissions: String,
    valueCreationDate: Option[Instant],
    newValueVersionIri: Option[SmartIri],
    requestingUser: User,
  ): Task[UnverifiedValueV2] =
    for {
      newValueIri <-
        iriService.checkOrCreateEntityIri(
          newValueVersionIri,
          stringFormatter.makeRandomValueIri(resourceInfo.resourceIri),
        )

      // If we're updating a text value, update direct links and LinkValues for any resource references in Standoff.
      standoffLinkUpdates <-
        (currentValue.valueContent, newValueVersion) match {
          case (
                currentTextValue: TextValueContentV2,
                newTextValue: TextValueContentV2,
              ) =>
            // Identify the resource references that have been added or removed in the new version of
            // the value.
            val addedResourceRefs =
              newTextValue.standoffLinkTagTargetResourceIris -- currentTextValue.standoffLinkTagTargetResourceIris
            val removedResourceRefs =
              currentTextValue.standoffLinkTagTargetResourceIris -- newTextValue.standoffLinkTagTargetResourceIris

            // Construct a SparqlTemplateLinkUpdate for each reference that was added.
            val standoffLinkUpdatesForAddedResourceRefFutures: Seq[Task[SparqlTemplateLinkUpdate]] =
              addedResourceRefs.toVector.map { targetResourceIri =>
                incrementLinkValue(
                  sourceResourceInfo = resourceInfo,
                  linkPropertyIri = OntologyConstants.KnoraBase.HasStandoffLinkTo.toSmartIri,
                  targetResourceIri = targetResourceIri,
                  valueCreator = KnoraUserRepo.builtIn.SystemUser.id.value,
                  valuePermissions = standoffLinkValuePermissions,
                )
              }

            val standoffLinkUpdatesForAddedResourceRefsFuture: Task[Seq[SparqlTemplateLinkUpdate]] =
              ZIO.collectAll(standoffLinkUpdatesForAddedResourceRefFutures)

            // Construct a SparqlTemplateLinkUpdate for each reference that was removed.
            val standoffLinkUpdatesForRemovedResourceRefFutures: Seq[Task[SparqlTemplateLinkUpdate]] =
              removedResourceRefs.toVector.map { removedTargetResource =>
                decrementLinkValue(
                  sourceResourceInfo = resourceInfo,
                  linkPropertyIri = OntologyConstants.KnoraBase.HasStandoffLinkTo.toSmartIri,
                  targetResourceIri = removedTargetResource,
                  valueCreator = KnoraUserRepo.builtIn.SystemUser.id.value,
                  valuePermissions = standoffLinkValuePermissions,
                )
              }

            val standoffLinkUpdatesForRemovedResourceRefFuture =
              ZIO.collectAll(standoffLinkUpdatesForRemovedResourceRefFutures)

            for {
              standoffLinkUpdatesForAddedResourceRefs <-
                standoffLinkUpdatesForAddedResourceRefsFuture
              standoffLinkUpdatesForRemovedResourceRefs <-
                standoffLinkUpdatesForRemovedResourceRefFuture
            } yield standoffLinkUpdatesForAddedResourceRefs ++ standoffLinkUpdatesForRemovedResourceRefs

          case _ =>
            ZIO.succeed(
              Vector.empty[SparqlTemplateLinkUpdate],
            )
        }

      // If no custom value creation date was provided, make a timestamp to indicate when the value
      // was updated.
      currentTime: Instant = valueCreationDate.getOrElse(Instant.now)

      // Generate a SPARQL update.
      sparqlUpdate = sparql.v2.txt.addValueVersion(
                       dataNamedGraph = dataNamedGraph,
                       resourceIri = resourceInfo.resourceIri,
                       propertyIri = propertyIri,
                       currentValueIri = currentValue.valueIri,
                       newValueIri = newValueIri,
                       valueTypeIri = currentValue.valueContent.valueType,
                       value = newValueVersion,
                       valueCreator = valueCreator,
                       valuePermissions = valuePermissions,
                       maybeComment = newValueVersion.comment,
                       linkUpdates = standoffLinkUpdates,
                       currentTime = currentTime,
                       requestingUser = requestingUser.id,
                     )

      // Do the update.
      _ <- triplestoreService.query(Update(sparqlUpdate))

    } yield UnverifiedValueV2(
      newValueIri = newValueIri,
      newValueUUID = currentValue.valueHasUUID,
      valueContent = newValueVersion.unescape,
      permissions = valuePermissions,
      creationDate = currentTime,
    )

  /**
   * Changes a link, assuming that pre-update checks have already been done.
   *
   * @param dataNamedGraph     the IRI of the named graph to be updated.
   * @param resourceInfo       information about the resource containing the link.
   * @param linkPropertyIri    the IRI of the link property.
   * @param currentLinkValue   a [[ReadLinkValueV2]] representing the `knora-base:LinkValue` for the existing link.
   * @param newLinkValue       a [[LinkValueContentV2]] indicating the new target resource.
   * @param valueCreator       the IRI of the new link value's owner.
   * @param valuePermissions   the literal that should be used as the object of the new link value's `knora-base:hasPermissions` predicate.
   * @param valueCreationDate  a custom value creation date.
   * @param newValueVersionIri an optional IRI to be used for the new value version.
   * @param requestingUser     the user making the request.
   * @return an [[UnverifiedValueV2]].
   */
  private def updateLinkValueV2AfterChecks(
    dataNamedGraph: IRI,
    resourceInfo: ReadResourceV2,
    linkPropertyIri: SmartIri,
    currentLinkValue: ReadLinkValueV2,
    newLinkValue: LinkValueContentV2,
    valueCreator: IRI,
    valuePermissions: String,
    valueCreationDate: Option[Instant],
    newValueVersionIri: Option[SmartIri],
    requestingUser: User,
  ): Task[UnverifiedValueV2] =
    // Are we changing the link target?
    if (currentLinkValue.valueContent.referredResourceIri != newLinkValue.referredResourceIri) {
      for {
        // Yes. Delete the existing link and decrement its LinkValue's reference count.
        sparqlTemplateLinkUpdateForCurrentLink <-
          decrementLinkValue(
            sourceResourceInfo = resourceInfo,
            linkPropertyIri = linkPropertyIri,
            targetResourceIri = currentLinkValue.valueContent.referredResourceIri,
            valueCreator = valueCreator,
            valuePermissions = valuePermissions,
          )

        // Create a new link, and create a new LinkValue for it.
        sparqlTemplateLinkUpdateForNewLink <-
          incrementLinkValue(
            sourceResourceInfo = resourceInfo,
            linkPropertyIri = linkPropertyIri,
            targetResourceIri = newLinkValue.referredResourceIri,
            customNewLinkValueIri = newValueVersionIri,
            valueCreator = valueCreator,
            valuePermissions = valuePermissions,
          )

        // If no custom value creation date was provided, make a timestamp to indicate when the link value
        // was updated.
        currentTime: Instant = valueCreationDate.getOrElse(Instant.now)

        // Make a new UUID for the new link value.
        newLinkValueUUID = UUID.randomUUID

        sparqlUpdate = sparql.v2.txt.changeLinkTarget(
                         dataNamedGraph = dataNamedGraph,
                         linkSourceIri = resourceInfo.resourceIri,
                         linkUpdateForCurrentLink = sparqlTemplateLinkUpdateForCurrentLink,
                         linkUpdateForNewLink = sparqlTemplateLinkUpdateForNewLink,
                         newLinkValueUUID = newLinkValueUUID,
                         maybeComment = newLinkValue.comment,
                         currentTime = currentTime,
                         requestingUser = requestingUser.id,
                       )

        _ <- triplestoreService.query(Update(sparqlUpdate))
      } yield UnverifiedValueV2(
        newValueIri = sparqlTemplateLinkUpdateForNewLink.newLinkValueIri,
        newValueUUID = newLinkValueUUID,
        valueContent = newLinkValue.unescape,
        permissions = valuePermissions,
        creationDate = currentTime,
      )
    } else {
      for {
        // We're not changing the link target, just the metadata on the LinkValue.
        sparqlTemplateLinkUpdate <-
          changeLinkValueMetadata(
            sourceResourceInfo = resourceInfo,
            linkPropertyIri = linkPropertyIri,
            targetResourceIri = currentLinkValue.valueContent.referredResourceIri,
            customNewLinkValueIri = newValueVersionIri,
            valueCreator = valueCreator,
            valuePermissions = valuePermissions,
          )

        // Make a timestamp to indicate when the link value was updated.
        currentTime: Instant = Instant.now

        sparqlUpdate = sparql.v2.txt.changeLinkMetadata(
                         dataNamedGraph = dataNamedGraph,
                         linkSourceIri = resourceInfo.resourceIri,
                         linkUpdate = sparqlTemplateLinkUpdate,
                         maybeComment = newLinkValue.comment,
                         currentTime = currentTime,
                         requestingUser = requestingUser.id,
                       )

        _ <- triplestoreService.query(Update(sparqlUpdate))
      } yield UnverifiedValueV2(
        newValueIri = sparqlTemplateLinkUpdate.newLinkValueIri,
        newValueUUID = currentLinkValue.valueHasUUID,
        valueContent = newLinkValue.unescape,
        permissions = valuePermissions,
        creationDate = currentTime,
      )
    }

  /**
   * Marks a value as deleted.
   *
   * @param deleteValue the information about value to be deleted.
   * @param requestingUser the user making the request.
   * @param apiRequestId the API request ID.
   */
  def deleteValueV2(
    deleteValue: DeleteValueV2,
    requestingUser: User,
    apiRequestId: UUID,
  ): Task[SuccessResponseV2] = {
    val deleteTask: Task[SuccessResponseV2] = {
      for {
        _ <- auth.ensureUserIsNotAnonymous(requestingUser)
        propertyIri <-
          iriConverter
            .asPropertyIri(deleteValue.propertyIri.toIri)
            .mapError(BadRequestException.apply)
            // Don't accept knora-api:hasStandoffLinkToValue.
            .filterOrFail(_.toComplexSchema.toIri != KA.HasStandoffLinkToValue)(
              BadRequestException(s"Values of <${KA.HasStandoffLinkToValue}> cannot be deleted directly"),
            )

        propertyInfoForSubmittedProperty <-
          ontologyRepo
            .findProperty(propertyIri)
            .someOrFail(NotFoundException(s"Property not found: $propertyIri"))
            // Don't accept link properties.
            .filterOrFail(!_.isLinkProp)(
              BadRequestException(s"Invalid property <$propertyIri>. Use a link value property to submit a link."),
            )

        // Make an adjusted version of the submitted property: if it's a link value property, substitute the
        // corresponding link property, whose objects we will need to query. Get ontology information about the
        // adjusted property.
        submittedInternalPropertyIri = propertyIri.toInternalSchema
        adjustedInternalPropertyInfo <-
          getAdjustedInternalPropertyInfo(
            submittedPropertyIri = deleteValue.propertyIri,
            maybeSubmittedValueType = None,
            propertyInfoForSubmittedProperty = propertyInfoForSubmittedProperty,
            requestingUser,
          )

        adjustedInternalPropertyIri =
          adjustedInternalPropertyInfo.entityInfoContent.propertyIri

        // Get the resource's metadata and relevant property objects, using the adjusted property. Do this as the system user,
        // so we can see objects that the user doesn't have permission to see.
        resourceInfo <-
          getResourceWithPropertyValues(
            resourceIri = deleteValue.resourceIri,
            propertyInfo = adjustedInternalPropertyInfo,
            requestingUser = KnoraSystemInstances.Users.SystemUser,
          )

        // Check that the resource belongs to the class that the client submitted.
        _ <- ZIO.when(resourceInfo.resourceClassIri != deleteValue.resourceClassIri.toOntologySchema(InternalSchema)) {
               ZIO.fail(
                 BadRequestException(
                   s"Resource <${deleteValue.resourceIri}> does not belong to class <${deleteValue.resourceClassIri}>",
                 ),
               )
             }

        // Check that the resource has the value that the user wants to delete, as an object of the submitted property.
        // Check that the user has permission to delete the value.
        currentValue <-
          ZIO
            .fromOption(for {
              values <- resourceInfo.values.get(submittedInternalPropertyIri)
              curVal <- values.find(_.valueIri == deleteValue.valueIri)
            } yield curVal)
            .orElseFail(
              NotFoundException(
                s"Resource <${deleteValue.resourceIri}> does not have value <${deleteValue.valueIri}> as an object of property <${deleteValue.propertyIri}>",
              ),
            )

        // Check that the value is of the type that the client submitted.
        _ <-
          ZIO.when(currentValue.valueContent.valueType != deleteValue.valueTypeIri.toOntologySchema(InternalSchema))(
            ZIO.fail(
              BadRequestException(
                s"Value <${deleteValue.valueIri}> in resource <${deleteValue.resourceIri}> is not of type <${deleteValue.valueTypeIri}>",
              ),
            ),
          )

        // Check the user's permissions on the value.
        _ <- resourceUtilV2.checkValuePermission(
               resourceInfo = resourceInfo,
               valueInfo = currentValue,
               permissionNeeded = Permission.ObjectAccess.Delete,
               requestingUser,
             )

        // Get the definition of the resource class.
        classInfoRequest =
          ClassesGetRequestV2(
            classIris = Set(resourceInfo.resourceClassIri),
            allLanguages = false,
            requestingUser,
          )

        classInfoResponse <- messageRelay.ask[ReadOntologyV2](classInfoRequest)
        cardinalityInfo <-
          ZIO
            .fromOption(
              classInfoResponse.classes
                .get(resourceInfo.resourceClassIri)
                .flatMap(_.allCardinalities.get(submittedInternalPropertyIri)),
            )
            .orElseFail(
              InconsistentRepositoryDataException(
                s"Resource <${deleteValue.resourceIri}> belongs to class <${resourceInfo.resourceClassIri
                    .toOntologySchema(ApiV2Complex)}>, which has no cardinality for property <${deleteValue.propertyIri}>",
              ),
            )

        // Check that the resource class's cardinality for the submitted property allows this value to be deleted.

        currentValuesForProp: Seq[ReadValueV2] =
          resourceInfo.values.getOrElse(submittedInternalPropertyIri, Seq.empty[ReadValueV2])

        _ <-
          ZIO.when(
            (cardinalityInfo.cardinality == ExactlyOne || cardinalityInfo.cardinality == AtLeastOne) && currentValuesForProp.size == 1,
          )(
            ZIO.fail(
              OntologyConstraintException(
                s"Resource class <${resourceInfo.resourceClassIri
                    .toOntologySchema(ApiV2Complex)}> has a cardinality of ${cardinalityInfo.cardinality} on property <${deleteValue.propertyIri}>, and this does not allow a value to be deleted for that property from resource <${deleteValue.resourceIri}>",
              ),
            ),
          )

        // If a custom delete date was submitted, make sure it's later than the date of the current version.
        _ <- ZIO.when(deleteValue.deleteDate.exists(!_.isAfter(currentValue.valueCreationDate)))(
               ZIO.fail(BadRequestException("A custom delete date must be later than the value's creation date")),
             )

        // Get information about the project that the resource is in, so we know which named graph to do the update in.
        dataNamedGraph: IRI = ProjectService.projectDataNamedGraphV2(resourceInfo.projectADM).value

        // Do the update.
        deletedValueIri <-
          deleteValueV2AfterChecks(
            dataNamedGraph,
            resourceInfo,
            adjustedInternalPropertyIri,
            deleteValue.deleteComment,
            deleteValue.deleteDate,
            currentValue,
            requestingUser,
          )

        // Check whether the update succeeded.
        sparqlSelectResponse <- triplestoreService.query(Select(sparql.v2.txt.checkValueDeletion(deletedValueIri)))
        rows                  = sparqlSelectResponse.results.bindings

        _ <-
          ZIO.when(
            rows.isEmpty || !ValuesValidator.optionStringToBoolean(rows.head.rowMap.get("isDeleted"), fallback = false),
          )(
            ZIO.fail(
              UpdateNotPerformedException(
                s"The request to mark value <${deleteValue.valueIri}> (or a new version of that value) as deleted did not succeed. Please report this as a possible bug.",
              ),
            ),
          )
      } yield SuccessResponseV2(s"Value <$deletedValueIri> marked as deleted")
    }
    IriLocker.runWithIriLock(apiRequestId, deleteValue.resourceIri, deleteTask)
  }

  /**
   * Deletes a value (either an ordinary value or a link), using an existing transaction, assuming that
   * pre-update checks have already been done.
   *
   * @param dataNamedGraph the named graph in which the value is to be deleted.
   * @param resourceInfo   information about the the resource in which to create the value.
   * @param propertyIri    the IRI of the property that points from the resource to the value.
   * @param currentValue   the value to be deleted.
   * @param deleteComment  an optional comment explaining why the value is being deleted.
   * @param deleteDate     an optional timestamp indicating when the value was deleted.
   * @param requestingUser the user making the request.
   * @return the IRI of the value that was marked as deleted.
   */
  private def deleteValueV2AfterChecks(
    dataNamedGraph: IRI,
    resourceInfo: ReadResourceV2,
    propertyIri: SmartIri,
    deleteComment: Option[String],
    deleteDate: Option[Instant],
    currentValue: ReadValueV2,
    requestingUser: User,
  ): Task[IRI] =
    currentValue.valueContent match {
      case _: LinkValueContentV2 =>
        deleteLinkValueV2AfterChecks(
          dataNamedGraph = dataNamedGraph,
          resourceInfo = resourceInfo,
          propertyIri = propertyIri,
          currentValue = currentValue,
          deleteComment = deleteComment,
          deleteDate = deleteDate,
          requestingUser = requestingUser,
        )

      case _ =>
        deleteOrdinaryValueV2AfterChecks(
          dataNamedGraph = dataNamedGraph,
          resourceInfo = resourceInfo,
          propertyIri = propertyIri,
          currentValue = currentValue,
          deleteComment = deleteComment,
          deleteDate = deleteDate,
          requestingUser = requestingUser,
        )
    }

  /**
   * Deletes a link after checks.
   *
   * @param dataNamedGraph the named graph in which the value is to be deleted.
   * @param resourceInfo   information about the the resource in which to create the value.
   * @param propertyIri    the IRI of the property that points from the resource to the value.
   * @param currentValue   the value to be deleted.
   * @param deleteComment  an optional comment explaining why the value is being deleted.
   * @param deleteDate     an optional timestamp indicating when the value was deleted.
   * @param requestingUser the user making the request.
   * @return the IRI of the value that was marked as deleted.
   */
  private def deleteLinkValueV2AfterChecks(
    dataNamedGraph: IRI,
    resourceInfo: ReadResourceV2,
    propertyIri: SmartIri,
    currentValue: ReadValueV2,
    deleteComment: Option[String],
    deleteDate: Option[Instant],
    requestingUser: User,
  ): Task[IRI] =
    // Make a new version of of the LinkValue with a reference count of 0, and mark the new
    // version as deleted. Give the new version the same permissions as the previous version.

    for {
      currentLinkValueContent <- currentValue.valueContent match {
                                   case linkValueContent: LinkValueContentV2 => ZIO.succeed(linkValueContent)
                                   case _                                    => ZIO.fail(AssertionException("Unreachable code"))
                                 }

      // If no custom delete date was provided, make a timestamp to indicate when the link value was
      // marked as deleted.
      currentTime: Instant = deleteDate.getOrElse(Instant.now)

      // Delete the existing link and decrement its LinkValue's reference count.
      sparqlTemplateLinkUpdate <-
        decrementLinkValue(
          sourceResourceInfo = resourceInfo,
          linkPropertyIri = propertyIri,
          targetResourceIri = currentLinkValueContent.referredResourceIri,
          valueCreator = currentValue.attachedToUser,
          valuePermissions = currentValue.permissions,
        )

      sparqlUpdate = sparql.v2.txt.deleteLink(
                       dataNamedGraph = dataNamedGraph,
                       linkSourceIri = resourceInfo.resourceIri,
                       linkUpdate = sparqlTemplateLinkUpdate,
                       maybeComment = deleteComment,
                       currentTime = currentTime,
                       requestingUser = requestingUser.id,
                     )

      _ <- triplestoreService.query(Update(sparqlUpdate))
    } yield sparqlTemplateLinkUpdate.newLinkValueIri

  /**
   * Deletes an ordinary value after checks.
   *
   * @param dataNamedGraph the named graph in which the value is to be deleted.
   * @param resourceInfo   information about the the resource in which to create the value.
   * @param propertyIri    the IRI of the property that points from the resource to the value.
   * @param currentValue   the value to be deleted.
   * @param deleteComment  an optional comment explaining why the value is being deleted.
   * @param deleteDate     an optional timestamp indicating when the value was deleted.
   * @param requestingUser the user making the request.
   * @return the IRI of the value that was marked as deleted.
   */
  private def deleteOrdinaryValueV2AfterChecks(
    dataNamedGraph: IRI,
    resourceInfo: ReadResourceV2,
    propertyIri: SmartIri,
    currentValue: ReadValueV2,
    deleteComment: Option[String],
    deleteDate: Option[Instant],
    requestingUser: User,
  ): Task[IRI] = {
    // Mark the existing version of the value as deleted.

    // If it's a TextValue, make SparqlTemplateLinkUpdates for updating LinkValues representing
    // links in standoff markup.
    val linkUpdateTasks: Seq[Task[SparqlTemplateLinkUpdate]] = currentValue.valueContent match {
      case textValue: TextValueContentV2 =>
        textValue.standoffLinkTagTargetResourceIris.toVector.map { removedTargetResource =>
          decrementLinkValue(
            sourceResourceInfo = resourceInfo,
            linkPropertyIri = OntologyConstants.KnoraBase.HasStandoffLinkTo.toSmartIri,
            targetResourceIri = removedTargetResource,
            valueCreator = KnoraUserRepo.builtIn.SystemUser.id.value,
            valuePermissions = standoffLinkValuePermissions,
          )
        }

      case _ => Seq.empty[Task[SparqlTemplateLinkUpdate]]
    }

    // If no custom delete date was provided, make a timestamp to indicate when the value was
    // marked as deleted.
    for {
      linkUpdates <- ZIO.collectAll(linkUpdateTasks)
      sparqlUpdate = sparql.v2.txt.deleteValue(
                       dataNamedGraph = dataNamedGraph,
                       resourceIri = resourceInfo.resourceIri,
                       propertyIri = propertyIri,
                       valueIri = currentValue.valueIri,
                       maybeDeleteComment = deleteComment,
                       linkUpdates = linkUpdates,
                       currentTime = deleteDate.getOrElse(Instant.now),
                       requestingUser = requestingUser.id,
                     )

      _ <- triplestoreService.query(Update(sparqlUpdate))
    } yield currentValue.valueIri
  }

  /**
   * When a property IRI is submitted for an update, makes an adjusted version of the submitted property:
   * if it's a link value property, substitutes the corresponding link property, whose objects we will need to query.
   *
   * @param submittedPropertyIri             the submitted property IRI, in the API v2 complex schema.
   * @param maybeSubmittedValueType          the submitted value type, if provided, in the API v2 complex schema.
   * @param propertyInfoForSubmittedProperty ontology information about the submitted property, in the internal schema.
   * @param requestingUser                   the requesting user.
   * @return ontology information about the adjusted property.
   */
  private def getAdjustedInternalPropertyInfo(
    submittedPropertyIri: SmartIri,
    maybeSubmittedValueType: Option[SmartIri],
    propertyInfoForSubmittedProperty: ReadPropertyInfoV2,
    requestingUser: User,
  ): Task[ReadPropertyInfoV2] = {
    val submittedInternalPropertyIri: SmartIri = submittedPropertyIri.toOntologySchema(InternalSchema)

    if (propertyInfoForSubmittedProperty.isLinkValueProp) {
      for {
        _ <- (maybeSubmittedValueType map { submittedValueType =>
               ZIO
                 .fail(
                   BadRequestException(
                     s"A value of type <$submittedValueType> cannot be an object of property <$submittedPropertyIri>",
                   ),
                 )
                 .when(submittedValueType.toString != OntologyConstants.KnoraApiV2Complex.LinkValue)
             }).getOrElse(ZIO.unit)
        internalLinkPropertyIri <- ZIO.attempt(submittedInternalPropertyIri.fromLinkValuePropToLinkProp)

        propertyInfoRequestForLinkProperty =
          PropertiesGetRequestV2(
            propertyIris = Set(internalLinkPropertyIri),
            allLanguages = false,
            requestingUser = requestingUser,
          )

        linkPropertyInfoResponse <- messageRelay.ask[ReadOntologyV2](propertyInfoRequestForLinkProperty)

      } yield linkPropertyInfoResponse.properties(internalLinkPropertyIri)
    } else if (propertyInfoForSubmittedProperty.isLinkProp) {
      ZIO.fail(
        BadRequestException(
          s"Invalid property for creating a link value (submit a link value property instead): $submittedPropertyIri",
        ),
      )
    } else {
      ZIO.succeed(propertyInfoForSubmittedProperty)
    }
  }

  /**
   * Given a set of resource IRIs, checks that they point to Knora resources.
   * If not, fails with an exception.
   *
   * @param targetResourceIris   the IRIs to be checked.
   *
   * @param requestingUser       the user making the request.
   */
  private def checkResourceIris(targetResourceIris: Set[IRI], requestingUser: User): Task[Unit] =
    messageRelay
      .ask[ReadResourcesSequenceV2](
        ResourcesPreviewGetRequestV2(
          resourceIris = targetResourceIris.toSeq,
          targetSchema = ApiV2Complex,
          requestingUser = requestingUser,
        ),
      )
      .unless(targetResourceIris.isEmpty)
      .unit

  /**
   * Returns a resource's metadata and its values, if any, for the specified property. If the property is a link property, the result
   * will contain any objects of the corresponding link value property (link values), as well as metadata for any resources that the link property points to.
   * If the property's object type is `knora-base:TextValue`, the result will contain any objects of the property (text values), as well metadata
   * for any resources that are objects of `knora-base:hasStandoffLinkTo`.
   *
   * @param resourceIri          the resource IRI.
   * @param propertyInfo         the property definition (in the internal schema). If the caller wants to query a link, this must be the link property,
   *                             not the link value property.
   *
   * @param requestingUser       the user making the request.
   * @return a [[ReadResourceV2]] containing only the resource's metadata and its values for the specified property.
   */
  private def getResourceWithPropertyValues(
    resourceIri: IRI,
    propertyInfo: ReadPropertyInfoV2,
    requestingUser: User,
  ): Task[ReadResourceV2] =
    for {
      // Get the property's object class constraint.
      objectClassConstraint <-
        ZIO
          .fromOption(
            propertyInfo.entityInfoContent.getIriObject(OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri),
          )
          .orElseFail(
            InconsistentRepositoryDataException(
              s"Property ${propertyInfo.entityInfoContent.propertyIri} has no knora-base:objectClassConstraint",
            ),
          )

      // If the property points to a text value, also query the resource's standoff links.
      maybeStandoffLinkToPropertyIri =
        if (objectClassConstraint.toString == OntologyConstants.KnoraBase.TextValue) {
          Some(OntologyConstants.KnoraBase.HasStandoffLinkTo.toSmartIri)
        } else {
          None
        }

      // Convert the property IRIs to be queried to the API v2 complex schema for Gravsearch.
      propertyIrisForGravsearchQuery =
        (Seq(propertyInfo.entityInfoContent.propertyIri) ++ maybeStandoffLinkToPropertyIri)
          .map(_.toOntologySchema(ApiV2Complex))

      // Make a Gravsearch query from a template.
      gravsearchQuery: String =
        org.knora.webapi.messages.twirl.queries.gravsearch.txt
          .getResourceWithSpecifiedProperties(
            resourceIri = resourceIri,
            propertyIris = propertyIrisForGravsearchQuery,
          )
          .toString()

      // Run the query.
      query <- ZIO.succeed(GravsearchParser.parseQuery(gravsearchQuery))
      searchResponse <-
        searchResponderV2.gravsearchV2(query, apiV2SchemaWithOption(MarkupRendering.Xml), requestingUser)
    } yield searchResponse.toResource(resourceIri)

  /**
   * Checks that a link value points to a resource with the correct type for the link property's object class constraint.
   *
   * @param linkPropertyIri       the IRI of the link property.
   * @param objectClassConstraint the object class constraint of the link property.
   * @param linkValueContent      the link value.
   *
   * @param requestingUser        the user making the request.
   */
  private def checkLinkPropertyObjectClassConstraint(
    linkPropertyIri: SmartIri,
    objectClassConstraint: SmartIri,
    linkValueContent: LinkValueContentV2,
    requestingUser: User,
  ): Task[Unit] =
    for {
      // Get a preview of the target resource, because we only need to find out its class and whether the user has permission to view it.
      resourcePreviewRequest <- ZIO.succeed(
                                  ResourcesPreviewGetRequestV2(
                                    resourceIris = Seq(linkValueContent.referredResourceIri),
                                    targetSchema = ApiV2Complex,
                                    requestingUser = requestingUser,
                                  ),
                                )

      resourcePreviewResponse <- messageRelay.ask[ReadResourcesSequenceV2](resourcePreviewRequest)

      // If we get a resource, we know the user has permission to view it.
      resource = resourcePreviewResponse.toResource(linkValueContent.referredResourceIri)

      // Ask the ontology responder whether the resource's class is a subclass of the link property's object class constraint.
      subClassRequest = CheckSubClassRequestV2(
                          subClassIri = resource.resourceClassIri,
                          superClassIri = objectClassConstraint,
                          requestingUser = requestingUser,
                        )

      subClassResponse <- messageRelay.ask[CheckSubClassResponseV2](subClassRequest)

      // If it isn't, fail with an exception.
      _ <-
        ZIO.when(!subClassResponse.isSubClass)(
          ZIO.fail(
            OntologyConstraintException(
              s"Resource <${linkValueContent.referredResourceIri}> cannot be the target of property <$linkPropertyIri>, because it is not a member of class <$objectClassConstraint>",
            ),
          ),
        )
    } yield ()

  /**
   * Checks that a non-link value has the correct type for a property's object class constraint.
   *
   * @param propertyIri           the IRI of the property that should point to the value.
   * @param objectClassConstraint the property's object class constraint.
   * @param valueContent          the value.
   * @param requestingUser        the user making the request.
   */
  private def checkNonLinkPropertyObjectClassConstraint(
    propertyIri: SmartIri,
    objectClassConstraint: SmartIri,
    valueContent: ValueContentV2,
    requestingUser: User,
  ): Task[Unit] =
    // Is the value type the same as the property's object class constraint?
    ZIO
      .unless(objectClassConstraint == valueContent.valueType) {
        for {
          subClassRequest <- ZIO.succeed(
                               CheckSubClassRequestV2(
                                 subClassIri = valueContent.valueType,
                                 superClassIri = objectClassConstraint,
                                 requestingUser = requestingUser,
                               ),
                             )

          subClassResponse <- messageRelay.ask[CheckSubClassResponseV2](subClassRequest)

          // If it isn't, fail with an exception.
          _ <-
            ZIO.when(!subClassResponse.isSubClass) {
              ZIO.fail(
                OntologyConstraintException(
                  s"A value of type <${valueContent.valueType}> cannot be the target of property <$propertyIri>, because it is not a member of class <$objectClassConstraint>",
                ),
              )
            }

        } yield ()
      }
      .unit

  /**
   * Checks that a value to be updated has the correct type for the `knora-base:objectClassConstraint` of
   * the property that is supposed to point to it.
   *
   * @param propertyInfo         the property whose object class constraint is to be checked. If the value is a link value, this is the link property.
   * @param valueContent         the value to be updated.
   *
   * @param requestingUser       the user making the request.
   */
  private def checkPropertyObjectClassConstraint(
    propertyInfo: ReadPropertyInfoV2,
    valueContent: ValueContentV2,
    requestingUser: User,
  ): Task[Unit] = {
    val propertyIri = propertyInfo.entityInfoContent.propertyIri
    for {
      objectClassConstraint <-
        ZIO
          .fromOption(
            propertyInfo.entityInfoContent.getIriObject(OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri),
          )
          .orElseFail(
            InconsistentRepositoryDataException(s"Property $propertyIri has no knora-base:objectClassConstraint"),
          )

      result <-
        valueContent match {
          // We're creating a link.
          case linkValueContent: LinkValueContentV2 =>
            ZIO.when(!propertyInfo.isLinkProp)(
              ZIO.fail(
                BadRequestException(s"Property <${propertyIri.toOntologySchema(ApiV2Complex)}> is not a link property"),
              ),
              // Check that the property whose object class constraint is to be checked is actually a link property.
            ) *> checkLinkPropertyObjectClassConstraint(
              propertyIri,
              objectClassConstraint,
              linkValueContent,
              requestingUser,
            )

          // We're creating an ordinary value.
          case otherValue =>
            // Check that its type is valid for the property's object class constraint.
            checkNonLinkPropertyObjectClassConstraint(propertyIri, objectClassConstraint, otherValue, requestingUser)
        }
    } yield result
  }

  /**
   * Given a [[ReadResourceV2]], finds a link that uses the specified property and points to the specified target
   * resource.
   *
   * @param sourceResourceInfo a [[ReadResourceV2]] describing the source of the link.
   * @param linkPropertyIri    the IRI of the link property.
   * @param targetResourceIri  the IRI of the target resource.
   * @return a [[ReadLinkValueV2]] describing the link value, if found.
   */
  private def findLinkValue(
    sourceResourceInfo: ReadResourceV2,
    linkPropertyIri: SmartIri,
    targetResourceIri: IRI,
  ): Option[ReadLinkValueV2] = {
    val linkValueProperty = linkPropertyIri.fromLinkPropToLinkValueProp

    sourceResourceInfo.values.get(linkValueProperty).flatMap { (linkValueInfos: Seq[ReadValueV2]) =>
      linkValueInfos.collectFirst {
        case linkValueInfo: ReadLinkValueV2 if linkValueInfo.valueContent.referredResourceIri == targetResourceIri =>
          linkValueInfo
      }
    }
  }

  /**
   * Generates a [[SparqlTemplateLinkUpdate]] to tell a SPARQL update template how to create a `LinkValue` or to
   * increment the reference count of an existing `LinkValue`. This happens in two cases:
   *
   *  - When the user creates a link. In this case, neither the link nor the `LinkValue` exist yet. The
   * [[SparqlTemplateLinkUpdate]] will specify that the link should be created, and that the `LinkValue` should be
   * created with a reference count of 1.
   *  - When a text value is updated so that its standoff markup refers to a resource that it did not previously
   * refer to. Here there are two possibilities:
   *    - If there is currently a `knora-base:hasStandoffLinkTo` link between the source and target resources, with a
   * corresponding `LinkValue`, a new version of the `LinkValue` will be made, with an incremented reference count.
   *    - If that link and `LinkValue` don't yet exist, they will be created, and the `LinkValue` will be given
   * a reference count of 1.
   *
   * @param sourceResourceInfo    information about the source resource.
   * @param linkPropertyIri       the IRI of the property that links the source resource to the target resource.
   * @param targetResourceIri     the IRI of the target resource.
   * @param customNewLinkValueIri the optional custom IRI supplied for the link value.
   * @param valueCreator          the IRI of the new link value's owner.
   * @param valuePermissions      the literal that should be used as the object of the new link value's `knora-base:hasPermissions` predicate.
   * @return a [[SparqlTemplateLinkUpdate]] that can be passed to a SPARQL update template.
   */
  private def incrementLinkValue(
    sourceResourceInfo: ReadResourceV2,
    linkPropertyIri: SmartIri,
    targetResourceIri: IRI,
    customNewLinkValueIri: Option[SmartIri] = None,
    valueCreator: IRI,
    valuePermissions: IRI,
  ) = {
    // Check whether a LinkValue already exists for this link.
    val maybeLinkValueInfo: Option[ReadLinkValueV2] = findLinkValue(
      sourceResourceInfo = sourceResourceInfo,
      linkPropertyIri = linkPropertyIri,
      targetResourceIri = targetResourceIri,
    )

    for {
      // Make an IRI for the new LinkValue.
      newLinkValueIri <-
        iriService.checkOrCreateEntityIri(
          customNewLinkValueIri,
          stringFormatter.makeRandomValueIri(sourceResourceInfo.resourceIri),
        )

      linkUpdate =
        maybeLinkValueInfo match {
          case Some(linkValueInfo) =>
            // There's already a LinkValue for links between these two resources. Increment
            // its reference count.
            SparqlTemplateLinkUpdate(
              linkPropertyIri = linkPropertyIri,
              directLinkExists = true,
              insertDirectLink = false,
              deleteDirectLink = false,
              linkValueExists = true,
              linkTargetExists = true,
              newLinkValueIri = newLinkValueIri,
              linkTargetIri = targetResourceIri,
              currentReferenceCount = linkValueInfo.valueHasRefCount,
              newReferenceCount = linkValueInfo.valueHasRefCount + 1,
              newLinkValueCreator = valueCreator,
              newLinkValuePermissions = valuePermissions,
            )

          case None =>
            // There's no LinkValue for links between these two resources, so create one, and give it
            // a reference count of 1.
            SparqlTemplateLinkUpdate(
              linkPropertyIri = linkPropertyIri,
              directLinkExists = false,
              insertDirectLink = true,
              deleteDirectLink = false,
              linkValueExists = false,
              linkTargetExists = true,
              newLinkValueIri = newLinkValueIri,
              linkTargetIri = targetResourceIri,
              currentReferenceCount = 0,
              newReferenceCount = 1,
              newLinkValueCreator = valueCreator,
              newLinkValuePermissions = valuePermissions,
            )
        }
    } yield linkUpdate
  }

  /**
   * Generates a [[SparqlTemplateLinkUpdate]] to tell a SPARQL update template how to decrement the reference count
   * of a `LinkValue`. This happens in two cases:
   *
   *  - When the user deletes (or changes) a user-created link. In this case, the current reference count will be 1.
   * The existing link will be removed. A new version of the `LinkValue` be made with a reference count of 0, and
   * will be marked as deleted.
   *  - When a resource reference is removed from standoff markup on a text value, so that the text value no longer
   * contains any references to that target resource. In this case, a new version of the `LinkValue` will be
   * made, with a decremented reference count. If the new reference count is 0, the link will be removed and the
   * `LinkValue` will be marked as deleted.
   *
   * @param sourceResourceInfo information about the source resource.
   * @param linkPropertyIri    the IRI of the property that links the source resource to the target resource.
   * @param targetResourceIri  the IRI of the target resource.
   * @param valueCreator       the IRI of the new link value's owner.
   * @param valuePermissions   the literal that should be used as the object of the new link value's `knora-base:hasPermissions` predicate.
   * @return a [[SparqlTemplateLinkUpdate]] that can be passed to a SPARQL update template.
   */
  private def decrementLinkValue(
    sourceResourceInfo: ReadResourceV2,
    linkPropertyIri: SmartIri,
    targetResourceIri: IRI,
    valueCreator: IRI,
    valuePermissions: IRI,
  ) = {

    // Check whether a LinkValue already exists for this link.
    val maybeLinkValueInfo = findLinkValue(
      sourceResourceInfo = sourceResourceInfo,
      linkPropertyIri = linkPropertyIri,
      targetResourceIri = targetResourceIri,
    )

    // Did we find it?
    maybeLinkValueInfo match {
      case Some(linkValueInfo) =>
        // Yes. Make a SparqlTemplateLinkUpdate.

        // Decrement the LinkValue's reference count.
        val newReferenceCount = linkValueInfo.valueHasRefCount - 1

        // If the new reference count is 0, specify that the direct link between the source and target
        // resources should be removed.
        val deleteDirectLink = newReferenceCount == 0

        makeUnusedValueIri(sourceResourceInfo.resourceIri)
          .map(newLinkValueIri =>
            SparqlTemplateLinkUpdate(
              linkPropertyIri = linkPropertyIri,
              directLinkExists = true,
              insertDirectLink = false,
              deleteDirectLink = deleteDirectLink,
              linkValueExists = true,
              linkTargetExists = true,
              newLinkValueIri = newLinkValueIri,
              linkTargetIri = targetResourceIri,
              currentReferenceCount = linkValueInfo.valueHasRefCount,
              newReferenceCount = newReferenceCount,
              newLinkValueCreator = valueCreator,
              newLinkValuePermissions = valuePermissions,
            ),
          )

      case None =>
        // We didn't find the LinkValue. This shouldn't happen.
        ZIO.die(
          InconsistentRepositoryDataException(
            s"There should be a knora-base:LinkValue describing a direct link from resource <${sourceResourceInfo.resourceIri}> to resource <$targetResourceIri> using property <$linkPropertyIri>, but it seems to be missing",
          ),
        )
    }
  }

  /**
   * Generates a [[SparqlTemplateLinkUpdate]] to tell a SPARQL update template how to change the metadata
   * on a `LinkValue`.
   *
   * @param sourceResourceInfo    information about the source resource.
   * @param linkPropertyIri       the IRI of the property that links the source resource to the target resource.
   * @param targetResourceIri     the IRI of the target resource.
   * @param customNewLinkValueIri the optional custom IRI supplied for the link value.
   * @param valueCreator          the IRI of the new link value's owner.
   * @param valuePermissions      the literal that should be used as the object of the new link value's `knora-base:hasPermissions` predicate.
   * @return a [[SparqlTemplateLinkUpdate]] that can be passed to a SPARQL update template.
   */
  private def changeLinkValueMetadata(
    sourceResourceInfo: ReadResourceV2,
    linkPropertyIri: SmartIri,
    targetResourceIri: IRI,
    customNewLinkValueIri: Option[SmartIri],
    valueCreator: IRI,
    valuePermissions: IRI,
  ) = {

    // Check whether a LinkValue already exists for this link.
    val maybeLinkValueInfo: Option[ReadLinkValueV2] = findLinkValue(
      sourceResourceInfo = sourceResourceInfo,
      linkPropertyIri = linkPropertyIri,
      targetResourceIri = targetResourceIri,
    )

    // Did we find it?
    maybeLinkValueInfo match {
      case Some(linkValueInfo) =>
        // Yes. Make a SparqlTemplateLinkUpdate.

        for {
          // If no custom IRI was provided, generate an IRI for the new LinkValue.
          newLinkValueIri <-
            iriService.checkOrCreateEntityIri(
              customNewLinkValueIri,
              stringFormatter.makeRandomValueIri(sourceResourceInfo.resourceIri),
            )

        } yield SparqlTemplateLinkUpdate(
          linkPropertyIri = linkPropertyIri,
          directLinkExists = true,
          insertDirectLink = false,
          deleteDirectLink = false,
          linkValueExists = true,
          linkTargetExists = true,
          newLinkValueIri = newLinkValueIri,
          linkTargetIri = targetResourceIri,
          currentReferenceCount = linkValueInfo.valueHasRefCount,
          newReferenceCount = linkValueInfo.valueHasRefCount,
          newLinkValueCreator = valueCreator,
          newLinkValuePermissions = valuePermissions,
        )

      case None =>
        // We didn't find the LinkValue. This shouldn't happen.
        ZIO.die(
          InconsistentRepositoryDataException(
            s"There should be a knora-base:LinkValue describing a direct link from resource <${sourceResourceInfo.resourceIri}> to resource <$targetResourceIri> using property <$linkPropertyIri>, but it seems to be missing",
          ),
        )
    }
  }

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

  /**
   * A convenience method for generating an unused random value IRI.
   *
   * @param resourceIri the IRI of the containing resource.
   * @return the new value IRI.
   */
  private def makeUnusedValueIri(resourceIri: IRI): Task[IRI] =
    iriService.makeUnusedIri(stringFormatter.makeRandomValueIri(resourceIri))
}

object ValuesResponderV2 {
  val layer = ZLayer.derive[ValuesResponderV2]

  /**
   * Make a new value UUID considering optional custom value UUID and custom value IRI.
   * If a custom UUID is given, this method checks that it matches the ending of a given IRI, if there was any.
   * If no custom UUID is given for a value, it checks if a custom value IRI is given or not. If yes, it extracts the
   * UUID from the given IRI. If no custom value IRI was given, it generates a random UUID.
   *
   * @param maybeCustomIri  the optional value IRI.
   * @param maybeCustomUUID the optional value UUID.
   * @return the new value UUID.
   */
  def makeNewValueUUID(
    maybeCustomIri: Option[SmartIri],
    maybeCustomUUID: Option[UUID],
  ): IO[BadRequestException, UUID] =
    (maybeCustomIri, maybeCustomUUID) match {
      case (Some(customIri: SmartIri), Some(customValueUUID)) => combineCustoms(customIri, customValueUUID)
      case (None, Some(customValueUUID))                      => ZIO.succeed(customValueUUID)
      case (Some(customIri), None) =>
        ZIO.fromOption(customIri.getUuid).orElseFail(BadRequestException(s"Invalid UUID in IRI: $customIri"))
      case (None, None) => Random.nextUUID

    }

  private def combineCustoms(iri: SmartIri, uuid: UUID): IO[BadRequestException, UUID] =
    if (iri.getUuid.contains(uuid)) ZIO.succeed(uuid)
    else
      ZIO.fail(
        BadRequestException(
          s"Given custom IRI $iri should contain the given custom UUID ${UuidUtil.base64Encode(uuid)}.",
        ),
      )
}
