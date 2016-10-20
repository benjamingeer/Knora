/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.util

import java.io._

import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.model.{Resource, Statement}
import org.eclipse.rdf4j.rio.turtle._
import org.eclipse.rdf4j.rio.{RDFHandler, RDFWriter}
import org.knora.webapi.messages.v1.responder.valuemessages._
import org.knora.webapi.{IRI, InconsistentTriplestoreDataException, OntologyConstants}
import org.rogach.scallop._

import scala.collection.immutable.{SortedSet, TreeMap}
import scala.collection.mutable

/**
  * Updates the structure of Knora repository data to accommodate changes in Knora.
  */
object TransformData extends App {
    private val IsDeletedTransformationOption = "deleted"
    private val PermissionsTransformationOption = "permissions"
    private val MissingValueHasStringTransformationOption = "strings"
    private val StandoffTransformationOption = "standoff"
    private val AllTransformationsOption = "all"

    private val allTransformations = Vector(
        IsDeletedTransformationOption,
        PermissionsTransformationOption,
        MissingValueHasStringTransformationOption,
        StandoffTransformationOption
    )

    private val TempFilePrefix = "TransformData"
    private val TempFileSuffix = ".ttl"

    private val HasRestrictedViewPermission = "http://www.knora.org/ontology/knora-base#hasRestrictedViewPermission"
    private val HasViewPermission = "http://www.knora.org/ontology/knora-base#hasViewPermission"
    private val HasModifyPermission = "http://www.knora.org/ontology/knora-base#hasModifyPermission"
    private val HasDeletePermission = "http://www.knora.org/ontology/knora-base#hasDeletePermission"
    private val HasChangeRightsPermission = "http://www.knora.org/ontology/knora-base#hasChangeRightsPermission"
    private val HasChangeRightsPermisson = "http://www.knora.org/ontology/knora-base#hasChangeRightsPermisson" // This misspelling occurs in some old test data

    private val allOldPermissions = Set(
        HasRestrictedViewPermission,
        HasViewPermission,
        HasModifyPermission,
        HasDeletePermission,
        HasChangeRightsPermission,
        HasChangeRightsPermisson
    )

    private val oldPermissionIri2Abbreviation = Map(
        HasRestrictedViewPermission -> OntologyConstants.KnoraBase.RestrictedViewPermission,
        HasViewPermission -> OntologyConstants.KnoraBase.ViewPermission,
        HasModifyPermission -> OntologyConstants.KnoraBase.ModifyPermission,
        HasDeletePermission -> OntologyConstants.KnoraBase.DeletePermission,
        HasChangeRightsPermission -> OntologyConstants.KnoraBase.ChangeRightsPermission,
        HasChangeRightsPermisson -> OntologyConstants.KnoraBase.ChangeRightsPermission
    )

    private val StandardClassesWithoutIsDeleted = Set(
        OntologyConstants.KnoraBase.User,
        OntologyConstants.KnoraBase.UserGroup,
        OntologyConstants.KnoraBase.KnoraProject,
        OntologyConstants.KnoraBase.Institution,
        OntologyConstants.KnoraBase.ListNode
    )

    val conf = new Conf(args)
    val transformationOption = conf.transform()
    val inputFile = new File(conf.input())
    val outputFile = new File(conf.output())

    if (transformationOption == AllTransformationsOption) {
        runAllTransformations(inputFile, outputFile)
    } else {
        runTransformation(transformationOption, inputFile, outputFile)
    }

    /**
      * Runs all transformations, using temporary files as needed.
      *
      * @param inputFile  the input file.
      * @param outputFile the output file.
      */
    private def runAllTransformations(inputFile: File, outputFile: File): Unit = {
        /**
          * Associates a transformation with an input file and and output file, either of which may be a temporary file.
          *
          * @param transformation     the name of the transformation.
          * @param inputFileForTrans  the input file to be used for the transformation.
          * @param outputFileForTrans the output file to be used for the transformation.
          */
        case class TransformationWithFiles(transformation: String, inputFileForTrans: File, outputFileForTrans: File)

        // Make a list of transformations to be run, with an input file and an output file for each one, generating
        // temporary file names as needed.
        val transformationsWithFiles: Vector[TransformationWithFiles] = allTransformations.foldLeft(Vector.empty[TransformationWithFiles]) {
            case (acc, trans) =>
                // Is this is the first transformation?
                val inputFileForTrans = if (trans == allTransformations.head) {
                    // Yes. Use the user's input file as the input file for the transformation.
                    inputFile
                } else {
                    // No. Use the previous transformation's output file as the input file for this transformation.
                    acc.last.outputFileForTrans
                }

                // Is this the last transformation?
                val outputFileForTrans = if (trans == allTransformations.last) {
                    // Yes. Use the user's output file as the output file for the transformation.
                    outputFile
                } else {
                    // No. Use a temporary file.
                    File.createTempFile(TempFilePrefix, TempFileSuffix)
                }

                acc :+ TransformationWithFiles(
                    transformation = trans,
                    inputFileForTrans = inputFileForTrans,
                    outputFileForTrans = outputFileForTrans
                )
        }

        // Run all the transformations.
        for (transformationWithFiles <- transformationsWithFiles) {
            runTransformation(
                transformation = transformationWithFiles.transformation,
                inputFile = transformationWithFiles.inputFileForTrans,
                outputFile = transformationWithFiles.outputFileForTrans
            )
        }
    }

