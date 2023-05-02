/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.transformers

import zio._

import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.util.search._
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

final case class ConstructToConstructTransformer(
  sparqlTransformerLive: SparqlTransformerLive,
  iriConverter: IriConverter
) {

  private def transformLuceneQueryPattern(
    luceneQueryPattern: LuceneQueryPattern
  ): Task[Seq[QueryPattern]] =
    for {
      predIri  <- iriConverter.asSmartIri("http://jena.apache.org/text#query")
      datatype <- iriConverter.asSmartIri(OntologyConstants.Xsd.String)
      obj       = XsdLiteral(luceneQueryPattern.queryString.getQueryString, datatype)
    } yield Seq(StatementPattern(luceneQueryPattern.subj, IriRef(predIri), obj))

  private def transformPattern(
    pattern: QueryPattern,
    limit: Option[Set[SmartIri]]
  ): Task[Seq[QueryPattern]] =
    pattern match {
      case statementPattern: StatementPattern =>
        sparqlTransformerLive.transformStatementInWhereForNoInference(
          statementPattern = statementPattern,
          simulateInference = true,
          limitInferenceToOntologies = limit
        )
      case FilterNotExistsPattern(patterns) =>
        ZIO.foreach(patterns)(transformPattern(_, limit).map(FilterNotExistsPattern))
      case MinusPattern(patterns)    => ZIO.foreach(patterns)(transformPattern(_, limit).map(MinusPattern))
      case OptionalPattern(patterns) => ZIO.foreach(patterns)(transformPattern(_, limit).map(OptionalPattern))
      case UnionPattern(blocks) =>
        ZIO.foreach(blocks)(transformPatterns(_, limit)).map(block => Seq(UnionPattern(block)))
      case lucenePattern: LuceneQueryPattern => transformLuceneQueryPattern(lucenePattern)
      case pattern: QueryPattern             => ZIO.succeed(Seq(pattern))
    }

  private def transformPatterns(
    patterns: Seq[QueryPattern],
    limit: Option[Set[SmartIri]]
  ): Task[Seq[QueryPattern]] = for {
    optimisedPatterns <-
      ZIO.attempt(
        SparqlTransformer.moveBindToBeginning(
          SparqlTransformer.optimiseIsDeletedWithFilter(
            SparqlTransformer.moveLuceneToBeginning(patterns)
          )
        )
      )
    transformedPatterns <- ZIO.foreach(optimisedPatterns)(transformPattern(_, limit))
  } yield transformedPatterns.flatten

  /**
   * Traverses a CONSTRUCT query, delegating transformation tasks, and returns the transformed query.
   *
   * @param inputQuery                 the query to be transformed.
   * @param transformer                the [[ConstructToConstructTransformer]] to be used.
   * @param limitInferenceToOntologies a set of ontology IRIs, to which the simulated inference will be limited. If `None`, all possible inference will be done.
   * @return the transformed query.
   */
  def transformConstructToConstruct(
    inputQuery: ConstructQuery,
    limitInferenceToOntologies: Option[Set[SmartIri]] = None
  ): Task[ConstructQuery] =
    for {
      patterns <- transformPatterns(inputQuery.whereClause.patterns, limitInferenceToOntologies)
    } yield inputQuery.copy(whereClause = WhereClause(patterns))

}
