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

import akka.pattern._
import org.apache.jena.graph.Graph

import org.knora.webapi.exceptions.AssertionException
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.store.triplestoremessages.{InsertGraphDataContentRequest, InsertGraphDataContentResponse, NamedGraphDataRequest, NamedGraphDataResponse}
import org.knora.webapi.messages.util.{RdfFormatUtil, ResponderData}
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.metadatamessages._
import org.knora.webapi.responders.Responder.handleUnexpectedMessage
import org.knora.webapi.responders.{IriLocker, Responder}
import org.knora.webapi.{IRI, RdfMediaTypes}

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
        case putRequest: MetadataPutRequestV2 => putProjectMetadata(putRequest)
        case other => handleUnexpectedMessage(other, log, this.getClass.getName)
    }

    /**
     * GET metadata graph of a project.
     *
     * @param projectADM     the project whose metadata is requested.
     * @return a [[MetadataGetResponseV2]].
     */
    private def getProjectMetadata(projectADM: ProjectADM): Future[MetadataGetResponseV2] = {
        val graphIri: IRI =  stringFormatter.projectMetadataNamedGraphV2(projectADM)

        for {
            metadataGraph <- (storeManager ? NamedGraphDataRequest(graphIri)).mapTo[NamedGraphDataResponse]
        } yield MetadataGetResponseV2(metadataGraph.turtle)

    }

    /**
     * PUT metadata graph of a project. Every time a new metdata information is given for a project, it overwrites the
     * previous metadata graph.
     *
     * @param request  the request to put the metadata graph into the triplestore.
     * @return a [[SuccessResponseV2]].
     */
    private def putProjectMetadata(request: MetadataPutRequestV2): Future[SuccessResponseV2] = {
        val metadataGraphIRI: IRI =  stringFormatter.projectMetadataNamedGraphV2(request.projectADM)
        val graphContent = request.toTurtle

        def makeTaskFuture: Future[SuccessResponseV2] = {
            for {
                // Create the project metadata graph.
                _ <- (storeManager ?
                        InsertGraphDataContentRequest(
                            graphContent = graphContent,
                            graphName = metadataGraphIRI
                        )
                    ).mapTo[InsertGraphDataContentResponse]

                // Check if the created metadata graph has the same content as the one in the request.
                createdMetadata: MetadataGetResponseV2 <- getProjectMetadata(request.projectADM)

                createdMetadataGraph: Graph = RdfFormatUtil.parseToJenaGraph(
                    rdfStr = createdMetadata.turtle,
                    mediaType = RdfMediaTypes.`text/turtle`
                )

                _ = if (!createdMetadataGraph.isIsomorphicWith(request.graph)) {
                    throw AssertionException("Project metadata was stored, but is not correct. Please report this a bug.")
                }
            } yield SuccessResponseV2(s"Project metadata was stored for project <${request.projectIri}>.")
        }

        for {
            // Create the metadata graph holding an update lock on the graph IRI so that no other client can
            // create or update the same graph simultaneously.
            taskResult <- IriLocker.runWithIriLock(
                request.apiRequestID,
                metadataGraphIRI,
                () => makeTaskFuture
            )
        } yield taskResult
    }
}
