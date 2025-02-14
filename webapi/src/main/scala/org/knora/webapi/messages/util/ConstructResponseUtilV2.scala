/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util

import zio.*

import java.time.Instant
import java.util.UUID
import scala.reflect.ClassTag

import dsp.errors.BadRequestException
import dsp.errors.InconsistentRepositoryDataException
import dsp.errors.NotFoundException
import dsp.errors.NotImplementedException
import dsp.valueobjects.UuidUtil
import org.knora.webapi.*
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.*
import org.knora.webapi.messages.store.triplestoremessages.SparqlExtendedConstructResponse.ConstructPredicateObjects
import org.knora.webapi.messages.util.ConstructResponseUtilV2.FlatStatements
import org.knora.webapi.messages.util.ConstructResponseUtilV2.MainResourcesAndValueRdfData
import org.knora.webapi.messages.util.ConstructResponseUtilV2.MappingAndXSLTransformation
import org.knora.webapi.messages.util.ConstructResponseUtilV2.PredicateObjects
import org.knora.webapi.messages.util.ConstructResponseUtilV2.RdfData
import org.knora.webapi.messages.util.ConstructResponseUtilV2.RdfPropertyValues
import org.knora.webapi.messages.util.ConstructResponseUtilV2.RdfResources
import org.knora.webapi.messages.util.ConstructResponseUtilV2.RdfWithUserPermission
import org.knora.webapi.messages.util.ConstructResponseUtilV2.ResourceWithValueRdfData
import org.knora.webapi.messages.util.ConstructResponseUtilV2.Statements
import org.knora.webapi.messages.util.ConstructResponseUtilV2.ValueRdfData
import org.knora.webapi.messages.util.ConstructResponseUtilV2.emptyFlatStatements
import org.knora.webapi.messages.util.ConstructResponseUtilV2.emptyRdfPropertyValues
import org.knora.webapi.messages.util.ConstructResponseUtilV2.emptyRdfResources
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.messages.v2.responder.ontologymessages.StandoffEntityInfoGetResponseV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourcesSequenceV2
import org.knora.webapi.messages.v2.responder.standoffmessages.GetMappingRequestV2
import org.knora.webapi.messages.v2.responder.standoffmessages.GetMappingResponseV2
import org.knora.webapi.messages.v2.responder.standoffmessages.GetXSLTransformationRequestV2
import org.knora.webapi.messages.v2.responder.standoffmessages.GetXSLTransformationResponseV2
import org.knora.webapi.messages.v2.responder.standoffmessages.MappingXMLtoStandoff
import org.knora.webapi.messages.v2.responder.valuemessages.*
import org.knora.webapi.slice.admin.domain.model.Authorship
import org.knora.webapi.slice.admin.domain.model.CopyrightHolder
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.LicenseIri
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.lists.domain.ListsService
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.slice.resources.IiifImageRequestUrl
import org.knora.webapi.store.iiif.errors.SipiException
import org.knora.webapi.util.ZioHelper

trait ConstructResponseUtilV2 {

  /**
   * A [[SparqlConstructResponse]] may contain both resources and value RDF data objects as well as standoff.
   * This method turns a graph (i.e. triples) into a structure organized by the principle of resources and their values,
   * i.e. a map of resource Iris to [[ResourceWithValueRdfData]].
   * The resource Iris represent main resources, dependent resources are contained in the link values as nested structures.
   *
   * @param constructQueryResults the results of a SPARQL construct query representing resources and their values.
   * @return an instance of [[MainResourcesAndValueRdfData]].
   */
  def splitMainResourcesAndValueRdfData(
    constructQueryResults: SparqlExtendedConstructResponse,
    requestingUser: User,
  ): MainResourcesAndValueRdfData

  /**
   * Collect all mapping Iris referred to in the given value assertions.
   *
   * @param valuePropertyAssertions the given assertions (property -> value object).
   * @return a set of mapping Iris.
   */
  def getMappingIrisFromValuePropertyAssertions(valuePropertyAssertions: RdfPropertyValues): Set[IRI]

  /**
   * Creates an API response.
   *
   * @param mainResourcesAndValueRdfData the query results.
   * @param orderByResourceIri           the order in which the resources should be returned. This sequence
   *                                     contains the resource IRIs received from the triplestore before filtering
   *                                     for permissions, but after filtering for duplicates.
   * @param pageSizeBeforeFiltering      the number of resources returned before filtering for permissions and duplicates.
   * @param mappings                     the mappings to convert standoff to XML, if any.
   * @param queryStandoff                if `true`, make separate queries to get the standoff for text values.
   * @param calculateMayHaveMoreResults  if `true`, calculate whether there may be more results for the query.
   * @param versionDate                  if defined, represents the requested time in the resources' version history.
   * @param targetSchema                 the schema of response.
   * @param requestingUser               the user making the request.
   * @return a collection of [[ReadResourceV2]] representing the search results.
   */
  def createApiResponse(
    mainResourcesAndValueRdfData: MainResourcesAndValueRdfData,
    orderByResourceIri: Seq[IRI],
    pageSizeBeforeFiltering: Int,
    mappings: Map[IRI, MappingAndXSLTransformation] = Map.empty[IRI, MappingAndXSLTransformation],
    queryStandoff: Boolean,
    calculateMayHaveMoreResults: Boolean,
    versionDate: Option[Instant],
    targetSchema: ApiV2Schema,
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2]

  /**
   * Gets mappings referred to in query results [[Map[IRI, ResourceWithValueRdfData]]].
   *
   * @param queryResultsSeparated query results referring to mappings.
   *
   * @param requestingUser        the user making the request.
   * @return the referred mappings.
   */
  def getMappingsFromQueryResultsSeparated(
    queryResultsSeparated: Map[IRI, ResourceWithValueRdfData],
    requestingUser: User,
  ): zio.Task[Map[IRI, MappingAndXSLTransformation]]
}
object ConstructResponseUtilV2 {

  /**
   * A map of resource IRIs to resource RDF data.
   */
  type RdfResources = Map[IRI, ResourceWithValueRdfData]

  /**
   * A map of subject IRIs to [[ConstructPredicateObjects]] instances.
   */
  type Statements = Map[IRI, ConstructPredicateObjects]

  /**
   * A flattened map of predicates to objects. This assumes that each predicate has
   * * only one object.
   */
  type PredicateObjects = Map[SmartIri, Seq[LiteralV2]]

  /**
   * A map of subject IRIs to flattened maps of predicates to objects.
   */
  type FlatStatements = Map[IRI, Map[SmartIri, LiteralV2]]

  /**
   * A map of property IRIs to value RDF data.
   */
  type RdfPropertyValues = Map[SmartIri, Seq[ValueRdfData]]

  /**
   * Makes an empty instance of [[RdfResources]].
   */
  val emptyRdfResources: RdfResources = Map.empty

  /**
   * Makes an empty instance of [[RdfPropertyValues]].
   */
  val emptyRdfPropertyValues: RdfPropertyValues = Map.empty

  /**
   * Makes an empty instance of [[FlatStatements]].
   */
  val emptyFlatStatements: FlatStatements = Map.empty

  /**
   * Represents assertions about an RDF subject.
   */
  sealed trait RdfData {

    /**
     * The IRI of the subject.
     */
    val subjectIri: IRI

    /**
     * Assertions about the subject.
     */
    val assertions: PredicateObjects

    private def maybeSingleAs[A <: LiteralV2](predicateIri: SmartIri)(implicit tag: ClassTag[A]): Option[A] =
      maybeAs[A](predicateIri).flatMap(_.headOption)

    private def requireSingleAs[A <: LiteralV2](predicateIri: SmartIri)(implicit tag: ClassTag[A]): A =
      maybeSingleAs(predicateIri).getOrElse(
        throw InconsistentRepositoryDataException(s"Subject $subjectIri does not have predicate $predicateIri"),
      )

    private def maybeAs[A <: LiteralV2](predicateIri: SmartIri)(implicit tag: ClassTag[A]): Option[Seq[A]] =
      assertions
        .get(predicateIri)
        .map(
          _.map(literal =>
            literal
              .as[A]()
              .getOrElse(
                throw InconsistentRepositoryDataException(s"Unexpected object of $subjectIri $predicateIri: $literal"),
              ),
          ),
        )

    /**
     * Returns the optional string object of the specified predicate. Throws an exception if the object is not a string.
     *
     * @param predicateIri the predicate.
     * @return the string object of the predicate.
     */
    def maybeStringObject(predicateIri: SmartIri): Option[String] =
      maybeSingleAs[StringLiteralV2](predicateIri).map(_.value)

    def maybeStringListObject(predicateIri: SmartIri): Option[Seq[String]] =
      maybeAs[StringLiteralV2](predicateIri).map(_.map(_.value))

    /**
     * Returns the required string object of the specified predicate. Throws an exception if the object is not found or
     * is not a string.
     *
     * @param predicateIri the predicate.
     * @return the string object of the predicate.
     */
    def requireStringObject(predicateIri: SmartIri): String =
      requireSingleAs[StringLiteralV2](predicateIri).value

