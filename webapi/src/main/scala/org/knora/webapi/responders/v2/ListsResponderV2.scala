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

package org.knora.webapi.responders.v2

import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v2.responder.listmessages._
import org.knora.webapi.responders.Responder
import org.knora.webapi.util.ActorUtil._

import scala.annotation.tailrec
import scala.collection.breakOut
import scala.concurrent.Future

/**
  * A responder that returns information about hierarchical lists.
  */
class ListsResponderV2 extends Responder {

    def receive = {
        case ListsGetRequestV2(projectIri, userProfile) => future2Message(sender(), listsGetRequestV2(projectIri, userProfile), log)
        case ListGetRequestV2(listIri, userProfile) => future2Message(sender(), listGetRequestV2(listIri, userProfile), log)
        case ListNodeInfoGetRequestV2(listIri, userProfile) => future2Message(sender(), listNodeInfoGetRequestV2(listIri, userProfile), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }


    /**
      * Gets all lists and returns them as a [[ReadListsSequenceV2]]. For performance reasons
      * (as lists can be very large), we only return the head of the list, i.e. the root node without
      * any children.
      *
      * @param projectIri  the IRI of the project the list belongs to.
      * @param userProfile the profile of the user making the request.
      * @return a [[ReadListsSequenceV2]].
      */
    def listsGetRequestV2(projectIri: Option[IRI], userProfile: UserProfileV1): Future[ReadListsSequenceV2] = {

        // log.debug("listsGetRequestV2")

        for {
            sparqlQuery <- Future(queries.sparql.v2.txt.getLists(
                triplestore = settings.triplestoreType,
                maybeProjectIri = projectIri
            ).toString())

            listsResponse <- (storeManager ? SparqlExtendedConstructRequest(sparqlQuery)).mapTo[SparqlExtendedConstructResponse]

            // _ = log.debug("listsGetRequestV2 - listsResponse: {}", listsResponse )

            // Seq(subjectIri, (objectIri -> Seq(stringWithOptionalLand))
            statements = listsResponse.statements.toList

            lists: Seq[ListRootNodeV2] = statements.map {
                case (listIri: IRI, propsMap: Map[IRI, Seq[StringV2]]) =>

                    ListRootNodeV2(
                        id = listIri,
                        projectIri = propsMap.get(OntologyConstants.KnoraBase.AttachedToProject).map(_.head.value),
                        labels = propsMap.getOrElse(OntologyConstants.Rdfs.Label, Seq.empty[StringV2]),
                        comments = propsMap.getOrElse(OntologyConstants.Rdfs.Comment, Seq.empty[StringV2]),
                        children = Seq.empty[ListChildNodeV2]
                    )
            }.toVector

        } yield ReadListsSequenceV2(items = lists)
    }

    /**
      * Retrieves a complete list (root and all children) from the triplestore and returns it as a [[ReadListsSequenceV2]].
      *
      * @param rootNodeIri the Iri if the root node of the list to be queried.
      * @param userProfile the profile of the user making the request.
      * @return a [[ReadListsSequenceV2]].
      */
    def listGetRequestV2(rootNodeIri: IRI, userProfile: UserProfileV1): Future[ReadListsSequenceV2] = {

        for {
            // this query will give us only the information about the root node.
            sparqlQuery <- Future(queries.sparql.v2.txt.getListNode(
                triplestore = settings.triplestoreType,
                nodeIri = rootNodeIri
            ).toString())

            listInfoResponse <- (storeManager ? SparqlExtendedConstructRequest(sparqlQuery)).mapTo[SparqlExtendedConstructResponse]

            // check to see if list could be found
            _ = if (listInfoResponse.statements.isEmpty) {
                throw NotFoundException(s"List not found: $rootNodeIri")
            }
            // _ = log.debug(s"listExtendedGetRequestV2 - statements: {}", MessageUtil.toSource(statements))

            // here we know that the list exists and it is fine if children is an empty list
            children: Seq[ListChildNodeV2] <- listGetChildrenV2(rootNodeIri, userProfile)

            // Map(subjectIri -> (objectIri -> Seq(stringWithOptionalLand))
            statements = listInfoResponse.statements
            list = statements.head match {
                case (nodeIri: IRI, propsMap: Map[IRI, Seq[StringV2]]) =>
                    ListRootNodeV2(
                        id = nodeIri,
                        projectIri = propsMap.get(OntologyConstants.KnoraBase.AttachedToProject).map(_.head.value),
                        labels = propsMap.getOrElse(OntologyConstants.Rdfs.Label, Seq.empty[StringV2]),
                        comments = propsMap.getOrElse(OntologyConstants.Rdfs.Comment, Seq.empty[StringV2]),
                        children = children
                        // status = groupedListProperties.get(OntologyConstants.KnoraBase.Status).map(_.head.toBoolean)
                    )
            }

            // _ = log.debug(s"listGetRequestV2 - list: {}", MessageUtil.toSource(list)")

        } yield ReadListsSequenceV2(items = Seq(list))
    }


    /**
      * Retrieves information about a single (child) node.
      *
      * @param nodeIri the Iri if the child node to be queried.
      * @param userProfile the profile of the user making the request.
      * @return a [[ReadListsSequenceV2]].
      */
    def listNodeInfoGetRequestV2(nodeIri: IRI, userProfile: UserProfileV1): Future[ReadListsSequenceV2] = {
        for {
            sparqlQuery <- Future(queries.sparql.v2.txt.getListNode(
                triplestore = settings.triplestoreType,
                nodeIri = nodeIri
            ).toString())

            // _ = log.debug("listNodeInfoGetRequestV2 - sparqlQuery: {}", sparqlQuery)

            listNodeResponse <- (storeManager ? SparqlExtendedConstructRequest(sparqlQuery)).mapTo[SparqlExtendedConstructResponse]

            _ = if (listNodeResponse.statements.isEmpty) {
                throw NotFoundException(s"List node not found: $nodeIri")
            }

            // Map(subjectIri -> (objectIri -> Seq(stringWithOptionalLand))
            statements = listNodeResponse.statements

            // _ = log.debug(s"listNodeInfoGetRequestV2 - statements: {}", MessageUtil.toSource(statements))

            node: ListNodeV2 = statements.head match {
                case (nodeIri: IRI, propsMap: Map[IRI, Seq[StringV2]]) =>

                    if (propsMap.get(OntologyConstants.KnoraBase.HasRootNode).nonEmpty) {
                        // we have a child node, as only the child node has this property attached
                        ListChildNodeV2 (
                            id = nodeIri,
                            name = propsMap.get(OntologyConstants.KnoraBase.ListNodeName).map(_.head.value),
                            labels = propsMap.getOrElse(OntologyConstants.Rdfs.Label, Seq.empty[StringV2]),
                            comments = propsMap.getOrElse(OntologyConstants.Rdfs.Comment, Seq.empty[StringV2]),
                            children = Seq.empty[ListChildNodeV2],
                            position = propsMap.get(OntologyConstants.KnoraBase.ListNodePosition).map(_.head.value.toInt)
                            // status = groupedListProperties.get(OntologyConstants.KnoraBase.Status).map(_.head.toBoolean)
                        )
                    } else {
                        ListRootNodeV2(
                            id = nodeIri,
                            projectIri = propsMap.get(OntologyConstants.KnoraBase.AttachedToProject).map(_.head.value),
                            labels = propsMap.getOrElse(OntologyConstants.Rdfs.Label, Seq.empty[StringV2]),
                            comments = propsMap.getOrElse(OntologyConstants.Rdfs.Comment, Seq.empty[StringV2]),
                            children = Seq.empty[ListChildNodeV2]
                            // status = groupedListProperties.get(OntologyConstants.KnoraBase.Status).map(_.head.toBoolean)
                        )
                    }
            }

            // _ = log.debug(s"listNodeInfoGetRequestV2 - node: {}", MessageUtil.toSource(node))

        } yield ReadListsSequenceV2(Seq(node))
    }


    /**
      * Retrieves a list from the triplestore and returns it as a sequence of child nodes.
      *
      * @param rootNodeIri the Iri of the root node of the list to be queried.
      * @param userProfile the profile of the user making the request.
      * @return a sequence of [[ListNodeV2]].
      */
    private def listGetChildrenV2(rootNodeIri: IRI, userProfile: UserProfileV1): Future[Seq[ListChildNodeV2]] = {

        /**
          * Compares the `position`-values of two nodes
          *
          * @param list1 node in a list
          * @param list2 node in the same list
          * @return true if the `position` of list1 is lower than the one of list2
          */
        def orderNodes(list1: ListChildNodeV2, list2: ListChildNodeV2): Boolean = {
            if (list1.position.nonEmpty && list2.position.nonEmpty) {
                list1.position.get < list2.position.get
            } else {
                true
            }
        }

        /**
          * This function recursively transforms SPARQL query results representing a hierarchical list into a [[ListNodeV2]].
          *
          * @param nodeIri          the IRI of the node to be created.
          * @param groupedByNodeIri a [[Map]] in which each key is the IRI of a node in the hierarchical list, and each value is a [[Seq]]
          *                         of SPARQL query results representing that node's children.
          * @return a [[ListNodeV2]].
          */
        def createListChildNodeV2(nodeIri: IRI, groupedByNodeIri: Map[IRI, Seq[VariableResultsRow]], level: Int): ListChildNodeV2 = {

            val childRows = groupedByNodeIri(nodeIri)

            /*
                childRows has the following structure:

                For each child of the parent node (represented by nodeIri), there is a row that provides the child's IRI.
                The information about the parent node is repeated in each row.
                Therefore, we can just access the first row for all the information about the parent.

                node                                      position	   nodeName   label         child

                http://data.knora.org/lists/10d16738cc    3            4          VOLKSKUNDE    http://data.knora.org/lists/a665b90cd
                http://data.knora.org/lists/10d16738cc    3            4          VOLKSKUNDE    http://data.knora.org/lists/4238eabcc
                http://data.knora.org/lists/10d16738cc    3            4          VOLKSKUNDE    http://data.knora.org/lists/a94bb71cc
                http://data.knora.org/lists/10d16738cc    3            4          VOLKSKUNDE    http://data.knora.org/lists/db6b61e4cc
                http://data.knora.org/lists/10d16738cc    3            4          VOLKSKUNDE    http://data.knora.org/lists/749fb41dcd
                http://data.knora.org/lists/10d16738cc    3            4          VOLKSKUNDE    http://data.knora.org/lists/dd3757cd

                In any case, childRows has at least one element (we know that at least one entry exists for a node without children).

             */

            val firstRowMap = childRows.head.rowMap

            ListChildNodeV2(
                id = nodeIri,
                name = firstRowMap.get("nodeName"),
                labels = if (firstRowMap.get("label").nonEmpty) {
                    Seq(StringV2(firstRowMap.get("label").get))
                } else {
                    Seq.empty[StringV2]
                },
                comments = Seq.empty[StringV2],
                children = if (firstRowMap.get("child").isEmpty) {
                    // If this node has no children, childRows will just contain one row with no value for "child".
                    Seq.empty[ListChildNodeV2]
                } else {
                    // Recursively get the child nodes.
                    childRows.map(childRow => createListChildNodeV2(childRow.rowMap("child"), groupedByNodeIri, level + 1)).sortWith(orderNodes)
                },
                position = firstRowMap.get("position").map(_.toInt)
            )
        }

        // TODO: Rewrite using a construct sparql query
        for {
            listQuery <- Future {
                queries.sparql.v2.txt.getList(
                    triplestore = settings.triplestoreType,
                    rootNodeIri = rootNodeIri,
                    preferredLanguage = userProfile.userData.lang,
                    fallbackLanguage = settings.fallbackLanguage
                ).toString()
            }
            listQueryResponse: SparqlSelectResponse <- (storeManager ? SparqlSelectRequest(listQuery)).mapTo[SparqlSelectResponse]

            // Group the results to map each node to the SPARQL query results representing its children.
            groupedByNodeIri: Map[IRI, Seq[VariableResultsRow]] = listQueryResponse.results.bindings.groupBy(row => row.rowMap("node"))

            rootNodeChildren = groupedByNodeIri.getOrElse(rootNodeIri, Seq.empty[VariableResultsRow])

            children: Seq[ListChildNodeV2] = if (rootNodeChildren.head.rowMap.get("child").isEmpty) {
                // The root node has no children, so we return an empty list.
                Seq.empty[ListChildNodeV2]
            } else {
                // Process each child of the root node.
                rootNodeChildren.map {
                    childRow => createListChildNodeV2(childRow.rowMap("child"), groupedByNodeIri, 0)
                }.sortWith(orderNodes)
            }

        } yield children
    }

    /**
      * Provides the path to a particular hierarchical list node.
      *
      * @param queryNodeIri the IRI of the node whose path is to be queried.
      * @param userProfile  the profile of the user making the request.
      */
    private def getNodePathResponseV2(queryNodeIri: IRI, userProfile: UserProfileV1): Future[ReadListsSequenceV2] = {
        /**
          * Recursively constructs the path to a node.
          *
          * @param node      the IRI of the node whose path is to be constructed.
          * @param nodeMap   a [[Map]] of node IRIs to query result row data, in the format described below.
          * @param parentMap a [[Map]] of child node IRIs to parent node IRIs.
          * @param path      the path constructed so far.
          * @return the complete path to `node`.
          */
        @tailrec
        def makePath(node: IRI, nodeMap: Map[IRI, Map[String, String]], parentMap: Map[IRI, IRI], path: Seq[ListChildNodeV2]): Seq[ListChildNodeV2] = {
            // Get the details of the node.
            val nodeData = nodeMap(node)

            // Construct a NodePathElementV2 containing those details.
            val pathElement = ListChildNodeV2(
                id = nodeData("node"),
                name = nodeData.get("nodeName"),
                labels = if (nodeData.contains("label")) {
                    Seq(StringV2(nodeData("label")))
                } else {
                    Seq.empty[StringV2]
                },
                comments = Seq.empty[StringV2],
                children = Seq.empty[ListChildNodeV2],
                position = None
            )

            // Add it to the path.
            val newPath = pathElement +: path

            // Does this node have a parent?
            parentMap.get(pathElement.id) match {
                case Some(parentIri) =>
                    // Yes: recurse.
                    makePath(parentIri, nodeMap, parentMap, newPath)

                case None =>
                    // No: the path is complete.
                    newPath
            }
        }

        // TODO: Rewrite using a construct sparql query
        for {
            nodePathQuery <- Future {
                queries.sparql.v2.txt.getNodePath(
                    triplestore = settings.triplestoreType,
                    queryNodeIri = queryNodeIri,
                    preferredLanguage = userProfile.userData.lang,
                    fallbackLanguage = settings.fallbackLanguage
                ).toString()
            }
            nodePathResponse: SparqlSelectResponse <- (storeManager ? SparqlSelectRequest(nodePathQuery)).mapTo[SparqlSelectResponse]

            /*

            If we request the path to the node <http://data.knora.org/lists/c7f07a3fc1> ("Heidi Film"), the response has the following format:

            node                                        nodeName     label                     child
            <http://data.knora.org/lists/c7f07a3fc1>    1            Heidi Film
            <http://data.knora.org/lists/2ebd2706c1>    7            FILM UND FOTO             <http://data.knora.org/lists/c7f07a3fc1>
            <http://data.knora.org/lists/691eee1cbe>    4KUN         ART                       <http://data.knora.org/lists/2ebd2706c1>

            The order of the rows is arbitrary. Now we need to reconstruct the path based on the parent-child relationships between
            nodes.

            */

            // A Map of node IRIs to query result rows.
            nodeMap: Map[IRI, Map[String, String]] = nodePathResponse.results.bindings.map {
                row => row.rowMap("node") -> row.rowMap
            }(breakOut)

            // A Map of child node IRIs to parent node IRIs.
            parentMap: Map[IRI, IRI] = nodePathResponse.results.bindings.foldLeft(Map.empty[IRI, IRI]) {
                case (acc, row) =>
                    row.rowMap.get("child") match {
                        case Some(child) => acc + (child -> row.rowMap("node"))
                        case None => acc
                    }
            }
        } yield ReadListsSequenceV2(items = makePath(queryNodeIri, nodeMap, parentMap, Nil))
    }
}
