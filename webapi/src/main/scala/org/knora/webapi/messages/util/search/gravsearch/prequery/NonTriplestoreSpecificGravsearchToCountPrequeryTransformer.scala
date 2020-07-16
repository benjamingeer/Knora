/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 *  This file is part of Knora.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.messages.util.search.gravsearch.prequery

import org.knora.webapi.ApiV2Schema
import org.knora.webapi.messages.util.search.gravsearch.types.GravsearchTypeInspectionResult
import org.knora.webapi.messages.util.search._

/**
 * Transforms a preprocessed CONSTRUCT query into a SELECT query that returns only the IRIs and sort order of the main resources that matched
 * the search criteria. This query will be used to get resource IRIs for a single page of results. These IRIs will be included in a CONSTRUCT
 * query to get the actual results for the page.
 *
 * @param constructClause      the CONSTRUCT clause from the input query.
 * @param typeInspectionResult the result of type inspection of the input query.
 * @param querySchema          the ontology schema used in the input query.
 */
class NonTriplestoreSpecificGravsearchToCountPrequeryTransformer(constructClause: ConstructClause,
                                                                 typeInspectionResult: GravsearchTypeInspectionResult,
                                                                 querySchema: ApiV2Schema)
    extends AbstractPrequeryGenerator(constructClause = constructClause,
        typeInspectionResult = typeInspectionResult,
        querySchema = querySchema) with ConstructToSelectTransformer {

    def transformStatementInWhere(statementPattern: StatementPattern, inputOrderBy: Seq[OrderCriterion]): Seq[QueryPattern] = {

        // Include any statements needed to meet the user's search criteria, but not statements that would be needed for permission checking or
        // other information about the matching resources or values.

        processStatementPatternFromWhereClause(
            statementPattern = statementPattern,
            inputOrderBy = inputOrderBy
        )

    }

    def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = {
        val filterExpression: TransformedFilterPattern = transformFilterPattern(filterPattern.expression, typeInspectionResult = typeInspectionResult, isTopLevel = true)

        filterExpression.expression match {
            case Some(expression: Expression) => filterExpression.additionalPatterns :+ FilterPattern(expression)

            case None => filterExpression.additionalPatterns // no FILTER expression given
        }

    }

    def getSelectColumns: Seq[SelectQueryColumn] = {
        // return count aggregation function for main variable
        Seq(Count(inputVariable = mainResourceVariable, distinct = true, outputVariableName = "count"))
    }

    def getGroupBy(orderByCriteria: TransformedOrderBy): Seq[QueryVariable] = {
        Seq.empty[QueryVariable]
    }

    def getOrderBy(inputOrderBy: Seq[OrderCriterion]): TransformedOrderBy = {
        // empty by default
        TransformedOrderBy()
    }

    def getLimit: Int = 1 // one row expected for count query

    def getOffset(inputQueryOffset: Long, limit: Int): Long = {
        // count queries do not consider offsets since there is only one result row
        0
    }

    override def optimiseQueryPatternOrder(patterns: Seq[QueryPattern]): Seq[QueryPattern] = patterns

    override def transformLuceneQueryPattern(luceneQueryPattern: LuceneQueryPattern): Seq[QueryPattern] = Seq(luceneQueryPattern)
}
