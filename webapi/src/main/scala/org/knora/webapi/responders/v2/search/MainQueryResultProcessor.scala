/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.responders.v2.search

import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages.{SparqlSelectResponse, VariableResultsRow}
import org.knora.webapi.responders.v2.search.gravsearch.mainquery.GravsearchMainQueryGenerator
import org.knora.webapi.responders.v2.search.gravsearch.prequery.{AbstractPrequeryGenerator, NonTriplestoreSpecificGravsearchToPrequeryGenerator}
import org.knora.webapi.responders.v2.search.gravsearch.types.GravsearchTypeInspectionResult
import org.knora.webapi.util.ConstructResponseUtilV2.{RdfPropertyValues, RdfResources, ResourceWithValueRdfData, ValueRdfData}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.{ConstructResponseUtilV2, ErrorHandlingMap, SmartIri, StringFormatter}

object MainQueryResultProcessor {

    /**
      * Represents the IRIs of resources and value objects.
      *
      * @param resourceIris    resource IRIs.
      * @param valueObjectIris value object IRIs.
      */
    case class ResourceIrisAndValueObjectIris(resourceIris: Set[IRI], valueObjectIris: Set[IRI])

    /**
      * Traverses value property assertions and returns the IRIs of the value objects and the dependent resources, recursively traversing their value properties as well.
      * This is method is needed in order to determine if the whole graph pattern is still present in the results after permissions checking handled in [[ConstructResponseUtilV2.splitMainResourcesAndValueRdfData]].
      * Due to insufficient permissions, some of the resources (both main and dependent resources) and/or values may have been filtered out.
      *
      * @param valuePropertyAssertions the assertions to be traversed.
      * @return a [[ResourceIrisAndValueObjectIris]] representing all resource and value object IRIs that have been found in `valuePropertyAssertions`.
      */
    def collectResourceIrisAndValueObjectIrisFromMainQueryResult(valuePropertyAssertions: RdfPropertyValues)(implicit stringFormatter: StringFormatter): ResourceIrisAndValueObjectIris = {

        // look at the value objects and ignore the property IRIs (we are only interested in value instances)
        val resAndValObjIris: Seq[ResourceIrisAndValueObjectIris] = valuePropertyAssertions.values.flatten.foldLeft(Seq.empty[ResourceIrisAndValueObjectIris]) {
            (acc: Seq[ResourceIrisAndValueObjectIris], valueRdfData: ValueRdfData) =>

                if (valueRdfData.nestedResource.nonEmpty) {
                    // this is a link value
                    // recursively traverse the dependent resource's values

                    val dependentRes: ResourceWithValueRdfData = valueRdfData.nestedResource.get

                    // recursively traverse the link value's nested resource and its assertions
                    val resAndValObjIrisForDependentRes: ResourceIrisAndValueObjectIris = collectResourceIrisAndValueObjectIrisFromMainQueryResult(dependentRes.valuePropertyAssertions)

                    // get the dependent resource's IRI from the current link value's rdf:object, or rdf:subject in case of an incoming link
                    val dependentResIri: IRI = if (valueRdfData.isIncomingLink) {
                        valueRdfData.requireIriObject(OntologyConstants.Rdf.Subject.toSmartIri)
                    } else {
                        valueRdfData.requireIriObject(OntologyConstants.Rdf.Object.toSmartIri)
                    }

                    // append results from recursion and current value object
                    ResourceIrisAndValueObjectIris(
                        resourceIris = resAndValObjIrisForDependentRes.resourceIris + dependentResIri,
                        valueObjectIris = resAndValObjIrisForDependentRes.valueObjectIris + valueRdfData.subjectIri
                    ) +: acc
                } else {
                    // not a link value or no dependent resource given (in order to avoid infinite recursion)
                    // no dependent resource present
                    // append results for current value object
                    ResourceIrisAndValueObjectIris(
                        resourceIris = Set.empty[IRI],
                        valueObjectIris = Set(valueRdfData.subjectIri)
                    ) +: acc
                }
        }

        // convert the collection of `ResourceIrisAndValueObjectIris` into one
        ResourceIrisAndValueObjectIris(
            resourceIris = resAndValObjIris.flatMap(_.resourceIris).toSet,
            valueObjectIris = resAndValObjIris.flatMap(_.valueObjectIris).toSet
        )

    }


