/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

package org.knora.webapi.messages.util.rdf.jenaimpl

import org.apache.jena
import org.knora.webapi.IRI
import org.knora.webapi.exceptions.RdfProcessingException
import org.knora.webapi.feature.Feature
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.util.rdf._

import scala.collection.JavaConverters._


sealed trait JenaNode extends RdfNode {
    def node: jena.graph.Node
}

case class JenaBlankNode(node: jena.graph.Node) extends JenaNode with BlankNode {
    override def id: String = node.getBlankNodeId.getLabelString

    override def stringValue: String = id
}

case class JenaIriNode(node: jena.graph.Node) extends JenaNode with IriNode {
    override def iri: IRI = node.getURI

    override def stringValue: String = iri
}

case class JenaDatatypeLiteral(node: jena.graph.Node) extends JenaNode with DatatypeLiteral {
    override def value: String = node.getLiteralLexicalForm

    override def datatype: IRI = node.getLiteralDatatypeURI

    override def stringValue: String = value
}

case class JenaStringWithLanguage(node: jena.graph.Node) extends JenaNode with StringWithLanguage {
    override def value: String = node.getLiteralLexicalForm

    override def language: String = node.getLiteralLanguage

    override def stringValue: String = value
}

object JenaResource {
    def fromJena(node: jena.graph.Node): Option[RdfResource] = {
        if (node.isURI) {
            Some(JenaIriNode(node))
        } else if (node.isBlank) {
            Some(JenaBlankNode(node))
        } else {
            None
        }
    }
}

case class JenaStatement(quad: jena.sparql.core.Quad) extends Statement {
    override def subj: RdfResource = {
        val subj: jena.graph.Node = quad.getSubject
        JenaResource.fromJena(subj).getOrElse(throw RdfProcessingException(s"Unexpected statement subject: $subj"))
    }

    override def pred: IriNode = JenaIriNode(quad.getPredicate)

    override def obj: RdfNode = {
        val obj: jena.graph.Node = quad.getObject

        JenaResource.fromJena(obj) match {
            case Some(rdfResource) => rdfResource

            case None =>
                if (obj.isLiteral) {
                    val literal: jena.graph.impl.LiteralLabel = obj.getLiteral

                    if (literal.language != "") {
                        JenaStringWithLanguage(obj)
                    } else {
                        JenaDatatypeLiteral(obj)
                    }
                } else {
                    throw RdfProcessingException(s"Unexpected statement object: $obj")
                }
        }
    }

    override def context: Option[IRI] = {
        Option(quad.getGraph).map(_.getURI)
    }
}

/**
 * Provides extension methods for converting between Knora RDF API classes and Jena classes
 * (see [[https://docs.scala-lang.org/overviews/core/value-classes.html#extension-methods Extension Methods]]).
 */
object JenaConversions {

    implicit class ConvertibleJenaNode(val self: RdfNode) extends AnyVal {
        def asJenaNode: jena.graph.Node = {
            self match {
                case jenaRdfNode: JenaNode => jenaRdfNode.node
                case other => throw RdfProcessingException(s"$other is not a Jena node")
            }
        }
    }

    implicit class ConvertibleJenaQuad(val self: Statement) extends AnyVal {
        def asJenaQuad: jena.sparql.core.Quad = {
            self match {
                case jenaStatement: JenaStatement => jenaStatement.quad
                case other => throw RdfProcessingException(s"$other is not a Jena statement")
            }
        }
    }

    implicit class ConvertibleJenaModel(val self: RdfModel) extends AnyVal {
        def asJenaDataset: jena.query.Dataset = {
            self match {
                case model: JenaModel => model.getDataset
                case other => throw RdfProcessingException(s"${other.getClass.getName} is not a Jena RDF model")
            }
        }
    }

}

/**
 * Generates Jena nodes representing contexts.
 */
abstract class JenaContextFactory {
    /**
     * Converts a named graph IRI to a [[jena.graph.Node]].
     */
    protected def contextIriToNode(context: IRI): jena.graph.Node = {
        jena.graph.NodeFactory.createURI(context)
    }

    /**
     * Converts an optional named graph IRI to a [[jena.graph.Node]], converting
     * `None` to the IRI of Jena's default graph.
     */
    protected def contextNodeOrDefaultGraph(context: Option[IRI]): jena.graph.Node = {
        context.map(contextIriToNode).getOrElse(jena.sparql.core.Quad.defaultGraphIRI)
    }

