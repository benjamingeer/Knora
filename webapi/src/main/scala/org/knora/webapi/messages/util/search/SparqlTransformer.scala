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

package org.knora.webapi.messages.util.search

import org.knora.webapi._
import org.knora.webapi.exceptions.{AssertionException, GravsearchException}
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.{OntologyConstants, SmartIri, StringFormatter}

/**
 * Methods and classes for transforming generated SPARQL.
 */
object SparqlTransformer {

    /**
     * Transforms a non-triplestore-specific SELECT query for GraphDB.
     *
     * @param useInference `true` if the triplestore's inference should be used.
     */
    class GraphDBSelectToSelectTransformer(useInference: Boolean) extends SelectToSelectTransformer {
        private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        override def transformStatementInSelect(statementPattern: StatementPattern): Seq[StatementPattern] = Seq(statementPattern)

        override def transformStatementInWhere(statementPattern: StatementPattern, inputOrderBy: Seq[OrderCriterion]): Seq[StatementPattern] = {
            transformKnoraExplicitToGraphDBExplicit(statement = statementPattern, useInference = useInference)
        }

        override def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = Seq(filterPattern)

        override def optimiseQueryPatterns(patterns: Seq[QueryPattern]): Seq[QueryPattern] = {
            moveBindToBeginning(moveResourceIrisToBeginning(moveLuceneToBeginning(patterns)))
        }

        override def transformLuceneQueryPattern(luceneQueryPattern: LuceneQueryPattern): Seq[QueryPattern] = {
            transformLuceneQueryPatternForGraphDB(luceneQueryPattern)
        }

        override def getFromClause: Option[FromClause] = {
            if (useInference) {
                None
            } else {
                // To turn off inference, add FROM <http://www.ontotext.com/explicit>.
                Some(FromClause(IriRef(OntologyConstants.NamedGraphs.GraphDBExplicitNamedGraph.toSmartIri)))
            }
        }
    }

    /**
     * Transforms a non-triplestore-specific SELECT for a triplestore that does not have inference enabled (e.g., Fuseki).
     *
     * @param simulateInference `true` if RDFS inference should be simulated using property path syntax.
     */
    class NoInferenceSelectToSelectTransformer(simulateInference: Boolean) extends SelectToSelectTransformer {
        private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        override def transformStatementInSelect(statementPattern: StatementPattern): Seq[StatementPattern] = Seq(statementPattern)

        override def transformStatementInWhere(statementPattern: StatementPattern, inputOrderBy: Seq[OrderCriterion]): Seq[StatementPattern] =
            transformStatementInWhereForNoInference(statementPattern = statementPattern, simulateInference = simulateInference)

        override def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = Seq(filterPattern)

        override def optimiseQueryPatterns(patterns: Seq[QueryPattern]): Seq[QueryPattern] = {
            moveBindToBeginning(moveIsDeletedToEnd(moveResourceIrisToBeginning(moveLuceneToBeginning(patterns))))
        }

        override def transformLuceneQueryPattern(luceneQueryPattern: LuceneQueryPattern): Seq[QueryPattern] =
            transformLuceneQueryPatternForFuseki(luceneQueryPattern)

        override def getFromClause: Option[FromClause] = None
    }

    /**
     * Transforms a non-triplestore-specific CONSTRUCT query for GraphDB.
     */
    class GraphDBConstructToConstructTransformer extends ConstructToConstructTransformer {
        private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        override def transformStatementInConstruct(statementPattern: StatementPattern): Seq[StatementPattern] = Seq(statementPattern)

        override def transformStatementInWhere(statementPattern: StatementPattern, inputOrderBy: Seq[OrderCriterion]): Seq[StatementPattern] = {
            transformKnoraExplicitToGraphDBExplicit(statement = statementPattern, useInference = true)
        }

        override def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = Seq(filterPattern)

        override def optimiseQueryPatterns(patterns: Seq[QueryPattern]): Seq[QueryPattern] = {
            moveBindToBeginning(moveResourceIrisToBeginning(moveLuceneToBeginning(patterns)))
        }

        override def transformLuceneQueryPattern(luceneQueryPattern: LuceneQueryPattern): Seq[QueryPattern] =
            transformLuceneQueryPatternForGraphDB(luceneQueryPattern)
    }

