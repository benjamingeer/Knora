/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo.service

import com.typesafe.scalalogging.LazyLogging
import zio.*

import java.time.Instant

import dsp.errors.*
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.InternalSchema
import org.knora.webapi.KnoraBaseVersionString
import org.knora.webapi.OntologySchema
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.twirl.queries.sparql
import org.knora.webapi.messages.util.ErrorHandlingMap
import org.knora.webapi.messages.util.OntologyUtil
import org.knora.webapi.messages.v2.responder.ontologymessages.*
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality.*
import org.knora.webapi.responders.v2.ontology.OntologyHelpers
import org.knora.webapi.responders.v2.ontology.OntologyHelpers.OntologyGraph
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo
import org.knora.webapi.slice.ontology.repo.model.OntologyCacheData
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select

object OntologyCache {
  // The global ontology cache lock. This is needed because every ontology update replaces the whole ontology cache
  // (because definitions in one ontology can refer to definitions in another ontology). Without a global lock,
  // concurrent updates (even to different ontologies) could overwrite each other.
  val ONTOLOGY_CACHE_LOCK_IRI = "http://rdfh.ch/ontologies"

  /**
   * Checks a class definition to ensure that it doesn't refer to any non-shared ontologies in other projects.
   *
   * @param cache    the ontology cache data.
   * @param classDef the class definition.
   * @param errorFun a function that throws an exception with the specified message if the property definition is invalid.
   */
  def checkOntologyReferencesInClassDef(
    cache: OntologyCacheData,
    classDef: ClassInfoContentV2,
    errorFun: String => Nothing,
  ): Unit = {
    classDef.subClassOf.foreach(checkOntologyReferenceInEntity(cache, classDef.classIri, _, errorFun))
    classDef.directCardinalities.keys.foreach(checkOntologyReferenceInEntity(cache, classDef.classIri, _, errorFun))
  }

  /**
   * Checks a reference between an ontology entity and another ontology entity to see if the target
   * is in a non-shared ontology in another project.
   *
   * @param ontologyCacheData the ontology cache data.
   * @param sourceEntityIri   the entity whose definition contains the reference.
   * @param targetEntityIri   the entity that's the target of the reference.
   * @param errorFun          a function that throws an exception with the specified message if the reference is invalid.
   */
  private def checkOntologyReferenceInEntity(
    ontologyCacheData: OntologyCacheData,
    sourceEntityIri: SmartIri,
    targetEntityIri: SmartIri,
    errorFun: String => Nothing,
  ): Unit =
    if (targetEntityIri.isKnoraDefinitionIri) {
      val sourceOntologyIri      = sourceEntityIri.getOntologyFromEntity
      val sourceOntologyMetadata = ontologyCacheData.ontologies(sourceOntologyIri).ontologyMetadata

      val targetOntologyIri      = targetEntityIri.getOntologyFromEntity
      val targetOntologyMetadata = ontologyCacheData.ontologies(targetOntologyIri).ontologyMetadata

      if (sourceOntologyMetadata.projectIri != targetOntologyMetadata.projectIri) {
        if (!(targetOntologyIri.isKnoraBuiltInDefinitionIri || targetOntologyIri.isKnoraSharedDefinitionIri)) {
          errorFun(
            s"Entity $sourceEntityIri refers to entity $targetEntityIri, which is in a non-shared ontology that belongs to another project",
          )
        }
      }
    }

  /**
   * Checks a property definition to ensure that it doesn't refer to any other non-shared ontologies.
   *
   * @param ontologyCacheData the ontology cache data.
   * @param propertyDef       the property definition.
   * @param errorFun          a function that throws an exception with the specified message if the property definition is invalid.
   */
  def checkOntologyReferencesInPropertyDef(
    ontologyCacheData: OntologyCacheData,
    propertyDef: PropertyInfoContentV2,
    errorFun: String => Nothing,
  )(implicit stringFormatter: StringFormatter): Unit = {
    // Ensure that the property isn't a subproperty of any property in a non-shared ontology in another project.

    for (subPropertyOf <- propertyDef.subPropertyOf) {
      checkOntologyReferenceInEntity(
        ontologyCacheData = ontologyCacheData,
        sourceEntityIri = propertyDef.propertyIri,
        targetEntityIri = subPropertyOf,
        errorFun = errorFun,
      )
    }

    // Ensure that the property doesn't have subject or object constraints pointing to a non-shared ontology in another project.

    propertyDef.getPredicateIriObject(OntologyConstants.KnoraBase.SubjectClassConstraint.toSmartIri) match {
      case Some(subjectClassConstraint) =>
        checkOntologyReferenceInEntity(
          ontologyCacheData = ontologyCacheData,
          sourceEntityIri = propertyDef.propertyIri,
          targetEntityIri = subjectClassConstraint,
          errorFun = errorFun,
        )

      case None => ()
    }

    propertyDef.getPredicateIriObject(OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri) match {
      case Some(objectClassConstraint) =>
        checkOntologyReferenceInEntity(
          ontologyCacheData = ontologyCacheData,
          sourceEntityIri = propertyDef.propertyIri,
          targetEntityIri = objectClassConstraint,
          errorFun = errorFun,
        )

      case None => ()
    }
  }

  /**
   * Checks references between ontologies to ensure that they do not refer to non-shared ontologies in other projects.
   *
   * @param ontologyCacheData the ontology cache data.
   */
  def checkReferencesBetweenOntologies(
    ontologyCacheData: OntologyCacheData,
  )(implicit stringFormatter: StringFormatter): Unit =
    for (ontology <- ontologyCacheData.ontologies.values) {
      for (propertyInfo <- ontology.properties.values) {
        checkOntologyReferencesInPropertyDef(
          ontologyCacheData = ontologyCacheData,
          propertyDef = propertyInfo.entityInfoContent,
          errorFun = { (msg: String) => throw InconsistentRepositoryDataException(msg) },
        )
      }

      for (classInfo <- ontology.classes.values) {
        checkOntologyReferencesInClassDef(
          cache = ontologyCacheData,
          classDef = classInfo.entityInfoContent,
          errorFun = { (msg: String) => throw InconsistentRepositoryDataException(msg) },
        )
      }
    }