    /**
     * Converts an optional named graph IRI to a [[jena.graph.Node]], converting
     * `None` to a wildcard that will match any graph.
     */
    protected def contextNodeOrWildcard(context: Option[IRI]): jena.graph.Node = {
        context.map(contextIriToNode).getOrElse(jena.graph.Node.ANY)
    }
}

/**
 * An implementation of [[RdfModel]] that wraps a [[jena.query.Dataset]].
 *
 * @param dataset the underlying Jena dataset.
 */
class JenaModel(private val dataset: jena.query.Dataset,
                private val nodeFactory: JenaNodeFactory) extends JenaContextFactory with RdfModel with Feature {

    import JenaConversions._

    private val datasetGraph: jena.sparql.core.DatasetGraph = dataset.asDatasetGraph

    /**
     * Returns the underlying [[jena.query.Dataset]].
     */
    def getDataset: jena.query.Dataset = dataset

    override def getNodeFactory: RdfNodeFactory = nodeFactory

    override def addStatement(statement: Statement): Unit = {
        datasetGraph.add(statement.asJenaQuad)
    }

    override def getStatements: Set[Statement] = datasetGraph.find.asScala.map(JenaStatement).toSet

    /**
     * Converts an optional [[RdfNode]] to a [[jena.graph.Node]], converting
     * `None` to a wildcard that will match any node.
     */
    private def asJenaNodeOrWildcard(node: Option[RdfNode]): jena.graph.Node = {
        node.map(_.asJenaNode).getOrElse(jena.graph.Node.ANY)
    }

    override def add(subj: RdfResource, pred: IriNode, obj: RdfNode, context: Option[IRI] = None): Unit = {
        datasetGraph.add(
            contextNodeOrDefaultGraph(context),
            subj.asJenaNode,
            pred.asJenaNode,
            obj.asJenaNode
        )
    }

    override def remove(subj: Option[RdfResource], pred: Option[IriNode], obj: Option[RdfNode], context: Option[IRI] = None): Unit = {
        datasetGraph.deleteAny(
            contextNodeOrWildcard(context),
            asJenaNodeOrWildcard(subj),
            asJenaNodeOrWildcard(pred),
            asJenaNodeOrWildcard(obj)
        )
    }

    override def removeStatement(statement: Statement): Unit = {
        datasetGraph.delete(statement.asJenaQuad)
    }

    override def find(subj: Option[RdfResource], pred: Option[IriNode], obj: Option[RdfNode], context: Option[IRI] = None): Set[Statement] = {
        datasetGraph.find(
            contextNodeOrWildcard(context),
            asJenaNodeOrWildcard(subj),
            asJenaNodeOrWildcard(pred),
            asJenaNodeOrWildcard(obj)
        ).asScala.map(JenaStatement).toSet
    }

    override def setNamespace(prefix: String, namespace: IRI): Unit = {
        def setNamespacesInGraph(graph: jena.graph.Graph): Unit = {
            val prefixMapping: jena.shared.PrefixMapping = graph.getPrefixMapping
            prefixMapping.setNsPrefix(prefix, namespace)
        }

        // Add the namespace to the default graph.
        setNamespacesInGraph(datasetGraph.getDefaultGraph)

        // Add the namespace to all the named graphs in the dataset.
        for (graphNode: jena.graph.Node <- datasetGraph.listGraphNodes.asScala) {
            val graph: jena.graph.Graph = datasetGraph.getGraph(graphNode)
            setNamespacesInGraph(graph)
        }
    }

    override def getNamespaces: Map[String, IRI] = {
        def getNamespacesFromGraph(graph: jena.graph.Graph): Map[String, IRI] = {
            val prefixMapping: jena.shared.PrefixMapping = graph.getPrefixMapping
            prefixMapping.getNsPrefixMap.asScala.toMap
        }

        // Get the namespaces from the default graph.
        val defaultGraphNamespaces: Map[String, IRI] = getNamespacesFromGraph(datasetGraph.getDefaultGraph)

        // Get the namespaces used in all the named graphs in the dataset.
        val namedGraphNamespaces: Map[String, IRI] = datasetGraph.listGraphNodes.asScala.flatMap {
            graphNode: jena.graph.Node =>
                val graph: jena.graph.Graph = datasetGraph.getGraph(graphNode)
                val prefixMapping: jena.shared.PrefixMapping = graph.getPrefixMapping
                prefixMapping.getNsPrefixMap.asScala
        }.toMap

        defaultGraphNamespaces ++ namedGraphNamespaces
    }

    override def isEmpty: Boolean = dataset.isEmpty

    override def getSubjects: Set[RdfResource] = {
        datasetGraph.find.asScala.map {
            quad: jena.sparql.core.Quad =>
                val subj: jena.graph.Node = quad.getSubject
                JenaResource.fromJena(subj).getOrElse(throw RdfProcessingException(s"Unexpected statement subject: $subj"))
        }.toSet
    }

    override def isIsomorphicWith(otherRdfModel: RdfModel): Boolean = {
        // Jena's DatasetGraph doesn't have a method for this, so we have to do it ourselves.

        val thatDatasetGraph: jena.sparql.core.DatasetGraph = otherRdfModel.asJenaDataset.asDatasetGraph

        // Get the IRIs of the named graphs.
        val thisModelNamedGraphIris: Set[jena.graph.Node] = datasetGraph.listGraphNodes.asScala.toSet
        val thatModelNamedGraphIris: Set[jena.graph.Node] = thatDatasetGraph.listGraphNodes.asScala.toSet

        // The two models are isomorphic if:
        // - They have the same set of named graph IRIs.
        // - The default graphs are isomorphic.
        // - The named graphs with the same IRIs are isomorphic.
        thisModelNamedGraphIris == thatModelNamedGraphIris &&
            datasetGraph.getDefaultGraph.isIsomorphicWith(thatDatasetGraph.getDefaultGraph) &&
            thisModelNamedGraphIris.forall {
                node => datasetGraph.getGraph(node).isIsomorphicWith(thatDatasetGraph.getGraph(node))
            }
    }

    override def getContexts: Set[IRI] = {
        datasetGraph.listGraphNodes.asScala.toSet.map {
            node: jena.graph.Node => node.getURI
        }
    }
}