    /**
     * Transforms a non-triplestore-specific CONSTRUCT query for a triplestore that does not have inference enabled (e.g., Fuseki).
     */
    class NoInferenceConstructToConstructTransformer extends ConstructToConstructTransformer {
        private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        override def transformStatementInConstruct(statementPattern: StatementPattern): Seq[StatementPattern] = Seq(statementPattern)

        override def transformStatementInWhere(statementPattern: StatementPattern, inputOrderBy: Seq[OrderCriterion]): Seq[StatementPattern] =
            transformStatementInWhereForNoInference(statementPattern = statementPattern, simulateInference = true)

        override def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = Seq(filterPattern)

        override def optimiseQueryPatterns(patterns: Seq[QueryPattern]): Seq[QueryPattern] = {
            moveBindToBeginning(moveIsDeletedToEnd(moveResourceIrisToBeginning(moveLuceneToBeginning(patterns))))
        }

        override def transformLuceneQueryPattern(luceneQueryPattern: LuceneQueryPattern): Seq[QueryPattern] =
            transformLuceneQueryPatternForFuseki(luceneQueryPattern)
    }

    /**
     * Creates a syntactically valid variable base name, based on the given entity.
     *
     * @param entity the entity to be used to create a base name for a variable.
     * @return a base name for a variable.
     */
    def escapeEntityForVariable(entity: Entity): String = {
        val entityStr = entity match {
            case QueryVariable(varName) => varName
            case IriRef(iriLiteral, _) => iriLiteral.toOntologySchema(InternalSchema).toString
            case XsdLiteral(stringLiteral, _) => stringLiteral
            case _ => throw GravsearchException(s"A unique variable name could not be made for ${entity.toSparql}")
        }

        entityStr.replaceAll("[:/.#-]", "").replaceAll("\\s", "") // TODO: check if this is complete and if it could lead to collision of variable names
    }

    /**
     * Creates a unique variable name from the given entity and the local part of a property IRI.
     *
     * @param base        the entity to use to create the variable base name.
     * @param propertyIri the IRI of the property whose local part will be used to form the unique name.
     * @return a unique variable.
     */
    def createUniqueVariableNameFromEntityAndProperty(base: Entity, propertyIri: IRI): QueryVariable = {
        val propertyHashIndex = propertyIri.lastIndexOf('#')

        if (propertyHashIndex > 0) {
            val propertyName = propertyIri.substring(propertyHashIndex + 1)
            QueryVariable(escapeEntityForVariable(base) + "__" + escapeEntityForVariable(QueryVariable(propertyName)))
        } else {
            throw AssertionException(s"Invalid property IRI: $propertyIri")
        }
    }

    /**
     * Creates a unique variable name representing the `rdf:type` of an entity with a given base class.
     *
     * @param base         the entity to use to create the variable base name.
     * @param baseClassIri a base class of the entity's type.
     * @return a unique variable.
     */
    def createUniqueVariableNameForEntityAndBaseClass(base: Entity, baseClassIri: IriRef): QueryVariable = {
        QueryVariable(escapeEntityForVariable(base) + "__subClassOf__" + escapeEntityForVariable(baseClassIri))
    }

    /**
     * Create a unique variable from a whole statement.
     *
     * @param baseStatement the statement to be used to create the variable base name.
     * @param suffix        the suffix to be appended to the base name.
     * @return a unique variable.
     */
    def createUniqueVariableFromStatement(baseStatement: StatementPattern, suffix: String): QueryVariable = {
        QueryVariable(escapeEntityForVariable(baseStatement.subj) + "__" + escapeEntityForVariable(baseStatement.pred) + "__" + escapeEntityForVariable(baseStatement.obj) + "__" + suffix)
    }

    /**
     * Create a unique variable name from a whole statement for a link value.
     *
     * @param baseStatement the statement to be used to create the variable base name.
     * @return a unique variable for a link value.
     */
    def createUniqueVariableFromStatementForLinkValue(baseStatement: StatementPattern): QueryVariable = {
        createUniqueVariableFromStatement(baseStatement, "LinkValue")
    }

    /**
     * Optimises a query by moving `knora-base:isDeleted` to the end of a block.
     *
     * @param patterns the block of patterns to be optimised.
     * @return the result of the optimisation.
     */
    def moveIsDeletedToEnd(patterns: Seq[QueryPattern]): Seq[QueryPattern] = {
        val (isDeletedPatterns: Seq[QueryPattern], otherPatterns: Seq[QueryPattern]) = patterns.partition {
            case statementPattern: StatementPattern =>
                statementPattern.pred match {
                    case iriRef: IriRef if iriRef.iri.toString == OntologyConstants.KnoraBase.IsDeleted => true
                    case _ => false
                }

            case _ => false
        }

        otherPatterns ++ isDeletedPatterns
    }