    /**
      * Collects the Iris of dependent resources per main resource from the results returned by the prequery.
      * Dependent resource Iris are grouped by main resource.
      *
      * @param prequeryResponse the results returned by the prequery.
      * @param transformer      the transformer that was used to turn the Gravsearch query into the prequery.
      * @param mainResourceVar  the variable representing the main resource.
      * @return a [[DependentResourcesPerMainResource]].
      */
    def getDependentResourceIrisPerMainResource(prequeryResponse: SparqlSelectResponse,
                                                transformer: NonTriplestoreSpecificGravsearchToPrequeryGenerator,
                                                mainResourceVar: QueryVariable): DependentResourcesPerMainResource = {

        // variables representing dependent resources
        val dependentResourceVariablesGroupConcat: Set[QueryVariable] = transformer.getDependentResourceVariablesGroupConcat

        val dependentResourcesPerMainRes = prequeryResponse.results.bindings.foldLeft(Map.empty[IRI, Set[IRI]]) {
            case (acc: Map[IRI, Set[IRI]], resultRow: VariableResultsRow) =>
                // collect all the dependent resource Iris for the current main resource from prequery's response

                // the main resource's Iri
                val mainResIri: String = resultRow.rowMap(mainResourceVar.variableName)

                // get the Iris of all the dependent resources for the given main resource
                val dependentResIris: Set[IRI] = dependentResourceVariablesGroupConcat.flatMap {
                    dependentResVar: QueryVariable =>

                        // check if key exists: the variable representing dependent resources
                        // could be contained in an OPTIONAL or a UNION and be unbound
                        // It would be suppressed by `VariableResultsRow` in that case.
                        //
                        // Example: the query contains a dependent resource variable ?book within an OPTIONAL or a UNION.
                        // If the query returns results for the dependent resource ?book (Iris of resources that match the given criteria),
                        // those would be accessible via the variable ?book__Concat containing the aggregated results (Iris).
                        val dependentResIriOption: Option[IRI] = resultRow.rowMap.get(dependentResVar.variableName)

                        dependentResIriOption match {
                            case Some(depResIri: IRI) =>

                                // IRIs are concatenated by GROUP_CONCAT using a separator, split them. Ignore empty
                                // strings, which could result from unbound variables in the query.
                                depResIri.split(AbstractPrequeryGenerator.groupConcatSeparator).toSeq.filter(_.nonEmpty)

                            case None => Set.empty[IRI] // no Iri present since variable was inside aan OPTIONAL or UNION
                        }

                }

                acc + (mainResIri -> dependentResIris)
        }

        DependentResourcesPerMainResource(new ErrorHandlingMap(dependentResourcesPerMainRes, { key => throw GravsearchException(s"main resource not found: $key") }))
    }

    /**
      * Collects object variables and their values per main resource from the results returned by the prequery.
      * Value objects variables and their Iris are grouped by main resource.
      *
      * @param prequeryResponse the results returned by the prequery.
      * @param transformer      the transformer that was used to turn the Gravsearch query into the prequery.
      * @param mainResourceVar  the variable representing the main resource.
      * @return [[ValueObjectVariablesAndValueObjectIris]].
      */
    def getValueObjectVarsAndIrisPerMainResource(prequeryResponse: SparqlSelectResponse,
                                                 transformer: NonTriplestoreSpecificGravsearchToPrequeryGenerator,
                                                 mainResourceVar: QueryVariable): ValueObjectVariablesAndValueObjectIris = {

        // value objects variables present in the prequery's WHERE clause
        val valueObjectVariablesConcat = transformer.getValueObjectVarsGroupConcat

        val valueObjVarsAndIris: Map[IRI, Map[QueryVariable, Set[IRI]]] = prequeryResponse.results.bindings.foldLeft(Map.empty[IRI, Map[QueryVariable, Set[IRI]]]) {
            (acc: Map[IRI, Map[QueryVariable, Set[IRI]]], resultRow: VariableResultsRow) =>

                // the main resource's Iri
                val mainResIri: String = resultRow.rowMap(mainResourceVar.variableName)

                // the the variables representing value objects and their Iris
                val valueObjVarToIris: Map[QueryVariable, Set[IRI]] = valueObjectVariablesConcat.map {
                    valueObjVarConcat: QueryVariable =>

                        // check if key exists: the variable representing value objects
                        // could be contained in an OPTIONAL or a UNION and be unbound
                        // It would be suppressed by `VariableResultsRow` in that case.

                        // this logic works like in the case of dependent resources, see `getDependentResourceIrisPerMainResource` above.
                        val valueObjIrisOption: Option[IRI] = resultRow.rowMap.get(valueObjVarConcat.variableName)

                        val valueObjIris: Set[IRI] = valueObjIrisOption match {

                            case Some(valObjIris) =>

                                // IRIs are concatenated by GROUP_CONCAT using a separator, split them. Ignore empty
                                // strings, which could result from unbound variables in the query.
                                valObjIris.split(AbstractPrequeryGenerator.groupConcatSeparator).toSet.filter(_.nonEmpty)

                            case None => Set.empty[IRI] // since variable was inside aan OPTIONAL or UNION

                        }

                        valueObjVarConcat -> valueObjIris
                }.toMap

                val valueObjVarToIrisErrorHandlingMap = new ErrorHandlingMap(valueObjVarToIris, { key: QueryVariable => throw GravsearchException(s"variable not found: $key") })
                acc + (mainResIri -> valueObjVarToIrisErrorHandlingMap)
        }

        ValueObjectVariablesAndValueObjectIris(new ErrorHandlingMap(valueObjVarsAndIris, { key => throw GravsearchException(s"main resource not found: $key") }))
    }