/**
 * An implementation of [[RdfNodeFactory]] that creates Jena node implementation wrappers.
 */
class JenaNodeFactory extends JenaContextFactory with RdfNodeFactory {

    import JenaConversions._

    /**
     * Represents a custom Knora datatype (used in the simple schema), for registration
     * with Jena's TypeMapper.
     *
     * @param iri the IRI of the datatype.
     */
    private class KnoraDatatype(iri: IRI)  extends jena.datatypes.BaseDatatype(iri) {
        override def unparse(value: java.lang.Object): String = {
            value.toString
        }

        override def parse(lexicalForm: String): java.lang.Object = {
            lexicalForm
        }

        override def isEqual(value1: jena.graph.impl.LiteralLabel, value2: jena.graph.impl.LiteralLabel): Boolean = {
            value1.getDatatype == value2.getDatatype && value1.getValue.equals(value2.getValue)
        }
    }

    // Jena's registry of datatypes.
    private val typeMapper = jena.datatypes.TypeMapper.getInstance

    // Register Knora's custom datatypes.
    for (knoraDatatypeIri <- OntologyConstants.KnoraApiV2Simple.KnoraDatatypes) {
        typeMapper.registerDatatype(new KnoraDatatype(knoraDatatypeIri))
    }

    override def makeBlankNode: BlankNode = {
        JenaBlankNode(jena.graph.NodeFactory.createBlankNode)
    }

    override def makeBlankNodeWithID(id: String): BlankNode = {
        JenaBlankNode(jena.graph.NodeFactory.createBlankNode(id))
    }

    override def makeIriNode(iri: IRI): IriNode = {
        JenaIriNode(jena.graph.NodeFactory.createURI(iri))
    }

    override def makeDatatypeLiteral(value: String, datatype: IRI): DatatypeLiteral = {
        JenaDatatypeLiteral(jena.graph.NodeFactory.createLiteral(value, typeMapper.getTypeByName(datatype)))
    }

    override def makeStringWithLanguage(value: String, language: String): StringWithLanguage = {
        JenaStringWithLanguage(jena.graph.NodeFactory.createLiteral(value, language))
    }

    override def makeStatement(subj: RdfResource, pred: IriNode, obj: RdfNode, context: Option[IRI]): Statement = {
        JenaStatement(
            new jena.sparql.core.Quad(
                contextNodeOrDefaultGraph(context),
                subj.asJenaNode,
                pred.asJenaNode,
                obj.asJenaNode
            )
        )
    }

}

/**
 * A factory for creating instances of [[JenaModel]].
 */
class JenaModelFactory(private val nodeFactory: JenaNodeFactory) extends RdfModelFactory {
    override def makeEmptyModel: JenaModel = new JenaModel(
        dataset = jena.query.DatasetFactory.create,
        nodeFactory = nodeFactory
    )
}