    /**
     * Optimises a query by moving BIND patterns to the beginning of a block.
     *
     * @param patterns the block of patterns to be optimised.
     * @return the result of the optimisation.
     */
    def moveBindToBeginning(patterns: Seq[QueryPattern]): Seq[QueryPattern] = {
        val (bindQueryPatterns: Seq[QueryPattern], otherPatterns: Seq[QueryPattern]) = patterns.partition {
            case _: BindPattern => true
            case _ => false
        }

        bindQueryPatterns ++ otherPatterns
    }

    /**
     * Optimises a query by moving Lucene query patterns to the beginning of a block.
     *
     * @param patterns the block of patterns to be optimised.
     * @return the result of the optimisation.
     */
    def moveLuceneToBeginning(patterns: Seq[QueryPattern]): Seq[QueryPattern] = {
        val (luceneQueryPatterns: Seq[QueryPattern], otherPatterns: Seq[QueryPattern]) = patterns.partition {
            case _: LuceneQueryPattern => true
            case _ => false
        }

        luceneQueryPatterns ++ otherPatterns
    }


    /**
     * Optimises a query by moving statement patterns containing resource IRIs to the beginning of a block.
     *
     * @param patterns the query patterns to be optimised.
     * @return the optimised query patterns.
     */
    def moveResourceIrisToBeginning(patterns: Seq[QueryPattern]): Seq[QueryPattern] = {
        val (patternsWithResourceIris: Seq[QueryPattern], otherPatterns: Seq[QueryPattern]) = patterns.partition {
            case statementPattern: StatementPattern =>
                val subjIsResourceIri = statementPattern.subj match {
                    case iriRef: IriRef if iriRef.iri.isKnoraResourceIri => true
                    case _ => false
                }

                val objIsResourceIri = statementPattern.obj match {
                    case iriRef: IriRef if iriRef.iri.isKnoraResourceIri => true
                    case _ => false
                }

                subjIsResourceIri || objIsResourceIri

            case _ => false
        }

        patternsWithResourceIris ++ otherPatterns
    }