  /**
   * Creates an [[OntologyCacheData]] object on the basis of a map of ontology IRIs to the corresponding [[ReadOntologyV2]].
   *
   * @param ontologies a map of ontology IRIs to the corresponding [[ReadOntologyV2]]
   * @return An [[OntologyCacheData]] object
   */
  def make(ontologies: Map[SmartIri, ReadOntologyV2])(implicit stringFormatter: StringFormatter): OntologyCacheData = {
    // A map of ontology IRIs to class IRIs in each ontology.
    val classIrisPerOntology: Map[SmartIri, Set[SmartIri]] = ontologies.map { case (iri, ontology) =>
      val classIris = ontology.classes.values.map { (classInfo: ReadClassInfoV2) =>
        classInfo.entityInfoContent.classIri
      }.toSet
      iri -> classIris
    }

    // A map of ontology IRIs to property IRIs in each ontology.
    val propertyIrisPerOntology: Map[SmartIri, Set[SmartIri]] = ontologies.map { case (iri, ontology) =>
      val propertyIris = ontology.properties.values.map { (propertyInfo: ReadPropertyInfoV2) =>
        propertyInfo.entityInfoContent.propertyIri
      }.toSet
      iri -> propertyIris
    }

    // Construct entity definitions.

    val readClassInfos: Map[SmartIri, ReadClassInfoV2] = ontologies.flatMap { case (_, ontology) =>
      ontology.classes
    }
    val readPropertyInfos: Map[SmartIri, ReadPropertyInfoV2] = ontologies.flatMap { case (_, ontology) =>
      ontology.properties
    }
    // A map of class IRIs to class definitions.
    val allClassDefs: Map[SmartIri, ClassInfoContentV2] = readClassInfos.map { case (iri, classInfo) =>
      iri -> classInfo.entityInfoContent
    }
    // A map of property IRIs to property definitions.
    val allPropertyDefs: Map[SmartIri, PropertyInfoContentV2] = readPropertyInfos.map { case (iri, propertyInfo) =>
      iri -> propertyInfo.entityInfoContent
    }

    // Determine relations between entities.

    // A map of class IRIs to their immediate super classes.
    val directSubClassOfRelations: Map[SmartIri, Set[SmartIri]] = allClassDefs.map { case (classIri, classDef) =>
      classIri -> classDef.subClassOf
    }

    // A map of property IRIs to their immediate super properties.
    val directSubPropertyOfRelations: Map[SmartIri, Set[SmartIri]] = allPropertyDefs.map {
      case (propertyIri, propertyDef) => propertyIri -> propertyDef.subPropertyOf
    }

    val allClassIris    = readClassInfos.keySet
    val allPropertyIris = readPropertyInfos.keySet

    // A map in which each class IRI points to the full sequence of its super classes.
    val allSubClassOfRelations: Map[SmartIri, Seq[SmartIri]] = allClassIris.toSeq.map { classIri =>
      // get the hierarchically ordered super classes.
      val superClasses: Seq[SmartIri] = OntologyUtil.getAllBaseDefs(classIri, directSubClassOfRelations)
      // prepend the classIri to the sequence of super classes because a class is also a subclass of itself.
      (classIri, classIri +: superClasses)
    }.toMap

    // Make a map in which each property IRI points to the full set of its base properties. A property is also a subproperty of itself.
    val allSubPropertyOfRelations: Map[SmartIri, Set[SmartIri]] = allPropertyIris.map { propertyIri =>
      (propertyIri, OntologyUtil.getAllBaseDefs(propertyIri, directSubPropertyOfRelations).toSet + propertyIri)
    }.toMap

    // A map in which each class IRI points to the full set of its subclasses. A class is also a subclass of itself.
    val allSuperClassOfRelations: Map[SmartIri, Set[SmartIri]] =
      OntologyHelpers.calculateSuperClassOfRelations(allSubClassOfRelations)

    // Make a map in which each property IRI points to the full set of its subproperties. A property is also a superproperty of itself.
    val allSuperPropertyOfRelations: Map[SmartIri, Set[SmartIri]] =
      OntologyHelpers.calculateSuperPropertiesOfRelations(allSubPropertyOfRelations)

    // A set of the IRIs of all properties used in cardinalities in standoff classes.
    val propertiesUsedInStandoffCardinalities: Set[SmartIri] = readClassInfos.flatMap { case (_, readClassInfo) =>
      if (readClassInfo.isStandoffClass) {
        readClassInfo.allCardinalities.keySet
      } else {
        Set.empty[SmartIri]
      }
    }.toSet

    // A set of the IRIs of all properties whose subject class constraint is a standoff class.
    val propertiesWithStandoffTagSubjects: Set[SmartIri] = readPropertyInfos.flatMap {
      case (propertyIri, readPropertyInfo) =>
        readPropertyInfo.entityInfoContent.getPredicateIriObject(
          OntologyConstants.KnoraBase.SubjectClassConstraint.toSmartIri,
        ) match {
          case Some(subjectClassConstraint: SmartIri) =>
            readClassInfos.get(subjectClassConstraint) match {
              case Some(subjectReadClassInfo: ReadClassInfoV2) =>
                if (subjectReadClassInfo.isStandoffClass) {
                  Some(propertyIri)
                } else {
                  None
                }

              case None => None
            }

          case None => None
        }
    }.toSet

    val classDefinedInOntology = classIrisPerOntology.flatMap { case (ontoIri, classIris) =>
      classIris.map(_ -> ontoIri)
    }
    val propertyDefinedInOntology = propertyIrisPerOntology.flatMap { case (ontoIri, propertyIris) =>
      propertyIris.map(_ -> ontoIri)
    }

    val ontologiesErrorMap =
      new ErrorHandlingMap[SmartIri, ReadOntologyV2](ontologies, key => s"Ontology not found in ontologies: $key")
    val subClassOfRelationsErrorMap =
      new ErrorHandlingMap[SmartIri, Seq[SmartIri]](
        allSubClassOfRelations,
        key => s"Class not found in classToSuperClassLookup: $key",
      )
    val superClassOfRelationsErrorMap =
      new ErrorHandlingMap[SmartIri, Set[SmartIri]](
        allSuperClassOfRelations,
        key => s"Class not found in superClassOfRelations: $key",
      )
    val subPropertyOfRelationsErrorMap =
      new ErrorHandlingMap[SmartIri, Set[SmartIri]](
        allSubPropertyOfRelations,
        key => s"Property not found in allSubPropertyOfRelations: $key",
      )
    val superPropertyOfRelationsErrorMap =
      new ErrorHandlingMap[SmartIri, Set[SmartIri]](
        allSuperPropertyOfRelations,
        key => s"Property not found in allSuperPropertyOfRelations: $key",
      )
    val classDefinedInOntologyErrorMap =
      new ErrorHandlingMap[SmartIri, SmartIri](
        classDefinedInOntology,
        key => s"Class not found classDefinedInOntology: $key",
      )
    val propertyDefinedInOntologyErrorMap =
      new ErrorHandlingMap[SmartIri, SmartIri](
        propertyDefinedInOntology,
        key => s"Property not found in propertyDefinedInOntology: $key",
      )
    val entityDefinedInOntologyErrorMap = new ErrorHandlingMap[SmartIri, SmartIri](
      propertyDefinedInOntology ++ classDefinedInOntology,
      key => s"Property not found in propertyDefinedInOntology: $key",
    )

    val standoffProperties = propertiesUsedInStandoffCardinalities ++ propertiesWithStandoffTagSubjects

    OntologyCacheData(
      ontologies = ontologiesErrorMap,
      classToSuperClassLookup = subClassOfRelationsErrorMap,
      classToSubclassLookup = superClassOfRelationsErrorMap,
      subPropertyOfRelations = subPropertyOfRelationsErrorMap,
      superPropertyOfRelations = superPropertyOfRelationsErrorMap,
      classDefinedInOntology = classDefinedInOntologyErrorMap,
      propertyDefinedInOntology = propertyDefinedInOntologyErrorMap,
      entityDefinedInOntology = entityDefinedInOntologyErrorMap,
      standoffProperties = standoffProperties,
    )
  }

