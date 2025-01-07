/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo.model
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.v2.responder.ontologymessages.PropertyInfoContentV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadOntologyV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadPropertyInfoV2

/**
 * The in-memory cache of ontologies.
 *
 * @param ontologies                a map of ontology IRIs to ontologies.
 * @param classToSuperClassLookup   a lookup table of class IRIs to their potential super-classes.
 * @param classToSubclassLookup     a lookup table of class IRIs to their potential subclasses.
 * @param subPropertyOfRelations    a map of subproperties to their base properties.
 * @param superPropertyOfRelations  a map of base classes to their subproperties.
 * @param classDefinedInOntology    a map of class IRIs to the ontology where the class is defined
 * @param propertyDefinedInOntology a map of property IRIs to the ontology where the property is defined
 * @param entityDefinedInOntology   a map of entity IRIs (property or class) to the ontology where the entity is defined
 * @param standoffProperties        a set of standoff properties.
 */
case class OntologyCacheData(
  ontologies: Map[SmartIri, ReadOntologyV2],
  private val classToSuperClassLookup: Map[SmartIri, Seq[SmartIri]],
  private val classToSubclassLookup: Map[SmartIri, Set[SmartIri]],
  private val subPropertyOfRelations: Map[SmartIri, Set[SmartIri]],
  private val superPropertyOfRelations: Map[SmartIri, Set[SmartIri]],
  private val classDefinedInOntology: Map[SmartIri, SmartIri],
  private val propertyDefinedInOntology: Map[SmartIri, SmartIri],
  private val entityDefinedInOntology: Map[SmartIri, SmartIri],
  private val standoffProperties: Set[SmartIri],
) {
  lazy val allPropertyDefs: Map[SmartIri, PropertyInfoContentV2] = ontologies.values
    .flatMap(_.properties.map { case (propertyIri, readPropertyInfo) =>
      propertyIri -> readPropertyInfo.entityInfoContent
    })
    .toMap

  def containsStandoffProperty(propertyIri: SmartIri): Boolean = standoffProperties.contains(propertyIri)

  def getAllStandoffPropertyEntities: Map[SmartIri, ReadPropertyInfoV2] =
    ontologies.values.flatMap(_.properties.view.filterKeys(standoffProperties)).toMap

  def entityDefinedInOntology(propertyIri: SmartIri): Option[SmartIri]   = entityDefinedInOntology.get(propertyIri)
  def classDefinedInOntology(classIri: SmartIri): Option[SmartIri]       = classDefinedInOntology.get(classIri)
  def propertyDefinedInOntology(propertyIri: SmartIri): Option[SmartIri] = propertyDefinedInOntology.get(propertyIri)

  def getSubPropertiesOf(propertyIri: SmartIri): Option[Set[SmartIri]]   = superPropertyOfRelations.get(propertyIri)
  def getSuperPropertiesOf(propertyIri: SmartIri): Option[Set[SmartIri]] = subPropertyOfRelations.get(propertyIri)

  def getSubClassesOf(classIri: SmartIri): Option[Set[SmartIri]]   = classToSubclassLookup.get(classIri)
  def getSuperClassesOf(classIri: SmartIri): Option[Seq[SmartIri]] = classToSuperClassLookup.get(classIri)
}
object OntologyCacheData {
  val Empty = OntologyCacheData(
    ontologies = Map.empty,
    classToSuperClassLookup = Map.empty,
    classToSubclassLookup = Map.empty,
    subPropertyOfRelations = Map.empty,
    superPropertyOfRelations = Map.empty,
    classDefinedInOntology = Map.empty,
    propertyDefinedInOntology = Map.empty,
    entityDefinedInOntology = Map.empty,
    standoffProperties = Set.empty,
  )
}