    /**
      * Runs the specified transformation.
      *
      * @param transformation the name of the transformation to be run.
      * @param inputFile      the input file.
      * @param outputFile     the output file.
      */
    private def runTransformation(transformation: String, inputFile: File, outputFile: File): Unit = {
        // println(s"Running transformation $transformation with inputFile $inputFile and outputFile $outputFile")

        val fileInputStream = new FileInputStream(inputFile)
        val fileOutputStream = new FileOutputStream(outputFile)
        val turtleParser = new TurtleParser()
        val turtleWriter = new TurtleWriter(fileOutputStream)

        val handler = transformation match {
            case IsDeletedTransformationOption => new IsDeletedHandler(turtleWriter)
            case PermissionsTransformationOption => new PermissionsHandler(turtleWriter)
            case MissingValueHasStringTransformationOption => new ValueHasStringHandler(turtleWriter)
            case StandoffTransformationOption => new StandoffHandler(turtleWriter)
            case "graph" => new GraphHandler(turtleWriter)
            case _ => throw new Exception(s"Unsupported transformation $transformation")
        }

        turtleParser.setRDFHandler(handler)
        turtleParser.parse(fileInputStream, inputFile.getAbsolutePath)
        fileOutputStream.close()
        fileInputStream.close()
    }


    /**
      * An abstract [[RDFHandler]] that collects all statements so they can be processed when the end of the
      * input file is reached. Subclasses need to implement only `endRDF`, which must call `turtleWriter.endRDF()`
      * when finished.
      *
      * @param turtleWriter an [[RDFWriter]] that writes to the output file.
      */
    protected abstract class StatementCollectingHandler(turtleWriter: RDFWriter) extends RDFHandler {
        protected val knoraIdUtil = new KnoraIdUtil

        /**
          * An instance of [[org.openrdf.model.ValueFactory]] for creating RDF statements.
          */
        protected val valueFactory = SimpleValueFactory.getInstance()

        /**
          * A collection of all the statements in the input file, grouped and sorted by subject IRI.
          */
        protected var statements = TreeMap.empty[IRI, Vector[Statement]]

        /**
          * A convenience method that returns the first object of the specified predicate in a list of statements.
          *
          * @param subjectStatements the statements to search.
          * @param predicateIri      the predicate to search for.
          * @return the first object found for the specified predicate.
          */
        protected def getObject(subjectStatements: Vector[Statement], predicateIri: IRI): Option[String] = {
            subjectStatements.find(_.getPredicate.stringValue == predicateIri).map(_.getObject.stringValue)
        }

        /**
          * Adds a statement to the collection `statements`.
          *
          * @param st the statement to be added.
          */
        override def handleStatement(st: Statement): Unit = {
            val subjectIri = st.getSubject.stringValue()
            val currentStatementsForSubject = statements.getOrElse(subjectIri, Vector.empty[Statement])

            if (st.getPredicate.stringValue == OntologyConstants.Rdf.Type) {
                // Make rdf:type the first statement for the subject.
                statements += (subjectIri -> (st +: currentStatementsForSubject))
            } else {
                statements += (subjectIri -> (currentStatementsForSubject :+ st))
            }
        }

        /**
          * Does nothing (comments are ignored).
          *
          * @param comment a Turtle comment.
          */
        override def handleComment(comment: String): Unit = {}

        /**
          * Writes the specified namepace declaration to the output file.
          *
          * @param prefix the namespace prefix.
          * @param uri    the namespace URI.
          */
        override def handleNamespace(prefix: String, uri: String): Unit = {
            turtleWriter.handleNamespace(prefix, uri)
        }

        /**
          * Calls `turtleWriter.startRDF()`.
          */
        override def startRDF(): Unit = {
            turtleWriter.startRDF()
        }
    }

    private class GraphHandler(turtleWriter: RDFWriter) extends StatementCollectingHandler(turtleWriter: RDFWriter) {
        val initialNode = "i:5"

        val names = Vector(
            "Alpha",
            "Bravo",
            "Charlie",
            "Delta",
            "Echo",
            "Foxtrot",
            "Golf",
            "Hotel",
            "India",
            "Juliet",
            "Kilo",
            "Lima",
            "Mike",
            "November",
            "Oscar",
            "Papa",
            "Quebec",
            "Romeo",
            "Sierra",
            "Tango",
            "Uniform",
            "Victor",
            "Whiskey",
            "X-ray",
            "Yankee",
            "Zulu"
        )

        case class LinkValueToCreate(linkValueIri: IRI, pred: IRI, obj: IRI)

