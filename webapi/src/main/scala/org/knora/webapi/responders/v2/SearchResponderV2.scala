/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import com.typesafe.scalalogging.LazyLogging
import zio.*

import dsp.errors.AssertionException
import dsp.errors.BadRequestException
import dsp.errors.GravsearchException
import dsp.errors.InconsistentRepositoryDataException
import dsp.valueobjects.Iri
import org.knora.webapi.*
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.*
import org.knora.webapi.messages.twirl.queries.sparql
import org.knora.webapi.messages.util.ConstructResponseUtilV2
import org.knora.webapi.messages.util.ConstructResponseUtilV2.MappingAndXSLTransformation
import org.knora.webapi.messages.util.ErrorHandlingMap
import org.knora.webapi.messages.util.rdf.JsonLDDocument
import org.knora.webapi.messages.util.rdf.JsonLDInt
import org.knora.webapi.messages.util.rdf.JsonLDObject
import org.knora.webapi.messages.util.rdf.JsonLDString
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
import org.knora.webapi.messages.util.rdf.SparqlSelectResultBody
import org.knora.webapi.messages.util.rdf.VariableResultsRow
import org.knora.webapi.messages.util.search.*
import org.knora.webapi.messages.util.search.gravsearch.GravsearchParser
import org.knora.webapi.messages.util.search.gravsearch.GravsearchQueryChecker
import org.knora.webapi.messages.util.search.gravsearch.mainquery.GravsearchMainQueryGenerator
import org.knora.webapi.messages.util.search.gravsearch.prequery.GravsearchToCountPrequeryTransformer
import org.knora.webapi.messages.util.search.gravsearch.prequery.GravsearchToPrequeryTransformer
import org.knora.webapi.messages.util.search.gravsearch.prequery.InferenceOptimizationService
import org.knora.webapi.messages.util.search.gravsearch.transformers.ConstructTransformer
import org.knora.webapi.messages.util.search.gravsearch.transformers.OntologyInferencer
import org.knora.webapi.messages.util.search.gravsearch.transformers.SelectTransformer
import org.knora.webapi.messages.util.search.gravsearch.types.*
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.messages.v2.responder.KnoraJsonLDResponseV2
import org.knora.webapi.messages.v2.responder.ontologymessages.EntityInfoGetRequestV2
import org.knora.webapi.messages.v2.responder.ontologymessages.EntityInfoGetResponseV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadClassInfoV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadPropertyInfoV2
import org.knora.webapi.messages.v2.responder.resourcemessages.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.util.ApacheLuceneSupport.*

/**
 * Represents the number of resources found by a search query.
 */
case class ResourceCountV2(numberOfResources: Int) extends KnoraJsonLDResponseV2 {
  override def toJsonLDDocument(
    targetSchema: ApiV2Schema,
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
  ): JsonLDDocument =
    JsonLDDocument(
      body = JsonLDObject(
        Map(
          OntologyConstants.SchemaOrg.NumberOfItems -> JsonLDInt(numberOfResources),
        ),
      ),
      context = JsonLDObject(
        Map(
          "schema" -> JsonLDString(OntologyConstants.SchemaOrg.SchemaOrgPrefixExpansion),
        ),
      ),
    )
}

trait SearchResponderV2 {

  /**
   * Performs a search using a Gravsearch query provided by the client.
   *
   * @param query            a Gravsearch query provided by the client.
   * @param schemaAndOptions the target API schema and its options submitted with the request.
   * @param user             the client making the request.
   * @return a [[ReadResourcesSequenceV2]] representing the resources that have been found.
   */
  def gravsearchV2(
    query: ConstructQuery,
    schemaAndOptions: SchemaRendering,
    user: User,
  ): Task[ReadResourcesSequenceV2]

  def gravsearchV2(query: IRI, rendering: SchemaRendering, user: User): Task[ReadResourcesSequenceV2] = for {
    q <- ZIO.attempt(GravsearchParser.parseQuery(query))
    r <- gravsearchV2(q, rendering, user)
  } yield r

  /**
   * Performs a count query for a Gravsearch query provided by the user.
   *
   * @param query a Gravsearch query provided by the client.
   * @param user  the client making the request.
   * @return a [[ResourceCountV2]] representing the number of resources that have been found.
   */
  def gravsearchCountV2(query: ConstructQuery, user: User): Task[ResourceCountV2]
  def gravsearchCountV2(query: IRI, user: User): Task[ResourceCountV2] =
    for {
      q <- ZIO.attempt(GravsearchParser.parseQuery(query))
      r <- gravsearchCountV2(q, user)
    } yield r

  /**
   * Performs a fulltext search and returns the resources count (how many resources match the search criteria),
   * without taking into consideration permission checking.
   *
   * This method does not return the resources themselves.
   *
   * @param searchValue          the values to search for.
   * @param limitToProject       limit search to given project.
   * @param limitToResourceClass limit search to given resource class.
   * @return a [[ResourceCountV2]] representing the number of resources that have been found.
   */
  def fulltextSearchCountV2(
    searchValue: IRI,
    limitToProject: Option[ProjectIri],
    limitToResourceClass: Option[SmartIri],
    limitToStandoffClass: Option[SmartIri],
  ): Task[ResourceCountV2]