  /**
   * Checks that a property's `knora-base:subjectClassConstraint` or `knora-base:objectClassConstraint` is compatible with (i.e. a subclass of)
   * the ones in all its base properties.
   *
   * @param internalPropertyIri        the internal IRI of the property to be checked.
   * @param constraintPredicateIri     the internal IRI of the constraint, i.e. `knora-base:subjectClassConstraint` or `knora-base:objectClassConstraint`.
   * @param constraintValueToBeChecked the constraint value to be checked.
   * @param allSuperPropertyIris       the IRIs of all the base properties of the property, including indirect base properties and the property itself.
   * @param errorSchema                the ontology schema to be used for error messages.
   * @param errorFun                   a function that throws an exception. It will be called with an error message argument if the property constraint is invalid.
   */
  def checkPropertyConstraint(
    cacheData: OntologyCacheData,
    internalPropertyIri: SmartIri,
    constraintPredicateIri: SmartIri,
    constraintValueToBeChecked: SmartIri,
    allSuperPropertyIris: Set[SmartIri],
    errorSchema: OntologySchema,
    errorFun: String => Nothing,
  ): Unit = {
    // The property constraint value must be a Knora class, or one of a limited set of classes defined in OWL.
    val superClassesOfConstraintValueToBeChecked: Set[SmartIri] =
      if (OntologyConstants.Owl.ClassesThatCanBeKnoraClassConstraints.contains(constraintValueToBeChecked.toString)) {
        Set(constraintValueToBeChecked)
      } else {
        cacheData
          .getSuperClassesOf(constraintValueToBeChecked)
          .getOrElse(
            errorFun(
              s"Property ${internalPropertyIri.toOntologySchema(errorSchema)} cannot have a ${constraintPredicateIri
                  .toOntologySchema(errorSchema)} of " +
                s"${constraintValueToBeChecked.toOntologySchema(errorSchema)}",
            ),
          )
          .toSet
      }

    // Get the definitions of all the Knora superproperties of the property.
    val superPropertyInfos: Set[ReadPropertyInfoV2] = (allSuperPropertyIris - internalPropertyIri).collect {
      case superPropertyIri if superPropertyIri.isKnoraDefinitionIri =>
        cacheData
          .ontologies(superPropertyIri.getOntologyFromEntity)
          .properties
          .getOrElse(
            superPropertyIri,
            errorFun(
              s"Property ${internalPropertyIri.toOntologySchema(errorSchema)} is a subproperty of $superPropertyIri, which is undefined",
            ),
          )
    }

    // For each superproperty definition, get the value of the specified constraint in that definition, if any. Here we
    // make a map of superproperty IRIs to superproperty constraint values.
    val superPropertyConstraintValues: Map[SmartIri, SmartIri] = superPropertyInfos.flatMap { superPropertyInfo =>
      superPropertyInfo.entityInfoContent.predicates
        .get(constraintPredicateIri)
        .map(
          _.requireIriObject(
            throw InconsistentRepositoryDataException(
              s"Property ${superPropertyInfo.entityInfoContent.propertyIri} has an invalid object for $constraintPredicateIri",
            ),
          ),
        )
        .map { superPropertyConstraintValue =>
          superPropertyInfo.entityInfoContent.propertyIri -> superPropertyConstraintValue
        }
    }.toMap