        override def endRDF(): Unit = {
            val resourceIris: Map[IRI, IRI] = statements.keySet.filterNot(_.startsWith("p:")).map {
                iri =>
                    iri -> (if (iri == initialNode) "http://data.knora.org/anything/start" else knoraIdUtil.makeRandomResourceIri)
            }.toMap

            val resourceNames: Map[IRI, IRI] = resourceIris.keysIterator.toVector.sorted.zip(names).toMap

            println(MessageUtil.toSource(resourceNames.toVector.sortBy(_._1).map { case (oldName, newName) => s"$oldName: $newName" }))

            val statementsWithoutPropertyDefs = statements.filterNot {
                case (subjectIri: IRI, subjectStatements: Vector[Statement]) =>
                    subjectIri.startsWith("p:")
            }

            val statementsWithStartfirst: Vector[(IRI, Vector[Statement])] = Vector(initialNode -> statementsWithoutPropertyDefs(initialNode)) ++ (statementsWithoutPropertyDefs - initialNode).toVector

            statementsWithStartfirst.foreach {
                case (subjectIri: IRI, subjectStatements: Vector[Statement]) =>
                    val resourceIri = resourceIris(subjectIri)
                    val linkValuesToCreate = new mutable.ArrayBuffer[LinkValueToCreate]()

                    turtleWriter.handleStatement(
                        valueFactory.createStatement(
                            valueFactory.createIRI(resourceIri),
                            valueFactory.createIRI(OntologyConstants.Rdf.Type),
                            valueFactory.createIRI("http://www.knora.org/ontology/anything#Thing")
                        )
                    )

                    if (!subjectStatements.exists(_.getPredicate.stringValue.endsWith("deleted"))) {
                        turtleWriter.handleStatement(
                            valueFactory.createStatement(
                                valueFactory.createIRI(resourceIri),
                                valueFactory.createIRI(OntologyConstants.KnoraBase.IsDeleted),
                                valueFactory.createLiteral(false)
                            )
                        )
                    }

                    turtleWriter.handleStatement(
                        valueFactory.createStatement(
                            valueFactory.createIRI(resourceIri),
                            valueFactory.createIRI(OntologyConstants.KnoraBase.AttachedToUser),
                            valueFactory.createIRI("http://data.knora.org/users/9XBCrDV3SRa7kS1WwynB4Q")
                        )
                    )

                    turtleWriter.handleStatement(
                        valueFactory.createStatement(
                            valueFactory.createIRI(resourceIri),
                            valueFactory.createIRI(OntologyConstants.KnoraBase.AttachedToProject),
                            valueFactory.createIRI("http://data.knora.org/projects/anything")
                        )
                    )

                    turtleWriter.handleStatement(
                        valueFactory.createStatement(
                            valueFactory.createIRI(resourceIri),
                            valueFactory.createIRI(OntologyConstants.KnoraBase.HasPermissions),
                            valueFactory.createLiteral("V knora-base:KnownUser|M knora-base:ProjectMember")
                        )
                    )

                    turtleWriter.handleStatement(
                        valueFactory.createStatement(
                            valueFactory.createIRI(resourceIri),
                            valueFactory.createIRI(OntologyConstants.KnoraBase.CreationDate),
                            valueFactory.createLiteral(new java.util.Date())
                        )
                    )

                    for (statement <- subjectStatements) {
                        val oldPredicate = statement.getPredicate.stringValue
                        val oldObject = statement.getObject.stringValue

                        val newPredicate = if (oldPredicate == OntologyConstants.Rdfs.Label) {
                            oldPredicate
                        } else if (oldPredicate.endsWith("deleted")) {
                            OntologyConstants.KnoraBase.IsDeleted
                        } else if (oldPredicate.endsWith("exclude")) {
                            "http://www.knora.org/ontology/anything#isPartOfOtherThing"
                        } else {
                            "http://www.knora.org/ontology/anything#hasOtherThing"
                        }

                        val newObject = if (newPredicate == OntologyConstants.Rdfs.Label) {
                            valueFactory.createLiteral(oldObject.replace("label " + subjectIri, resourceNames(subjectIri)))
                        } else if (newPredicate == OntologyConstants.KnoraBase.IsDeleted) {
                            valueFactory.createLiteral(oldObject.toBoolean)
                        } else {
                            valueFactory.createIRI(resourceIris(oldObject))
                        }

                        if (!(newPredicate == OntologyConstants.Rdfs.Label || newPredicate == OntologyConstants.KnoraBase.IsDeleted)) {
                            val linkValueIri = knoraIdUtil.makeRandomValueIri(resourceIri)

                            turtleWriter.handleStatement(
                                valueFactory.createStatement(
                                    valueFactory.createIRI(resourceIri),
                                    valueFactory.createIRI(newPredicate + "Value"),
                                    valueFactory.createIRI(linkValueIri)
                                )
                            )

                            linkValuesToCreate += LinkValueToCreate(linkValueIri = linkValueIri, pred = newPredicate, obj = newObject.stringValue)
                        }

                        turtleWriter.handleStatement(
                            valueFactory.createStatement(
                                valueFactory.createIRI(resourceIri),
                                valueFactory.createIRI(newPredicate),
                                newObject
                            )
                        )
                    }

                    for (linkValueToCreate <- linkValuesToCreate) {
                        turtleWriter.handleStatement(
                            valueFactory.createStatement(
                                valueFactory.createIRI(linkValueToCreate.linkValueIri),
                                valueFactory.createIRI(OntologyConstants.Rdf.Type),
                                valueFactory.createIRI(OntologyConstants.KnoraBase.LinkValue)
                            )
                        )

                        turtleWriter.handleStatement(
                            valueFactory.createStatement(
                                valueFactory.createIRI(linkValueToCreate.linkValueIri),
                                valueFactory.createIRI(OntologyConstants.Rdf.Subject),
                                valueFactory.createIRI(resourceIri)
                            )
                        )

                        turtleWriter.handleStatement(
                            valueFactory.createStatement(
                                valueFactory.createIRI(linkValueToCreate.linkValueIri),
                                valueFactory.createIRI(OntologyConstants.Rdf.Predicate),
                                valueFactory.createIRI(linkValueToCreate.pred)
                            )
                        )

                        turtleWriter.handleStatement(
                            valueFactory.createStatement(
                                valueFactory.createIRI(linkValueToCreate.linkValueIri),
                                valueFactory.createIRI(OntologyConstants.Rdf.Object),
                                valueFactory.createIRI(linkValueToCreate.obj)
                            )
                        )

                        turtleWriter.handleStatement(
                            valueFactory.createStatement(
                                valueFactory.createIRI(linkValueToCreate.linkValueIri),
                                valueFactory.createIRI(OntologyConstants.KnoraBase.IsDeleted),
                                valueFactory.createLiteral(false)
                            )
                        )

                        turtleWriter.handleStatement(
                            valueFactory.createStatement(
                                valueFactory.createIRI(linkValueToCreate.linkValueIri),
                                valueFactory.createIRI(OntologyConstants.KnoraBase.AttachedToUser),
                                valueFactory.createIRI("http://data.knora.org/users/9XBCrDV3SRa7kS1WwynB4Q")
                            )
                        )

                        turtleWriter.handleStatement(
                            valueFactory.createStatement(
                                valueFactory.createIRI(linkValueToCreate.linkValueIri),
                                valueFactory.createIRI(OntologyConstants.KnoraBase.HasPermissions),
                                valueFactory.createLiteral("V knora-base:KnownUser|M knora-base:ProjectMember")
                            )
                        )

                        turtleWriter.handleStatement(
                            valueFactory.createStatement(
                                valueFactory.createIRI(linkValueToCreate.linkValueIri),
                                valueFactory.createIRI(OntologyConstants.KnoraBase.ValueCreationDate),
                                valueFactory.createLiteral(new java.util.Date())
                            )
                        )

                        turtleWriter.handleStatement(
                            valueFactory.createStatement(
                                valueFactory.createIRI(linkValueToCreate.linkValueIri),
                                valueFactory.createIRI(OntologyConstants.KnoraBase.ValueHasString),
                                valueFactory.createLiteral(linkValueToCreate.obj)
                            )
                        )

                        turtleWriter.handleStatement(
                            valueFactory.createStatement(
                                valueFactory.createIRI(linkValueToCreate.linkValueIri),
                                valueFactory.createIRI(OntologyConstants.KnoraBase.ValueHasRefCount),
                                valueFactory.createLiteral(1)
                            )
                        )
                    }

            }

            turtleWriter.endRDF()
        }
    }