  /**
   * Performs a fulltext search (simple search).
   *
   * @param searchValue          the values to search for.
   * @param offset               the offset to be used for paging.
   * @param limitToProject       limit search to given project.
   * @param limitToResourceClass limit search to given resource class.
   * @param limitToStandoffClass limit search to given standoff class.
   * @param returnFiles          if true, return any file value attached to each matching resource.
   * @param schemaAndOptions     the target API schema and the schema options submitted with the request.
   * @param requestingUser       the client making the request.
   * @return a [[ReadResourcesSequenceV2]] representing the resources that have been found.
   */
  def fulltextSearchV2(
    searchValue: IRI,
    offset: Int,
    limitToProject: Option[ProjectIri],
    limitToResourceClass: Option[SmartIri],
    limitToStandoffClass: Option[SmartIri],
    returnFiles: Boolean,
    schemaAndOptions: SchemaRendering,
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2]

  /**
   * Performs a count query for a search for resources by their rdfs:label.
   *
   * @param searchValue          the values to search for.
   * @param limitToProject       limit search to given project.
   * @param limitToResourceClass limit search to given resource class.
   * @return a [[ResourceCountV2]] representing the resources that have been found.
   */
  def searchResourcesByLabelCountV2(
    searchValue: String,
    limitToProject: Option[ProjectIri],
    limitToResourceClass: Option[SmartIri],
  ): Task[ResourceCountV2]

  /**
   * Performs a search for resources by their rdfs:label.
   *
   * @param searchValue          the values to search for.
   * @param offset               the offset to be used for paging.
   * @param limitToProject       limit search to given project.
   * @param limitToResourceClass limit search to given resource class.
   * @param targetSchema         the schema of the response.
   * @param requestingUser       the client making the request.
   * @return a [[ReadResourcesSequenceV2]] representing the resources that have been found.
   */
  def searchResourcesByLabelV2(
    searchValue: String,
    offset: Int,
    limitToProject: Option[ProjectIri],
    limitToResourceClass: Option[SmartIri],
    targetSchema: ApiV2Schema,
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2]

  /**
   * Requests resources of the specified class from the specified project.
   *
   * @param projectIri       the IRI of the project.
   * @param resourceClass    the IRI of the resource class, in the complex schema.
   * @param orderByProperty  the IRI of the property that the resources are to be ordered by, in the complex schema.
   * @param page             the page number of the results page to be returned.
   * @param schemaAndOptions the schema of the response and schema options submitted with the request.
   * @param requestingUser   the user making the request.
   * @return a [[ReadResourcesSequenceV2]].
   */
  def searchResourcesByProjectAndClassV2(
    projectIri: ProjectIri,
    resourceClass: SmartIri,
    orderByProperty: Option[SmartIri],
    page: Int,
    schemaAndOptions: SchemaRendering,
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2]
}

