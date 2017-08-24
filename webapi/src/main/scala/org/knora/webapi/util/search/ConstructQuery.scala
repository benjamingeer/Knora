/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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

package org.knora.webapi.util.search

import org.knora.webapi._
import org.knora.webapi.util.InputValidation

/**
  * Represents something that can generate SPARQL source code.
  */
sealed trait SparqlGenerator {
    def toSparql: String
}

/**
  * Represents something that can be the subject, predicate, or object of a triple pattern in a query.
  */
sealed trait Entity extends Expression

/**
  * Represents a variable in a query.
  *
  * @param variableName the name of the variable.
  */
case class QueryVariable(variableName: String) extends Entity {
    def toSparql: String = s"?$variableName"
}

/**
  * Represents an IRI in a query.
  *
  * @param iri the IRI.
  */
case class IriRef(iri: IRI) extends Entity {
    val isInternalEntityIri: Boolean = InputValidation.isInternalEntityIri(iri)
    val isApiEntityIri = InputValidation.isKnoraApiEntityIri(iri)
    val isEntityIri: Boolean = isApiEntityIri || isInternalEntityIri

    /**
      * If this is a knora-api entity IRI, converts it to an internal entity IRI.
      *
      * @return the equivalent internal entity IRI.
      */
    def toInternalEntityIri: IriRef = {
        if (isInternalEntityIri) {
            IriRef(InputValidation.externalIriToInternalIri(iri, () => throw BadRequestException(s"$iri is not a valid external knora-api entity Iri")))
        } else {
            throw AssertionException("$iri is not a knora-api entity IRI")
        }
    }

    def toSparql: String = s"<$iri>"
}

/**
  * Represents a literal value with an XSD type.
  *
  * @param value    the literal value.
  * @param datatype the value's XSD type IRI.
  */
case class XsdLiteral(value: String, datatype: IRI) extends Entity {
    def toSparql: String = "\"" + value + "\"^^<" + datatype + ">"
}

/**
  * Represents a statement pattern or block pattern in a query.
  */
sealed trait QueryPattern extends SparqlGenerator

/**
  * Represents a statement pattern in a query.
  *
  * @param subj the subject of the statement.
  * @param pred the predicate of the statement.
  * @param obj  the object of the statement.
  * @param namedGraph the named graph this statement should be searched in.
  * @param includeInConstructClause indicates whether this statement should be included in the Construct clause of the query. Some statements are only needed in the Where clause.
  *
  */
case class StatementPattern(subj: Entity, pred: Entity, obj: Entity, namedGraph: Option[IriRef] = None, includeInConstructClause: Boolean = true) extends QueryPattern {
    def toSparql: String = {
        val triple = s"${subj.toSparql} ${pred.toSparql} ${obj.toSparql} ."

        namedGraph match {
            case Some(graph) =>
                s"""GRAPH ${graph.toSparql} {
                   |    $triple
                   |}
                   |""".stripMargin

            case None =>
                triple + "\n"
        }
    }

    /**
      * A convenience function that returns a copy of this statement pattern, with its named graph set to
      * `http://www.knora.org/explicit`, indicating that inference should be disabled for the pattern.
      *
      * @return a copy of this statement pattern with its named graph set to `http://www.knora.org/explicit`.
      */
    def toKnoraExplicit: StatementPattern = copy(namedGraph = Some(IriRef(OntologyConstants.NamedGraphs.KnoraExplicitNamedGraph)))
}

/**
  * Represents the supported logical operators in a [[CompareExpression]].
  */
object CompareExpressionOperator extends Enumeration {

    val EQUALS = Value("=")

    val GREATERTHAN = Value(">")

    val GREATERTHANEQUALS = Value(">=")

    val LOWERTHAN = Value("<")

    val LOWERTHANEQUALS = Value("<=")

    val NOTEQUALS = Value("!=")

    val valueMap: Map[String, Value] = values.map(v => (v.toString, v)).toMap