    /**
      * Adds `knora-base:isDeleted false` to resources and values that don't have a `knora-base:isDeleted` predicate.
      *
      * @param turtleWriter an [[RDFWriter]] that writes to the output file.
      */
    private class IsDeletedHandler(turtleWriter: RDFWriter) extends StatementCollectingHandler(turtleWriter: RDFWriter) {
        override def endRDF(): Unit = {
            statements.foreach {
                case (subjectIri: IRI, subjectStatements: Vector[Statement]) =>
                    // Check whether the subject already has a knora-base:isDeleted predicate.
                    val hasIsDeleted = subjectStatements.exists(_.getPredicate.stringValue == OntologyConstants.KnoraBase.IsDeleted)
                    val isStandoff = subjectStatements.exists(_.getPredicate.stringValue == OntologyConstants.KnoraBase.StandoffTagHasStart)

                    subjectStatements.foreach {
                        statement =>
                            turtleWriter.handleStatement(statement)

                            // If this statement provides the rdf:type of the subject, and the subject doesn't have a
                            // knora-base:isDeleted predicate, check whether it needs one.
                            if (statement.getPredicate.stringValue == OntologyConstants.Rdf.Type && !hasIsDeleted) {
                                val rdfType = statement.getObject.stringValue

                                // If the rdf:type isn't one of the standard classes that can't be marked as deleted,
                                // and the subject isn't a standoff tag, assume it must be a Resource or Value,
                                // and add knora-base:isDeleted false.
                                if (!(StandardClassesWithoutIsDeleted.contains(rdfType) || isStandoff)) {
                                    val isDeletedStatement = valueFactory.createStatement(
                                        statement.getSubject,
                                        valueFactory.createIRI(OntologyConstants.KnoraBase.IsDeleted),
                                        valueFactory.createLiteral(false)
                                    )

                                    turtleWriter.handleStatement(isDeletedStatement)
                                }
                            }
                    }
            }

            turtleWriter.endRDF()
        }
    }