    // Check that the constraint value to be checked is a subclass of the constraint value in every superproperty.

    superPropertyConstraintValues.foreach { case (superPropertyIri, superPropertyConstraintValue) =>
      if (!superClassesOfConstraintValueToBeChecked.contains(superPropertyConstraintValue)) {
        errorFun(
          s"Property ${internalPropertyIri.toOntologySchema(errorSchema)} cannot have a ${constraintPredicateIri
              .toOntologySchema(errorSchema)} of " +
            s"${constraintValueToBeChecked.toOntologySchema(errorSchema)}, because that is not a subclass of " +
            s"${superPropertyConstraintValue.toOntologySchema(errorSchema)}, which is the ${constraintPredicateIri
                .toOntologySchema(errorSchema)} of " +
            s"a base property, ${superPropertyIri.toOntologySchema(errorSchema)}",
        )
      }
    }
  }

}

trait OntologyCache {

  /**
   * Loads and caches all ontology information.
   */
  def loadOntologies(): Task[Unit]

  /**
   * Gets the ontology data from the cache.
   *
   * @return an [[OntologyCacheData]]
   */
  def getCacheData: Task[OntologyCacheData]

  /**
   * Updates an existing ontology in the cache without updating the cache lookup maps. This should only be used if only the ontology metadata has changed.
   *
   * @param updatedOntologyIri  the IRI of the updated ontology
   * @param updatedOntologyData the [[ReadOntologyV2]] representation of the updated ontology
   * @return the updated cache data
   */
  def cacheUpdatedOntologyWithoutUpdatingMaps(
    updatedOntologyIri: SmartIri,
    updatedOntologyData: ReadOntologyV2,
  ): Task[OntologyCacheData]

  /**
   * Deletes an ontology from the cache.
   *
   * @param ontologyIri the IRI of the ontology to delete
   * @return the updated cache data
   */
  def deleteOntology(ontologyIri: SmartIri): Task[OntologyCacheData]

  /**
   * Updates an existing ontology in the cache. If a class has changed, use `cacheUpdatedOntologyWithClass()`.
   *
   * @param updatedOntologyIri  the IRI of the updated ontology
   * @param updatedOntologyData the [[ReadOntologyV2]] representation of the updated ontology
   * @return the updated cache data
   */
  def cacheUpdatedOntology(updatedOntologyIri: SmartIri, updatedOntologyData: ReadOntologyV2): Task[OntologyCacheData]

  /**
   * Updates an existing ontology in the cache and ensures that the sub- and superclasses of a (presumably changed) class get updated correctly.
   *
   * @param updatedOntologyIri  the IRI of the updated ontology
   * @param updatedOntologyData the [[ReadOntologyV2]] representation of the updated ontology
   * @param updatedClassIri     the IRI of the changed class
   * @return the updated cache data
   */
  def cacheUpdatedOntologyWithClass(
    updatedOntologyIri: SmartIri,
    updatedOntologyData: ReadOntologyV2,
    updatedClassIri: SmartIri,
  ): Task[OntologyCacheData]

}

