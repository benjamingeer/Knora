/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.transformers

import zio.*
import zio.ZLayer

import dsp.errors.GravsearchException
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.search.*
import org.knora.webapi.slice.ontology.repo.model.OntologyCacheData
import org.knora.webapi.slice.ontology.repo.service.OntologyCache

final case class OntologyInferencer(
  private val ontologyCache: OntologyCache,
)(implicit
  private val stringFormatter: StringFormatter,
) {
  private def inferSubclasses(
    statementPattern: StatementPattern,
    cache: OntologyCacheData,
    limitInferenceToOntologies: Option[Set[SmartIri]],
    queryVariableSuffix: Option[String],
  ): IO[GravsearchException, Seq[QueryPattern]] = for {
    baseClassIri <-
      statementPattern.obj match {
        case iriRef: IriRef => ZIO.succeed(iriRef)
        case other =>
          ZIO.fail(GravsearchException(s"The object of rdf:type must be an IRI, but $other was used"))
      }
  } yield {
    // look up subclasses from ontology cache
    val knownSubClasses = cache.getSubClassesOf(baseClassIri.iri).getOrElse(Set(baseClassIri.iri)).toSeq

    // if provided, limit the child classes to those that belong to relevant ontologies
    val subClasses = limitInferenceToOntologies match {
      case None                       => knownSubClasses
      case Some(relevantOntologyIris) =>
        // filter the known subclasses against the relevant ontologies
        knownSubClasses.filter(cache.classDefinedInOntology(_).exists(relevantOntologyIris.contains(_)))
    }

    // Searches for a `?v a <subClassIRI>`, or if multiple subclasses are present, then
    // a `VALUES ?resTypes { <subClassIRI> }` statement is created with a `?v a ?resTypes`.
    val types = QueryVariable(s"resTypes${queryVariableSuffix.getOrElse(scala.util.Random.nextInt.abs)}")
    if (subClasses.length > 1)
      Seq(ValuesPattern(types, subClasses.map(IriRef.apply(_, None)).toSet), statementPattern.copy(obj = types))
    else
      Seq(statementPattern)
  }

  private def inferSubproperties(
    statementPattern: StatementPattern,
    predIri: SmartIri,
    cache: OntologyCacheData,
    limitInferenceToOntologies: Option[Set[SmartIri]],
    queryVariableSuffix: Option[String],
  ): Seq[QueryPattern] = {
    // look up subproperties from ontology cache
    val knownSubProps = cache.getSubPropertiesOf(predIri).getOrElse(Set(predIri)).toSeq

    // if provided, limit the child properties to those that belong to relevant ontologies
    val subProps = limitInferenceToOntologies match {
      case Some(ontologyIris) =>
        knownSubProps.filter(cache.propertyDefinedInOntology(_).exists(ontologyIris.contains(_)))
      case None => knownSubProps
    }
    // Searches for a `?v <propertyIRI> ?b`, or if multiple propertyIRIs are present, then
    // a `VALUES ?subProp { <propertyIRI>+ }` statement is created with a `?a ?subProp ?b`.
    val subProp = QueryVariable(s"subProp${queryVariableSuffix.getOrElse(scala.util.Random.nextInt.abs)}")
    if (subProps.length > 1) {
      Seq(ValuesPattern(subProp, subProps.map(IriRef.apply(_, None)).toSet), statementPattern.copy(pred = subProp))
    } else {
      // if no subproperties are available, the initial statement can be used
      Seq(statementPattern)
    }
  }

  /**
   * Transforms a statement in a WHERE clause for a triplestore that does not provide inference.
   *
   * @param statementPattern           the statement pattern.
   * @param simulateInference          `true` if RDFS inference should be simulated on basis of the ontology cache.
   * @param limitInferenceToOntologies a set of ontology IRIs, to which the simulated inference will be limited. If `None`, all possible inference will be done.
   * @return the statement pattern as expanded to work without inference.
   */
  def transformStatementInWhere(
    statementPattern: StatementPattern,
    simulateInference: Boolean,
    limitInferenceToOntologies: Option[Set[SmartIri]] = None,
    queryVariableSuffix: Option[String] = None,
  ): Task[Seq[QueryPattern]] =
    statementPattern.pred match {
      case iriRef: IriRef if iriRef.iri.toString == OntologyConstants.KnoraBase.StandoffTagHasStartAncestor =>
        // Simulate knora-api:standoffTagHasStartAncestor, using knora-api:standoffTagHasStartParent.
        val pred = IriRef(OntologyConstants.KnoraBase.StandoffTagHasStartParent.toSmartIri, Some('*'))
        ZIO.succeed(Seq(statementPattern.copy(pred = pred)))
      case iriRef: IriRef if simulateInference =>
        for {
          ontoCache <- ontologyCache.getCacheData
          patternsWithInference <-
            if (iriRef.iri.toIri == OntologyConstants.Rdf.Type)
              inferSubclasses(statementPattern, ontoCache, limitInferenceToOntologies, queryVariableSuffix)
            else
              ZIO.succeed(
                inferSubproperties(
                  statementPattern,
                  iriRef.iri,
                  ontoCache,
                  limitInferenceToOntologies,
                  queryVariableSuffix,
                ),
              )
        } yield patternsWithInference
      case _ =>
        // The predicate isn't a property IRI or no inference should be done, so no expansion needed.
        ZIO.succeed(Seq(statementPattern))
    }
}

object OntologyInferencer {
  val layer = ZLayer.derive[OntologyInferencer]
}