    /**
      * Transforms old-style Knora permissions statements into new-style permissions statements.
      */
    private class PermissionsHandler(turtleWriter: RDFWriter) extends StatementCollectingHandler(turtleWriter: RDFWriter) {
        override def endRDF(): Unit = {
            statements.foreach {
                case (subjectIri: IRI, subjectStatements: Vector[Statement]) =>
                    // Write the statements about each resource.
                    val subjectPermissions = subjectStatements.foldLeft(Map.empty[String, Set[IRI]]) {
                        case (acc, st) =>
                            val predicateStr = st.getPredicate.stringValue

                            // If a statement describes an old-style permission, save it.
                            if (allOldPermissions.contains(predicateStr)) {
                                val group = st.getObject.stringValue()
                                val abbreviation = oldPermissionIri2Abbreviation(predicateStr)
                                val currentGroupsForPermission = acc.getOrElse(abbreviation, Set.empty[IRI])
                                acc + (abbreviation -> (currentGroupsForPermission + group))
                            } else {
                                // Otherwise, write it.
                                turtleWriter.handleStatement(st)
                                acc
                            }
                    }

                    // Write the resource's permissions as a single statement.
                    if (subjectPermissions.nonEmpty) {
                        val permissionLiteral = PermissionUtilV1.formatPermissions(subjectPermissions)

                        val permissionStatement = valueFactory.createStatement(
                            valueFactory.createIRI(subjectIri),
                            valueFactory.createIRI(OntologyConstants.KnoraBase.HasPermissions),
                            valueFactory.createLiteral(permissionLiteral.get)
                        )

                        turtleWriter.handleStatement(permissionStatement)
                    }
            }

            turtleWriter.endRDF()
        }
    }

    /**
      * Adds missing `knora-base:valueHasString` statements.
      */
    private class ValueHasStringHandler(turtleWriter: RDFWriter) extends StatementCollectingHandler(turtleWriter: RDFWriter) {

        private def maybeWriteValueHasString(subjectIri: IRI, subjectStatements: Vector[Statement]): Unit = {
            val resourceClass = getObject(subjectStatements, OntologyConstants.Rdf.Type).get

            // Is this Knora value object?
            if (resourceClass.startsWith(OntologyConstants.KnoraBase.KnoraBasePrefixExpansion) && resourceClass.endsWith("Value")) {
                // Yes. Does it already have a valueHasString?
                val maybeValueHasStringStatement: Option[Statement] = subjectStatements.find(_.getPredicate.stringValue == OntologyConstants.KnoraBase.ValueHasString)

                if (maybeValueHasStringStatement.isEmpty) {
                    // No. Generate one.

                    val stringLiteral = resourceClass match {
                        case OntologyConstants.KnoraBase.IntValue => getObject(subjectStatements, OntologyConstants.KnoraBase.ValueHasInteger).get
                        case OntologyConstants.KnoraBase.BooleanValue => getObject(subjectStatements, OntologyConstants.KnoraBase.ValueHasBoolean).get
                        case OntologyConstants.KnoraBase.UriValue => getObject(subjectStatements, OntologyConstants.KnoraBase.ValueHasUri).get
                        case OntologyConstants.KnoraBase.DecimalValue => getObject(subjectStatements, OntologyConstants.KnoraBase.ValueHasDecimal).get

                        case OntologyConstants.KnoraBase.DateValue =>
                            val startJDC = getObject(subjectStatements, OntologyConstants.KnoraBase.ValueHasStartJDC).get
                            val endJDC = getObject(subjectStatements, OntologyConstants.KnoraBase.ValueHasEndJDC).get
                            val startPrecision = getObject(subjectStatements, OntologyConstants.KnoraBase.ValueHasStartPrecision).get
                            val endPrecision = getObject(subjectStatements, OntologyConstants.KnoraBase.ValueHasEndPrecision).get
                            val calendar = getObject(subjectStatements, OntologyConstants.KnoraBase.ValueHasCalendar).get

                            val jdcValue = JulianDayCountValueV1(
                                dateval1 = startJDC.toInt,
                                dateval2 = endJDC.toInt,
                                calendar = KnoraCalendarV1.lookup(calendar),
                                dateprecision1 = KnoraPrecisionV1.lookup(startPrecision),
                                dateprecision2 = KnoraPrecisionV1.lookup(endPrecision)
                            )

                            jdcValue.toString

                        case OntologyConstants.KnoraBase.ColorValue => getObject(subjectStatements, OntologyConstants.KnoraBase.ValueHasColor).get
                        case OntologyConstants.KnoraBase.GeomValue => getObject(subjectStatements, OntologyConstants.KnoraBase.ValueHasGeometry).get
                        case OntologyConstants.KnoraBase.StillImageFileValue => getObject(subjectStatements, OntologyConstants.KnoraBase.OriginalFilename).get
                        case OntologyConstants.KnoraBase.ListValue => getObject(subjectStatements, OntologyConstants.KnoraBase.ValueHasListNode).get

                        case OntologyConstants.KnoraBase.IntervalValue =>
                            val intervalStart = getObject(subjectStatements, OntologyConstants.KnoraBase.ValueHasIntervalStart).get
                            val intervalEnd = getObject(subjectStatements, OntologyConstants.KnoraBase.ValueHasIntervalEnd).get

                            val intervalValue = IntervalValueV1(
                                timeval1 = BigDecimal(intervalStart),
                                timeval2 = BigDecimal(intervalEnd)
                            )

                            intervalValue.toString

                        case OntologyConstants.KnoraBase.GeonameValue => getObject(subjectStatements, OntologyConstants.KnoraBase.ValueHasGeonameCode).get
                        case OntologyConstants.KnoraBase.LinkValue => getObject(subjectStatements, OntologyConstants.Rdf.Object).get

                        case _ => throw InconsistentTriplestoreDataException(s"Unsupported value type $resourceClass")
                    }

                    val valueHasStringStatement = valueFactory.createStatement(
                        valueFactory.createIRI(subjectIri),
                        valueFactory.createIRI(OntologyConstants.KnoraBase.ValueHasString),
                        valueFactory.createLiteral(stringLiteral)
                    )

                    turtleWriter.handleStatement(valueHasStringStatement)
                }
            }
        }

