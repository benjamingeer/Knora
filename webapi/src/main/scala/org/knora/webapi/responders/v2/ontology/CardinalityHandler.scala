/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2.ontology

import zio.*

import java.time.Instant

import dsp.errors.BadRequestException
import dsp.errors.InconsistentRepositoryDataException
import org.knora.webapi.InternalSchema
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.twirl.queries.sparql
import org.knora.webapi.messages.v2.responder.CanDoResponseV2
import org.knora.webapi.messages.v2.responder.ontologymessages.*
import org.knora.webapi.slice.ontology.repo.model.OntologyCacheData
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

/**
 * Contains methods used for dealing with cardinalities on a class
 */
final case class CardinalityHandler(
  ontologyCache: OntologyCache,
  triplestoreService: TriplestoreService,
  ontologyCacheHelpers: OntologyCacheHelpers,
  ontologyTriplestoreHelpers: OntologyTriplestoreHelpers,
)(implicit val stringFormatter: StringFormatter) {

  /**
   * @param deleteCardinalitiesFromClassRequest the requested cardinalities to be deleted.
   * @param internalClassIri the Class from which the cardinalities are deleted.
   * @param internalOntologyIri the Ontology of which the Class and Cardinalities are part of.
   * @return a [[CanDoResponseV2]] indicating whether a class's cardinalities can be deleted.
   */
  def canDeleteCardinalitiesFromClass(
    deleteCardinalitiesFromClassRequest: CanDeleteCardinalitiesFromClassRequestV2,
    internalClassIri: SmartIri,
    internalOntologyIri: SmartIri,
  ): Task[CanDoResponseV2] = {
    val internalClassInfo = deleteCardinalitiesFromClassRequest.classInfoContent.toOntologySchema(InternalSchema)
    for {
      cacheData <- ontologyCache.getCacheData

      _ <- // Check that the ontology exists and has not been updated by another user since the client last read it.
        ontologyTriplestoreHelpers.checkOntologyLastModificationDateBeforeUpdate(
          internalOntologyIri = internalOntologyIri,
          expectedLastModificationDate = deleteCardinalitiesFromClassRequest.lastModificationDate,
        )

      _ <- getRdfTypeAndEnsureSingleCardinality(internalClassInfo)

      // Check that the class exists
      currentClassDefinition <- classExists(cacheData, internalClassInfo, internalClassIri, internalOntologyIri)

      // Check that the submitted cardinality to delete is defined on this class
      cardinalitiesToDelete: Map[SmartIri, OwlCardinality.KnoraCardinalityInfo] = internalClassInfo.directCardinalities
      isDefinedOnClassList <- ZIO.foreach(cardinalitiesToDelete.toList) { case (k, v) =>
                                isCardinalityDefinedOnClass(cacheData, k, v, internalClassIri, internalOntologyIri)
                              }
      atLeastOneCardinalityNotDefinedOnClass: Boolean = isDefinedOnClassList.contains(false)

      // Check if property is used in resources of this class

      submittedPropertyToDelete: SmartIri = cardinalitiesToDelete.head._1
      propertyIsUsed <- isPropertyUsedInResources(
                          internalClassIri.toInternalIri,
                          submittedPropertyToDelete.toInternalIri,
                        )

      // Make an update class definition in which the cardinality to delete is removed

      submittedPropertyToDeleteIsLinkProperty = cacheData
                                                  .ontologies(submittedPropertyToDelete.getOntologyFromEntity)
                                                  .properties(submittedPropertyToDelete)
                                                  .isLinkProp

      newClassDefinitionWithRemovedCardinality =
        currentClassDefinition.copy(
          directCardinalities = {
            val cardinalities = currentClassDefinition.directCardinalities - submittedPropertyToDelete
            if (submittedPropertyToDeleteIsLinkProperty) {
              // if we want to remove a link property,
              // then we also need to remove the corresponding link property value
              cardinalities - submittedPropertyToDelete.fromLinkPropToLinkValueProp
            } else cardinalities
          },
        )

      // Check that the new cardinalities are valid, and don't add any inherited cardinalities.

      allBaseClassIrisWithoutInternal =
        newClassDefinitionWithRemovedCardinality.subClassOf.toSeq.flatMap { baseClassIri =>
          cacheData.getSuperClassesOf(baseClassIri).getOrElse(Seq.empty[SmartIri])
        }

      allBaseClassIris = internalClassIri +: allBaseClassIrisWithoutInternal

      (newInternalClassDefWithLinkValueProps, _) =
        OntologyHelpers
          .checkCardinalitiesBeforeAddingAndIfNecessaryAddLinkValueProperties(
            internalClassDef = newClassDefinitionWithRemovedCardinality,
            allBaseClassIris = allBaseClassIris.toSet,
            cacheData = cacheData,
            // since we only want to delete (and have already removed what we want), the rest of the link properties
            // need to be marked as wanting to keep.
            existingLinkPropsToKeep = newClassDefinitionWithRemovedCardinality.directCardinalities.keySet
              // turn the propertyIri into a ReadPropertyInfoV2
              .map(propertyIri => cacheData.ontologies(propertyIri.getOntologyFromEntity).properties(propertyIri))
              .filter(_.isLinkProp)                 // we are only interested in link properties
              .map(_.entityInfoContent.propertyIri), // turn whatever is left back to a propertyIri
          )
          .fold(e => throw e.head, v => v)

      // Check that the class definition doesn't refer to any non-shared ontologies in other projects.
      _ <- ZIO.attempt(
             OntologyCache.checkOntologyReferencesInClassDef(
               cacheData,
               newInternalClassDefWithLinkValueProps,
               msg => throw BadRequestException(msg),
             ),
           )

      // response is true only when property is not used in data and cardinality is defined directly on that class
    } yield CanDoResponseV2.of(!propertyIsUsed && !atLeastOneCardinalityNotDefinedOnClass)
  }

  /**
   * Check that the class's rdf:type is owl:Class.
   * Check that cardinalities were submitted.
   * Check that only one cardinality was submitted.
   * @param classInfo the submitted class Info
   * @return the rdfType
   *
   *         [[BadRequestException]] if no rdf:type was specified
   *
   *         [[BadRequestException]] if no invalid rdf:type was specified
   *
   *         [[BadRequestException]] if no cardinalities was specified
   *
   *         [[BadRequestException]] if more than one one cardinality was specified
   */
  private def getRdfTypeAndEnsureSingleCardinality(classInfo: ClassInfoContentV2): Task[SmartIri] =
    for {
      rdfType <- ZIO
                   .fromOption(classInfo.getIriObject(OntologyConstants.Rdf.Type.toSmartIri))
                   .orElseFail(BadRequestException(s"No rdf:type specified"))
      // Check that the class's rdf:type is owl:Class.
      _ <- ZIO
             .fail(BadRequestException(s"Invalid rdf:type for property: $rdfType"))
             .when(rdfType != OntologyConstants.Owl.Class.toSmartIri)
      // Check that cardinalities were submitted.
      _ <- ZIO
             .fail(BadRequestException("No cardinalities specified"))
             .when(classInfo.directCardinalities.isEmpty)
      // Check that only one cardinality was submitted.
      _ <- ZIO
             .fail(BadRequestException("Only one cardinality is allowed to be submitted."))
             .when(classInfo.directCardinalities.size > 1)
    } yield rdfType

  /**
   * FIXME(DSP-1856): Only works if a single cardinality is supplied.
   * Deletes the supplied cardinalities from a class, if the referenced properties are not used in instances
   * of the class and any subclasses.
   *
   * @param deleteCardinalitiesFromClassRequest the requested cardinalities to be deleted.
   * @param internalClassIri the Class from which the cardinalities are deleted.
   * @param internalOntologyIri the Ontology of which the Class and Cardinalities are part of.
   * @return a [[ReadOntologyV2]] in the internal schema, containing the new class definition.
   */
  def deleteCardinalitiesFromClass(
    deleteCardinalitiesFromClassRequest: DeleteCardinalitiesFromClassRequestV2,
    internalClassIri: SmartIri,
    internalOntologyIri: SmartIri,
  ): Task[ReadOntologyV2] = {
    val internalClassInfo = deleteCardinalitiesFromClassRequest.classInfoContent.toOntologySchema(InternalSchema)
    for {
      cacheData <- ontologyCache.getCacheData
      ontology   = cacheData.ontologies(internalOntologyIri)

      // Check that the ontology exists and has not been updated by another user since the client last read it.
      _ <- ontologyTriplestoreHelpers.checkOntologyLastModificationDateBeforeUpdate(
             internalOntologyIri,
             deleteCardinalitiesFromClassRequest.lastModificationDate,
           )
      _ <- getRdfTypeAndEnsureSingleCardinality(internalClassInfo)

      // Check that the class exists
      currentClassDefinition <- classExists(cacheData, internalClassInfo, internalClassIri, internalOntologyIri)

      // Check that the submitted cardinality to delete is defined on this class
      cardinalitiesToDelete: Map[SmartIri, OwlCardinality.KnoraCardinalityInfo] = internalClassInfo.directCardinalities
      isDefinedOnClassList <- ZIO.foreach(cardinalitiesToDelete.toList) { case (k, v) =>
                                isCardinalityDefinedOnClass(cacheData, k, v, internalClassIri, internalOntologyIri)
                              }
      _ <- ZIO
             .fail(BadRequestException("The cardinality is not defined directly on the class and cannot be deleted."))
             .when(isDefinedOnClassList.contains(false))

      // Check if property is used in resources of this class

      submittedPropertyToDelete: SmartIri = cardinalitiesToDelete.head._1
      propertyIsUsed <-
        isPropertyUsedInResources(internalClassIri.toInternalIri, submittedPropertyToDelete.toInternalIri)
      _ <- ZIO
             .fail(BadRequestException("Property is used in data. The cardinality cannot be deleted."))
             .when(propertyIsUsed)

      // Make an update class definition in which the cardinality to delete is removed

      submittedPropertyToDeleteIsLinkProperty = cacheData
                                                  .ontologies(submittedPropertyToDelete.getOntologyFromEntity)
                                                  .properties(submittedPropertyToDelete)
                                                  .isLinkProp

      newClassDefinitionWithRemovedCardinality =
        currentClassDefinition.copy(
          directCardinalities = {
            val cardinalities = currentClassDefinition.directCardinalities - submittedPropertyToDelete
            if (submittedPropertyToDeleteIsLinkProperty) {
              // if we want to remove a link property,
              // then we also need to remove the corresponding link property value
              cardinalities - submittedPropertyToDelete.fromLinkPropToLinkValueProp
            } else cardinalities
          },
        )

      // Check that the new cardinalities are valid, and don't add any inherited cardinalities.

      allBaseClassIrisWithoutInternal =
        newClassDefinitionWithRemovedCardinality.subClassOf.toSeq.flatMap { baseClassIri =>
          cacheData.getSuperClassesOf(baseClassIri).getOrElse(Seq.empty[SmartIri])
        }

      allBaseClassIris = internalClassIri +: allBaseClassIrisWithoutInternal

      (newInternalClassDefWithLinkValueProps, cardinalitiesForClassWithInheritance) =
        OntologyHelpers
          .checkCardinalitiesBeforeAddingAndIfNecessaryAddLinkValueProperties(
            internalClassDef = newClassDefinitionWithRemovedCardinality,
            allBaseClassIris = allBaseClassIris.toSet,
            cacheData = cacheData,
            // since we only want to delete (and have already removed what we want), the rest of the link properties
            // need to be marked as wanting to keep.
            existingLinkPropsToKeep = newClassDefinitionWithRemovedCardinality.directCardinalities.keySet
              // turn the propertyIri into a ReadPropertyInfoV2
              .map(propertyIri => cacheData.ontologies(propertyIri.getOntologyFromEntity).properties(propertyIri))
              .filter(_.isLinkProp)                 // we are only interested in link properties
              .map(_.entityInfoContent.propertyIri), // turn whatever is left back to a propertyIri
          )
          .fold(e => throw e.head, v => v)

      // Check that the class definition doesn't refer to any non-shared ontologies in other projects.
      _ <- ZIO.attempt(
             OntologyCache.checkOntologyReferencesInClassDef(
               cacheData,
               newInternalClassDefWithLinkValueProps,
               msg => throw BadRequestException(msg),
             ),
           )

      // Prepare to update the ontology cache. (No need to deal with SPARQL-escaping here, because there
      // isn't any text to escape in cardinalities.)

      propertyIrisOfAllCardinalitiesForClass = cardinalitiesForClassWithInheritance.keySet

      inheritedCardinalities = cardinalitiesForClassWithInheritance.filterNot { case (propertyIri, _) =>
                                 newInternalClassDefWithLinkValueProps.directCardinalities.contains(propertyIri)
                               }

      readClassInfo = ReadClassInfoV2(
                        entityInfoContent = newInternalClassDefWithLinkValueProps,
                        allBaseClasses = allBaseClassIris,
                        isResourceClass = true,
                        canBeInstantiated = true,
                        inheritedCardinalities = inheritedCardinalities,
                        knoraResourceProperties = propertyIrisOfAllCardinalitiesForClass.filter(propertyIri =>
                          OntologyHelpers.isKnoraResourceProperty(propertyIri, cacheData),
                        ),
                        linkProperties = propertyIrisOfAllCardinalitiesForClass.filter(propertyIri =>
                          OntologyHelpers.isLinkProp(propertyIri, cacheData),
                        ),
                        linkValueProperties = propertyIrisOfAllCardinalitiesForClass.filter(propertyIri =>
                          OntologyHelpers.isLinkValueProp(propertyIri, cacheData),
                        ),
                        fileValueProperties = propertyIrisOfAllCardinalitiesForClass.filter(propertyIri =>
                          OntologyHelpers.isFileValueProp(propertyIri, cacheData),
                        ),
                      )

      // Add the cardinalities to the class definition in the triplestore.

      currentTime: Instant = Instant.now

      updateSparql = sparql.v2.txt.replaceClassCardinalities(
                       ontologyNamedGraphIri = internalOntologyIri,
                       ontologyIri = internalOntologyIri,
                       classIri = internalClassIri,
                       newCardinalities = newInternalClassDefWithLinkValueProps.directCardinalities,
                       lastModificationDate = deleteCardinalitiesFromClassRequest.lastModificationDate,
                       currentTime = currentTime,
                     )

      _ <- triplestoreService.query(Update(updateSparql))

      // Check that the ontology's last modification date was updated.
      _ <- ontologyTriplestoreHelpers.checkOntologyLastModificationDateAfterUpdate(internalOntologyIri, currentTime)

      // Check that the data that was saved corresponds to the data that was submitted.

      loadedClassDef <- ontologyTriplestoreHelpers.loadClassDefinition(internalClassIri)

      _ = if (loadedClassDef != newInternalClassDefWithLinkValueProps) {
            throw InconsistentRepositoryDataException(
              s"Attempted to save class definition $newInternalClassDefWithLinkValueProps, " +
                s"but $loadedClassDef was saved",
            )
          }

      // Update subclasses and write the cache.

      updatedOntology = ontology.copy(
                          ontologyMetadata = ontology.ontologyMetadata.copy(
                            lastModificationDate = Some(currentTime),
                          ),
                          classes = ontology.classes + (internalClassIri -> readClassInfo),
                        )

      _ <- ontologyCache.cacheUpdatedOntologyWithClass(internalOntologyIri, updatedOntology, internalClassIri)

      // Read the data back from the cache.

      response <- ontologyCacheHelpers.getClassDefinitionsFromOntologyV2(
                    Set(internalClassIri),
                    allLanguages = true,
                    deleteCardinalitiesFromClassRequest.requestingUser,
                  )

    } yield response
  }

  /**
   * Check if a property entity is used in resource instances. Returns `true` if
   * it is used, and `false` if it is not used.
   *
   * @param classIri the IRI of the class that is being checked for usage.
   * @param propertyIri the IRI of the entity that is being checked for usage.
   *
   * @return a [[Boolean]] denoting if the property entity is used.
   */
  def isPropertyUsedInResources(classIri: InternalIri, propertyIri: InternalIri): Task[Boolean] =
    triplestoreService.query(Ask(sparql.v2.txt.isPropertyUsed(propertyIri, classIri)))

  /**
   * Checks if the class is defined inside the ontology found in the cache.
   *
   * @param cacheData the cached ontology data
   * @param submittedClassInfoContentV2 the submitted class information
   * @param internalClassIri the internal class IRI
   * @param internalOntologyIri the internal ontology IRI
   * @return `true` if the class is defined inside the ontology found in the cache, otherwise throws an exception.
   */
  private def classExists(
    cacheData: OntologyCacheData,
    submittedClassInfoContentV2: ClassInfoContentV2,
    internalClassIri: SmartIri,
    internalOntologyIri: SmartIri,
  ): Task[ClassInfoContentV2] =
    ZIO
      .fromOption(cacheData.ontologies(internalOntologyIri).classes.get(internalClassIri))
      .mapBoth(
        _ => BadRequestException(s"Class ${submittedClassInfoContentV2.classIri} does not exist"),
        _.entityInfoContent,
      )

  /**
   * Check if the cardinality for a property is defined on a class.
   *
   * @param cacheData the cached ontology data.
   * @param propertyIri the property IRI for which we want to check if the cardinality is defined on the class.
   * @param cardinalityInfo the cardinality that should be defined for the property.
   * @param internalClassIri the class we are checking against.
   * @param internalOntologyIri the ontology containing the class.
   * @return `true` if the cardinality is defined on the class, `false` otherwise
   */
  private def isCardinalityDefinedOnClass(
    cacheData: OntologyCacheData,
    propertyIri: SmartIri,
    cardinalityInfo: OwlCardinality.KnoraCardinalityInfo,
    internalClassIri: SmartIri,
    internalOntologyIri: SmartIri,
  ): Task[Boolean] = ZIO
    .fromOption(cacheData.ontologies(internalOntologyIri).classes.get(internalClassIri))
    .orElseFail(BadRequestException(s"Class $internalClassIri does not exist"))
    .flatMap { readClassInfo =>
      // if cardinality is inherited, it's not directly defined on that class
      if (readClassInfo.inheritedCardinalities.keySet.contains(propertyIri)) {
        ZIO.succeed(false)
      } else {
        readClassInfo.entityInfoContent.directCardinalities.get(propertyIri) match {
          case Some(cardinality) =>
            if (cardinality.cardinality.equals(cardinalityInfo.cardinality)) {
              ZIO.succeed(true)
            } else {
              ZIO.fail(
                BadRequestException(
                  s"Submitted cardinality for property $propertyIri does not match existing cardinality.",
                ),
              )
            }
          case None =>
            ZIO.fail(
              BadRequestException(
                s"Submitted cardinality for property $propertyIri is not defined for class $internalClassIri.",
              ),
            )
        }
      }
    }
}

object CardinalityHandler {
  val layer = ZLayer.derive[CardinalityHandler]
}