    /**
      * Given the results of the main query, filters out all values that the user did not ask for in the input query,
      * i.e that are not present in its CONSTRUCT clause.
      *
      * @param queryResultsWithFullGraphPattern        results with full graph pattern (that user has sufficient permissions on).
      * @param valueObjectVarsAndIrisPerMainResource   value object variables and their Iris per main resource.
      * @param allResourceVariablesFromTypeInspection  all variables representing resources.
      * @param dependentResourceIrisFromTypeInspection Iris of dependent resources used in the input query.
      * @param transformer                             the transformer that was used to turn the input query into the prequery.
      * @param typeInspectionResult                    results of type inspection of the input query.
      * @return results with only the values the user asked for in the input query's CONSTRUCT clause.
      */
    def getRequestedValuesFromResultsWithFullGraphPattern(queryResultsWithFullGraphPattern: RdfResources,
                                                          valueObjectVarsAndIrisPerMainResource: ValueObjectVariablesAndValueObjectIris,
                                                          allResourceVariablesFromTypeInspection: Set[QueryVariable],
                                                          dependentResourceIrisFromTypeInspection: Set[IRI],
                                                          transformer: NonTriplestoreSpecificGravsearchToPrequeryGenerator,
                                                          typeInspectionResult: GravsearchTypeInspectionResult,
                                                          inputQuery: ConstructQuery): RdfResources = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        // sort out those value objects that the user did not ask for in the input query's CONSTRUCT clause
        // those are present in the input query's WHERE clause but not in its CONSTRUCT clause

        // for each resource variable (both main and dependent resources),
        // collect the value object variables associated with it in the input query's CONSTRUCT clause
        // resource variables from types inspection are used
        //
        // Example: the statement "?page incunabula:seqnum ?seqnum ." is contained in the input query's CONSTRUCT clause.
        // ?seqnum (?seqnum__Concat) is a requested value and is associated with the resource variable ?page.
        val requestedValueObjectVariablesForAllResVars: Set[QueryVariable] = allResourceVariablesFromTypeInspection.flatMap {
            resVar =>
                GravsearchMainQueryGenerator.collectValueVariablesForResource(inputQuery.constructClause, resVar, typeInspectionResult, transformer.groupConcatVariableSuffix)
        }

        // for each resource Iri (only dependent resources),
        // collect the value object variables associated with it in the input query's CONSTRUCT clause
        // dependent resource Iris from types inspection are used
        //
        // Example: the statement "<http://rdfh.ch/5e77e98d2603> incunabula:title ?title ." is contained in the input query's CONSTRUCT clause.
        // ?title (?title__Concat) is a requested value and is associated with the dependent resource Iri <http://rdfh.ch/5e77e98d2603>.
        val requestedValueObjectVariablesForDependentResIris: Set[QueryVariable] = dependentResourceIrisFromTypeInspection.flatMap {
            depResIri =>
                GravsearchMainQueryGenerator.collectValueVariablesForResource(inputQuery.constructClause, IriRef(iri = depResIri.toSmartIri), typeInspectionResult, transformer.groupConcatVariableSuffix)
        }

        // combine all value object variables into one set
        val allRequestedValueObjectVariables: Set[QueryVariable] = requestedValueObjectVariablesForAllResVars ++ requestedValueObjectVariablesForDependentResIris