        override def endRDF(): Unit = {
            statements.foreach {
                case (subjectIri: IRI, subjectStatements: Vector[Statement]) =>
                    subjectStatements.foreach(st => turtleWriter.handleStatement(st))
                    maybeWriteValueHasString(subjectIri, subjectStatements)
            }

            turtleWriter.endRDF()
        }
    }

    /**
      * Changes standoff blank nodes into `StandoffTag` objects.
      */
    private class StandoffHandler(turtleWriter: RDFWriter) extends StatementCollectingHandler(turtleWriter: RDFWriter) {
        // The obsolete standoffHasAttribute predicate.
        private val StandoffHasAttribute = OntologyConstants.KnoraBase.KnoraBasePrefixExpansion + "standoffHasAttribute"

        // A Map of some old standoff class names to new ones.
        private val oldToNewClassIris = Map(
            OntologyConstants.KnoraBase.KnoraBasePrefixExpansion + "StandoffLink" -> OntologyConstants.KnoraBase.StandoffLinkTag,
            OntologyConstants.KnoraBase.KnoraBasePrefixExpansion + "StandoffHref" -> OntologyConstants.KnoraBase.StandoffUriTag
        )

        private val StandoffHasStart = OntologyConstants.KnoraBase.KnoraBasePrefixExpansion + "standoffHasStart"
        private val StandoffHasEnd = OntologyConstants.KnoraBase.KnoraBasePrefixExpansion + "standoffHasEnd"

        // A map of old standoff predicates to new ones.
        private val oldToNewPredicateIris = Map(
            StandoffHasStart -> OntologyConstants.KnoraBase.StandoffTagHasStart,
            StandoffHasEnd -> OntologyConstants.KnoraBase.StandoffTagHasEnd,
            OntologyConstants.KnoraBase.KnoraBasePrefixExpansion + "standoffHasLink" -> OntologyConstants.KnoraBase.StandoffTagHasLink,
            OntologyConstants.KnoraBase.KnoraBasePrefixExpansion + "standoffHasHref" -> OntologyConstants.KnoraBase.ValueHasUri
        )

