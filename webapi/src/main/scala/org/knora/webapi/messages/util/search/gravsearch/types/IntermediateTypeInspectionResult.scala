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

package org.knora.webapi.messages.util.search.gravsearch.types

import org.knora.webapi.exceptions.AssertionException
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.IRI
import org.knora.webapi.messages.{OntologyConstants, StringFormatter}

/**
  * Represents an intermediate result during type inspection. This is different from [[GravsearchTypeInspectionResult]]
  * in that an entity can have multiple types, which means that the entity has been used inconsistently.
  *
  * @param entities a map of Gravsearch entities to the types that were determined for them. If an entity
  *                 has more than one type, this means that it has been used with inconsistent types.
  */
case class IntermediateTypeInspectionResult(entities: Map[TypeableEntity, Set[GravsearchEntityTypeInfo]]) {
    /**
      * Adds types for an entity.
      *
      * @param entity      the entity for which types have been found.
      * @param entityTypes the types to be added.
      * @return a new [[IntermediateTypeInspectionResult]] containing the additional type information.
      */
    def addTypes(entity: TypeableEntity, entityTypes: Set[GravsearchEntityTypeInfo]): IntermediateTypeInspectionResult = {
        val newTypes = entities.getOrElse(entity, Set.empty[GravsearchEntityTypeInfo]) ++ entityTypes
        IntermediateTypeInspectionResult(entities = entities + (entity -> newTypes))
    }

    /**
      * Returns the entities for which types have not been found.
      */
    def untypedEntities: Set[TypeableEntity] = {
        entities.collect {
            case (entity, entityTypes) if entityTypes.isEmpty => entity
        }.toSet
    }

    /**
      * Returns the entities that have been used with inconsistent types.
      */
    def entitiesWithInconsistentTypes: Map[TypeableEntity, Set[GravsearchEntityTypeInfo]] = {
        entities.filter {
            case (_, entityTypes) => entityTypes.size > 1
        }
    }

    /**
      * Converts this [[IntermediateTypeInspectionResult]] to a [[GravsearchTypeInspectionResult]]. Before calling
      * this method, ensure that `entitiesWithInconsistentTypes` returns an empty map.
      */
    def toFinalResult: GravsearchTypeInspectionResult = {
        GravsearchTypeInspectionResult(
            entities = entities.map {
                case (entity, entityTypes) =>
                    if (entityTypes.size == 1) {
                        entity -> entityTypes.head
                    } else {
                        throw AssertionException(s"Cannot generate final type inspection result because of inconsistent types")
                    }
            }
        )
    }
}

object IntermediateTypeInspectionResult {
    /**
      * Constructs an [[IntermediateTypeInspectionResult]] for the given set of typeable entities, with built-in
      * types specified (e.g. for `rdfs:label`).
      *
      * @param entities the set of typeable entities found in the WHERE clause of a Gravsearch query.
      */
    def apply(entities: Set[TypeableEntity])(implicit stringFormatter: StringFormatter): IntermediateTypeInspectionResult = {
        // Make an IntermediateTypeInspectionResult in which each typeable entity has no types.
        val emptyResult = new IntermediateTypeInspectionResult(entities = entities.map(entity => entity -> Set.empty[GravsearchEntityTypeInfo]).toMap)

        // Collect the typeable IRIs used.
        val irisUsed: Set[IRI] = entities.collect {
            case typeableIri: TypeableIri => typeableIri.iri.toString
        }

        // Find the IRIs that represent resource metadata properties, and get their object types.
        val resourceMetadataPropertyTypesUsed: Map[TypeableIri, PropertyTypeInfo] = OntologyConstants.ResourceMetadataPropertyAxioms.filterKeys(irisUsed).map {
            case (propertyIri, objectTypeIri) => TypeableIri(propertyIri.toSmartIri) -> PropertyTypeInfo(objectTypeIri = objectTypeIri.toSmartIri)
        }

        // Add those types to the IntermediateTypeInspectionResult.
        resourceMetadataPropertyTypesUsed.foldLeft(emptyResult) {
            case (acc: IntermediateTypeInspectionResult, (entity: TypeableIri, entityType: PropertyTypeInfo)) =>
                acc.addTypes(entity, Set(entityType))
        }
    }
}