        // collect requested value object Iris for each main resource
        val requestedValObjIrisPerMainResource: Map[IRI, Set[IRI]] = queryResultsWithFullGraphPattern.keySet.map {
            mainResIri =>

                // get all value object variables and Iris for the current main resource
                val valueObjIrisForRes: Map[QueryVariable, Set[IRI]] = valueObjectVarsAndIrisPerMainResource.valueObjectVariablesAndValueObjectIris(mainResIri)

                // get those value object Iris from the results that the user asked for in the input query's CONSTRUCT clause
                val valObjIrisRequestedForRes: Set[IRI] = allRequestedValueObjectVariables.flatMap {
                    requestedQueryVar: QueryVariable =>
                        valueObjIrisForRes.getOrElse(requestedQueryVar, throw AssertionException(s"key $requestedQueryVar is absent in prequery's value object IRIs collection for resource $mainResIri"))
                }

                mainResIri -> valObjIrisRequestedForRes
        }.toMap

        // for each main resource, get only the requested value objects
        queryResultsWithFullGraphPattern.map {
            case (mainResIri: IRI, assertions: ResourceWithValueRdfData) =>

                // get the Iris of all the value objects requested for the current main resource
                val valueObjIrisRequestedForRes: Set[IRI] = requestedValObjIrisPerMainResource.getOrElse(mainResIri, throw AssertionException(s"key $mainResIri is absent in requested value object IRIs collection for resource $mainResIri"))

                /**
                  * Recursively filters out those values that the user does not want to see.
                  * Starts with the values of the main resource and also processes link values, possibly containing dependent resources with values.
                  *
                  * @param values the values to be filtered.
                  * @return filtered values.
                  */
                def traverseAndFilterValues(values: ResourceWithValueRdfData): RdfPropertyValues = {
                    values.valuePropertyAssertions.foldLeft(ConstructResponseUtilV2.emptyRdfPropertyValues) {
                        case (acc, (propIri: SmartIri, values: Seq[ValueRdfData])) =>

                            // filter values for the current resource
                            val valuesFiltered: Seq[ValueRdfData] = values.filter {
                                valueObj: ValueRdfData =>
                                    // only return those value objects whose Iris are contained in valueObjIrisRequestedForRes
                                    valueObjIrisRequestedForRes(valueObj.subjectIri)
                            }

                            // if there are link values including a target resource, apply filter to their values too
                            val valuesFilteredRecursively: Seq[ValueRdfData] = valuesFiltered.map {
                                valObj: ValueRdfData =>
                                    if (valObj.nestedResource.nonEmpty) {

                                        val targetResourceAssertions: ResourceWithValueRdfData = valObj.nestedResource.get

                                        // apply filter to the target resource's values
                                        val targetResourceAssertionsFiltered: RdfPropertyValues = traverseAndFilterValues(targetResourceAssertions)

                                        valObj.copy(
                                            nestedResource = Some(targetResourceAssertions.copy(
                                                valuePropertyAssertions = targetResourceAssertionsFiltered
                                            ))
                                        )
                                    } else {
                                        valObj
                                    }
                            }

                            // ignore properties if there are no value objects to be displayed.
                            // if the user does not want to see a value, the property pointing to that value has to be ignored.
                            if (valuesFilteredRecursively.nonEmpty) {
                                acc + (propIri -> valuesFilteredRecursively)
                            } else {
                                // ignore this property since there are no value objects
                                // Example: the input query's WHERE clause contains the statement "?page incunabula:seqnum ?seqnum .",
                                // but the statement is not present in its CONSTRUCT clause. Therefore, the property incunabula:seqnum can be ignored
                                // since no value objects are returned for it.
                                acc
                            }
                    }
                }

                // filter values for the current main resource
                val requestedValuePropertyAssertions: RdfPropertyValues = traverseAndFilterValues(assertions)

                // only return the requested values for the current main resource
                mainResIri -> assertions.copy(
                    valuePropertyAssertions = requestedValuePropertyAssertions
                )
        }
    }

    /**
      * Represents dependent resources organized by main resource.
      *
      * @param dependentResourcesPerMainResource a set of dependent resource Iris organized by main resource.
      */
    case class DependentResourcesPerMainResource(dependentResourcesPerMainResource: Map[IRI, Set[IRI]])

    /**
      * Represents value object variables and value object Iris organized by main resource.
      *
      * @param valueObjectVariablesAndValueObjectIris a set of value object Iris organized by value object variable and main resource.
      */
    case class ValueObjectVariablesAndValueObjectIris(valueObjectVariablesAndValueObjectIris: Map[IRI, Map[QueryVariable, Set[IRI]]])

}