    /**
     * Returns the optional IRI object of the specified predicate. Throws an exception if the object is not an IRI.
     *
     * @param predicateIri the predicate.
     * @return the IRI object of the predicate.
     */
    def maybeIriObject(predicateIri: SmartIri): Option[IRI] =
      maybeSingleAs[IriLiteralV2](predicateIri).map(_.value)

    /**
     * Returns the required IRI object of the specified predicate. Throws an exception if the object is not found or
     * is not an IRI.
     *
     * @param predicateIri the predicate.
     * @return the IRI object of the predicate.
     */
    def requireIriObject(predicateIri: SmartIri): IRI =
      requireSingleAs[IriLiteralV2](predicateIri).value

    /**
     * Returns the optional integer object of the specified predicate. Throws an exception if the object is not an integer.
     *
     * @param predicateIri the predicate.
     * @return the integer object of the predicate.
     */
    def maybeIntObject(predicateIri: SmartIri): Option[Int] =
      maybeSingleAs[IntLiteralV2](predicateIri).map(_.value)

    /**
     * Returns the required integer object of the specified predicate. Throws an exception if the object is not found or
     * is not an integer.
     *
     * @param predicateIri the predicate.
     * @return the integer object of the predicate.
     */
    def requireIntObject(predicateIri: SmartIri): Int =
      requireSingleAs[IntLiteralV2](predicateIri).value

    /**
     * Returns the optional boolean object of the specified predicate. Throws an exception if the object is not a boolean.
     *
     * @param predicateIri the predicate.
     * @return the boolean object of the predicate.
     */
    def maybeBooleanObject(predicateIri: SmartIri): Option[Boolean] =
      maybeSingleAs[BooleanLiteralV2](predicateIri).map(_.value)

    /**
     * Returns the required boolean object of the specified predicate. Throws an exception if the object is not found or
     * is not a boolean value.
     *
     * @param predicateIri the predicate.
     * @return the boolean object of the predicate.
     */
    def requireBooleanObject(predicateIri: SmartIri): Boolean =
      requireSingleAs[BooleanLiteralV2](predicateIri).value

    /**
     * Returns the optional decimal object of the specified predicate. Throws an exception if the object is not a decimal.
     *
     * @param predicateIri the predicate.
     * @return the decimal object of the predicate.
     */
    def maybeDecimalObject(predicateIri: SmartIri): Option[BigDecimal] =
      maybeSingleAs[DecimalLiteralV2](predicateIri).map(_.value)

    /**
     * Returns the required decimal object of the specified predicate. Throws an exception if the object is not found or
     * is not a decimal value.
     *
     * @param predicateIri the predicate.
     * @return the decimal object of the predicate.
     */
    def requireDecimalObject(predicateIri: SmartIri): BigDecimal =
      requireSingleAs[DecimalLiteralV2](predicateIri).value

    /**
     * Returns the optional timestamp object of the specified predicate. Throws an exception if the object is not a timestamp.
     *
     * @param predicateIri the predicate.
     * @return the timestamp object of the predicate.
     */
    def maybeDateTimeObject(predicateIri: SmartIri): Option[Instant] =
      maybeSingleAs[DateTimeLiteralV2](predicateIri).map(_.value)

    /**
     * Returns the required timestamp object of the specified predicate. Throws an exception if the object is not found or
     * is not a timestamp value.
     *
     * @param predicateIri the predicate.
     * @return the timestamp object of the predicate.
     */
    def requireDateTimeObject(predicateIri: SmartIri): Instant =
      requireSingleAs[DateTimeLiteralV2](predicateIri).value
  }

  /**
   * Represents the RDF data about a value, possibly including standoff.
   *
   * @param subjectIri       the value object's IRI.
   * @param valueObjectClass the type (class) of the value object.
   * @param nestedResource   the nested resource in case of a link value (either the source or the target of a link value, depending on [[isIncomingLink]]).
   * @param isIncomingLink   indicates if it is an incoming or outgoing link in case of a link value.
   * @param userPermission   the permission that the requesting user has on the value.
   * @param assertions       the value objects assertions.
   * @param standoff         standoff assertions, if any.
   */
  case class ValueRdfData(
    subjectIri: IRI,
    valueObjectClass: SmartIri,
    nestedResource: Option[ResourceWithValueRdfData] = None,
    isIncomingLink: Boolean = false,
    userPermission: Permission.ObjectAccess,
    assertions: PredicateObjects,
    standoff: FlatStatements,
  ) extends RdfData

  /**
   * Represents a resource and its values.
   *
   * @param subjectIri              the resource IRI.
   * @param assertions              assertions about the resource (direct statements).
   * @param isMainResource          indicates if this represents a top level resource or a referred resource (depending on the query).
   * @param userPermission          the permission that the requesting user has on the resource.
   * @param valuePropertyAssertions assertions about value properties.
   */
  case class ResourceWithValueRdfData(
    subjectIri: IRI,
    assertions: PredicateObjects,
    isMainResource: Boolean,
    userPermission: Option[Permission.ObjectAccess],
    valuePropertyAssertions: RdfPropertyValues,
  ) extends RdfData

  /**
   * Represents a mapping including information about the standoff entities.
   * May include a default XSL transformation.
   *
   * @param mapping           the mapping from XML to standoff and vice versa.
   * @param standoffEntities  information about the standoff entities referred to in the mapping.
   * @param XSLTransformation the default XSL transformation to convert the resulting XML (e.g., to HTML), if any.
   */
  case class MappingAndXSLTransformation(
    mapping: MappingXMLtoStandoff,
    standoffEntities: StandoffEntityInfoGetResponseV2,
    XSLTransformation: Option[String],
  )

  /**
   * Represents a tree structure of resources, values and dependent resources returned by a SPARQL CONSTRUCT query.
   *
   * @param resources          a map of resource Iris to [[ResourceWithValueRdfData]]. The resource Iris represent main resources, dependent
   *                           resources are contained in the link values as nested structures.
   * @param hiddenResourceIris the IRIs of resources that were hidden because the user does not have permission
   *                           to see them.
   */
  case class MainResourcesAndValueRdfData(resources: RdfResources, hiddenResourceIris: Set[IRI] = Set.empty)

  /**
   * An intermediate data structure containing RDF assertions about an entity and the user's permission on the entity.
   *
   * @param assertions          RDF assertions about the entity.
   * @param maybeUserPermission the user's permission on the entity, if any.
   */
  case class RdfWithUserPermission(
    assertions: ConstructPredicateObjects,
    maybeUserPermission: Option[Permission.ObjectAccess],
  )

}