final case class SearchResponderV2Live(
  private val appConfig: AppConfig,
  private val triplestore: TriplestoreService,
  private val messageRelay: MessageRelay,
  private val constructResponseUtilV2: ConstructResponseUtilV2,
  private val ontologyCache: OntologyCache,
  private val standoffTagUtilV2: StandoffTagUtilV2,
  private val queryTraverser: QueryTraverser,
  private val sparqlTransformerLive: OntologyInferencer,
  private val gravsearchTypeInspectionRunner: GravsearchTypeInspectionRunner,
  private val inferenceOptimizationService: InferenceOptimizationService,
  private val stringFormatter: StringFormatter,
  private val iriConverter: IriConverter,
  private val constructTransformer: ConstructTransformer,
) extends SearchResponderV2
    with LazyLogging {

  private implicit val sf: StringFormatter = stringFormatter

  /**
   * Performs a fulltext search and returns the resources count (how many resources match the search criteria),
   * without taking into consideration permission checking.
   *
   * This method does not return the resources themselves.
   *
   * @param searchValue          the values to search for.
   * @param limitToProject       limit search to given project.
   * @param limitToResourceClass limit search to given resource class.
   *
   * @return a [[ResourceCountV2]] representing the number of resources that have been found.
   */
  override def fulltextSearchCountV2(
    searchValue: IRI,
    limitToProject: Option[ProjectIri],
    limitToResourceClass: Option[SmartIri],
    limitToStandoffClass: Option[SmartIri],
  ): Task[ResourceCountV2] =
    for {
      _                    <- ensureIsFulltextSearch(searchValue)
      searchValue          <- validateSearchString(searchValue)
      limitToResourceClass <- ZIO.foreach(limitToResourceClass)(ensureResourceClassIri)
      limitToStandoffClass <- ZIO.foreach(limitToStandoffClass)(ensureStandoffClass)
      countSparql <- ZIO.attempt(
                       sparql.v2.txt
                         .searchFulltext(
                           searchTerms = LuceneQueryString(searchValue),
                           limitToProject = limitToProject.map(_.value),
                           limitToResourceClass = limitToResourceClass.map(_.toString),
                           limitToStandoffClass = limitToStandoffClass.map(_.toString),
                           returnFiles = false, // not relevant for a count query
                           separator = None,    // no separator needed for count query
                           limit = 1,
                           offset = 0,
                           countQuery = true, // do not get the resources themselves, but the sum of results
                         ),
                     )
      bindings <- triplestore.query(Select(countSparql)).map(_.results.bindings)
      count <- // query response should contain one result with one row with the name "count"
        ZIO.fail {
          val msg = s"Fulltext count query is expected to return exactly one row, but ${bindings.size} given"
          GravsearchException(msg)
        }
          .when(bindings.length != 1)
          .as(bindings.head.rowMap("count"))
    } yield ResourceCountV2(numberOfResources = count.toInt)

  /**
   * Performs a fulltext search (simple search).
   *
   * @param searchValue          the values to search for.
   * @param offset               the offset to be used for paging.
   * @param limitToProject       limit search to given project.
   * @param limitToResourceClass limit search to given resource class.
   * @param limitToStandoffClass limit search to given standoff class.
   * @param returnFiles          if true, return any file value attached to each matching resource.
   * @param schemaAndOptions     the target API schema and the schema options submitted with the request.
   * @param requestingUser       the client making the request.
   * @return a [[ReadResourcesSequenceV2]] representing the resources that have been found.
   */
  override def fulltextSearchV2(
    searchValue: String,
    offset: Int,
    limitToProject: Option[ProjectIri],
    limitToResourceClass: Option[SmartIri],
    limitToStandoffClass: Option[SmartIri],
    returnFiles: Boolean,
    schemaAndOptions: SchemaRendering,
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2] = {
    import org.knora.webapi.messages.util.search.FullTextMainQueryGenerator.FullTextSearchConstants
    for {
      _                    <- ensureIsFulltextSearch(searchValue)
      searchValue          <- validateSearchString(searchValue)
      limitToResourceClass <- ZIO.foreach(limitToResourceClass)(ensureResourceClassIri)
      limitToStandoffClass <- ZIO.foreach(limitToStandoffClass)(ensureStandoffClass)
      searchSparql <-
        ZIO.attempt(
          sparql.v2.txt
            .searchFulltext(
              searchTerms = LuceneQueryString(searchValue),
              limitToProject = limitToProject.map(_.value),
              limitToResourceClass = limitToResourceClass.map(_.toString),
              limitToStandoffClass = limitToStandoffClass.map(_.toString),
              returnFiles = returnFiles,
              separator = Some(StringFormatter.INFORMATION_SEPARATOR_ONE),
              limit = appConfig.v2.resourcesSequence.resultsPerPage,
              offset = offset * appConfig.v2.resourcesSequence.resultsPerPage, // determine the actual offset
              countQuery = false,
            ),
        )

      prequeryResponseNotMerged <- triplestore.query(Select(searchSparql))

      mainResourceVar = QueryVariable("resource")

      // Merge rows with the same resource IRI.
      prequeryResponse = mergePrequeryResults(prequeryResponseNotMerged, mainResourceVar)

      // a sequence of resource IRIs that match the search criteria
      // attention: no permission checking has been done so far
      resourceIris: Seq[IRI] = prequeryResponse.getColOrThrow(FullTextSearchConstants.resourceVar.variableName)

      // If the prequery returned some results, prepare a main query.
      mainResourcesAndValueRdfData <-
        if (resourceIris.nonEmpty) {

          // for each resource, create a Set of value object IRIs
          val valueObjectIrisPerResource: Map[IRI, Set[IRI]] =
            prequeryResponse.results.bindings.foldLeft(Map.empty[IRI, Set[IRI]]) {
              (acc: Map[IRI, Set[IRI]], resultRow: VariableResultsRow) =>
                val mainResIri: IRI = resultRow.rowMap(FullTextSearchConstants.resourceVar.variableName)

                resultRow.rowMap.get(FullTextSearchConstants.valueObjectConcatVar.variableName) match {

                  case Some(valObjIris) =>
                    // Filter out empty IRIs (which we could get if a variable used in GROUP_CONCAT is unbound)
                    acc + (mainResIri -> valObjIris
                      .split(StringFormatter.INFORMATION_SEPARATOR_ONE)
                      .toSet
                      .filterNot(_.isEmpty))

                  case None => acc
                }
            }

          // collect all value object IRIs
          val allValueObjectIris = valueObjectIrisPerResource.values.flatten.toSet

          // create a CONSTRUCT query to query resources and their values
          val mainQuery = FullTextMainQueryGenerator.createMainQuery(
            resourceIris = resourceIris.toSet,
            valueObjectIris = allValueObjectIris,
            targetSchema = schemaAndOptions.schema,
            schemaOptions = schemaAndOptions.rendering,
          )

          for {
            query          <- constructTransformer.transform(mainQuery).map(_.toSparql)
            searchResponse <- triplestore.query(Construct.gravsearch(query)).flatMap(_.asExtended)
            // separate resources and value objects
            queryResultsSep = constructResponseUtilV2.splitMainResourcesAndValueRdfData(searchResponse, requestingUser)
          } yield queryResultsSep
        } else {
          // the prequery returned no results, no further query is necessary
          ZIO.attempt(ConstructResponseUtilV2.MainResourcesAndValueRdfData(resources = Map.empty))
        }

      // Find out whether to query standoff along with text values. This boolean value will be passed to
      // ConstructResponseUtilV2.makeTextValueContentV2.
      queryStandoff: Boolean = SchemaOptions.queryStandoffWithTextValues(
                                 targetSchema = schemaAndOptions.schema,
                                 schemaOptions = schemaAndOptions.rendering,
                               )

      // If we're querying standoff, get XML-to standoff mappings.
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
          orderByResourceIri = resourceIris,
          pageSizeBeforeFiltering = resourceIris.size,
          mappings = mappingsAsMap,
          queryStandoff = queryStandoff,
          calculateMayHaveMoreResults = true,
          versionDate = None,
          targetSchema = schemaAndOptions.schema,
          requestingUser = requestingUser,
        )

    } yield apiResponse
  }

  override def gravsearchCountV2(query: ConstructQuery, user: User): Task[ResourceCountV2] =
    for {
      _ <- // make sure that OFFSET is 0
        ZIO
          .fail(GravsearchException(s"OFFSET must be 0 for a count query, but ${query.offset} given"))
          .when(query.offset != 0)

      // Do type inspection and remove type annotations from the WHERE clause.
      typeInspectionResult <- gravsearchTypeInspectionRunner.inspectTypes(query.whereClause, user)

      whereClauseWithoutAnnotations <- GravsearchTypeInspectionUtil.removeTypeAnnotations(query.whereClause)

      // Validate schemas and predicates in the CONSTRUCT clause.
      _ <- GravsearchQueryChecker.checkConstructClause(query.constructClause, typeInspectionResult)

      // Create a Select prequery
      querySchema <-
        ZIO.fromOption(query.querySchema).orElseFail(AssertionException(s"WhereClause has no querySchema"))
      gravsearchToCountTransformer: GravsearchToCountPrequeryTransformer =
        new GravsearchToCountPrequeryTransformer(
          constructClause = query.constructClause,
          typeInspectionResult = typeInspectionResult,
          querySchema = querySchema,
        )

      prequery <-
        queryTraverser.transformConstructToSelect(
          inputQuery = query.copy(
            whereClause = whereClauseWithoutAnnotations,
            orderBy = Seq.empty[OrderCriterion], // count queries do not need any sorting criteria
          ),
          transformer = gravsearchToCountTransformer,
        )

      selectTransformer: SelectTransformer =
        new SelectTransformer(
          simulateInference = gravsearchToCountTransformer.useInference,
          sparqlTransformerLive,
          stringFormatter,
        )

      ontologiesForInferenceMaybe <-
        inferenceOptimizationService.getOntologiesRelevantForInference(query.whereClause)

      countQuery <- queryTraverser.transformSelectToSelect(
                      inputQuery = prequery,
                      transformer = selectTransformer,
                      ontologiesForInferenceMaybe,
                    )

      countResponse <- triplestore.query(Select.gravsearch(countQuery.toSparql))

      _ <- // query response should contain one result with one row with the name "count"
        ZIO
          .fail(
            GravsearchException(s"Count query is expected to return exactly one row, but ${countResponse.size} given"),
          )
          .when(countResponse.size != 1)

      count: String = countResponse.getFirstOrThrow("count")

    } yield ResourceCountV2(numberOfResources = count.toInt)

  override def gravsearchV2(
    query: ConstructQuery,
    schemaAndOptions: SchemaRendering,
    user: User,
  ): Task[ReadResourcesSequenceV2] = {

    for {
      // Do type inspection and remove type annotations from the WHERE clause.
      typeInspectionResult          <- gravsearchTypeInspectionRunner.inspectTypes(query.whereClause, user)
      whereClauseWithoutAnnotations <- GravsearchTypeInspectionUtil.removeTypeAnnotations(query.whereClause)

      // Validate schemas and predicates in the CONSTRUCT clause.
      _ <- GravsearchQueryChecker.checkConstructClause(query.constructClause, typeInspectionResult)

      // Create a Select prequery
      querySchema <-
        ZIO.fromOption(query.querySchema).orElseFail(AssertionException(s"InputQuery has no querySchema"))
      gravsearchToPrequeryTransformer <- ZIO.attempt(
                                           new GravsearchToPrequeryTransformer(
                                             constructClause = query.constructClause,
                                             typeInspectionResult = typeInspectionResult,
                                             querySchema = querySchema,
                                             appConfig = appConfig,
                                           ),
                                         )

      // TODO: if the ORDER BY criterion is a property whose occurrence is not 1, then the logic does not work correctly
      // TODO: the ORDER BY criterion has to be included in a GROUP BY statement, returning more than one row if property occurs more than once

      ontologiesForInferenceMaybe <-
        inferenceOptimizationService.getOntologiesRelevantForInference(query.whereClause)

      prequery <-
        queryTraverser.transformConstructToSelect(
          inputQuery = query.copy(whereClause = whereClauseWithoutAnnotations),
          transformer = gravsearchToPrequeryTransformer,
        )

      // variable representing the main resources
      mainResourceVar: QueryVariable = gravsearchToPrequeryTransformer.mainResourceVariable

      selectTransformer: SelectTransformer =
        new SelectTransformer(
          simulateInference = gravsearchToPrequeryTransformer.useInference,
          sparqlTransformerLive,
          stringFormatter,
        )

      // Convert the preprocessed query to a non-triplestore-specific query.

      transformedPrequery <-
        queryTraverser.transformSelectToSelect(
          inputQuery = prequery,
          transformer = selectTransformer,
          limitInferenceToOntologies = ontologiesForInferenceMaybe,
        )

      prequerySparql = transformedPrequery.toSparql

      prequeryResponseNotMerged <-
        triplestore
          .query(Select.gravsearch(prequerySparql))
          .logError(s"Gravsearch timed out for prequery:\n$prequerySparql")

      pageSizeBeforeFiltering: Int = prequeryResponseNotMerged.size

      // Merge rows with the same main resource IRI. This could happen if there are unbound variables in a UNION.
      prequeryResponse =
        mergePrequeryResults(
          prequeryResponseNotMerged = prequeryResponseNotMerged,
          mainResourceVar = mainResourceVar,
        )

      // a sequence of resource IRIs that match the search criteria
      // attention: no permission checking has been done so far
      mainResourceIris: Seq[IRI] = prequeryResponse.getColOrThrow(mainResourceVar.variableName)

      mainQueryResults <-
        if (mainResourceIris.nonEmpty) {
          // at least one resource matched the prequery

          // get all the IRIs for variables representing dependent resources per main resource
          val dependentResourceIrisPerMainResource: GravsearchMainQueryGenerator.DependentResourcesPerMainResource =
            GravsearchMainQueryGenerator.getDependentResourceIrisPerMainResource(
              prequeryResponse = prequeryResponse,
              transformer = gravsearchToPrequeryTransformer,
              mainResourceVar = mainResourceVar,
            )

          // collect all variables representing resources
          val allResourceVariablesFromTypeInspection: Set[QueryVariable] = typeInspectionResult.entities.collect {
            case (queryVar: TypeableVariable, nonPropTypeInfo: NonPropertyTypeInfo) if nonPropTypeInfo.isResourceType =>
              QueryVariable(queryVar.variableName)
          }.toSet

          // the user may have defined IRIs of dependent resources in the input query (type annotations)
          // only add them if they are mentioned in a positive context (not negated like in a FILTER NOT EXISTS or MINUS)
          val dependentResourceIrisFromTypeInspection: Set[IRI] = typeInspectionResult.entities.collect {
            case (iri: TypeableIri, _: NonPropertyTypeInfo)
                if whereClauseWithoutAnnotations.positiveEntities.contains(IriRef(iri.iri)) =>
              iri.iri.toString
          }.toSet

          // the IRIs of all dependent resources for all main resources
          val allDependentResourceIris: Set[IRI] =
            dependentResourceIrisPerMainResource.dependentResourcesPerMainResource.values.flatten.toSet ++ dependentResourceIrisFromTypeInspection

          // for each main resource, create a Map of value object variables and their Iris
          val valueObjectVarsAndIrisPerMainResource
            : GravsearchMainQueryGenerator.ValueObjectVariablesAndValueObjectIris =
            GravsearchMainQueryGenerator.getValueObjectVarsAndIrisPerMainResource(
              prequeryResponse = prequeryResponse,
              transformer = gravsearchToPrequeryTransformer,
              mainResourceVar = mainResourceVar,
            )

          // collect all value objects IRIs (for all main resources and for all value object variables)
          val allValueObjectIris: Set[IRI] =
            valueObjectVarsAndIrisPerMainResource.valueObjectVariablesAndValueObjectIris.values.foldLeft(
              Set.empty[IRI],
            ) { case (acc: Set[IRI], valObjIrisForQueryVar: Map[QueryVariable, Set[IRI]]) =>
              acc ++ valObjIrisForQueryVar.values.flatten.toSet
            }

          // create the main query
          // it is a Union of two sets: the main resources and the dependent resources
          val mainQuery: ConstructQuery = GravsearchMainQueryGenerator.createMainQuery(
            mainResourceIris = mainResourceIris.map(iri => IriRef(iri.toSmartIri)).toSet,
            dependentResourceIris = allDependentResourceIris.map(iri => IriRef(iri.toSmartIri)),
            valueObjectIris = allValueObjectIris,
            targetSchema = schemaAndOptions.schema,
            schemaOptions = schemaAndOptions.rendering,
          )

          for {
            mainQuery         <- constructTransformer.transform(mainQuery, ontologiesForInferenceMaybe).map(_.toSparql)
            mainQueryResponse <- triplestore.query(Construct.gravsearch(mainQuery)).flatMap(_.asExtended)

            // Filter out values that the user doesn't have permission to see.
            queryResultsFilteredForPermissions =
              constructResponseUtilV2.splitMainResourcesAndValueRdfData(mainQueryResponse, user)

            // filter out those value objects that the user does not want to be returned by the query (not present in the input query's CONSTRUCT clause)
            queryResWithFullGraphPatternOnlyRequestedValues: Map[
              IRI,
              ConstructResponseUtilV2.ResourceWithValueRdfData,
            ] = MainQueryResultProcessor
                  .getRequestedValuesFromResultsWithFullGraphPattern(
                    queryResultsFilteredForPermissions.resources,
                    valueObjectVarsAndIrisPerMainResource,
                    allResourceVariablesFromTypeInspection,
                    dependentResourceIrisFromTypeInspection,
                    gravsearchToPrequeryTransformer,
                  )
          } yield queryResultsFilteredForPermissions.copy(
            resources = queryResWithFullGraphPatternOnlyRequestedValues,
          )

        } else {
          // the prequery returned no results, no further query is necessary
          ZIO.attempt(ConstructResponseUtilV2.MainResourcesAndValueRdfData(resources = Map.empty))
        }

      // Find out whether to query standoff along with text values. This boolean value will be passed to
      // ConstructResponseUtilV2.makeTextValueContentV2.
      queryStandoff: Boolean = SchemaOptions.queryStandoffWithTextValues(
                                 targetSchema = schemaAndOptions.schema,
                                 schemaOptions = schemaAndOptions.rendering,
                               )

      // If we're querying standoff, get XML-to standoff mappings.
      mappingsAsMap <-
        if (queryStandoff) {
          constructResponseUtilV2.getMappingsFromQueryResultsSeparated(mainQueryResults.resources, user)
        } else {
          ZIO.succeed(Map.empty[IRI, MappingAndXSLTransformation])
        }

      apiResponse <- constructResponseUtilV2.createApiResponse(
                       mainResourcesAndValueRdfData = mainQueryResults,
                       orderByResourceIri = mainResourceIris,
                       pageSizeBeforeFiltering = pageSizeBeforeFiltering,
                       mappings = mappingsAsMap,
                       queryStandoff = queryStandoff,
                       versionDate = None,
                       calculateMayHaveMoreResults = true,
                       targetSchema = schemaAndOptions.schema,
                       requestingUser = user,
                     )
    } yield apiResponse
  }

  /**
   * Requests resources of the specified class from the specified project.
   *
   * @param projectIri      the IRI of the project.
   * @param resourceClass   the IRI of the resource class, in the complex schema.
   * @param orderByProperty the IRI of the property that the resources are to be ordered by, in the complex schema.
   * @param page            the page number of the results page to be returned.
   * @param schemaAndOptions    the schema of the response and schema options submitted with the request.
   * @param requestingUser  the user making the request.
   * @return a [[ReadResourcesSequenceV2]].
   */
  override def searchResourcesByProjectAndClassV2(
    projectIri: ProjectIri,
    resourceClass: SmartIri,
    orderByProperty: Option[SmartIri],
    page: Int,
    schemaAndOptions: SchemaRendering,
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2] = {
    val internalClassIri = resourceClass.toOntologySchema(InternalSchema)
    val maybeInternalOrderByPropertyIri: Option[SmartIri] =
      orderByProperty.map(_.toOntologySchema(InternalSchema))

    for {
      // Get information about the resource class, and about the ORDER BY property if specified.
      entityInfoResponse <- messageRelay.ask[EntityInfoGetResponseV2](
                              EntityInfoGetRequestV2(
                                classIris = Set(internalClassIri),
                                propertyIris = maybeInternalOrderByPropertyIri.toSet,
                                requestingUser = requestingUser,
                              ),
                            )

      classDef: ReadClassInfoV2 = entityInfoResponse.classInfoMap(internalClassIri)

      // If an ORDER BY property was specified, determine which subproperty of knora-base:valueHas to use to get the
      // literal value to sort by.
      maybeOrderByValuePredicate <- ZIO.attempt {
                                      maybeInternalOrderByPropertyIri match {
                                        case Some(internalOrderByPropertyIri) =>
                                          val internalOrderByPropertyDef: ReadPropertyInfoV2 =
                                            entityInfoResponse.propertyInfoMap(
                                              internalOrderByPropertyIri,
                                            )

                                          // Ensure that the ORDER BY property is one that we can sort by.
                                          if (
                                            !internalOrderByPropertyDef.isResourceProp || internalOrderByPropertyDef.isLinkProp || internalOrderByPropertyDef.isLinkValueProp || internalOrderByPropertyDef.isFileValueProp
                                          ) {
                                            throw BadRequestException(
                                              s"Cannot sort by property <$orderByProperty>",
                                            )
                                          }

                                          // Ensure that the resource class has a cardinality on the ORDER BY property.
                                          if (
                                            !classDef.knoraResourceProperties.contains(
                                              internalOrderByPropertyIri,
                                            )
                                          ) {
                                            throw BadRequestException(
                                              s"Class <$resourceClass> has no cardinality on property <$orderByProperty>",
                                            )
                                          }

                                          // Get the value class that's the object of the knora-base:objectClassConstraint of the ORDER BY property.
                                          val orderByValueType: SmartIri =
                                            internalOrderByPropertyDef.entityInfoContent
                                              .requireIriObject(
                                                OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri,
                                                throw InconsistentRepositoryDataException(
                                                  s"Property <$internalOrderByPropertyIri> has no knora-base:objectClassConstraint",
                                                ),
                                              )

                                          // Determine which subproperty of knora-base:valueHas corresponds to that value class.
                                          val orderByValuePredicate =
                                            orderByValueType.toString match {
                                              case OntologyConstants.KnoraBase.IntValue =>
                                                OntologyConstants.KnoraBase.ValueHasInteger
                                              case OntologyConstants.KnoraBase.DecimalValue =>
                                                OntologyConstants.KnoraBase.ValueHasDecimal
                                              case OntologyConstants.KnoraBase.BooleanValue =>
                                                OntologyConstants.KnoraBase.ValueHasBoolean
                                              case OntologyConstants.KnoraBase.DateValue =>
                                                OntologyConstants.KnoraBase.ValueHasStartJDN
                                              case OntologyConstants.KnoraBase.ColorValue =>
                                                OntologyConstants.KnoraBase.ValueHasColor
                                              case OntologyConstants.KnoraBase.GeonameValue =>
                                                OntologyConstants.KnoraBase.ValueHasGeonameCode
                                              case OntologyConstants.KnoraBase.IntervalValue =>
                                                OntologyConstants.KnoraBase.ValueHasIntervalStart
                                              case OntologyConstants.KnoraBase.UriValue =>
                                                OntologyConstants.KnoraBase.ValueHasUri
                                              case _ => OntologyConstants.KnoraBase.ValueHasString
                                            }

                                          Some(orderByValuePredicate.toSmartIri)

                                        case None => None
                                      }
                                    }

      // Do a SELECT prequery to get the IRIs of the requested page of resources.
      prequery = sparql.v2.txt
                   .getResourcesByClassInProjectPrequery(
                     projectIri = projectIri.value,
                     resourceClassIri = internalClassIri,
                     maybeOrderByProperty = maybeInternalOrderByPropertyIri,
                     maybeOrderByValuePredicate = maybeOrderByValuePredicate,
                     limit = appConfig.v2.resourcesSequence.resultsPerPage,
                     offset = page * appConfig.v2.resourcesSequence.resultsPerPage,
                   )
      sparqlSelectResponse      <- triplestore.query(Select(prequery))
      mainResourceIris: Seq[IRI] = sparqlSelectResponse.getColOrThrow("resource")

      // Find out whether to query standoff along with text values. This boolean value will be passed to
      // ConstructResponseUtilV2.makeTextValueContentV2.
      queryStandoff: Boolean = SchemaOptions.queryStandoffWithTextValues(
                                 targetSchema = ApiV2Complex,
                                 schemaOptions = schemaAndOptions.rendering,
                               )

      // Are there any matching resources?
      apiResponse <-
        if (mainResourceIris.nonEmpty) {
          // Yes. Do a CONSTRUCT query to get the contents of those resources. If we're querying standoff, get
          // at most one page of standoff per text value.
          val resourceRequestSparql =
            Construct(
              sparql.v2.txt
                .getResourcePropertiesAndValues(
                  resourceIris = mainResourceIris,
                  preview = false,
                  withDeleted = false,
                  queryAllNonStandoff = true,
                  queryStandoff = queryStandoff,
                  maybePropertyIri = None,
                  maybeVersionDate = None,
                ),
            )

          for {
            resourceRequestResponse <- triplestore.query(resourceRequestSparql).flatMap(_.asExtended)

            // separate resources and values
            mainResourcesAndValueRdfData = constructResponseUtilV2.splitMainResourcesAndValueRdfData(
                                             resourceRequestResponse,
                                             requestingUser,
                                           )

            // If we're querying standoff, get XML-to standoff mappings.
            mappings <-
              if (queryStandoff) {
                constructResponseUtilV2.getMappingsFromQueryResultsSeparated(
                  mainResourcesAndValueRdfData.resources,
                  requestingUser,
                )
              } else {
                ZIO.succeed(Map.empty[IRI, MappingAndXSLTransformation])
              }

            // Construct a ReadResourceV2 for each resource that the user has permission to see.
            readResourcesSequence <- constructResponseUtilV2.createApiResponse(
                                       mainResourcesAndValueRdfData = mainResourcesAndValueRdfData,
                                       orderByResourceIri = mainResourceIris,
                                       pageSizeBeforeFiltering = mainResourceIris.size,
                                       mappings = mappings,
                                       queryStandoff = queryStandoff,
                                       versionDate = None,
                                       calculateMayHaveMoreResults = true,
                                       targetSchema = schemaAndOptions.schema,
                                       requestingUser = requestingUser,
                                     )
          } yield readResourcesSequence
        } else {
          ZIO.succeed(ReadResourcesSequenceV2(Vector.empty[ReadResourceV2]))
        }
    } yield apiResponse
  }

  override def searchResourcesByLabelCountV2(
    searchValue: String,
    limitToProject: Option[ProjectIri],
    limitToResourceClass: Option[SmartIri],
  ): Task[ResourceCountV2] =
    for {
      searchValue          <- validateSearchString(searchValue)
      _                    <- ensureIsFulltextSearch(searchValue)
      limitToResourceClass <- ZIO.foreach(limitToResourceClass)(ensureResourceClassIri)
      searchTerm            = MatchStringWhileTyping(searchValue).generateLiteralForLuceneIndexWithoutExactSequence
      countSparql =
        SearchQueries.selectCountByLabel(searchTerm, limitToProject.map(_.value), limitToResourceClass.map(_.toString))
      countResponse <- triplestore.query(countSparql)

      count <- // query response should contain one result with one row with the name "count"
        ZIO
          .fail(
            GravsearchException(
              s"Fulltext count query is expected to return exactly one row, but ${countResponse.size} given",
            ),
          )
          .when(countResponse.size != 1)
          .as(countResponse.getFirstOrThrow("count"))

    } yield ResourceCountV2(count.toInt)

  private def ensureResourceClassIri(resourceClassIri: SmartIri): Task[SmartIri] = {
    val errMsg = s"Resource class IRI <$resourceClassIri> is not a valid Knora API v2 entity IRI"
    if (resourceClassIri.isKnoraApiV2EntityIri) {
      iriConverter.asInternalSmartIri(resourceClassIri).orElseFail(BadRequestException(errMsg))
    } else { ZIO.fail(BadRequestException(errMsg)) }
  }

  private def ensureStandoffClass(standoffClassIri: SmartIri): Task[SmartIri] = {
    val errMsg = s"Invalid standoff class IRI: $standoffClassIri"
    if (standoffClassIri.isApiV2ComplexSchema) {
      iriConverter.asInternalSmartIri(standoffClassIri).orElseFail(BadRequestException(errMsg))
    } else { ZIO.fail(BadRequestException(errMsg)) }
  }

  private def ensureIsFulltextSearch(searchStr: String) =
    ZIO
      .fail(BadRequestException("It looks like you are submitting a Gravsearch request to a full-text search route"))
      .when(searchStr.contains(OntologyConstants.KnoraApi.ApiOntologyHostname))

  private def validateSearchString(searchStr: String) = {
    val searchValueMinLength = appConfig.v2.fulltextSearch.searchValueMinLength
    ZIO
      .fromOption(Iri.toSparqlEncodedString(searchStr))
      .orElseFail(throw BadRequestException(s"Invalid search string: '$searchStr'"))
      .filterOrElseWith(_.length >= searchValueMinLength) { it =>
        val errorMsg =
          s"A search value is expected to have at least length of $searchValueMinLength, but '$it' given of length ${it.length}."
        ZIO.fail(BadRequestException(errorMsg))
      }
  }

  override def searchResourcesByLabelV2(
    searchValue: String,
    offset: Int,
    limitToProject: Option[ProjectIri],
    limitToResourceClass: Option[SmartIri],
    targetSchema: ApiV2Schema,
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2] = {
    val searchLimit  = appConfig.v2.resourcesSequence.resultsPerPage
    val searchOffset = offset * appConfig.v2.resourcesSequence.resultsPerPage
    for {
      searchValue          <- validateSearchString(searchValue)
      _                    <- ensureIsFulltextSearch(searchValue)
      limitToResourceClass <- ZIO.foreach(limitToResourceClass)(ensureResourceClassIri)
      searchTerm            = MatchStringWhileTyping(searchValue).generateLiteralForLuceneIndexWithoutExactSequence
      searchResourceByLabelSparql = SearchQueries.constructSearchByLabel(
                                      searchTerm,
                                      limitToResourceClass.map(_.toIri),
                                      limitToProject.map(_.value),
                                      searchLimit,
                                      searchOffset,
                                    )
      searchResourceByLabelResponse <- triplestore.query(searchResourceByLabelSparql).flatMap(_.asExtended)

      // collect the IRIs of main resources returned
      mainResourceIris <- ZIO.attempt {
                            searchResourceByLabelResponse.statements.foldLeft(Set.empty[IRI]) {
                              case (
                                    acc: Set[IRI],
                                    (subject: SubjectV2, assertions: Map[SmartIri, Seq[LiteralV2]]),
                                  ) =>
                                // check if the assertions represent a main resource and include its IRI if so
                                val subjectIsMainResource: Boolean =
                                  assertions
                                    .getOrElse(
                                      OntologyConstants.KnoraBase.IsMainResource.toSmartIri,
                                      Seq.empty,
                                    )
                                    .headOption match {
                                    case Some(BooleanLiteralV2(booleanVal)) => booleanVal
                                    case _                                  => false
                                  }

                                if (subjectIsMainResource) {
                                  val subjIri: IRI = subject match {
                                    case IriSubjectV2(value) => value
                                    case other =>
                                      throw InconsistentRepositoryDataException(
                                        s"Unexpected subject of resource: $other",
                                      )
                                  }

                                  acc + subjIri
                                } else {
                                  acc
                                }
                            }
                          }

      // separate resources and value objects
      mainResourcesAndValueRdfData =
        constructResponseUtilV2.splitMainResourcesAndValueRdfData(searchResourceByLabelResponse, requestingUser)
      apiResponse <- constructResponseUtilV2.createApiResponse(
                       mainResourcesAndValueRdfData = mainResourcesAndValueRdfData,
                       orderByResourceIri = mainResourceIris.toSeq.sorted,
                       pageSizeBeforeFiltering = mainResourceIris.size,
                       queryStandoff = false,
                       versionDate = None,
                       calculateMayHaveMoreResults = true,
                       targetSchema = targetSchema,
                       requestingUser = requestingUser,
                     )

    } yield apiResponse
  }

  /**
   * Given a prequery result, merges rows with the same main resource IRI. This could happen if there are unbound
   * variables in `GROUP_CONCAT` expressions.
   *
   * @param prequeryResponseNotMerged the prequery response before merging.
   * @param mainResourceVar           the name of the column representing the main resource.
   * @return the merged results.
   */
  private def mergePrequeryResults(
    prequeryResponseNotMerged: SparqlSelectResult,
    mainResourceVar: QueryVariable,
  ): SparqlSelectResult = {
    // Make a Map of merged results per main resource IRI.
    val prequeryRowsMergedMap: Map[IRI, VariableResultsRow] = prequeryResponseNotMerged.results.bindings.groupBy { row =>
      // Get the rows for each main resource IRI.
      row.rowMap(mainResourceVar.variableName)
    }.map { case (resourceIri: IRI, rows: Seq[VariableResultsRow]) =>
      // Make a Set of all the column names in the rows to be merged.
      val columnNamesToMerge: Set[String] = rows.flatMap(_.rowMap.keySet).toSet

      // Make a Map of column names to merged values.
      val mergedRowMap: Map[String, String] = columnNamesToMerge.map { columnName =>
        // For each column name, get the values to be merged.
        val columnValues: Seq[String] = rows.flatMap(_.rowMap.get(columnName))

        // Is this is the column containing the main resource IRI?
        val mergedColumnValue: String = if (columnName == mainResourceVar.variableName) {
          // Yes. Use that IRI as the merged value.
          resourceIri
        } else {
          // No. This must be a column resulting from GROUP_CONCAT, so use the GROUP_CONCAT
          // separator to concatenate the column values.
          columnValues.mkString(StringFormatter.INFORMATION_SEPARATOR_ONE.toString)
        }

        columnName -> mergedColumnValue
      }.toMap

      resourceIri -> VariableResultsRow(
        new ErrorHandlingMap(
          mergedRowMap,
          { (key: String) =>
            s"No value found for SPARQL query variable '$key' in query result row"
          },
        ),
      )
    }

    // Construct a sequence of the distinct main resource IRIs in the query results, preserving the
    // order of the result rows.
    val mainResourceIris: Seq[IRI] = prequeryResponseNotMerged.getColOrThrow(mainResourceVar.variableName).distinct

    // Arrange the merged rows in the same order.
    val prequeryRowsMerged: Seq[VariableResultsRow] = mainResourceIris.map { resourceIri =>
      prequeryRowsMergedMap(resourceIri)
    }

    prequeryResponseNotMerged.copy(
      results = SparqlSelectResultBody(prequeryRowsMerged),
    )
  }
}

object SearchResponderV2Live {
  val layer = ZLayer.derive[SearchResponderV2Live]
}