final case class OntologyCacheLive(triplestore: TriplestoreService, cacheDataRef: Ref[OntologyCacheData])(implicit
  val stringFormatter: StringFormatter,
) extends OntologyCache
    with LazyLogging {

  /**
   * Loads and caches all ontology information.
   */
  override def loadOntologies(): Task[Unit] =
    for {
      // Get all ontology metadata.
      _                           <- ZIO.logInfo(s"Loading ontologies into cache")
      allOntologyMetadataResponse <- triplestore.query(Select(sparql.v2.txt.getAllOntologyMetadata()))
      allOntologyMetadata          = OntologyHelpers.buildOntologyMetadata(allOntologyMetadataResponse)

      knoraBaseOntologyMetadata =
        allOntologyMetadata.getOrElse(
          OntologyConstants.KnoraBase.KnoraBaseOntologyIri.toSmartIri,
          throw InconsistentRepositoryDataException(s"No knora-base ontology found"),
        )
      knoraBaseOntologyVersion =
        knoraBaseOntologyMetadata.ontologyVersion.getOrElse(
          throw InconsistentRepositoryDataException(
            "The knora-base ontology in the repository is not up to date. See the Knora documentation on repository updates.",
          ),
        )

      _ = if (knoraBaseOntologyVersion != KnoraBaseVersionString) {
            throw InconsistentRepositoryDataException(
              s"The knora-base ontology in the repository has version '$knoraBaseOntologyVersion', but this version of Knora requires '$KnoraBaseVersionString'. See the Knora documentation on repository updates.",
            )
          }

      // Get the contents of each named graph containing an ontology.
      ontologyGraphResponseTasks =
        allOntologyMetadata.keys.map { ontologyIri =>
          val ontology: OntologyMetadataV2 =
            allOntologyMetadata(ontologyIri)
          val lastModificationDate: Option[Instant] =
            ontology.lastModificationDate
          val attachedToProject: Option[SmartIri] =
            ontology.projectIri

          // throw an exception if ontology doesn't have lastModificationDate property and isn't attached to system project
          lastModificationDate match {
            case None =>
              attachedToProject match {
                case Some(iri: SmartIri) =>
                  if (iri != KnoraProjectRepo.builtIn.SystemProject.id.value.toSmartIri) {
                    throw MissingLastModificationDateOntologyException(
                      s"Required property knora-base:lastModificationDate is missing in `$ontologyIri`",
                    )
                  }
                case _ => ()
              }
            case _ => ()
          }

          triplestore
            .query(Construct(sparql.v2.txt.getOntologyGraph(ontologyIri)))
            .flatMap(_.asExtended)
            .map(OntologyGraph(ontologyIri, _))
        }

      ontologyGraphs <- ZIO.collectAll(ontologyGraphResponseTasks)

      cacheData <- makeOntologyCache(allOntologyMetadata, ontologyGraphs)
      _         <- ZIO.logInfo(s"Loaded ${cacheData.ontologies.values.size} ontologies into cache")
    } yield ()

  /**
   * Given ontology metadata and ontology graphs read from the triplestore, constructs the ontology cache.
   *
   * @param allOntologyMetadata a map of ontology IRIs to ontology metadata.
   * @param ontologyGraphs      a list of ontology graphs.
   */
  private def makeOntologyCache(
    allOntologyMetadata: Map[SmartIri, OntologyMetadataV2],
    ontologyGraphs: Iterable[OntologyGraph],
  ): Task[OntologyCacheData] = ZIO.attempt {
    // Get the IRIs of all the entities in each ontology.

    // A map of ontology IRIs to class IRIs in each ontology.
    val classIrisPerOntology: Map[SmartIri, Set[SmartIri]] = OntologyHelpers.getEntityIrisFromOntologyGraphs(
      ontologyGraphs = ontologyGraphs,
      entityTypes = Set(OntologyConstants.Owl.Class),
    )

    // A map of ontology IRIs to property IRIs in each ontology.
    val propertyIrisPerOntology: Map[SmartIri, Set[SmartIri]] = OntologyHelpers.getEntityIrisFromOntologyGraphs(
      ontologyGraphs = ontologyGraphs,
      entityTypes = Set(
        OntologyConstants.Owl.ObjectProperty,
        OntologyConstants.Owl.DatatypeProperty,
        OntologyConstants.Owl.AnnotationProperty,
        OntologyConstants.Rdf.Property,
      ),
    )

    // A map of ontology IRIs to named individual IRIs in each ontology.
    val individualIrisPerOntology: Map[SmartIri, Set[SmartIri]] = OntologyHelpers.getEntityIrisFromOntologyGraphs(
      ontologyGraphs = ontologyGraphs,
      entityTypes = Set(OntologyConstants.Owl.NamedIndividual),
    )

    // Construct entity definitions.

    // A map of class IRIs to class definitions.
    val allClassDefs: Map[SmartIri, ClassInfoContentV2] = ontologyGraphs.flatMap { ontologyGraph =>
      OntologyHelpers.constructResponseToClassDefinitions(
        classIrisPerOntology(ontologyGraph.ontologyIri),
        ontologyGraph.constructResponse,
      )
    }.toMap

    // A map of property IRIs to property definitions.
    val allPropertyDefs: Map[SmartIri, PropertyInfoContentV2] = ontologyGraphs.flatMap { ontologyGraph =>
      OntologyHelpers.constructResponseToPropertyDefinitions(
        propertyIris = propertyIrisPerOntology(ontologyGraph.ontologyIri),
        constructResponse = ontologyGraph.constructResponse,
      )
    }.toMap

    // A map of OWL named individual IRIs to named individuals.
    val allIndividuals: Map[SmartIri, IndividualInfoContentV2] = ontologyGraphs.flatMap { ontologyGraph =>
      OntologyHelpers.constructResponseToIndividuals(
        individualIris = individualIrisPerOntology(ontologyGraph.ontologyIri),
        constructResponse = ontologyGraph.constructResponse,
      )
    }.toMap

    // Determine relations between entities.

    // A map of class IRIs to their immediate base classes.
    val directSubClassOfRelations: Map[SmartIri, Set[SmartIri]] = allClassDefs.map { case (classIri, classDef) =>
      classIri -> classDef.subClassOf
    }

    // A map of property IRIs to their immediate base properties.
    val directSubPropertyOfRelations: Map[SmartIri, Set[SmartIri]] = allPropertyDefs.map {
      case (propertyIri, propertyDef) => propertyIri -> propertyDef.subPropertyOf
    }

    val allClassIris    = allClassDefs.keySet
    val allPropertyIris = allPropertyDefs.keySet

    // A map in which each class IRI points to the full sequence of its base classes.
    val allSubClassOfRelations: Map[SmartIri, Seq[SmartIri]] = allClassIris.toSeq.map { classIri =>
      // get the hierarchically ordered base classes.
      val baseClasses: Seq[SmartIri] = OntologyUtil.getAllBaseDefs(classIri, directSubClassOfRelations)
      // prepend the classIri to the sequence of base classes because a class is also a subclass of itself.
      (classIri, classIri +: baseClasses)
    }.toMap

    // A map in which each class IRI points to the full set of its subclasses. A class is also
    // a subclass of itself.
    val allSuperClassOfRelations: Map[SmartIri, Set[SmartIri]] =
      OntologyHelpers.calculateSuperClassOfRelations(allSubClassOfRelations)

    // Make a map in which each property IRI points to the full set of its base properties. A property is also
    // a subproperty of itself.
    val allSubPropertyOfRelations: Map[SmartIri, Set[SmartIri]] = allPropertyIris.map { propertyIri =>
      (propertyIri, OntologyUtil.getAllBaseDefs(propertyIri, directSubPropertyOfRelations).toSet + propertyIri)
    }.toMap

    // Make a map in which each property IRI points to the full set of its subproperties. A property is also
    // a superproperty of itself.
    val allSuperPropertyOfRelations: Map[SmartIri, Set[SmartIri]] =
      OntologyHelpers.calculateSuperPropertiesOfRelations(allSubPropertyOfRelations)

    // A set of all subproperties of knora-base:resourceProperty.
    val allKnoraResourceProps: Set[SmartIri] = allPropertyIris.filter { prop =>
      val allPropSubPropertyOfRelations = allSubPropertyOfRelations(prop)
      prop == OntologyConstants.KnoraBase.ResourceProperty.toSmartIri ||
      allPropSubPropertyOfRelations.contains(OntologyConstants.KnoraBase.HasValue.toSmartIri) ||
      allPropSubPropertyOfRelations.contains(OntologyConstants.KnoraBase.HasLinkTo.toSmartIri)
    }

    // A set of all subproperties of knora-base:hasLinkTo.
    val allLinkProps: Set[SmartIri] = allPropertyIris.filter(prop =>
      allSubPropertyOfRelations(prop).contains(OntologyConstants.KnoraBase.HasLinkTo.toSmartIri),
    )

    // A set of all subproperties of knora-base:hasLinkToValue.
    val allLinkValueProps: Set[SmartIri] = allPropertyIris.filter(prop =>
      allSubPropertyOfRelations(prop).contains(OntologyConstants.KnoraBase.HasLinkToValue.toSmartIri),
    )

    // A set of all subproperties of knora-base:hasFileValue.
    val allFileValueProps: Set[SmartIri] = allPropertyIris.filter(prop =>
      allSubPropertyOfRelations(prop).contains(OntologyConstants.KnoraBase.HasFileValue.toSmartIri),
    )

    // A map of the cardinalities defined directly on each resource class. Each class IRI points to a map of
    // property IRIs to KnoraCardinalityInfo objects.
    val directClassCardinalities: Map[SmartIri, Map[SmartIri, KnoraCardinalityInfo]] = allClassDefs.map {
      case (classIri, classDef) =>
        classIri -> classDef.directCardinalities
    }

    // Allow each class to inherit cardinalities from its base classes.
    val classCardinalitiesWithInheritance: Map[SmartIri, Map[SmartIri, KnoraCardinalityInfo]] = allClassIris.map {
      resourceClassIri =>
        val resourceClassCardinalities: Map[SmartIri, KnoraCardinalityInfo] =
          OntologyHelpers.inheritCardinalitiesInLoadedClass(
            classIri = resourceClassIri,
            directSubClassOfRelations = directSubClassOfRelations,
            superPropertyLookup = allSubPropertyOfRelations.get,
            directClassCardinalities = directClassCardinalities,
          )

        resourceClassIri -> resourceClassCardinalities
    }.toMap

    // Construct a ReadClassInfoV2 for each class.
    val readClassInfos: Map[SmartIri, ReadClassInfoV2] = OntologyHelpers.makeReadClassInfos(
      classDefs = allClassDefs,
      directClassCardinalities = directClassCardinalities,
      classCardinalitiesWithInheritance = classCardinalitiesWithInheritance,
      allSubClassOfRelations = allSubClassOfRelations,
      superPropertyLookup = allSubPropertyOfRelations.get,
      allPropertyDefs = allPropertyDefs,
      allKnoraResourceProps = allKnoraResourceProps,
      allLinkProps = allLinkProps,
      allLinkValueProps = allLinkValueProps,
      allFileValueProps = allFileValueProps,
    )

    // Construct a ReadPropertyInfoV2 for each property definition.
    val readPropertyInfos: Map[SmartIri, ReadPropertyInfoV2] = OntologyHelpers.makeReadPropertyInfos(
      propertyDefs = allPropertyDefs,
      allSubPropertyOfRelations = allSubPropertyOfRelations,
      allKnoraResourceProps = allKnoraResourceProps,
      allLinkProps = allLinkProps,
      allLinkValueProps = allLinkValueProps,
      allFileValueProps = allFileValueProps,
    )

    // Construct a ReadIndividualV2 for each OWL named individual.
    val readIndividualInfos = OntologyHelpers.makeReadIndividualInfos(allIndividuals)

    // A ReadOntologyV2 for each ontology to be cached.
    val readOntologies: Map[SmartIri, ReadOntologyV2] = allOntologyMetadata.map {
      case (ontologyIri, ontologyMetadata) =>
        ontologyIri -> ReadOntologyV2(
          ontologyMetadata = ontologyMetadata,
          classes = readClassInfos.filter { case (classIri, _) =>
            classIri.getOntologyFromEntity == ontologyIri
          },
          properties = readPropertyInfos.filter { case (propertyIri, _) =>
            propertyIri.getOntologyFromEntity == ontologyIri
          },
          individuals = readIndividualInfos.filter { case (individualIri, _) =>
            individualIri.getOntologyFromEntity == ontologyIri
          },
          isWholeOntology = true,
        )
    }

    // A set of the IRIs of all properties used in cardinalities in standoff classes.
    val propertiesUsedInStandoffCardinalities: Set[SmartIri] = readClassInfos.flatMap { case (_, readClassInfo) =>
      if (readClassInfo.isStandoffClass) {
        readClassInfo.allCardinalities.keySet
      } else {
        Set.empty[SmartIri]
      }
    }.toSet

    // A set of the IRIs of all properties whose subject class constraint is a standoff class.
    val propertiesWithStandoffTagSubjects: Set[SmartIri] = readPropertyInfos.flatMap {
      case (propertyIri, readPropertyInfo) =>
        readPropertyInfo.entityInfoContent.getPredicateIriObject(
          OntologyConstants.KnoraBase.SubjectClassConstraint.toSmartIri,
        ) match {
          case Some(subjectClassConstraint: SmartIri) =>
            readClassInfos.get(subjectClassConstraint) match {
              case Some(subjectReadClassInfo: ReadClassInfoV2) =>
                if (subjectReadClassInfo.isStandoffClass) {
                  Some(propertyIri)
                } else {
                  None
                }

              case None => None
            }

          case None => None
        }
    }.toSet

    val classDefinedInOntology = classIrisPerOntology.flatMap { case (ontoIri, classIris) =>
      classIris.map(_ -> ontoIri)
    }
    val propertyDefinedInOntology = propertyIrisPerOntology.flatMap { case (ontoIri, propertyIris) =>
      propertyIris.map(_ -> ontoIri)
    }

    // Construct the ontology cache data.
    val ontologyCacheData: OntologyCacheData = OntologyCacheData(
      ontologies = new ErrorHandlingMap[SmartIri, ReadOntologyV2](readOntologies, key => s"Ontology not found: $key"),
      classToSuperClassLookup =
        new ErrorHandlingMap[SmartIri, Seq[SmartIri]](allSubClassOfRelations, key => s"Class not found: $key"),
      classToSubclassLookup =
        new ErrorHandlingMap[SmartIri, Set[SmartIri]](allSuperClassOfRelations, key => s"Class not found: $key"),
      subPropertyOfRelations =
        new ErrorHandlingMap[SmartIri, Set[SmartIri]](allSubPropertyOfRelations, key => s"Property not found: $key"),
      superPropertyOfRelations =
        new ErrorHandlingMap[SmartIri, Set[SmartIri]](allSuperPropertyOfRelations, key => s"Property not found: $key"),
      classDefinedInOntology =
        new ErrorHandlingMap[SmartIri, SmartIri](classDefinedInOntology, key => s"Class not found: $key"),
      propertyDefinedInOntology =
        new ErrorHandlingMap[SmartIri, SmartIri](propertyDefinedInOntology, key => s"Property not found: $key"),
      entityDefinedInOntology = new ErrorHandlingMap[SmartIri, SmartIri](
        propertyDefinedInOntology ++ classDefinedInOntology,
        key => s"Property not found: $key",
      ),
      standoffProperties = propertiesUsedInStandoffCardinalities ++ propertiesWithStandoffTagSubjects,
    )

    // Check property subject and object class constraints.

    readPropertyInfos.foreach { case (propertyIri, readPropertyInfo) =>
      val allSuperPropertyIris: Set[SmartIri] = allSubPropertyOfRelations.getOrElse(propertyIri, Set.empty[SmartIri])

      readPropertyInfo.entityInfoContent.getPredicateIriObject(
        OntologyConstants.KnoraBase.SubjectClassConstraint.toSmartIri,
      ) match {
        case Some(subjectClassConstraint) =>
          // Each property's subject class constraint, if provided, must be a subclass of the subject class constraints of all its base properties.
          OntologyCache.checkPropertyConstraint(
            cacheData = ontologyCacheData,
            internalPropertyIri = propertyIri,
            constraintPredicateIri = OntologyConstants.KnoraBase.SubjectClassConstraint.toSmartIri,
            constraintValueToBeChecked = subjectClassConstraint,
            allSuperPropertyIris = allSuperPropertyIris,
            errorSchema = InternalSchema,
            errorFun = { (msg: String) =>
              throw InconsistentRepositoryDataException(msg)
            },
          )

          // If the property is defined in a project-specific ontology, its subject class constraint, if provided, must be a Knora resource or standoff class.
          if (!propertyIri.isKnoraBuiltInDefinitionIri) {
            val baseClassesOfSubjectClassConstraint = allSubClassOfRelations(subjectClassConstraint)

            if (
              !(baseClassesOfSubjectClassConstraint.contains(OntologyConstants.KnoraBase.Resource.toSmartIri) ||
                baseClassesOfSubjectClassConstraint.contains(OntologyConstants.KnoraBase.StandoffTag.toSmartIri))
            ) {
              throw InconsistentRepositoryDataException(
                s"Property $propertyIri is defined in a project-specific ontology, but its knora-base:subjectClassConstraint, $subjectClassConstraint, is not a subclass of knora-base:Resource or knora-base:StandoffTag",
              )
            }
          }

        case None => ()
      }

      readPropertyInfo.entityInfoContent.getPredicateIriObject(
        OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri,
      ) match {
        case Some(objectClassConstraint) =>
          // Each property's object class constraint, if provided, must be a subclass of the object class constraints of all its base properties.
          OntologyCache.checkPropertyConstraint(
            cacheData = ontologyCacheData,
            internalPropertyIri = propertyIri,
            constraintPredicateIri = OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri,
            constraintValueToBeChecked = objectClassConstraint,
            allSuperPropertyIris = allSuperPropertyIris,
            errorSchema = InternalSchema,
            errorFun = { (msg: String) =>
              throw InconsistentRepositoryDataException(msg)
            },
          )

        case None =>
          // A resource property must have an object class constraint, unless it's knora-base:resourceProperty.
          if (
            readPropertyInfo.isResourceProp && propertyIri != OntologyConstants.KnoraBase.ResourceProperty.toSmartIri
          ) {
            throw InconsistentRepositoryDataException(s"Property $propertyIri has no knora-base:objectClassConstraint")
          }
      }
    }

    // Check references between ontologies.
    OntologyCache.checkReferencesBetweenOntologies(ontologyCacheData)

    // Update the cache.
    ontologyCacheData
  }.tap(cacheDataRef.set)

  /**
   * Gets the ontology data from the cache.
   *
   * @return an [[OntologyCacheData]]
   */
  override def getCacheData: Task[OntologyCacheData] = cacheDataRef.get

  /**
   * Given the IRI of a base class, updates inherited cardinalities in subclasses.
   *
   * @param baseClassIri the internal IRI of the base class.
   * @param cacheData    the ontology cache.
   * @return the updated ontology cache.
   */
  private def updateSubClasses(baseClassIri: SmartIri, cacheData: OntologyCacheData): OntologyCacheData = {
    // Get the class definitions of all the subclasses of the base class.

    val allSubClassIris: Set[SmartIri] = cacheData.getSubClassesOf(baseClassIri).get

    val allSubClasses: Set[ReadClassInfoV2] = allSubClassIris.map { subClassIri =>
      cacheData.ontologies(subClassIri.getOntologyFromEntity).classes(subClassIri)
    }

    // Filter them to get only the direct subclasses.

    val directSubClasses: Set[ReadClassInfoV2] = allSubClasses.filter { subClass =>
      subClass.entityInfoContent.subClassOf
        .contains(baseClassIri) && subClass.entityInfoContent.classIri != baseClassIri
    }

    // Iterate over the subclasses, updating cardinalities.
    val cacheDataWithUpdatedSubClasses = directSubClasses.foldLeft(cacheData) {
      case (cacheDataAcc: OntologyCacheData, directSubClass: ReadClassInfoV2) =>
        val directSubClassIri = directSubClass.entityInfoContent.classIri

        // Get the cardinalities that this subclass can inherit from its direct base classes.

        val inheritableCardinalities: Map[SmartIri, KnoraCardinalityInfo] =
          directSubClass.entityInfoContent.subClassOf.flatMap { baseClassIri =>
            for {
              ontology  <- cacheData.ontologies.get(baseClassIri.getOntologyFromEntity)
              classInfo <- ontology.classes.get(baseClassIri)
            } yield classInfo
          }.flatMap(_.allCardinalities).toMap

        // Override inherited cardinalities with directly defined cardinalities.
        val newInheritedCardinalities: Map[SmartIri, KnoraCardinalityInfo] = OntologyHelpers.overrideCardinalities(
          classIri = directSubClassIri,
          thisClassCardinalities = directSubClass.entityInfoContent.directCardinalities,
          inheritableCardinalities = inheritableCardinalities,
          superPropertyLookup = cacheData.getSuperPropertiesOf,
          errorSchema = ApiV2Complex,
          errorFun = { (msg: String) =>
            throw BadRequestException(msg)
          },
        )

        // Update the cache.

        val ontologyIri              = directSubClass.entityInfoContent.classIri.getOntologyFromEntity
        val ontology: ReadOntologyV2 = cacheDataAcc.ontologies(ontologyIri)

        val newKnoraResourceProperties = newInheritedCardinalities.keySet.filter(
          OntologyHelpers.isKnoraResourceProperty(_, cacheData),
        )

        val updatedOntology = ontology.copy(
          classes = ontology.classes + (directSubClassIri -> directSubClass.copy(
            inheritedCardinalities = newInheritedCardinalities,
            knoraResourceProperties = newKnoraResourceProperties,
          )),
        )

        cacheDataAcc.copy(
          ontologies = cacheDataAcc.ontologies + (ontologyIri -> updatedOntology),
        )
    }

    // Recurse to subclasses of subclasses.

    directSubClasses.map(_.entityInfoContent.classIri).foldLeft(cacheDataWithUpdatedSubClasses) {
      case (cacheDataAcc: OntologyCacheData, directSubClassIri: SmartIri) =>
        updateSubClasses(baseClassIri = directSubClassIri, cacheDataAcc)
    }
  }

  /**
   * Updates an existing ontology in the cache and ensures that the sub- and superclasses of a (presumably changed) class get updated correctly.
   *
   * @param updatedOntologyIri  the IRI of the updated ontology
   * @param updatedOntologyData the [[ReadOntologyV2]] representation of the updated ontology
   * @param updatedClassIri     the IRI of the changed class
   * @return the updated cache data
   */
  override def cacheUpdatedOntologyWithClass(
    updatedOntologyIri: SmartIri,
    updatedOntologyData: ReadOntologyV2,
    updatedClassIri: SmartIri,
  ): Task[OntologyCacheData] =
    cacheDataRef.updateAndGet { data =>
      updateSubClasses(
        updatedClassIri,
        OntologyCache.make(data.ontologies + (updatedOntologyIri -> updatedOntologyData)),
      )
    }

  /**
   * Updates an existing ontology in the cache. If a class has changed, use `cacheUpdatedOntologyWithClass()`.
   *
   * @param updatedOntologyIri  the IRI of the updated ontology
   * @param updatedOntologyData the [[ReadOntologyV2]] representation of the updated ontology
   * @return the updated cache data
   */
  override def cacheUpdatedOntology(
    updatedOntologyIri: SmartIri,
    updatedOntologyData: ReadOntologyV2,
  ): Task[OntologyCacheData] =
    cacheDataRef.updateAndGet(data => OntologyCache.make(data.ontologies + (updatedOntologyIri -> updatedOntologyData)))

  /**
   * Updates an existing ontology in the cache without updating the cache lookup maps. This should only be used if only the ontology metadata has changed.
   *
   * @param updatedOntologyIri  the IRI of the updated ontology
   * @param updatedOntologyData the [[ReadOntologyV2]] representation of the updated ontology
   * @return the updated cache data
   */
  override def cacheUpdatedOntologyWithoutUpdatingMaps(
    updatedOntologyIri: SmartIri,
    updatedOntologyData: ReadOntologyV2,
  ): Task[OntologyCacheData] =
    cacheDataRef.updateAndGet(data => data.copy(data.ontologies + (updatedOntologyIri -> updatedOntologyData)))

  /**
   * Deletes an ontology from the cache.
   *
   * @param ontologyIri the IRI of the ontology to delete
   * @return the updated cache data
   */
  override def deleteOntology(ontologyIri: SmartIri): Task[OntologyCacheData] =
    cacheDataRef.updateAndGet(data => OntologyCache.make(data.ontologies - ontologyIri))
}

object OntologyCacheLive {
  val layer = ZLayer.fromZIO(Ref.make(OntologyCacheData.Empty)) >>> ZLayer.derive[OntologyCacheLive]
}