final case class ConstructResponseUtilV2Live(
  appConfig: AppConfig,
  messageRelay: MessageRelay,
  listsService: ListsService,
  standoffTagUtilV2: StandoffTagUtilV2,
  projectService: ProjectService,
)(implicit val stringFormatter: StringFormatter)
    extends ConstructResponseUtilV2 {

  private val inferredPredicates = Set(
    OntologyConstants.KnoraBase.HasValue,
    OntologyConstants.KnoraBase.IsMainResource,
  )

  /**
   * A [[SparqlConstructResponse]] may contain both resources and value RDF data objects as well as standoff.
   * This method turns a graph (i.e. triples) into a structure organized by the principle of resources and their values,
   * i.e. a map of resource Iris to [[ResourceWithValueRdfData]].
   * The resource Iris represent main resources, dependent resources are contained in the link values as nested structures.
   *
   * @param constructQueryResults the results of a SPARQL construct query representing resources and their values.
   * @return an instance of [[MainResourcesAndValueRdfData]].
   */
  override def splitMainResourcesAndValueRdfData(
    constructQueryResults: SparqlExtendedConstructResponse,
    requestingUser: User,
  ): MainResourcesAndValueRdfData = {

    // Make sure all the subjects are IRIs, because blank nodes are not used in resources.
    val resultsWithIriSubjects: Statements = constructQueryResults.statements.map {
      case (iriSubject: IriSubjectV2, statements: ConstructPredicateObjects) => iriSubject.value -> statements
      case (otherSubject: SubjectV2, _: ConstructPredicateObjects) =>
        throw InconsistentRepositoryDataException(s"Unexpected subject: $otherSubject")
    }

    // split statements about resources and other statements (value objects and standoff)
    // resources are identified by the triple "resourceIri a knora-base:Resource" which is an inferred information returned by the SPARQL Construct query.
    val (resourceStatements: Statements, nonResourceStatements: Statements) = resultsWithIriSubjects.partition {
      case (_: IRI, assertions: ConstructPredicateObjects) =>
        // check if the subject is a Knora resource
        assertions
          .getOrElse(OntologyConstants.Rdf.Type.toSmartIri, Seq.empty)
          .contains(IriLiteralV2(OntologyConstants.KnoraBase.Resource))
    }

    // create a single map of all resources with their representing values (rdf data)
    val flatResourcesWithValues: RdfResources = resourceStatements.map {
      case (resourceIri: IRI, assertions: ConstructPredicateObjects) =>
        // remove inferred statements (non explicit) returned in the query result
        // the query returns the following inferred information:
        // - every resource is a knora-base:Resource
        // - every value property is a subproperty of knora-base:hasValue
        // - every resource that's a main resource (not a dependent resource) in the query result has knora-base:isMainResource true
        val assertionsExplicit: ConstructPredicateObjects = assertions.filterNot { case (pred: SmartIri, _) =>
          inferredPredicates(pred.toString)
        }.map { case (pred: SmartIri, objs: Seq[LiteralV2]) =>
          if (pred.toString == OntologyConstants.Rdf.Type) {
            pred -> objs.filterNot {
              case IriLiteralV2(OntologyConstants.KnoraBase.Resource) => true
              case _                                                  => false
            }
          } else {
            pred -> objs
          }
        }

        // check for the knora-base:isMainResource flag created by the SPARQL CONSTRUCT query
        val isMainResource: Boolean = assertions.get(OntologyConstants.KnoraBase.IsMainResource.toSmartIri) match {
          case Some(Seq(BooleanLiteralV2(value))) => value
          case _                                  => false
        }

        // Make a set of all the value object IRIs, because we're going to associate them with their properties.
        val valueObjectIris: Set[IRI] = assertions.collect {
          case (pred: SmartIri, objs: Seq[LiteralV2]) if pred.toString == OntologyConstants.KnoraBase.HasValue =>
            objs.map {
              case IriLiteralV2(iri) => iri
              case other =>
                throw InconsistentRepositoryDataException(
                  s"Unexpected object for $resourceIri knora-base:hasValue: $other",
                )
            }
        }.flatten.toSet

        // Make a map of property IRIs to sequences of value IRIs.
        val valuePropertyToObjectIris: Map[SmartIri, Seq[IRI]] =
          mapPropertyIrisToValueIris(assertionsExplicit, valueObjectIris)

        // Make an RdfPropertyValues representing the values of the resource.
        val valuePropertyToValueObject: RdfPropertyValues = makeRdfPropertyValuesForResource(
          valuePropertyToObjectIris = valuePropertyToObjectIris,
          resourceIri = resourceIri,
          requestingUser = requestingUser,
          assertionsExplicit = assertionsExplicit,
          nonResourceStatements = nonResourceStatements,
        )

        val userPermission: Option[Permission.ObjectAccess] =
          PermissionUtilADM.getUserPermissionFromConstructAssertionsADM(resourceIri, assertions, requestingUser)

        // Make a ResourceWithValueRdfData for each resource IRI.
        resourceIri -> ResourceWithValueRdfData(
          subjectIri = resourceIri,
          assertions = assertionsExplicit,
          isMainResource = isMainResource,
          userPermission = userPermission,
          valuePropertyAssertions = valuePropertyToValueObject,
        )
    }

    // Identify the resources that the user has permission to see.

    val (visibleResources: RdfResources, hiddenResources: RdfResources) = flatResourcesWithValues.partition {
      case (_: IRI, resource: ResourceWithValueRdfData) => resource.userPermission.nonEmpty
    }

    val mainResourceIrisVisible: Set[IRI] = visibleResources.collect {
      case (resourceIri: IRI, resource: ResourceWithValueRdfData) if resource.isMainResource => resourceIri
    }.toSet

    val mainResourceIrisNotVisible: Set[IRI] = hiddenResources.collect {
      case (resourceIri: IRI, resource: ResourceWithValueRdfData) if resource.isMainResource => resourceIri
    }.toSet

    val dependentResourceIrisVisible: Set[IRI] = visibleResources.collect {
      case (resourceIri: IRI, resource: ResourceWithValueRdfData) if !resource.isMainResource => resourceIri
    }.toSet

    val dependentResourceIrisNotVisible: Set[IRI] = hiddenResources.collect {
      case (resourceIri: IRI, resource: ResourceWithValueRdfData) if !resource.isMainResource => resourceIri
    }.toSet

    // get incoming links for each resource: a map of resource IRIs to resources that link to it
    val incomingLinksForResource: Map[IRI, RdfResources] = getIncomingLink(visibleResources, flatResourcesWithValues)

    val mainResourcesNested: Map[IRI, ResourceWithValueRdfData] = mainResourceIrisVisible.map { resourceIri =>
      val transformedResource = nestResources(
        resourceIri = resourceIri,
        flatResourcesWithValues = flatResourcesWithValues,
        visibleResources = visibleResources,
        dependentResourceIrisVisible = dependentResourceIrisVisible,
        dependentResourceIrisNotVisible = dependentResourceIrisNotVisible,
        incomingLinksForResource = incomingLinksForResource,
      )

      resourceIri -> transformedResource
    }.toMap

    MainResourcesAndValueRdfData(
      resources = mainResourcesNested,
      hiddenResourceIris = mainResourceIrisNotVisible ++ dependentResourceIrisNotVisible,
    )
  }

  /**
   * Converts a [[ConstructPredicateObjects]] to a map of property IRIs to sequences of value IRIs.
   *
   * @param assertionsExplicit all non-inferred statements.
   * @param valueObjectIris    a set of all value object IRIs.
   * @return a map of property IRIs to sequences of value IRIs.
   */
  private def mapPropertyIrisToValueIris(
    assertionsExplicit: ConstructPredicateObjects,
    valueObjectIris: Set[IRI],
  ): Map[SmartIri, Seq[IRI]] =
    assertionsExplicit.map { case (pred: SmartIri, objs: Seq[LiteralV2]) =>
      // Get only the assertions in which the object is a value object IRI.
      val valueObjIris: Seq[IriLiteralV2] = objs.collect {
        case iriObj: IriLiteralV2 if valueObjectIris(iriObj.value) => iriObj
      }

      // create an entry using pred as a key and valueObjIris as the value
      pred -> valueObjIris
    }.filter { case (_: SmartIri, objs: Seq[IriLiteralV2]) =>
      objs.nonEmpty
    }.groupBy { case (pred: SmartIri, _: Seq[IriLiteralV2]) =>
      // Turn the sequence of assertions into a Map of predicate IRIs to assertions.
      pred
    }.map { case (pred: SmartIri, valueAssertions: Map[SmartIri, Seq[IriLiteralV2]]) =>
      // Replace the assertions with their objects, i.e. the value object IRIs.
      pred -> valueAssertions.values.flatten.map(_.value).toSeq
    }

  /**
   * Given the assertions that describe a resource and its values, makes an [[RdfPropertyValues]] representing the values.
   *
   * @param valuePropertyToObjectIris a map of property IRIs to value IRIs.
   * @param resourceIri               the IRI of the resource.
   * @param requestingUser            the user making the request.
   * @param assertionsExplicit        all non-inferred statements.
   * @param nonResourceStatements     statements that are not about the containing resource.
   * @return an [[RdfPropertyValues]] describing the values of the resource.
   */
  private def makeRdfPropertyValuesForResource(
    valuePropertyToObjectIris: Map[SmartIri, Seq[IRI]],
    resourceIri: IRI,
    requestingUser: User,
    assertionsExplicit: ConstructPredicateObjects,
    nonResourceStatements: Statements,
  )(implicit stringFormatter: StringFormatter): RdfPropertyValues = {
    valuePropertyToObjectIris.map { case (property: SmartIri, valObjIris: Seq[IRI]) =>
      // Make an RdfWithUserPermission for each value of the property.
      val rdfWithUserPermissionsForValues: Seq[(IRI, RdfWithUserPermission)] = valObjIris.map { (valObjIri: IRI) =>
        val valueObjAssertions: ConstructPredicateObjects = nonResourceStatements(valObjIri)

        // get the resource's project
        // value objects belong to the parent resource's project

        val resourceProjectLiteral: LiteralV2 = assertionsExplicit
          .getOrElse(
            OntologyConstants.KnoraBase.AttachedToProject.toSmartIri,
            throw InconsistentRepositoryDataException(s"Resource $resourceIri has no knora-base:attachedToProject"),
          )
          .head

        // add the resource's project to the value's assertions, and get the user's permission on the value
        val maybeUserPermission = PermissionUtilADM.getUserPermissionFromConstructAssertionsADM(
          entityIri = valObjIri,
          assertions = valueObjAssertions + (OntologyConstants.KnoraBase.AttachedToProject.toSmartIri -> Seq(
            resourceProjectLiteral,
          )),
          requestingUser = requestingUser,
        )

        valObjIri -> RdfWithUserPermission(valueObjAssertions, maybeUserPermission)
      }

      // Filter out objects that the user doesn't have permission to see.
      val visibleRdfWithUserPermissionsForValues: Seq[(IRI, RdfWithUserPermission)] =
        rdfWithUserPermissionsForValues.filter {
          // check if the user has sufficient permissions to see the value object
          case (_: IRI, rdfWithUserPermission: RdfWithUserPermission) =>
            rdfWithUserPermission.maybeUserPermission.nonEmpty
        }

      // Make a ValueRdfData for each value object.
      val valueRdfDataForProperty: Seq[ValueRdfData] = visibleRdfWithUserPermissionsForValues.flatMap {
        case (valObjIri: IRI, valueRdfWithUserPermission: RdfWithUserPermission) =>
          // get all the standoff node IRIs possibly belonging to this value object
          val standoffNodeIris: Set[IRI] = valueRdfWithUserPermission.assertions.collect {
            case (pred: SmartIri, objs: Seq[LiteralV2])
                if pred.toString == OntologyConstants.KnoraBase.ValueHasStandoff =>
              objs.map(_.toString)
          }.flatten.toSet

          // given the standoff node IRIs, get the standoff assertions
          val standoffAssertions: FlatStatements = nonResourceStatements.collect {
            case (subjIri: IRI, assertions: ConstructPredicateObjects) if standoffNodeIris(subjIri) =>
              subjIri -> assertions.flatMap { case (pred: SmartIri, objs: Seq[LiteralV2]) =>
                objs.map { obj =>
                  pred -> obj
                }
              }
          }

          // Get the rdf:type of the value.
          val rdfTypeLiteral: LiteralV2 = valueRdfWithUserPermission.assertions
            .getOrElse(
              OntologyConstants.Rdf.Type.toSmartIri,
              throw InconsistentRepositoryDataException(s"Value $valObjIri has no rdf:type"),
            )
            .head

          val valueObjectClass: SmartIri = rdfTypeLiteral
            .as[IriLiteralV2]()
            .getOrElse(
              throw InconsistentRepositoryDataException(s"Unexpected object of $valObjIri rdf:type: $rdfTypeLiteral"),
            )
            .value
            .toSmartIri

          // check if it is a link value
          if (valueObjectClass.toString == OntologyConstants.KnoraBase.LinkValue) {
            // create a link value object
            Some(
              ValueRdfData(
                subjectIri = valObjIri,
                valueObjectClass = valueObjectClass,
                userPermission = valueRdfWithUserPermission.maybeUserPermission.get,
                assertions = valueRdfWithUserPermission.assertions,
                standoff = emptyFlatStatements, // link value does not contain standoff
              ),
            )

          } else {
            // create a non-link value object
            Some(
              ValueRdfData(
                subjectIri = valObjIri,
                valueObjectClass = valueObjectClass,
                userPermission = valueRdfWithUserPermission.maybeUserPermission.get,
                assertions = valueRdfWithUserPermission.assertions,
                standoff = standoffAssertions,
              ),
            )
          }
      }

      // Associate each property IRI with its Seq[ValueRdfData].
      property -> valueRdfDataForProperty
    }.filterNot {
      // filter out those properties that do not have value objects (they may have been filtered out because the user does not have sufficient permissions to see them)
      case (_, valObjs: Seq[ValueRdfData]) =>
        valObjs.isEmpty
    }
  }

  /**
   * This method returns all the incoming link for each resource as a map of resource IRI to resources that link to it.
   *
   * @param visibleResources        the resources that the user has permission to see
   * @param flatResourcesWithValues the set of resources with their representing values, before permission filtering
   * @return the incoming links as a map of resource IRIs
   */
  private def getIncomingLink(visibleResources: RdfResources, flatResourcesWithValues: RdfResources)(implicit
    stringFormatter: StringFormatter,
  ): Map[IRI, RdfResources] =
    visibleResources.map { case (resourceIri: IRI, values: ResourceWithValueRdfData) =>
      // get all incoming links for resourceIri
      val incomingLinksForRes: RdfResources = flatResourcesWithValues.foldLeft(emptyRdfResources) {
        case (acc: RdfResources, (otherResourceIri: IRI, otherResource: ResourceWithValueRdfData)) =>
          // get all incoming links having assertions about value properties pointing to this resource
          val incomingLinkPropertyAssertions: RdfPropertyValues =
            otherResource.valuePropertyAssertions.foldLeft(emptyRdfPropertyValues) {
              case (acc: RdfPropertyValues, (prop: SmartIri, otherResourceValues: Seq[ValueRdfData])) =>
                // collect all link values that point to resourceIri
                val incomingLinkValues: Seq[ValueRdfData] = otherResourceValues.foldLeft(Seq.empty[ValueRdfData]) {
                  (acc, value: ValueRdfData) =>
                    // check if it is a link value and points to this resource
                    if (
                      value.valueObjectClass.toString == OntologyConstants.KnoraBase.LinkValue && value
                        .requireIriObject(OntologyConstants.Rdf.Object.toSmartIri) == resourceIri
                    ) {
                      acc :+ value
                    } else {
                      acc
                    }
                }

                // check if the link value already exists
                if (incomingLinkValues.nonEmpty) {
                  // add link value to the existing values
                  acc + (prop -> incomingLinkValues)
                } else {
                  // it does not already exists therefore add the new one
                  acc
                }
            }

          // check if the property assertion already exists
          if (incomingLinkPropertyAssertions.nonEmpty) {
            // add resource values to the existing values
            acc + (otherResourceIri -> values.copy(
              valuePropertyAssertions = incomingLinkPropertyAssertions,
            ))
          } else {
            // it does not already exist therefore add the new one
            acc
          }

      }

      // create an entry using the resource's Iri as a key and its incoming links as the value
      resourceIri -> incomingLinksForRes
    }

  /**
   * Given a resource IRI, finds any link values in the resource, and recursively embeds the target resource in each link value.
   *
   * @param resourceIri                     the IRI of the resource to start with.
   * @param flatResourcesWithValues         the complete set of resources with their values, before permission filtering.
   * @param visibleResources                the resources that the user has permission to see.
   * @param dependentResourceIrisVisible    the IRIs of dependent resources that the user has permission to see.
   * @param dependentResourceIrisNotVisible the IRIs of dependent resources that the user does not have permission to see.
   * @param incomingLinksForResource        a map of resource IRIs to resources that link to each resource.
   * @param alreadyTraversed                a set (initially empty) of the IRIs of resources that this function has already
   *                                        traversed, to prevent an infinite loop if a cycle is encountered.
   * @return the same resource, with any nested resources attached to it.
   */
  private def nestResources(
    resourceIri: IRI,
    flatResourcesWithValues: RdfResources,
    visibleResources: RdfResources,
    dependentResourceIrisVisible: Set[IRI],
    dependentResourceIrisNotVisible: Set[IRI],
    incomingLinksForResource: Map[IRI, RdfResources],
    alreadyTraversed: Set[IRI] = Set.empty[IRI],
  )(implicit stringFormatter: StringFormatter): ResourceWithValueRdfData = {
    val resource = visibleResources(resourceIri)

    val transformedValuePropertyAssertions: RdfPropertyValues = resource.valuePropertyAssertions.map {
      case (propIri: SmartIri, values: Seq[ValueRdfData]) =>
        val transformedValues: Seq[ValueRdfData] = transformValuesByNestingResources(
          values = values,
          flatResourcesWithValues = flatResourcesWithValues,
          visibleResources = visibleResources,
          dependentResourceIrisVisible = dependentResourceIrisVisible,
          dependentResourceIrisNotVisible = dependentResourceIrisNotVisible,
          incomingLinksForResource = incomingLinksForResource,
          alreadyTraversed = alreadyTraversed + resourceIri,
        )

        propIri -> transformedValues
    }.filter { case (_: SmartIri, values: Seq[ValueRdfData]) =>
      // If we filtered out all the values for the property, filter out the property, too.
      values.nonEmpty
    }

    // incomingLinksForResource contains incoming link values for each resource
    // flatResourcesWithValues contains the complete information

    // filter out those resources that already have been processed
    // and the main resources (they are already present on the top level of the response)
    //
    // the main resources point to dependent resources and would be treated as incoming links of dependent resources
    // this would create circular dependencies

    // resources that point to this resource
    val referringResources: RdfResources = incomingLinksForResource(resourceIri).filterNot {
      case (incomingResIri: IRI, _: ResourceWithValueRdfData) =>
        alreadyTraversed(incomingResIri) || flatResourcesWithValues(incomingResIri).isMainResource
    }

    // link value assertions that point to this resource
    val incomingLinkAssertions: RdfPropertyValues = referringResources.values.foldLeft(emptyRdfPropertyValues) {
      case (acc: RdfPropertyValues, assertions: ResourceWithValueRdfData) =>
        val values: RdfPropertyValues = assertions.valuePropertyAssertions.flatMap {
          case (propIri: SmartIri, values: Seq[ValueRdfData]) =>
            // check if the property Iri already exists (there could be several instances of the same property)
            if (acc.contains(propIri)) {
              // add values to property Iri (keeping the already existing values)
              acc + (propIri -> (acc(propIri) ++ values).sortBy(_.subjectIri))
            } else {
              // prop Iri does not exists yet, add it
              acc + (propIri -> values.sortBy(_.subjectIri))
            }
        }

        values
    }

    if (incomingLinkAssertions.nonEmpty) {
      // create a virtual property representing an incoming link
      val incomingProps: (SmartIri, Seq[ValueRdfData]) =
        OntologyConstants.KnoraBase.HasIncomingLinkValue.toSmartIri -> incomingLinkAssertions.values.toSeq.flatten.map {
          (linkValue: ValueRdfData) =>
            // get the source of the link value (it points to the resource that is currently processed)
            val sourceIri: IRI = linkValue.requireIriObject(OntologyConstants.Rdf.Subject.toSmartIri)
            val source = Some(
              nestResources(
                resourceIri = sourceIri,
                flatResourcesWithValues = flatResourcesWithValues,
                visibleResources = visibleResources,
                dependentResourceIrisVisible = dependentResourceIrisVisible,
                dependentResourceIrisNotVisible = dependentResourceIrisNotVisible,
                incomingLinksForResource = incomingLinksForResource,
                alreadyTraversed = alreadyTraversed + resourceIri,
              ),
            )

            linkValue.copy(
              nestedResource = source,
              isIncomingLink = true,
            )
        }

      resource.copy(
        valuePropertyAssertions = transformedValuePropertyAssertions + incomingProps,
      )
    } else {
      resource.copy(
        valuePropertyAssertions = transformedValuePropertyAssertions,
      )
    }
  }

  /**
   * Transforms a resource's values by nesting dependent resources in link values.
   *
   * @param values                          the values of the resource.
   * @param flatResourcesWithValues         the complete set of resources with their values, before permission filtering.
   * @param visibleResources                the resources that the user has permission to see.
   * @param dependentResourceIrisVisible    the IRIs of dependent resources that the user has permission to see.
   * @param dependentResourceIrisNotVisible the IRIs of dependent resources that the user does not have permission to see.
   * @param incomingLinksForResource        a map of resource IRIs to resources that link to each resource.
   * @param alreadyTraversed                a set (initially empty) of the IRIs of resources that this function has already
   *                                        traversed, to prevent an infinite loop if a cycle is encountered.
   * @return the transformed values.
   */
  private def transformValuesByNestingResources(
    values: Seq[ValueRdfData],
    flatResourcesWithValues: RdfResources,
    visibleResources: RdfResources,
    dependentResourceIrisVisible: Set[IRI],
    dependentResourceIrisNotVisible: Set[IRI],
    incomingLinksForResource: Map[IRI, RdfResources],
    alreadyTraversed: Set[IRI],
  )(implicit stringFormatter: StringFormatter): Seq[ValueRdfData] =
    values.foldLeft(Vector.empty[ValueRdfData]) { case (acc: Vector[ValueRdfData], value: ValueRdfData) =>
      if (value.valueObjectClass.toString == OntologyConstants.KnoraBase.LinkValue) {
        val dependentResourceIri: IRI = value.requireIriObject(OntologyConstants.Rdf.Object.toSmartIri)

        if (alreadyTraversed(dependentResourceIri)) {
          acc :+ value
        } else {
          // Do we have the dependent resource?
          if (dependentResourceIrisVisible.contains(dependentResourceIri)) {
            // Yes. Nest it in the link value.
            val dependentResource: ResourceWithValueRdfData = nestResources(
              dependentResourceIri,
              flatResourcesWithValues = flatResourcesWithValues,
              visibleResources = visibleResources,
              dependentResourceIrisVisible = dependentResourceIrisVisible,
              dependentResourceIrisNotVisible = dependentResourceIrisNotVisible,
              incomingLinksForResource = incomingLinksForResource,
              alreadyTraversed = alreadyTraversed,
            )

            acc :+ value.copy(
              nestedResource = Some(dependentResource),
            )
          } else if (dependentResourceIrisNotVisible.contains(dependentResourceIri)) {
            // No, because the user doesn't have permission to see it. Skip the link value.
            acc
          } else {
            // We don't have the dependent resource because it is marked as deleted. Just
            // return the link value without a nested resource.
            acc :+ value
          }
        }
      } else {
        acc :+ value
      }
    }

  /**
   * Collect all mapping Iris referred to in the given value assertions.
   *
   * @param valuePropertyAssertions the given assertions (property -> value object).
   * @return a set of mapping Iris.
   */
  override def getMappingIrisFromValuePropertyAssertions(
    valuePropertyAssertions: RdfPropertyValues,
  ): Set[IRI] =
    valuePropertyAssertions.foldLeft(Set.empty[IRI]) {
      case (acc: Set[IRI], (_: SmartIri, valObjs: Seq[ValueRdfData])) =>
        val mappings: Seq[String] = valObjs.filter { (valObj: ValueRdfData) =>
          valObj.valueObjectClass == OntologyConstants.KnoraBase.TextValue.toSmartIri && valObj.assertions.contains(
            OntologyConstants.KnoraBase.ValueHasMapping.toSmartIri,
          )
        }.map { (textValObj: ValueRdfData) =>
          textValObj.requireIriObject(OntologyConstants.KnoraBase.ValueHasMapping.toSmartIri)
        }

        // get mappings from linked resources
        val mappingsFromReferredResources: Set[IRI] = valObjs.filter { (valObj: ValueRdfData) =>
          valObj.nestedResource.nonEmpty
        }.flatMap { (valObj: ValueRdfData) =>
          val referredRes: ResourceWithValueRdfData = valObj.nestedResource.get

          // recurse on the nested resource's values
          getMappingIrisFromValuePropertyAssertions(referredRes.valuePropertyAssertions)
        }.toSet

        acc ++ mappings ++ mappingsFromReferredResources
    }

  /**
   * Given a [[ValueRdfData]], constructs a [[TextValueContentV2]]. This method is used to process a text value
   * as returned in an API response, as well as to process a page of standoff markup that is being queried
   * separately from its text value.
   *
   * @param valueObject               the given [[ValueRdfData]].
   * @param valueObjectValueHasString the value's `knora-base:valueHasString`.
   * @param valueCommentOption        the value's comment, if any.
   * @param mappings                  the mappings needed for standoff conversions and XSL transformations.
   * @param requestingUser            the user making the request.
   * @return a [[TextValueContentV2]].
   */
  private def makeTextValueContentV2(
    valueObject: ValueRdfData,
    valueObjectValueHasString: Option[String],
    valueCommentOption: Option[String],
    mappings: Map[IRI, MappingAndXSLTransformation],
    requestingUser: User,
  ): Task[TextValueContentV2] = {
    // Any knora-base:TextValue may have a language
    val valueLanguageOption: Option[String] =
      valueObject.maybeStringObject(OntologyConstants.KnoraBase.ValueHasLanguage.toSmartIri)

    if (valueObject.standoff.nonEmpty) {
      val mappingIri: Option[IRI] = valueObject.maybeIriObject(OntologyConstants.KnoraBase.ValueHasMapping.toSmartIri)
      val mappingAndXsltTransformation: Option[MappingAndXSLTransformation] =
        mappingIri.flatMap(definedMappingIri => mappings.get(definedMappingIri))

      for {
        standoff <- standoffTagUtilV2.createStandoffTagsV2FromConstructResults(
                      standoffAssertions = valueObject.standoff,
                      requestingUser = requestingUser,
                    )
        textTypeInferred = mappingIri match
                             case None                                              => TextValueType.UnformattedText
                             case Some(OntologyConstants.KnoraBase.StandardMapping) => TextValueType.FormattedText
                             case Some(iri)                                         => TextValueType.CustomFormattedText(InternalIri(iri))
        textType = valueObject
                     .maybeIriObject(OntologyConstants.KnoraBase.HasTextValueType.toSmartIri)
                     .flatMap {
                       case OntologyConstants.KnoraBase.UnformattedText => Some(TextValueType.UnformattedText)
                       case OntologyConstants.KnoraBase.FormattedText   => Some(TextValueType.FormattedText)
                       case OntologyConstants.KnoraBase.CustomFormattedText =>
                         mappingIri.map(iri => TextValueType.CustomFormattedText(InternalIri(iri)))
                       case OntologyConstants.KnoraBase.UndefinedTextType => None
                       case _                                             => None
                     }
                     .getOrElse(textTypeInferred)
      } yield TextValueContentV2(
        ontologySchema = InternalSchema,
        maybeValueHasString = valueObjectValueHasString,
        textValueType = textType,
        valueHasLanguage = valueLanguageOption,
        standoff = standoff,
        mappingIri = mappingIri,
        mapping = mappingAndXsltTransformation.map(_.mapping),
        xslt = mappingAndXsltTransformation.flatMap(_.XSLTransformation),
        comment = valueCommentOption,
      )
    } else {
      // The query returned no standoff markup.
      ZIO.succeed(
        TextValueContentV2(
          ontologySchema = InternalSchema,
          maybeValueHasString = valueObjectValueHasString,
          textValueType = TextValueType.UnformattedText,
          valueHasLanguage = valueLanguageOption,
          comment = valueCommentOption,
        ),
      )
    }
  }

  /**
   * Given a [[ValueRdfData]], constructs a [[FileValueContentV2]].
   *
   * @param valueType                 the IRI of the file value type
   * @param valueObject               the given [[ValueRdfData]].
   * @param valueCommentOption        the value's comment, if any.
   * @return a [[FileValueContentV2]].
   */
  private def makeFileValueContentV2(valueType: IRI, valueObject: ValueRdfData, valueCommentOption: Option[IRI]) = {
    val licenseIri =
      valueObject.maybeIriObject(OntologyConstants.KnoraBase.HasLicense.toSmartIri).map(LicenseIri.unsafeFrom)
    val fileValue = FileValueV2(
      internalMimeType = valueObject.requireStringObject(OntologyConstants.KnoraBase.InternalMimeType.toSmartIri),
      internalFilename = valueObject.requireStringObject(OntologyConstants.KnoraBase.InternalFilename.toSmartIri),
      originalFilename = valueObject.maybeStringObject(OntologyConstants.KnoraBase.OriginalFilename.toSmartIri),
      originalMimeType = valueObject.maybeStringObject(OntologyConstants.KnoraBase.OriginalMimeType.toSmartIri),
      copyrightHolder = valueObject
        .maybeStringObject(OntologyConstants.KnoraBase.HasCopyrightHolder.toSmartIri)
        .map(CopyrightHolder.unsafeFrom),
      authorship = valueObject
        .maybeStringListObject(OntologyConstants.KnoraBase.HasAuthorship.toSmartIri)
        .map(_.map(Authorship.unsafeFrom).toList),
      licenseIri,
    )

    valueType match {
      case OntologyConstants.KnoraBase.StillImageFileValue =>
        ZIO.succeed(
          StillImageFileValueContentV2(
            ontologySchema = InternalSchema,
            fileValue = fileValue,
            dimX = valueObject.requireIntObject(OntologyConstants.KnoraBase.DimX.toSmartIri),
            dimY = valueObject.requireIntObject(OntologyConstants.KnoraBase.DimY.toSmartIri),
            comment = valueCommentOption,
          ),
        )

      case OntologyConstants.KnoraBase.StillImageExternalFileValue =>
        ZIO.succeed(
          StillImageExternalFileValueContentV2(
            InternalSchema,
            fileValue,
            IiifImageRequestUrl.unsafeFrom(
              valueObject.requireStringObject(OntologyConstants.KnoraBase.ExternalUrl.toSmartIri),
            ),
            comment = valueCommentOption,
          ),
        )

      case OntologyConstants.KnoraBase.DocumentFileValue =>
        ZIO.succeed(
          DocumentFileValueContentV2(
            ontologySchema = InternalSchema,
            fileValue = fileValue,
            pageCount = valueObject.maybeIntObject(OntologyConstants.KnoraBase.PageCount.toSmartIri),
            dimX = valueObject.maybeIntObject(OntologyConstants.KnoraBase.DimX.toSmartIri),
            dimY = valueObject.maybeIntObject(OntologyConstants.KnoraBase.DimY.toSmartIri),
            comment = valueCommentOption,
          ),
        )

      case OntologyConstants.KnoraBase.TextFileValue =>
        ZIO.succeed(
          TextFileValueContentV2(
            ontologySchema = InternalSchema,
            fileValue = fileValue,
            comment = valueCommentOption,
          ),
        )

      case OntologyConstants.KnoraBase.AudioFileValue =>
        ZIO.succeed(
          AudioFileValueContentV2(
            ontologySchema = InternalSchema,
            fileValue = fileValue,
            comment = valueCommentOption,
          ),
        )

      case OntologyConstants.KnoraBase.MovingImageFileValue =>
        ZIO.succeed(
          MovingImageFileValueContentV2(
            ontologySchema = InternalSchema,
            fileValue = fileValue,
            comment = valueCommentOption,
          ),
        )

      case OntologyConstants.KnoraBase.ArchiveFileValue =>
        ZIO.succeed(
          ArchiveFileValueContentV2(
            ontologySchema = InternalSchema,
            fileValue = fileValue,
            comment = valueCommentOption,
          ),
        )

      case _ => ZIO.fail(InconsistentRepositoryDataException(s"Unexpected file value type: $valueType"))
    }
  }

  /**
   * Given a [[ValueRdfData]], constructs a [[LinkValueContentV2]].
   *
   * @param valueObject               the given [[ValueRdfData]].
   * @param valueCommentOption        the value's comment, if any.
   * @param mappings                  the mappings needed for standoff conversions and XSL transformations.
   * @param queryStandoff             if `true`, make separate queries to get the standoff for text values.
   * @param versionDate               if defined, represents the requested time in the the resources' version history.
   * @param targetSchema              the schema of the response.
   * @param requestingUser            the user making the request.
   * @return a [[LinkValueContentV2]].
   */
  private def makeLinkValueContentV2(
    valueObject: ValueRdfData,
    valueCommentOption: Option[IRI],
    mappings: Map[IRI, MappingAndXSLTransformation],
    queryStandoff: Boolean,
    versionDate: Option[Instant],
    targetSchema: ApiV2Schema,
    requestingUser: User,
  ) = {
    val referredResourceIri: IRI = if (valueObject.isIncomingLink) {
      valueObject.requireIriObject(OntologyConstants.Rdf.Subject.toSmartIri)
    } else {
      valueObject.requireIriObject(OntologyConstants.Rdf.Object.toSmartIri)
    }

    val linkValue = LinkValueContentV2(
      ontologySchema = InternalSchema,
      referredResourceIri = referredResourceIri,
      isIncomingLink = valueObject.isIncomingLink,
      nestedResource = None,
      comment = valueCommentOption,
    )

    // Is there a nested resource in the link value?
    valueObject.nestedResource match {
      case Some(nestedResourceAssertions: ResourceWithValueRdfData) =>
        // Yes. Construct a ReadResourceV2 representing the nested resource.
        for {
          nestedResource <- constructReadResourceV2(
                              resourceIri = referredResourceIri,
                              resourceWithValueRdfData = nestedResourceAssertions,
                              mappings = mappings,
                              queryStandoff = queryStandoff,
                              versionDate = versionDate,
                              requestingUser = requestingUser,
                              targetSchema = targetSchema,
                            )
        } yield linkValue.copy(
          nestedResource = Some(nestedResource),
        )

      case None =>
        // There is no nested resource.
        ZIO.succeed(linkValue)
    }
  }

  /**
   * Given a [[ValueRdfData]], constructs a [[ValueContentV2]], considering the specific type of the given [[ValueRdfData]].
   *
   * @param valueObject          the given [[ValueRdfData]].
   * @param mappings             the mappings needed for standoff conversions and XSL transformations.
   * @param queryStandoff        if `true`, make separate queries to get the standoff for text values.
   * @param versionDate          if defined, represents the requested time in the the resources' version history.
   * @param targetSchema         the schema of the response.
   * @param requestingUser       the user making the request.
   * @return a [[ValueContentV2]] representing a value.
   */
  private def createValueContentV2FromValueRdfData(
    valueObject: ValueRdfData,
    mappings: Map[IRI, MappingAndXSLTransformation],
    queryStandoff: Boolean,
    versionDate: Option[Instant] = None,
    targetSchema: ApiV2Schema,
    requestingUser: User,
  ) = {
    // every knora-base:Value (any of its subclasses) has a string representation, but it is not necessarily returned with text values.
    val valueObjectValueHasString: Option[String] =
      valueObject.maybeStringObject(OntologyConstants.KnoraBase.ValueHasString.toSmartIri)

    // every knora-base:value (any of its subclasses) may have a comment
    val valueCommentOption: Option[String] =
      valueObject.maybeStringObject(OntologyConstants.KnoraBase.ValueHasComment.toSmartIri)

    val valueTypeStr: IRI = valueObject.valueObjectClass.toString

    valueTypeStr match {
      case OntologyConstants.KnoraBase.TextValue =>
        makeTextValueContentV2(
          valueObject = valueObject,
          valueObjectValueHasString = valueObjectValueHasString,
          valueCommentOption = valueCommentOption,
          mappings = mappings,
          requestingUser = requestingUser,
        )

      case OntologyConstants.KnoraBase.DateValue =>
        val startPrecisionStr =
          valueObject.requireStringObject(OntologyConstants.KnoraBase.ValueHasStartPrecision.toSmartIri)
        val endPrecisionStr =
          valueObject.requireStringObject(OntologyConstants.KnoraBase.ValueHasEndPrecision.toSmartIri)
        val calendarNameStr = valueObject.requireStringObject(OntologyConstants.KnoraBase.ValueHasCalendar.toSmartIri)

        ZIO.succeed(
          DateValueContentV2(
            ontologySchema = InternalSchema,
            valueHasStartJDN = valueObject.requireIntObject(OntologyConstants.KnoraBase.ValueHasStartJDN.toSmartIri),
            valueHasEndJDN = valueObject.requireIntObject(OntologyConstants.KnoraBase.ValueHasEndJDN.toSmartIri),
            valueHasStartPrecision = DatePrecisionV2.parse(
              startPrecisionStr,
              throw InconsistentRepositoryDataException(s"Invalid date precision: $startPrecisionStr"),
            ),
            valueHasEndPrecision = DatePrecisionV2.parse(
              endPrecisionStr,
              throw InconsistentRepositoryDataException(s"Invalid date precision: $endPrecisionStr"),
            ),
            valueHasCalendar = CalendarNameV2.parse(
              calendarNameStr,
              throw InconsistentRepositoryDataException(s"Invalid calendar name: $calendarNameStr"),
            ),
            comment = valueCommentOption,
          ),
        )

      case OntologyConstants.KnoraBase.IntValue =>
        ZIO.succeed(
          IntegerValueContentV2(
            ontologySchema = InternalSchema,
            valueHasInteger = valueObject.requireIntObject(OntologyConstants.KnoraBase.ValueHasInteger.toSmartIri),
            comment = valueCommentOption,
          ),
        )

      case OntologyConstants.KnoraBase.DecimalValue =>
        ZIO.succeed(
          DecimalValueContentV2(
            ontologySchema = InternalSchema,
            valueHasDecimal = valueObject.requireDecimalObject(OntologyConstants.KnoraBase.ValueHasDecimal.toSmartIri),
            comment = valueCommentOption,
          ),
        )

      case OntologyConstants.KnoraBase.BooleanValue =>
        ZIO.succeed(
          BooleanValueContentV2(
            ontologySchema = InternalSchema,
            valueHasBoolean = valueObject.requireBooleanObject(OntologyConstants.KnoraBase.ValueHasBoolean.toSmartIri),
            comment = valueCommentOption,
          ),
        )

      case OntologyConstants.KnoraBase.UriValue =>
        ZIO.succeed(
          UriValueContentV2(
            ontologySchema = InternalSchema,
            valueHasUri = valueObject.requireIriObject(OntologyConstants.KnoraBase.ValueHasUri.toSmartIri),
            comment = valueCommentOption,
          ),
        )

      case OntologyConstants.KnoraBase.ColorValue =>
        ZIO.succeed(
          ColorValueContentV2(
            ontologySchema = InternalSchema,
            valueHasColor = valueObject.requireStringObject(OntologyConstants.KnoraBase.ValueHasColor.toSmartIri),
            comment = valueCommentOption,
          ),
        )

      case OntologyConstants.KnoraBase.GeomValue =>
        ZIO.succeed(
          GeomValueContentV2(
            ontologySchema = InternalSchema,
            valueHasGeometry = valueObject.requireStringObject(OntologyConstants.KnoraBase.ValueHasGeometry.toSmartIri),
            comment = valueCommentOption,
          ),
        )

      case OntologyConstants.KnoraBase.GeonameValue =>
        ZIO.succeed(
          GeonameValueContentV2(
            ontologySchema = InternalSchema,
            valueHasGeonameCode =
              valueObject.requireStringObject(OntologyConstants.KnoraBase.ValueHasGeonameCode.toSmartIri),
            comment = valueCommentOption,
          ),
        )

      case OntologyConstants.KnoraBase.ListValue =>
        val listNodeIri: IRI = valueObject.requireIriObject(OntologyConstants.KnoraBase.ValueHasListNode.toSmartIri)

        val listNode = HierarchicalListValueContentV2(
          ontologySchema = InternalSchema,
          valueHasListNode = listNodeIri,
          listNodeLabel = None,
          comment = valueCommentOption,
        )

        // only query the list node if the response is requested in the simple schema
        // (label is required in the simple schema, but not in the complex schema)

        targetSchema match {
          case ApiV2Simple =>
            for {
              nodeIri <- ZIO
                           .fromEither(ListIri.from(listNodeIri))
                           .orElseFail(BadRequestException(s"Invalid list iri: $listNodeIri"))
              nodeResponse <- listsService.getNode(nodeIri, requestingUser).mapError { e =>
                                e.fold(NotFoundException(s"List node $nodeIri not found"))(identity)
                              }
              listNodeLabel =
                nodeResponse.node.getLabelInPreferredLanguage(requestingUser.lang, appConfig.fallbackLanguage)
            } yield listNode.copy(listNodeLabel = listNodeLabel)
          case ApiV2Complex => ZIO.succeed(listNode)
        }

      case OntologyConstants.KnoraBase.IntervalValue =>
        ZIO.succeed(
          IntervalValueContentV2(
            ontologySchema = InternalSchema,
            valueHasIntervalStart =
              valueObject.requireDecimalObject(OntologyConstants.KnoraBase.ValueHasIntervalStart.toSmartIri),
            valueHasIntervalEnd =
              valueObject.requireDecimalObject(OntologyConstants.KnoraBase.ValueHasIntervalEnd.toSmartIri),
            comment = valueCommentOption,
          ),
        )

      case OntologyConstants.KnoraBase.TimeValue =>
        ZIO.succeed(
          TimeValueContentV2(
            ontologySchema = InternalSchema,
            valueHasTimeStamp =
              valueObject.requireDateTimeObject(OntologyConstants.KnoraBase.ValueHasTimeStamp.toSmartIri),
            comment = valueCommentOption,
          ),
        )

      case OntologyConstants.KnoraBase.LinkValue =>
        makeLinkValueContentV2(
          valueObject = valueObject,
          valueCommentOption = valueCommentOption,
          mappings = mappings,
          queryStandoff = queryStandoff,
          versionDate = versionDate,
          targetSchema = targetSchema,
          requestingUser = requestingUser,
        )

      case fileValueClass: IRI if OntologyConstants.KnoraBase.FileValueClasses.contains(fileValueClass) =>
        makeFileValueContentV2(
          valueType = fileValueClass,
          valueObject = valueObject,
          valueCommentOption = valueCommentOption,
        )

      case other => throw NotImplementedException(s"Not implemented yet: $other")
    }
  }

  /**
   * Creates a [[ReadResourceV2]] from a [[ResourceWithValueRdfData]].
   *
   * @param resourceIri              the IRI of the resource.
   * @param resourceWithValueRdfData the Rdf data belonging to the resource.
   * @param mappings                 the mappings needed for standoff conversions and XSL transformations.
   * @param queryStandoff            if `true`, make separate queries to get the standoff for text values.
   * @param versionDate              if defined, represents the requested time in the the resources' version history.
   * @param targetSchema             the schema of the response.
   * @param requestingUser           the user making the request.
   * @return a [[ReadResourceV2]].
   */
  private def constructReadResourceV2(
    resourceIri: IRI,
    resourceWithValueRdfData: ResourceWithValueRdfData,
    mappings: Map[IRI, MappingAndXSLTransformation],
    queryStandoff: Boolean,
    versionDate: Option[Instant],
    targetSchema: ApiV2Schema,
    requestingUser: User,
  ): Task[ReadResourceV2] = {
    def getDeletionInfo(rdfData: RdfData): Option[DeletionInfo] = {
      val mayHaveDeletedStatements: Option[Boolean] =
        rdfData.maybeBooleanObject(OntologyConstants.KnoraBase.IsDeleted.toSmartIri)
      mayHaveDeletedStatements match {
        case Some(isDeleted: Boolean) =>
          if (isDeleted) {
            val deleteDate         = rdfData.requireDateTimeObject(OntologyConstants.KnoraBase.DeleteDate.toSmartIri)
            val maybeDeleteComment = rdfData.maybeStringObject(OntologyConstants.KnoraBase.DeleteComment.toSmartIri)

            Some(
              DeletionInfo(
                deleteDate = deleteDate,
                maybeDeleteComment = maybeDeleteComment,
              ),
            )
          } else {
            None
          }
        case _ => None
      }
    }

    val resourceLabel: String = resourceWithValueRdfData.requireStringObject(OntologyConstants.Rdfs.Label.toSmartIri)
    val resourceClassStr: IRI = resourceWithValueRdfData.requireIriObject(OntologyConstants.Rdf.Type.toSmartIri)
    val resourceClass = resourceClassStr.toSmartIriWithErr(
      throw InconsistentRepositoryDataException(
        s"Couldn't parse rdf:type of resource <$resourceIri>: <$resourceClassStr>",
      ),
    )
    val resourceAttachedToUser: IRI =
      resourceWithValueRdfData.requireIriObject(OntologyConstants.KnoraBase.AttachedToUser.toSmartIri)
    val resourceAttachedToProject: IRI =
      resourceWithValueRdfData.requireIriObject(OntologyConstants.KnoraBase.AttachedToProject.toSmartIri)
    val resourcePermissions: String =
      resourceWithValueRdfData.requireStringObject(OntologyConstants.KnoraBase.HasPermissions.toSmartIri)
    val resourceCreationDate: Instant =
      resourceWithValueRdfData.requireDateTimeObject(OntologyConstants.KnoraBase.CreationDate.toSmartIri)
    val resourceLastModificationDate: Option[Instant] =
      resourceWithValueRdfData.maybeDateTimeObject(OntologyConstants.KnoraBase.LastModificationDate.toSmartIri)
    val resourceDeletionInfo = getDeletionInfo(resourceWithValueRdfData)

    // get the resource's values
    val valueObjectFutures: Map[SmartIri, Seq[Task[ReadValueV2]]] =
      resourceWithValueRdfData.valuePropertyAssertions.map { case (property: SmartIri, valObjs: Seq[ValueRdfData]) =>
        val readValues: Seq[Task[ReadValueV2]] = valObjs
          .sortBy(_.subjectIri)
          .sortBy { // order values by value IRI, then by knora-base:valueHasOrder
            (valObj: ValueRdfData) =>
              // set order to zero if not given
              valObj.maybeIntObject(OntologyConstants.KnoraBase.ValueHasOrder.toSmartIri).getOrElse(0)
          }
          .map { (valObj: ValueRdfData) =>
            for {
              valueContent <-
                createValueContentV2FromValueRdfData(
                  valueObject = valObj,
                  mappings = mappings,
                  queryStandoff = queryStandoff,
                  targetSchema = targetSchema,
                  requestingUser = requestingUser,
                )

              attachedToUser = valObj.requireIriObject(OntologyConstants.KnoraBase.AttachedToUser.toSmartIri)
              permissions    = valObj.requireStringObject(OntologyConstants.KnoraBase.HasPermissions.toSmartIri)
              valueCreationDate: Instant = valObj.requireDateTimeObject(
                                             OntologyConstants.KnoraBase.ValueCreationDate.toSmartIri,
                                           )
              valueDeletionInfo = getDeletionInfo(valObj)
              valueHasUUID: UUID = UuidUtil.decode(
                                     valObj.requireStringObject(OntologyConstants.KnoraBase.ValueHasUUID.toSmartIri),
                                   )
              previousValueIri: Option[IRI] = valObj.maybeIriObject(
                                                OntologyConstants.KnoraBase.PreviousValue.toSmartIri,
                                              )

            } yield valueContent match {
              case linkValueContentV2: LinkValueContentV2 =>
                val valueHasRefCount: Int =
                  valObj.requireIntObject(OntologyConstants.KnoraBase.ValueHasRefCount.toSmartIri)

                ReadLinkValueV2(
                  valueIri = valObj.subjectIri,
                  attachedToUser = attachedToUser,
                  permissions = permissions,
                  userPermission = valObj.userPermission,
                  valueCreationDate = valueCreationDate,
                  valueHasUUID = valueHasUUID,
                  valueContent = linkValueContentV2,
                  valueHasRefCount = valueHasRefCount,
                  previousValueIri = previousValueIri,
                  deletionInfo = valueDeletionInfo,
                )

              case textValueContentV2: TextValueContentV2 =>
                val maybeValueHasMaxStandoffStartIndex: Option[Int] =
                  valObj.maybeIntObject(OntologyConstants.KnoraBase.ValueHasMaxStandoffStartIndex.toSmartIri)

                ReadTextValueV2(
                  valueIri = valObj.subjectIri,
                  attachedToUser = attachedToUser,
                  permissions = permissions,
                  userPermission = valObj.userPermission,
                  valueCreationDate = valueCreationDate,
                  valueHasUUID = valueHasUUID,
                  valueContent = textValueContentV2,
                  valueHasMaxStandoffStartIndex = maybeValueHasMaxStandoffStartIndex,
                  previousValueIri = previousValueIri,
                  deletionInfo = valueDeletionInfo,
                )

              case otherValueContentV2: ValueContentV2 =>
                ReadOtherValueV2(
                  valueIri = valObj.subjectIri,
                  attachedToUser = attachedToUser,
                  permissions = permissions,
                  userPermission = valObj.userPermission,
                  valueCreationDate = valueCreationDate,
                  valueHasUUID = valueHasUUID,
                  valueContent = otherValueContentV2,
                  previousValueIri = previousValueIri,
                  deletionInfo = valueDeletionInfo,
                )
            }
          }

        property -> readValues
      }

    for {
      projectIri <- ZIO.fromEither(ProjectIri.from(resourceAttachedToProject)).mapError(BadRequestException.apply)
      project <-
        projectService.findById(projectIri).someOrFail(NotFoundException(s"Project '${projectIri.value}' not found"))

      valueObjects <- ZioHelper.sequence(valueObjectFutures.map { case (k, v) => k -> ZIO.collectAll(v) })
    } yield ReadResourceV2(
      resourceIri = resourceIri,
      resourceClassIri = resourceClass,
      label = resourceLabel,
      attachedToUser = resourceAttachedToUser,
      projectADM = project,
      permissions = resourcePermissions,
      userPermission = resourceWithValueRdfData.userPermission.get,
      values = valueObjects,
      creationDate = resourceCreationDate,
      lastModificationDate = resourceLastModificationDate,
      versionDate = versionDate,
      deletionInfo = resourceDeletionInfo,
    )
  }

  /**
   * Creates an API response.
   *
   * @param mainResourcesAndValueRdfData the query results.
   * @param orderByResourceIri           the order in which the resources should be returned. This sequence
   *                                     contains the resource IRIs received from the triplestore before filtering
   *                                     for permissions, but after filtering for duplicates.
   * @param pageSizeBeforeFiltering      the number of resources returned before filtering for permissions and duplicates.
   * @param mappings                     the mappings to convert standoff to XML, if any.
   * @param queryStandoff                if `true`, make separate queries to get the standoff for text values.
   * @param calculateMayHaveMoreResults  if `true`, calculate whether there may be more results for the query.
   * @param versionDate                  if defined, represents the requested time in the the resources' version history.
   * @param targetSchema                 the schema of response.
   * @param requestingUser               the user making the request.
   * @return a collection of [[ReadResourceV2]] representing the search results.
   */
  override def createApiResponse(
    mainResourcesAndValueRdfData: MainResourcesAndValueRdfData,
    orderByResourceIri: Seq[IRI],
    pageSizeBeforeFiltering: Int,
    mappings: Map[IRI, MappingAndXSLTransformation] = Map.empty[IRI, MappingAndXSLTransformation],
    queryStandoff: Boolean,
    calculateMayHaveMoreResults: Boolean,
    versionDate: Option[Instant],
    targetSchema: ApiV2Schema,
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2] = {

    val visibleResourceIris: Seq[IRI] =
      orderByResourceIri.filter(resourceIri => mainResourcesAndValueRdfData.resources.keySet.contains(resourceIri))

    // iterate over visibleResourceIris and construct the response in the correct order
    val readResourceFutures: Vector[Task[ReadResourceV2]] = visibleResourceIris.map { (resourceIri: IRI) =>
      val data = mainResourcesAndValueRdfData.resources(resourceIri)
      constructReadResourceV2(
        resourceIri = resourceIri,
        resourceWithValueRdfData = data,
        mappings = mappings,
        queryStandoff = queryStandoff,
        versionDate = versionDate,
        targetSchema = targetSchema,
        requestingUser = requestingUser,
      )
    }.toVector

    for {
      resources <- ZIO.collectAll(readResourceFutures)

      // If we got a full page of results from the triplestore (before filtering for permissions), there
      // might be at least one more page of results that the user could request.
      mayHaveMoreResults =
        calculateMayHaveMoreResults && pageSizeBeforeFiltering == appConfig.v2.resourcesSequence.resultsPerPage
    } yield ReadResourcesSequenceV2(
      resources = resources,
      hiddenResourceIris = mainResourcesAndValueRdfData.hiddenResourceIris,
      mayHaveMoreResults = mayHaveMoreResults,
    )
  }

  /**
   * Gets mappings referred to in query results [[Map[IRI, ResourceWithValueRdfData]]].
   *
   * @param queryResultsSeparated query results referring to mappings.
   *
   * @param requestingUser        the user making the request.
   * @return the referred mappings.
   */
  override def getMappingsFromQueryResultsSeparated(
    queryResultsSeparated: Map[IRI, ResourceWithValueRdfData],
    requestingUser: User,
  ): Task[Map[IRI, MappingAndXSLTransformation]] = {

    // collect the Iris of the mappings referred to in the resources' text values
    val mappingIris = queryResultsSeparated.flatMap { case (_, assertions: ResourceWithValueRdfData) =>
      getMappingIrisFromValuePropertyAssertions(assertions.valuePropertyAssertions)
    }.toSet

    // get all the mappings
    val mappingResponsesFuture = mappingIris.map { (mappingIri: IRI) =>
      messageRelay.ask[GetMappingResponseV2](GetMappingRequestV2(mappingIri))
    }

    for {
      mappingResponses <- ZIO.collectAll(mappingResponsesFuture)

      // get the default XSL transformations
      mappingsWithFuture =
        mappingResponses.map { (mapping: GetMappingResponseV2) =>
          for {
            // if given, get the default XSL transformation
            xsltOption <-
              if (mapping.mapping.defaultXSLTransformation.nonEmpty) {
                val xslTransformationFuture = for {
                  xslTransformation <- messageRelay.ask[GetXSLTransformationResponseV2](
                                         GetXSLTransformationRequestV2(
                                           mapping.mapping.defaultXSLTransformation.get,
                                           requestingUser = requestingUser,
                                         ),
                                       )
                } yield Some(xslTransformation.xslt)

                xslTransformationFuture.mapError { case notFound: NotFoundException =>
                  throw SipiException(
                    s"Default XSL transformation <${mapping.mapping.defaultXSLTransformation.get}> not found for mapping <${mapping.mappingIri}>: ${notFound.message}",
                  )
                }
              } else {
                ZIO.none
              }
          } yield mapping.mappingIri -> MappingAndXSLTransformation(
            mapping = mapping.mapping,
            standoffEntities = mapping.standoffEntities,
            XSLTransformation = xsltOption,
          )

        }
      mappings <- ZIO.collectAll(mappingsWithFuture)
    } yield mappings.toMap
  }
}

object ConstructResponseUtilV2Live {
  val layer = ZLayer.derive[ConstructResponseUtilV2Live]
}
