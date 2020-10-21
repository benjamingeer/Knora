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

package org.knora.webapi.responders.v2

import java.util.UUID

import akka.pattern._
import org.apache.jena.graph.Graph
import org.knora.webapi.{IRI, RdfMediaTypes}
import org.knora.webapi.exceptions.{BadRequestException}
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.store.triplestoremessages.{InsertGraphDataContentRequest, InsertGraphDataContentResponse, NamedGraphDataRequest, NamedGraphDataResponse}
import org.knora.webapi.messages.util.ResponderData
import org.knora.webapi.messages.v2.responder.{RdfRequestParser, SuccessResponseV2}
import org.knora.webapi.messages.v2.responder.metadatamessages._
import org.knora.webapi.responders.{IriLocker, Responder}
import org.knora.webapi.responders.Responder.handleUnexpectedMessage

import scala.concurrent.Future

/**
 * Responds to requests dealing with project metadata.
 *
 **/
class MetadataResponderV2(responderData: ResponderData) extends Responder(responderData) {

    /**
     * Receives a message of type [[MetadataResponderRequestV2]], and returns an appropriate response message.
     */
    def receive(msg: MetadataResponderRequestV2) = msg match {
        case getRequest: MetadataGetRequestV2 => getProjectMetadata(projectADM = getRequest.projectADM)
        case putRequest: MetadataPutRequestV2 => putProjectMetdata(putRequest)
        case other => handleUnexpectedMessage(other, log, this.getClass.getName)
    }

    /**
     * GET metadata graph of a project.
     *
     * @param projectADM     the project whose metadata is requested.
     * @return a [[MetadataGetResponseV2]].
     */
    def getProjectMetadata(projectADM: ProjectADM): Future[MetadataGetResponseV2] = {

        val graphIri: IRI =  stringFormatter.projectMetadataNamedGraphV2(projectADM)
        for {
            metadataGraph <- (storeManager ?
                                NamedGraphDataRequest(graphIri = graphIri)
                            ).mapTo[NamedGraphDataResponse]
        } yield MetadataGetResponseV2(
                    turtle = metadataGraph.turtle
                )
    }

    /**
     * PUT metadata graph of a project. Every time a new metdata information is given for a project, it overwrites the
     * previous metadata graph.
     *
     * @param request  the request to put the metadata graph into the triplestore.
     * @return a [[SuccessResponseV2]].
     */
    def putProjectMetdata(request: MetadataPutRequestV2): Future[SuccessResponseV2] = {
        val metadataGraphIRI: IRI =  stringFormatter.projectMetadataNamedGraphV2(request.projectADM)
        val graphContent = request.toTurtle
        def makeTaskFuture: Future[SuccessResponseV2] = {
            for {
                //create the project metadata graph
                _ <- (storeManager ?
                        InsertGraphDataContentRequest(graphContent = graphContent, graphName = metadataGraphIRI)
                    ).mapTo[InsertGraphDataContentResponse]

                //check if the created metadata graph has the same content as the one in the request.
                createdMetadata: MetadataGetResponseV2 <- getProjectMetadata(request.projectADM)
                createdMetadataGraph: Graph = RdfRequestParser.requestToJenaGraph(entityStr = createdMetadata.turtle,
                    contentType = RdfMediaTypes.`text/turtle`)

                _ = if (!createdMetadataGraph.isIsomorphicWith(request.graph)) {
                    throw BadRequestException("Graph content is not correct.")
                }
            } yield SuccessResponseV2(s"Metadata Graph $metadataGraphIRI created.")
        }
        for {

            // Create the metadata graph holding an update lock on the graph IRI so that no other graph with the same
            // name can be created simultaneously.
            taskResult <- IriLocker.runWithIriLock(
                request.apiRequestID,
                metadataGraphIRI,
                () => makeTaskFuture
            )
        } yield taskResult
    }
}