        override def endRDF(): Unit = {
            // Make a flat list of all statements in the data.
            val allStatements: Vector[Statement] = statements.values.flatten.toVector

            // A Map of standoff tag IRIs to their containing text value IRIs.
            val standoffTagIrisToTextValueIris = new mutable.HashMap[IRI, IRI]()

            // A Map of old standoff tag blank node identifiers to new standoff tag IRIs.
            val standoffTagIriMap = mutable.HashMap[IRI, IRI]()

            // Populate standoffTagIriMap and standoffTagIrisToTextValueIris.
            for (statement <- allStatements) {
                val predicate = statement.getPredicate.stringValue()

                // Does this statement point to a standoff node?
                if (predicate == OntologyConstants.KnoraBase.ValueHasStandoff) {
                    // Yes.
                    val valueObjectIri = statement.getSubject.stringValue()
                    val oldStandoffIri = statement.getObject.stringValue()

                    // Is the object an old-style blank node identifier?
                    val standoffTagIri = if (!oldStandoffIri.contains("/standoff/")) {
                        // Yes. Make a new IRI for it.
                        val newStandoffIri = knoraIdUtil.makeRandomStandoffTagIri(valueObjectIri)
                        standoffTagIriMap += (oldStandoffIri -> newStandoffIri)
                        newStandoffIri
                    } else {
                        oldStandoffIri
                    }

                    standoffTagIrisToTextValueIris += (standoffTagIri -> valueObjectIri)
                }
            }

            // Replace blank node identifiers with IRIs.
            val statementsWithNewIris = allStatements.map {
                statement =>
                    val subject = statement.getSubject.stringValue()
                    val predicate = statement.getPredicate.stringValue()
                    val obj = statement.getObject
                    val objStr = statement.getObject.stringValue()

                    // If the subject is a standoff blank node identifier, replace it with the corresponding IRI.
                    val newSubject = standoffTagIriMap.getOrElse(subject, subject)

                    // If the object is a standoff blank node identifier, replace it with the corresponding IRI.
                    val newObject = predicate match {
                        case OntologyConstants.KnoraBase.ValueHasStandoff =>
                            valueFactory.createIRI(standoffTagIriMap.getOrElse(objStr, objStr))

                        case _ => obj
                    }

                    valueFactory.createStatement(
                        valueFactory.createIRI(newSubject),
                        valueFactory.createIRI(predicate),
                        newObject
                    )
            }

            // Separate out the statements about standoff tags, and group them by subject IRI.
            val standoffTagIris = standoffTagIriMap.values.toSet
            val (standoffStatements: Vector[Statement], nonStandoffStatements: Vector[Statement]) = statementsWithNewIris.partition(statement => standoffTagIris.contains(statement.getSubject.stringValue()))
            val groupedStandoffStatements: Map[Resource, Vector[Statement]] = standoffStatements.groupBy(_.getSubject)

            // A data structure to store the positions of linefeeds to be inserted in text values, replacing linebreak tags.
            val linefeedsToInsert = new mutable.HashMap[IRI, SortedSet[Int]]

            // IRIs of removed standoff linebreak tags.
            val removedLinebreakTagIris = new mutable.HashSet[IRI]

            // Remove the linebreak tags.
            val standoffWithoutLinebreakTags = groupedStandoffStatements.filter {
                case (tag, tagStatements) =>
                    getObject(tagStatements, StandoffHasAttribute) match {
                        case Some("linebreak") =>
                            // If we got a linebreak tag, delete it and save its position so we can add a linefeed to the text value later.
                            val tagIri = tag.stringValue()
                            val textValueIri = standoffTagIrisToTextValueIris(tagIri)
                            val currentLinefeedsForTextValue = linefeedsToInsert.getOrElse(textValueIri, SortedSet.empty[Int])
                            val linefeedPos = getObject(tagStatements, StandoffHasStart).get.toInt
                            linefeedsToInsert += (textValueIri -> (currentLinefeedsForTextValue + linefeedPos))
                            removedLinebreakTagIris += tagIri
                            false

                        case _ => true
                    }
            }

            // Transform the structure of each standoff tag.
            val transformedStandoff: Vector[Statement] = standoffWithoutLinebreakTags.flatMap {
                case (tag, tagStatements) =>
                    val oldTagClassIri = getObject(tagStatements, OntologyConstants.Rdf.Type).get
                    val textValueIri = standoffTagIrisToTextValueIris(tag.stringValue)
                    val linefeedsToInsertForTextValue = linefeedsToInsert.get(textValueIri)
                    val maybeTagName = getObject(tagStatements, StandoffHasAttribute)

                    val newTagClassIri = maybeTagName match {
                        case Some(tagName) =>
                            if (tagName == "_link") {
                                oldToNewClassIris(oldTagClassIri)
                            } else {
                                // Otherwise, generate the new class name from the tag name.
                                StandoffTagV1.EnumValueToIri(StandoffTagV1.lookup(tagName, () => throw InconsistentTriplestoreDataException(s"Unrecognised standoff tag name $tagName")))
                            }

                        case None => oldTagClassIri
                    }

                    // Throw away the standoffHasAttribute statement.
                    val tagStatementsWithoutStandoffHasAttribute = tagStatements.filterNot(_.getPredicate.stringValue == StandoffHasAttribute)

                    // Transform the remaining statements.
                    tagStatementsWithoutStandoffHasAttribute.map {
                        statement =>
                            val oldPredicate = statement.getPredicate.stringValue

                            oldPredicate match {
                                case OntologyConstants.Rdf.Type =>
                                    // Replace the old rdf:type with the new one.
                                    valueFactory.createStatement(
                                        statement.getSubject,
                                        statement.getPredicate,
                                        valueFactory.createIRI(newTagClassIri)
                                    )

                                case StandoffHasStart =>
                                    val currentStartPos = statement.getObject.stringValue.toInt

                                    val newStartPos = linefeedsToInsertForTextValue match {
                                        case Some(linefeeds) =>
                                            // Count the number of linefeeds whose positions are less than or equal
                                            // to the current value of standoffHasStart, and increment standoffHasStart
                                            // by that number.
                                            val linefeedsToInsertBeforeTag = linefeeds.count(_ <= currentStartPos)
                                            currentStartPos + linefeedsToInsertBeforeTag

                                        case None => currentStartPos
                                    }

                                    valueFactory.createStatement(
                                        statement.getSubject,
                                        valueFactory.createIRI(oldToNewPredicateIris(StandoffHasStart)),
                                        valueFactory.createLiteral(newStartPos)
                                    )

                                case StandoffHasEnd =>
                                    val currentEndPos = statement.getObject.stringValue.toInt

                                    val newEndPos = linefeedsToInsertForTextValue match {
                                        case Some(linefeeds) =>

                                            // Count the number of linefeeds whose positions are less than
                                            // to the current value of standoffHasEnd, and increment standoffHasEnd
                                            // by that number.
                                            val linefeedsToInsertBeforeTag = linefeeds.count(_ < currentEndPos)
                                            currentEndPos + linefeedsToInsertBeforeTag

                                        case None => currentEndPos
                                    }

                                    valueFactory.createStatement(
                                        statement.getSubject,
                                        valueFactory.createIRI(oldToNewPredicateIris(StandoffHasEnd)),
                                        valueFactory.createLiteral(newEndPos)
                                    )

                                case _ =>
                                    // Replace other old predicates with new ones.
                                    valueFactory.createStatement(
                                        statement.getSubject,
                                        valueFactory.createIRI(oldToNewPredicateIris.getOrElse(oldPredicate, oldPredicate)),
                                        statement.getObject
                                    )
                            }
                    }
            }.toVector

            // Fix the objects of valueHasString by replacing \r with INFORMATION SEPARATOR TWO and inserting
            // linefeeds that were previously represented as "linebreak" standoff tags.
            val transformedNonStandoffStatements = nonStandoffStatements.foldLeft(Vector.empty[Statement]) {
                case (acc, statement) =>
                    statement.getPredicate.stringValue match {
                        case OntologyConstants.KnoraBase.ValueHasString =>
                            // Replace \r with INFORMATION SEPARATOR TWO.
                            val objectWithSeparators = statement.getObject.stringValue.replace('\r', FormatConstants.INFORMATION_SEPARATOR_TWO)

                            // Insert linefeeds that were previously represented as "linebreak" standoff tags.
                            val objectWithLinefeeds: String = linefeedsToInsert.get(statement.getSubject.stringValue) match {
                                case Some(linefeeds) =>
                                    // Each time we add a linefeed to the string, the positions of all the remaining
                                    // linefeeds to be added must be shifted to the right. Do this shifting in advance,
                                    // by adding the position of each linefeed to its index.
                                    val adjustedLinefeeds: SortedSet[Int] = linefeeds.zipWithIndex.map {
                                        case (pos, index) => pos + index
                                    }

                                    val stringBuilder = new StringBuilder()

                                    // Make a list of the Unicode code points in the string. We can't just iterate over
                                    // the Char values in the string, because the JVM uses UTF-16 internally, and it uses
                                    // surrogate pairs of Chars for Unicode values that won't fit in a 16-bit Char. So
                                    // a single character could actually be represented by two UTF-16 Chars. We assume
                                    // that a standoff position is the index of a code point.

                                    val codePoints: Vector[String] = objectWithSeparators.codePoints.toArray.toVector.map {
                                        codePoint => new String(Character.toChars(codePoint))
                                    }

                                    // Iterate over the code points in the string, adding line feeds at the appropriate
                                    // positions.
                                    for ((codePointStr, pos) <- codePoints.zipWithIndex) {
                                        if (adjustedLinefeeds.contains(pos)) {
                                            stringBuilder.append('\n')
                                        }

                                        stringBuilder.append(codePointStr)
                                    }

                                    stringBuilder.toString

                                case None => objectWithSeparators
                            }

                            acc :+ valueFactory.createStatement(
                                statement.getSubject,
                                statement.getPredicate,
                                valueFactory.createLiteral(objectWithLinefeeds)
                            )

                        case OntologyConstants.KnoraBase.ValueHasStandoff if removedLinebreakTagIris.contains(statement.getObject.stringValue) =>
                            // This is a valueHasStandoff statement referring to a linebreak tag that was removed, so ignore it.
                            acc

                        case _ => acc :+ statement
                    }
            }

            // Recombine the transformed standoff tags with the rest of the statements in the data.
            val allTransformedStatements = transformedStandoff ++ transformedNonStandoffStatements

            // Sort them by subject IRI.
            val sortedStatements = allTransformedStatements.sortBy(_.getSubject.stringValue())

            // Write them to the output file.
            for (statement <- sortedStatements) {
                turtleWriter.handleStatement(statement)
            }

            turtleWriter.endRDF()
        }
    }