    /**
      * Given the name of a value in this enumeration, returns the value. If the value is not found, the provided error function is called.
      *
      * @param name     the name of the value.
      * @param errorFun the function to be called in case of an error.
      * @return the requested value.
      */
    def lookup(name: String, errorFun: () => Nothing): Value = {
        valueMap.get(name) match {
            case Some(value) => value
            case None => errorFun()
        }
    }
}

/**
  * Represents an expression that can be used in a FILTER.
  */
sealed trait Expression extends SparqlGenerator

/**
  * Represents a comparison expression in a FILTER.
  *
  * @param leftArg  the left argument.
  * @param operator the operator.
  * @param rightArg the right argument.
  */
case class CompareExpression(leftArg: Expression, operator: CompareExpressionOperator.Value, rightArg: Expression) extends Expression {
    def toSparql: String = s"(${leftArg.toSparql} $operator ${rightArg.toSparql})"
}

/**
  * Represents an AND expression in a filter.
  *
  * @param leftArg  the left argument.
  * @param rightArg the right argument.
  */
case class AndExpression(leftArg: Expression, rightArg: Expression) extends Expression {
    def toSparql: String = s"(${leftArg.toSparql} && ${rightArg.toSparql})"
}

/**
  * Represents an OR expression in a filter.
  *
  * @param leftArg  the left argument.
  * @param rightArg the right argument.
  */
case class OrExpression(leftArg: Expression, rightArg: Expression) extends Expression {
    def toSparql: String = s"(${leftArg.toSparql} || ${rightArg.toSparql})"
}

/**
  * Represents a FILTER pattern in a query.
  *
  * @param expression the expression in the FILTER.
  */
case class FilterPattern(expression: Expression) extends QueryPattern {
    def toSparql: String = s"FILTER(${expression.toSparql})\n"
}

/**
  * Represents a UNION in the WHERE clause of a query.
  *
  * @param blocks the blocks of patterns contained in the UNION.
  */
case class UnionPattern(blocks: Seq[Seq[QueryPattern]]) extends QueryPattern {
    def toSparql: String = {
        val blocksAsStrings = blocks.map {
            block: Seq[QueryPattern] =>
                val queryPatternStrings: Seq[String] = block.map {
                    queryPattern: QueryPattern => queryPattern.toSparql
                }

                queryPatternStrings.mkString
        }

        "{\n" + blocksAsStrings.mkString("} UNION {\n") + "}\n"
    }
}

/**
  * Represents an OPTIONAL in the WHERE clause of a query.
  *
  * @param patterns the patterns in the OPTIONAL block.
  */
case class OptionalPattern(patterns: Seq[QueryPattern]) extends QueryPattern {
    def toSparql: String = {
        val queryPatternStrings: Seq[String] = patterns.map {
            queryPattern: QueryPattern => queryPattern.toSparql
        }

        "OPTIONAL {\n" + queryPatternStrings.mkString + "}\n"
    }
}

/**
  * Represents a CONSTRUCT clause in a query.
  *
  * @param statements the statements in the CONSTRUCT clause.
  */
case class ConstructClause(statements: Seq[StatementPattern]) extends SparqlGenerator {
    def toSparql: String = "CONSTRUCT {\n" + statements.map(_.toSparql).mkString + "} "
}

/**
  * Represents a WHERE clause in a query.
  *
  * @param patterns the patterns in the WHERE clause.
  */
case class WhereClause(patterns: Seq[QueryPattern]) extends SparqlGenerator {
    def toSparql: String = "WHERE {\n" + patterns.map(_.toSparql).mkString + "}\n"
}

/**
  * Represents a SPARQL CONSTRUCT query.
  *
  * @param constructClause the CONSTRUCT clause.
  * @param whereClause     the WHERE clause.
  */
case class ConstructQuery(constructClause: ConstructClause, whereClause: WhereClause) extends SparqlGenerator {
    def toSparql: String = constructClause.toSparql + whereClause.toSparql
}