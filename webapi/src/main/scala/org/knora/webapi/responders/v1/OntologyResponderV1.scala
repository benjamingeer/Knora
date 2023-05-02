/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v1

import com.typesafe.scalalogging.LazyLogging
import zio._

import dsp.constants.SalsahGui
import dsp.errors.InconsistentRepositoryDataException
import dsp.errors.NotFoundException
import org.knora.webapi.IRI
import org.knora.webapi._
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageHandler
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.ResponderRequest
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsGetRequestADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsGetResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.util.ValueUtilV1
import org.knora.webapi.messages.v1.responder.ontologymessages._
import org.knora.webapi.messages.v1.responder.resourcemessages.SalsahGuiConversions
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality.KnoraCardinalityInfo
import org.knora.webapi.messages.v2.responder.ontologymessages._
import org.knora.webapi.responders.Responder
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.util.ZioHelper

/**
 * Handles requests for information about ontology entities.
 *
 * All ontology data is loaded and cached when the application starts. To refresh the cache, you currently have to restart
 * the application.
 */
trait OntologyResponderV1

final case class OntologyResponderV1Live(
  appConfig: AppConfig,
  messageRelay: MessageRelay,
  iriConverter: IriConverter,
  valueUtilV1: ValueUtilV1
) extends OntologyResponderV1
    with MessageHandler
    with LazyLogging {

  override def isResponsibleFor(message: ResponderRequest): Boolean =
    message.isInstanceOf[OntologyResponderRequestV1]

  override def handle(msg: ResponderRequest): Task[Any] = msg match {
    case LoadOntologiesRequestV1(userProfile) => loadOntologies(userProfile)
    case EntityInfoGetRequestV1(resourceIris, propertyIris, userProfile) =>
      getEntityInfoResponseV1(resourceIris, propertyIris, userProfile)
    case ResourceTypeGetRequestV1(resourceTypeIri, userProfile) =>
      getResourceTypeResponseV1(resourceTypeIri, userProfile)
    case checkSubClassRequest: CheckSubClassRequestV1 => checkSubClass(checkSubClassRequest)
    case subClassesGetRequest: SubClassesGetRequestV1 => getSubClasses(subClassesGetRequest)
    case NamedGraphsGetRequestV1(projectIris, userProfile) =>
      getNamedGraphs(projectIris, userProfile)
    case ResourceTypesForNamedGraphGetRequestV1(namedGraphIri, userProfile) =>
      getResourceTypesForNamedGraph(namedGraphIri, userProfile)
    case PropertyTypesForNamedGraphGetRequestV1(namedGraphIri, userProfile) =>
      getPropertyTypesForNamedGraph(namedGraphIri, userProfile)
    case PropertyTypesForResourceTypeGetRequestV1(restypeId, userProfile) =>
      getPropertyTypesForResourceType(restypeId, userProfile)
    case StandoffEntityInfoGetRequestV1(standoffClassIris, standoffPropertyIris, userProfile) =>
      getStandoffEntityInfoResponseV1(standoffClassIris, standoffPropertyIris, userProfile)
    case StandoffClassesWithDataTypeGetRequestV1(userProfile) => getStandoffStandoffClassesWithDataTypeV1(userProfile)
    case StandoffAllPropertiesGetRequestV1(userProfile)       => getAllStandoffPropertyEntities(userProfile)
    case NamedGraphEntityInfoRequestV1(namedGraphIri, userProfile) =>
      getNamedGraphEntityInfoV1ForNamedGraph(namedGraphIri, userProfile)
    case other => Responder.handleUnexpectedMessage(other, this.getClass.getName)
  }

  private def noClassConstraintException(iri: IRI) = InconsistentRepositoryDataException(
    s"Property $iri has no knora-base:objectClassConstraint"
  )

  /**
   * Loads and caches all ontology information.
   *
   * @param userProfile          the profile of the user making the request.
   * @return a [[LoadOntologiesResponse]].
   */
  private def loadOntologies(userProfile: UserADM): Task[LoadOntologiesResponse] =
    messageRelay
      .ask[SuccessResponseV2](LoadOntologiesRequestV2(userProfile))
      .as(LoadOntologiesResponse())

  /**
   * Given a list of resource IRIs and a list of property IRIs (ontology entities), returns an [[EntityInfoGetResponseV1]] describing both resource and property entities.
   *
   * @param resourceClassIris the IRIs of the resource entities to be queried.
   * @param propertyIris      the IRIs of the property entities to be queried.
   * @param userProfile       the profile of the user making the request.
   * @return an [[EntityInfoGetResponseV1]].
   */
  private def getEntityInfoResponseV1(
    resourceClassIris: Set[IRI] = Set.empty[IRI],
    propertyIris: Set[IRI] = Set.empty[IRI],
    userProfile: UserADM
  ): Task[EntityInfoGetResponseV1] =
    for {
      classIri <- iriConverter.asSmartIris(resourceClassIris)
      propIri  <- iriConverter.asSmartIris(propertyIris)
      response <- messageRelay
                    .ask[EntityInfoGetResponseV2](
                      EntityInfoGetRequestV2(classIri, propIri, userProfile)
                    )
    } yield EntityInfoGetResponseV1(
      resourceClassInfoMap = ConvertOntologyClassV2ToV1.classInfoMapV2ToV1(response.classInfoMap),
      propertyInfoMap = ConvertOntologyClassV2ToV1.propertyInfoMapV2ToV1(response.propertyInfoMap)
    )

  /**
   * Given a list of standoff class IRIs and a list of property IRIs (ontology entities), returns an [[StandoffEntityInfoGetResponseV1]] describing both resource and property entities.
   *
   * @param standoffClassIris    the IRIs of the resource entities to be queried.
   * @param standoffPropertyIris the IRIs of the property entities to be queried.
   * @param userProfile          the profile of the user making the request.
   * @return an [[EntityInfoGetResponseV1]].
   */
  private def getStandoffEntityInfoResponseV1(
    standoffClassIris: Set[IRI] = Set.empty[IRI],
    standoffPropertyIris: Set[IRI] = Set.empty[IRI],
    userProfile: UserADM
  ): Task[StandoffEntityInfoGetResponseV1] =
    for {
      classIri <- iriConverter.asSmartIris(standoffClassIris)
      propIri  <- iriConverter.asSmartIris(standoffPropertyIris)
      response <- messageRelay
                    .ask[StandoffEntityInfoGetResponseV2](
                      StandoffEntityInfoGetRequestV2(classIri, propIri, userProfile)
                    )

    } yield StandoffEntityInfoGetResponseV1(
      standoffClassInfoMap = ConvertOntologyClassV2ToV1.classInfoMapV2ToV1(response.standoffClassInfoMap),
      standoffPropertyInfoMap = ConvertOntologyClassV2ToV1.propertyInfoMapV2ToV1(response.standoffPropertyInfoMap)
    )

  /**
   * Gets information about all standoff classes that are a subclass of a data type standoff class.
   *
   * @param userProfile the profile of the user making the request.
   * @return a [[StandoffClassesWithDataTypeGetResponseV1]]
   */
  private def getStandoffStandoffClassesWithDataTypeV1(
    userProfile: UserADM
  ): Task[StandoffClassesWithDataTypeGetResponseV1] =
    for {
      response <- messageRelay
                    .ask[StandoffClassesWithDataTypeGetResponseV2](
                      StandoffClassesWithDataTypeGetRequestV2(
                        userProfile
                      )
                    )

    } yield StandoffClassesWithDataTypeGetResponseV1(
      standoffClassInfoMap = ConvertOntologyClassV2ToV1.classInfoMapV2ToV1(response.standoffClassInfoMap)
    )

  /**
   * Gets all standoff property entities.
   *
   * @param userProfile the profile of the user making the request.
   * @return a [[StandoffAllPropertiesGetResponseV1]].
   */
  private def getAllStandoffPropertyEntities(userProfile: UserADM): Task[StandoffAllPropertiesGetResponseV1] =
    for {
      response <- messageRelay
                    .ask[StandoffAllPropertyEntitiesGetResponseV2](
                      StandoffAllPropertyEntitiesGetRequestV2(
                        userProfile
                      )
                    )

    } yield StandoffAllPropertiesGetResponseV1(
      standoffAllPropertiesInfoMap =
        ConvertOntologyClassV2ToV1.propertyInfoMapV2ToV1(response.standoffAllPropertiesEntityInfoMap)
    )

  /**
   * Given the IRI of a resource type, returns a [[ResourceTypeResponseV1]] describing the resource type and its possible
   * properties.
   *
   * @param resourceTypeIri the IRI of the resource type to be queried.
   * @param userProfile     the profile of the user making the request.
   * @return a [[ResourceTypeResponseV1]].
   */
  private def getResourceTypeResponseV1(
    resourceTypeIri: String,
    userProfile: UserADM
  ): Task[ResourceTypeResponseV1] = {

    for {
      // Get all information about the resource type, including its property cardinalities.
      resourceClassInfoResponse <- getEntityInfoResponseV1(
                                     resourceClassIris = Set(resourceTypeIri),
                                     userProfile = userProfile
                                   )
      resourceClassInfo <-
        ZIO
          .fromOption(resourceClassInfoResponse.resourceClassInfoMap.get(resourceTypeIri))
          .orElseFail(NotFoundException(s"Resource class $resourceTypeIri not found"))

      // Get all information about those properties.
      propertyInfo <- getEntityInfoResponseV1(
                        propertyIris = resourceClassInfo.knoraResourceCardinalities.keySet,
                        userProfile = userProfile
                      )

      // Build the property definitions.
      propertyDefinitions <-
        ZIO
          .collectAll(resourceClassInfo.knoraResourceCardinalities.filterNot {
            // filter out the properties that point to LinkValue objects
            case (propertyIri, _) =>
              resourceClassInfo.linkValueProperties(
                propertyIri
              ) || propertyIri == OntologyConstants.KnoraBase.HasStandoffLinkTo
          }.map {
            case (
                  propertyIri: IRI,
                  cardinalityInfo: KnoraCardinalityInfo
                ) =>
              propertyInfo.propertyInfoMap.get(propertyIri) match {
                case Some(entityInfo: PropertyInfoV1) =>
                  if (entityInfo.isLinkProp) {
                    // It is a linking prop: its valuetype_id is knora-base:LinkValue.
                    // It is restricted to the resource class that is given for knora-base:objectClassConstraint
                    // for the given property which goes in the attributes that will be read by the GUI.
                    for {
                      attributeResType <-
                        ZIO
                          .fromOption(entityInfo.getPredicateObject(OntologyConstants.KnoraBase.ObjectClassConstraint))
                          .orElseFail(noClassConstraintException(propertyIri))
                    } yield PropertyDefinitionV1(
                      id = propertyIri,
                      name = propertyIri,
                      label = entityInfo.getPredicateObject(
                        predicateIri = OntologyConstants.Rdfs.Label,
                        preferredLangs = Some(
                          userProfile.lang,
                          appConfig.fallbackLanguage
                        )
                      ),
                      description = entityInfo.getPredicateObject(
                        predicateIri = OntologyConstants.Rdfs.Comment,
                        preferredLangs = Some(
                          userProfile.lang,
                          appConfig.fallbackLanguage
                        )
                      ),
                      vocabulary = entityInfo.ontologyIri,
                      occurrence = cardinalityInfo.cardinality.toString,
                      valuetype_id = OntologyConstants.KnoraBase.LinkValue,
                      attributes = valueUtilV1.makeAttributeString(
                        entityInfo
                          .getPredicateStringObjectsWithoutLang(
                            SalsahGui.GuiAttribute
                          ) + valueUtilV1.makeAttributeRestype(attributeResType)
                      ),
                      gui_name = entityInfo
                        .getPredicateObject(
                          SalsahGui.GuiElementProp
                        )
                        .map(iri => SalsahGuiConversions.iri2SalsahGuiElement(iri)),
                      guiorder = cardinalityInfo.guiOrder
                    )

                  } else {
                    for {
                      valueTypeId <-
                        ZIO
                          .fromOption(entityInfo.getPredicateObject(OntologyConstants.KnoraBase.ObjectClassConstraint))
                          .orElseFail(noClassConstraintException(propertyIri))
                    } yield PropertyDefinitionV1(
                      id = propertyIri,
                      name = propertyIri,
                      label = entityInfo.getPredicateObject(
                        predicateIri = OntologyConstants.Rdfs.Label,
                        preferredLangs = Some(
                          userProfile.lang,
                          appConfig.fallbackLanguage
                        )
                      ),
                      description = entityInfo.getPredicateObject(
                        predicateIri = OntologyConstants.Rdfs.Comment,
                        preferredLangs = Some(
                          userProfile.lang,
                          appConfig.fallbackLanguage
                        )
                      ),
                      vocabulary = entityInfo.ontologyIri,
                      occurrence = cardinalityInfo.cardinality.toString,
                      valuetype_id = valueTypeId,
                      attributes = valueUtilV1.makeAttributeString(
                        entityInfo.getPredicateStringObjectsWithoutLang(
                          SalsahGui.GuiAttribute
                        )
                      ),
                      gui_name = entityInfo
                        .getPredicateObject(
                          SalsahGui.GuiElementProp
                        )
                        .map(iri => SalsahGuiConversions.iri2SalsahGuiElement(iri)),
                      guiorder = cardinalityInfo.guiOrder
                    )
                  }
                case None =>
                  ZIO.fail(
                    new InconsistentRepositoryDataException(
                      s"Resource type $resourceTypeIri is defined as having property $propertyIri, which doesn't exist"
                    )
                  )
              }
          }.toVector)
          .map(_.sortBy(_.guiorder))

      // Build the API response.
      resourceTypeResponse = ResourceTypeResponseV1(
                               restype_info = ResTypeInfoV1(
                                 name = resourceTypeIri,
                                 label = resourceClassInfo.getPredicateObject(
                                   predicateIri = OntologyConstants.Rdfs.Label,
                                   preferredLangs = Some(userProfile.lang, appConfig.fallbackLanguage)
                                 ),
                                 description = resourceClassInfo.getPredicateObject(
                                   predicateIri = OntologyConstants.Rdfs.Comment,
                                   preferredLangs = Some(userProfile.lang, appConfig.fallbackLanguage)
                                 ),
                                 iconsrc =
                                   resourceClassInfo.getPredicateObject(OntologyConstants.KnoraBase.ResourceIcon),
                                 properties = propertyDefinitions
                               )
                             )
    } yield resourceTypeResponse
  }

  /**
   * Checks whether a certain Knora resource or value class is a subclass of another class.
   *
   * @param checkSubClassRequest a [[CheckSubClassRequestV1]]
   * @return a [[CheckSubClassResponseV1]].
   */
  private def checkSubClass(checkSubClassRequest: CheckSubClassRequestV1): Task[CheckSubClassResponseV1] =
    for {
      subIri   <- iriConverter.asSmartIri(checkSubClassRequest.subClassIri)
      superIri <- iriConverter.asSmartIri(checkSubClassRequest.superClassIri)
      response <- messageRelay
                    .ask[CheckSubClassResponseV2](
                      CheckSubClassRequestV2(
                        subClassIri = subIri,
                        superClassIri = superIri,
                        checkSubClassRequest.userProfile
                      )
                    )

    } yield CheckSubClassResponseV1(response.isSubClass)

  /**
   * Gets the IRIs of the subclasses of a resource class.
   *
   * @param getSubClassesRequest a [[SubClassesGetRequestV1]].
   * @return a [[SubClassesGetResponseV1]].
   */
  private def getSubClasses(getSubClassesRequest: SubClassesGetRequestV1): Task[SubClassesGetResponseV1] =
    for {
      iri <- iriConverter.asSmartIri(getSubClassesRequest.resourceClassIri)
      response <- messageRelay
                    .ask[SubClassesGetResponseV2](
                      SubClassesGetRequestV2(iri, getSubClassesRequest.userADM)
                    )

      subClasses = response.subClasses.map { subClassInfoV2 =>
                     SubClassInfoV1(
                       id = subClassInfoV2.id.toString,
                       label = subClassInfoV2.label
                     )
                   }

    } yield SubClassesGetResponseV1(subClasses)

  /**
   * Returns information about ontology named graphs as a [[NamedGraphsResponseV1]].
   *
   * @param projectIris          the IRIs of the projects whose named graphs should be returned.
   *
   * @param userProfile          the profile of the user making the request.
   * @return a [[NamedGraphsResponseV1]].
   */
  private def getNamedGraphs(
    projectIris: Set[IRI] = Set.empty[IRI],
    userProfile: UserADM
  ): Task[NamedGraphsResponseV1] =
    for {
      projectsResponse <- messageRelay.ask[ProjectsGetResponseADM](ProjectsGetRequestADM(withSystemProjects = true))
      iris             <- iriConverter.asSmartIris(projectIris)

      readOntologyMetadataV2 <-
        messageRelay
          .ask[ReadOntologyMetadataV2](
            OntologyMetadataGetByProjectRequestV2(iris, userProfile)
          )

      projectsMap =
        projectsResponse.projects.map { project =>
          project.id -> project
        }.toMap

      namedGraphs <-
        ZIO
          .attempt(ZIO.foreach {
            readOntologyMetadataV2.ontologies.toVector
              .map(_.toOntologySchema(InternalSchema))
              .filter { ontologyMetadata =>
                // In V1, the only built-in ontology we show is knora-base.
                val ontologyLabel = ontologyMetadata.ontologyIri.getOntologyName
                ontologyLabel == OntologyConstants.KnoraBase.KnoraBaseOntologyLabel || !OntologyConstants.BuiltInOntologyLabels
                  .contains(ontologyLabel)
              }
          } { ontologyMetadata =>
            val project = projectsMap(ontologyMetadata.projectIri.get.toString)

            for {
              longname <-
                ZIO
                  .fromOption(project.longname)
                  .orElseFail(InconsistentRepositoryDataException(s"Project ${project.id} has no longname"))
              description <-
                ZIO
                  .fromOption(project.description.headOption)
                  .orElseFail(InconsistentRepositoryDataException(s"Project ${project.id} has no description"))

            } yield NamedGraphV1(
              id = ontologyMetadata.ontologyIri.toString,
              shortname = project.shortname,
              longname = longname,
              description = description.toString,
              project_id = project.id,
              uri = ontologyMetadata.ontologyIri.toString,
              active = project.status
            )
          })
          .flatten

      response = NamedGraphsResponseV1(vocabularies = namedGraphs)
    } yield response

  /**
   * Gets the [[NamedGraphEntityInfoV1]] for a named graph
   *
   * @param namedGraphIri the IRI of the named graph to query
   * @param userProfile   the profile of the user making the request.
   * @return a [[NamedGraphEntityInfoV1]].
   */
  private def getNamedGraphEntityInfoV1ForNamedGraph(
    namedGraphIri: IRI,
    userProfile: UserADM
  ): Task[NamedGraphEntityInfoV1] =
    for {
      iri <- iriConverter.asSmartIri(namedGraphIri)
      response <- messageRelay.ask[OntologyKnoraEntitiesIriInfoV2](
                    OntologyKnoraEntityIrisGetRequestV2(iri, userProfile)
                  )

      classIrisForV1    = response.classIris.map(_.toString) -- OntologyConstants.KnoraBase.AbstractResourceClasses
      propertyIrisForV1 = response.propertyIris.map(_.toString) - OntologyConstants.KnoraBase.ResourceProperty

    } yield NamedGraphEntityInfoV1(
      namedGraphIri = response.ontologyIri.toString,
      resourceClasses = classIrisForV1,
      propertyIris = propertyIrisForV1
    )

  /**
   * Gets all the resource classes and their properties for a named graph.
   *
   * @param namedGraphIriOption  the IRI of the named graph or None if all the named graphs should be queried.
   *
   * @param userProfile          the profile of the user making the request.
   * @return [[ResourceTypesForNamedGraphResponseV1]].
   */
  private def getResourceTypesForNamedGraph(
    namedGraphIriOption: Option[IRI],
    userProfile: UserADM
  ): Task[ResourceTypesForNamedGraphResponseV1] = {

    def getProperties(
      resInfo: ResourceTypeResponseV1
    ): IO[InconsistentRepositoryDataException, Vector[PropertyTypeV1]] = ZIO.collectAll(
      resInfo.restype_info.properties.map { prop =>
        for {
          label <-
            ZIO.fromOption(prop.label).orElseFail(InconsistentRepositoryDataException(s"No label given for ${prop.id}"))
        } yield PropertyTypeV1(prop.id, label)
      }.toVector
    )

    // get the resource types for a named graph
    def getResourceTypes(namedGraphIri: IRI): Task[Seq[ResourceTypeV1]] =
      for {
        // get NamedGraphEntityInfoV1 for the given named graph
        namedGraphEntityInfo <- getNamedGraphEntityInfoV1ForNamedGraph(namedGraphIri, userProfile)

        // get resinfo for each resource class in namedGraphEntityInfo
        resInfosForNamedGraph <-
          ZIO.foreach(namedGraphEntityInfo.resourceClasses) { resClassIri =>
            for {
              resInfo <- getResourceTypeResponseV1(resClassIri, userProfile)
            } yield (resClassIri, resInfo)
          }

        resourceTypes <-
          ZIO.collectAll(resInfosForNamedGraph.map { case (resClassIri, resInfo) =>
            for {
              label <-
                ZIO
                  .fromOption(resInfo.restype_info.label)
                  .orElseFail(InconsistentRepositoryDataException(s"No label given for $resClassIri"))
              props <- getProperties(resInfo)
            } yield ResourceTypeV1(
              id = resClassIri,
              label = label,
              properties = props
            )
          }.toVector)
      } yield resourceTypes

    // get resource types for named graph depending on given IRI-Option
    namedGraphIriOption match {
      case Some(namedGraphIri) => // get the resource types for the given named graph
        for {
          resourceTypes <- getResourceTypes(namedGraphIri)
        } yield ResourceTypesForNamedGraphResponseV1(resourcetypes = resourceTypes)

      case None => // map over all named graphs and collect the resource types
        for {
          projectNamedGraphsResponse     <- getNamedGraphs(userProfile = userProfile)
          projectNamedGraphIris: Seq[IRI] = projectNamedGraphsResponse.vocabularies.map(_.uri)
          resourceTypesPerProject: Seq[Task[Seq[ResourceTypeV1]]] =
            projectNamedGraphIris.map(iri => getResourceTypes(iri))
          resourceTypes <- ZioHelper.sequence(resourceTypesPerProject)
        } yield ResourceTypesForNamedGraphResponseV1(resourcetypes = resourceTypes.flatten)
    }
  }

  /**
   * Gets the property types defined in the given named graph. If there is no named graph defined, get property types for all existing named graphs.
   *
   * @param namedGraphIriOption  the IRI of the named graph or None if all the named graphs should be queried.
   *
   * @param userProfile          the profile of the user making the request.
   * @return a [[PropertyTypesForNamedGraphResponseV1]].
   */
  private def getPropertyTypesForNamedGraph(
    namedGraphIriOption: Option[IRI],
    userProfile: UserADM
  ): Task[PropertyTypesForNamedGraphResponseV1] = {
    def getPropertiesForNamedGraph(
      namedGraphIri: IRI,
      userProfile: UserADM
    ): Task[Seq[PropertyDefinitionInNamedGraphV1]] =
      for {
        namedGraphEntityInfo  <- getNamedGraphEntityInfoV1ForNamedGraph(namedGraphIri, userProfile)
        propertyIris: Set[IRI] = namedGraphEntityInfo.propertyIris
        entities              <- getEntityInfoResponseV1(propertyIris = propertyIris, userProfile = userProfile)
        propertyInfoMap: Map[IRI, PropertyInfoV1] =
          entities.propertyInfoMap.filterNot { case (_, propertyEntityInfo) =>
            propertyEntityInfo.isLinkValueProp
          }

        propertyDefinitions <-
          ZIO
            .collectAll(propertyInfoMap.map { case (propertyIri: IRI, entityInfo: PropertyInfoV1) =>
              val label = entityInfo.getPredicateObject(
                predicateIri = OntologyConstants.Rdfs.Label,
                preferredLangs = Some(userProfile.lang, appConfig.fallbackLanguage)
              )
              val description = entityInfo.getPredicateObject(
                predicateIri = OntologyConstants.Rdfs.Comment,
                preferredLangs = Some(userProfile.lang, appConfig.fallbackLanguage)
              )
              val guiName = entityInfo
                .getPredicateObject(SalsahGui.GuiElementProp)
                .map(iri => SalsahGuiConversions.iri2SalsahGuiElement(iri))
              if (entityInfo.isLinkProp) {
                // It is a linking prop: its valuetype_id is knora-base:LinkValue.
                // It is restricted to the resource class that is given for knora-base:objectClassConstraint
                // for the given property which goes in the attributes that will be read by the GUI.

                for {
                  attributeResType <-
                    ZIO
                      .fromOption(entityInfo.getPredicateObject(OntologyConstants.KnoraBase.ObjectClassConstraint))
                      .orElseFail(noClassConstraintException(propertyIri))
                } yield {
                  val attributes = valueUtilV1.makeAttributeString(
                    entityInfo
                      .getPredicateStringObjectsWithoutLang(SalsahGui.GuiAttribute) + valueUtilV1
                      .makeAttributeRestype(attributeResType)
                  )
                  PropertyDefinitionInNamedGraphV1(
                    id = propertyIri,
                    name = propertyIri,
                    label = label,
                    description = description,
                    vocabulary = entityInfo.ontologyIri,
                    valuetype_id = OntologyConstants.KnoraBase.LinkValue,
                    attributes = attributes,
                    gui_name = guiName
                  )
                }

              } else {
                for {
                  valueTypeId <-
                    ZIO
                      .fromOption(entityInfo.getPredicateObject(OntologyConstants.KnoraBase.ObjectClassConstraint))
                      .orElseFail(noClassConstraintException(propertyIri))
                  attributes =
                    valueUtilV1.makeAttributeString(
                      entityInfo.getPredicateStringObjectsWithoutLang(SalsahGui.GuiAttribute)
                    )
                } yield PropertyDefinitionInNamedGraphV1(
                  id = propertyIri,
                  name = propertyIri,
                  label = label,
                  description = description,
                  vocabulary = entityInfo.ontologyIri,
                  valuetype_id = valueTypeId,
                  attributes = attributes,
                  gui_name = guiName
                )
              }
            }.toVector)
      } yield propertyDefinitions

    namedGraphIriOption match {
      case Some(namedGraphIri) => // get all the property types for the given named graph
        for {
          propertyTypes <- getPropertiesForNamedGraph(namedGraphIri, userProfile)

        } yield PropertyTypesForNamedGraphResponseV1(properties = propertyTypes)
      case None => // get the property types for all named graphs (collect them by mapping over all named graphs)
        for {
          projectNamedGraphsResponse     <- getNamedGraphs(userProfile = userProfile)
          projectNamedGraphIris: Seq[IRI] = projectNamedGraphsResponse.vocabularies.map(_.uri)
          propertyTypesPerProject: Seq[Task[Seq[PropertyDefinitionInNamedGraphV1]]] =
            projectNamedGraphIris.map(iri => getPropertiesForNamedGraph(iri, userProfile))
          propertyTypes <- ZioHelper.sequence(propertyTypesPerProject)
        } yield PropertyTypesForNamedGraphResponseV1(properties = propertyTypes.flatten)
    }
  }

  /**
   * Gets the property types defined for the given resource class.
   *
   * @param resourceClassIri the IRI of the resource class to query for.
   * @param userProfile      the profile of the user making the request.
   * @return a [[PropertyTypesForResourceTypeResponseV1]].
   */
  private def getPropertyTypesForResourceType(
    resourceClassIri: IRI,
    userProfile: UserADM
  ): Task[PropertyTypesForResourceTypeResponseV1] =
    for {
      resInfo      <- getResourceTypeResponseV1(resourceClassIri, userProfile)
      propertyTypes = resInfo.restype_info.properties.toVector
    } yield PropertyTypesForResourceTypeResponseV1(properties = propertyTypes)
}

object OntologyResponderV1Live {
  val layer: URLayer[
    ValueUtilV1 with MessageRelay with IriConverter with AppConfig,
    OntologyResponderV1Live
  ] =
    ZLayer.fromZIO {
      for {
        config  <- ZIO.service[AppConfig]
        mr      <- ZIO.service[MessageRelay]
        ic      <- ZIO.service[IriConverter]
        vu      <- ZIO.service[ValueUtilV1]
        handler <- mr.subscribe(OntologyResponderV1Live(config, mr, ic, vu))
      } yield handler
    }
}