    /**
      * Parses command-line arguments.
      */
    class Conf(arguments: Seq[String]) extends ScallopConf(arguments) {
        banner(
            s"""
               |Updates the structure of Knora repository data to accommodate changes in Knora.
               |
               |Usage: org.knora.webapi.util.TransformData -t [$IsDeletedTransformationOption|$PermissionsTransformationOption|$MissingValueHasStringTransformationOption|$StandoffTransformationOption|$AllTransformationsOption] input output
            """.stripMargin)

        val transform = opt[String](
            required = true,
            validate = t => Set("graph", IsDeletedTransformationOption, PermissionsTransformationOption, MissingValueHasStringTransformationOption, StandoffTransformationOption, AllTransformationsOption).contains(t),
            descr = s"Selects a transformation. Available transformations: '$IsDeletedTransformationOption' (adds missing 'knora-base:isDeleted' statements), '$PermissionsTransformationOption' (combines old-style multiple permission statements into single permission statements), '$MissingValueHasStringTransformationOption' (adds missing valueHasString), '$StandoffTransformationOption' (transforms old-style standoff into new-style standoff), '$AllTransformationsOption' (all of the above)"
        )

        val input = trailArg[String](required = true, descr = "Input Turtle file")
        val output = trailArg[String](required = true, descr = "Output Turtle file")
        verify()
    }

}