    /**
     * Transforms a statement in a WHERE clause for a triplestore that does not provide inference.
     *
     * @param statementPattern  the statement pattern.
     * @param simulateInference `true` if RDFS inference should be simulated using property path syntax.
     * @return the statement pattern as expanded to work without inference.
     */
    def transformStatementInWhereForNoInference(statementPattern: StatementPattern, simulateInference: Boolean): Seq[StatementPattern] = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        statementPattern.pred match {
            case iriRef: IriRef if iriRef.iri.toString == OntologyConstants.KnoraBase.StandoffTagHasStartAncestor =>
                // Simulate knora-api:standoffTagHasStartAncestor, using knora-api:standoffTagHasStartParent.
                Seq(
                    statementPattern.copy(
                        pred = IriRef(OntologyConstants.KnoraBase.StandoffTagHasStartParent.toSmartIri, Some('*'))
                    )
                )

            case _ =>
                // Is the statement in KnoraExplicitNamedGraph?
                statementPattern.namedGraph match {
                    case Some(graphIri: IriRef) if graphIri.iri.toString == OntologyConstants.NamedGraphs.KnoraExplicitNamedGraph =>
                        // Yes. No expansion needed. Just remove KnoraExplicitNamedGraph.
                        Seq(statementPattern.copy(namedGraph = None))

                    case _ =>
                        // Is inference enabled?
                        if (simulateInference) {
                            // Yes. The statement might need to be expanded. Is the predicate a property IRI?
                            statementPattern.pred match {
                                case iriRef: IriRef =>
                                    // Yes.
                                    val propertyIri = iriRef.iri.toString

                                    // Is the property rdf:type?
                                    if (propertyIri == OntologyConstants.Rdf.Type) {
                                        // Yes. Expand using rdfs:subClassOf*.

                                        val baseClassIri: IriRef = statementPattern.obj match {
                                            case iriRef: IriRef => iriRef
                                            case other => throw GravsearchException(s"The object of rdf:type must be an IRI, but $other was used")
                                        }

                                        val rdfTypeVariable: QueryVariable = createUniqueVariableNameForEntityAndBaseClass(base = statementPattern.subj, baseClassIri = baseClassIri)

                                        Seq(
                                            StatementPattern(
                                                subj = rdfTypeVariable,
                                                pred = IriRef(
                                                    iri = OntologyConstants.Rdfs.SubClassOf.toSmartIri,
                                                    propertyPathOperator = Some('*')
                                                ),
                                                obj = statementPattern.obj
                                            ),
                                            StatementPattern(
                                                subj = statementPattern.subj,
                                                pred = statementPattern.pred,
                                                obj = rdfTypeVariable
                                            )
                                        )
                                    } else {
                                        // No. Expand using rdfs:subPropertyOf*.

                                        val propertyVariable: QueryVariable = createUniqueVariableNameFromEntityAndProperty(base = statementPattern.pred, propertyIri = OntologyConstants.Rdfs.SubPropertyOf)

                                        Seq(
                                            StatementPattern(
                                                subj = propertyVariable,
                                                pred = IriRef(
                                                    iri = OntologyConstants.Rdfs.SubPropertyOf.toSmartIri,
                                                    propertyPathOperator = Some('*')
                                                ),
                                                obj = statementPattern.pred
                                            ),
                                            StatementPattern(
                                                subj = statementPattern.subj,
                                                pred = propertyVariable,
                                                obj = statementPattern.obj
                                            )
                                        )
                                    }

                                case _ =>
                                    // The predicate isn't a property IRI, so no expansion needed.
                                    Seq(statementPattern)
                            }
                        } else {
                            // Inference is disabled. Just return the statement as is.
                            Seq(statementPattern)
                        }
                }
        }
    }

    /**
     * Transforms the the Knora explicit graph name to GraphDB explicit graph name.
     *
     * @param statement    the given statement whose graph name has to be renamed.
     * @param useInference `true` if the triplestore's inference should be used.
     * @return the statement with the renamed graph, if given.
     */
    private def transformKnoraExplicitToGraphDBExplicit(statement: StatementPattern, useInference: Boolean): Seq[StatementPattern] = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val transformedPattern = statement.copy(
            pred = statement.pred,
            namedGraph = statement.namedGraph match {
                case Some(IriRef(SmartIri(OntologyConstants.NamedGraphs.KnoraExplicitNamedGraph), _)) =>
                    if (useInference) {
                        Some(IriRef(OntologyConstants.NamedGraphs.GraphDBExplicitNamedGraph.toSmartIri))
                    } else {
                        // Inference will be turned off in a FROM clause, so there's no need to specify a named graph here.
                        None
                    }

                case Some(IriRef(_, _)) => throw AssertionException(s"Named graphs other than ${OntologyConstants.NamedGraphs.KnoraExplicitNamedGraph} cannot occur in non-triplestore-specific generated search query SPARQL")

                case None => None
            }
        )

        Seq(transformedPattern)
    }

    /**
     * Transforms a [[LuceneQueryPattern]] for GraphDB.
     *
     * @param luceneQueryPattern the query pattern.
     * @return GraphDB-specific statements implementing the query.
     */
    private def transformLuceneQueryPatternForGraphDB(luceneQueryPattern: LuceneQueryPattern): Seq[QueryPattern] = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        Seq(
            StatementPattern(
                subj = luceneQueryPattern.obj, // In GraphDB, an index entry is associated with a literal.
                pred = IriRef("http://www.ontotext.com/owlim/lucene#fullTextSearchIndex".toSmartIri),
                obj = XsdLiteral(
                    value = luceneQueryPattern.queryString.getQueryString,
                    datatype = OntologyConstants.Xsd.String.toSmartIri
                )
            )
        ) ++ luceneQueryPattern.literalStatement
    }

    /**
     * Transforms a [[LuceneQueryPattern]] for Fuseki.
     *
     * @param luceneQueryPattern the query pattern.
     * @return Fuseki-specific statements implementing the query.
     */
    private def transformLuceneQueryPatternForFuseki(luceneQueryPattern: LuceneQueryPattern): Seq[QueryPattern] = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        Seq(
            StatementPattern(
                subj = luceneQueryPattern.subj, // In Fuseki, an index entry is associated with an entity that has a literal.
                pred = IriRef("http://jena.apache.org/text#query".toSmartIri),
                obj = XsdLiteral(
                    value = luceneQueryPattern.queryString.getQueryString,
                    datatype = OntologyConstants.Xsd.String.toSmartIri
                )
            )
        )
    }
}
