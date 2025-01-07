/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.prequery

import zio.*

import dsp.errors.ValidationException
import org.knora.webapi.InternalSchema
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.search.BindPattern
import org.knora.webapi.messages.util.search.Entity
import org.knora.webapi.messages.util.search.FilterNotExistsPattern
import org.knora.webapi.messages.util.search.IriRef
import org.knora.webapi.messages.util.search.MinusPattern
import org.knora.webapi.messages.util.search.OptionalPattern
import org.knora.webapi.messages.util.search.QueryPattern
import org.knora.webapi.messages.util.search.StatementPattern
import org.knora.webapi.messages.util.search.UnionPattern
import org.knora.webapi.messages.util.search.ValuesPattern
import org.knora.webapi.messages.util.search.WhereClause
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.ontology.repo.service.OntologyCache

final case class InferenceOptimizationService(
  private val projectService: ProjectService,
  private val ontologyCache: OntologyCache,
)(implicit private val stringFormatter: StringFormatter) {

  /**
   * Helper method that analyzed an RDF Entity and returns a sequence of Ontology IRIs that are being referenced by the entity.
   * If an IRI appears that can not be resolved by the ontology cache, it will check if the IRI points to project data;
   * if so, all ontologies defined by the project to which the data belongs, will be included in the results.
   *
   * @param entity       an RDF entity.
   * @param lookup       a function optionally defining the ontology in an entity is defined.
   * @return a sequence of ontology IRIs which relate to the input RDF entity.
   */
  private def resolveEntity(entity: Entity, lookup: SmartIri => Option[SmartIri]): Task[Seq[SmartIri]] =
    entity match {
      case IriRef(iri, _) =>
        val internal     = iri.toOntologySchema(InternalSchema)
        val maybeOntoIri = lookup(internal)
        maybeOntoIri match {
          // if the map contains an ontology IRI corresponding to the entity IRI, then this can be returned
          case Some(iri) => ZIO.succeed(Seq(iri))
          case None      =>
            // if the map doesn't contain a corresponding ontology IRI, then the entity IRI points to a resource or value
            // in that case, all ontologies of the project, to which the entity belongs, should be returned.
            resolveEntityByShortcode(internal)
        }
      case _ => ZIO.succeed(Seq.empty)
    }

  /**
   * Attempts to retrieve all ontologies of the project, to which the entity IRI (resource or property IRI) belongs.
   */
  private def resolveEntityByShortcode(entityIri: SmartIri): Task[Seq[SmartIri]] = {
    val shortcode = entityIri.getProjectCode
    shortcode match {
      case None => ZIO.succeed(Seq.empty)
      case Some(value) =>
        for {
          shortcode    <- ZIO.fromEither(Shortcode.from(value)).mapError(ValidationException.apply)
          projectMaybe <- projectService.findByShortcode(shortcode)
          projectOntologies =
            projectMaybe match {
              case None          => Seq.empty
              case Some(project) => project.ontologies.map(stringFormatter.toSmartIri(_))
            }
        } yield projectOntologies
    }
  }

  /**
   * Extracts all ontologies that are relevant to a gravsearch query, in order to allow optimized cache-based inference simulation.
   *
   * @param whereClause  the WHERE-clause of a gravsearch query.
   * @return a set of ontology IRIs relevant to the query, or `None`, if no meaningful result could be produced.
   *         In the latter case, inference should be done on the basis of all available ontologies.
   */
  def getOntologiesRelevantForInference(whereClause: WhereClause): Task[Option[Set[SmartIri]]] = {
    // gets a sequence of [[QueryPattern]] and returns the set of entities that the patterns consist of
    def getEntities(patterns: Seq[QueryPattern]): Seq[Entity] =
      patterns.flatMap { pattern =>
        pattern match {
          case ValuesPattern(_, values)          => values.toSeq
          case BindPattern(_, expression)        => List(expression.asInstanceOf[Entity])
          case UnionPattern(blocks)              => blocks.flatMap(block => getEntities(block))
          case StatementPattern(subj, pred, obj) => List(subj, pred, obj)
          case FilterNotExistsPattern(patterns)  => getEntities(patterns)
          case MinusPattern(patterns)            => getEntities(patterns)
          case OptionalPattern(patterns)         => getEntities(patterns)
          case _                                 => List.empty
        }
      }

    // get the entities for all patterns in the WHERE clause
    val entities = getEntities(whereClause.patterns)

    for {
      ontoCache <- ontologyCache.getCacheData
      // resolve all entities from the WHERE clause to the ontology where they are defined
      relevantOntologies   <- ZIO.foreach(entities)(resolveEntity(_, ontoCache.entityDefinedInOntology))
      relevantOntologiesSet = relevantOntologies.flatten.toSet
      relevantOntologiesMaybe =
        relevantOntologiesSet match {
          case ontologies =>
            // if only knora-base was found, then None should be returned too
            if (ontologies == Set(stringFormatter.toSmartIri(OntologyConstants.KnoraBase.KnoraBaseOntologyIri))) None
            // in all other cases, it should be made sure that knora-base is contained in the result
            else Some(ontologies + stringFormatter.toSmartIri(OntologyConstants.KnoraBase.KnoraBaseOntologyIri))
        }
    } yield relevantOntologiesMaybe
  }
}

object InferenceOptimizationService {
  val layer = ZLayer.derive[InferenceOptimizationService]
}
