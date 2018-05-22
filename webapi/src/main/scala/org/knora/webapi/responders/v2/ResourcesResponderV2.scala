/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

import java.io.File

import akka.pattern._
import org.apache.commons.io.FileUtils
import org.knora.webapi.OntologyConstants.KnoraBase
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages.{SparqlConstructRequest, SparqlConstructResponse}
import org.knora.webapi.messages.v2.responder.resourcemessages.{ResourcesGetRequestV2, ResourcesPreviewGetRequestV2, _}
import org.knora.webapi.messages.v2.responder.standoffmessages.{GetMappingRequestV2, GetMappingResponseV2}
import org.knora.webapi.responders.ResponderWithStandoffV2
import org.knora.webapi.twirl.StandoffTagV2
import org.knora.webapi.util.ActorUtil.{future2Message, handleUnexpectedMessage}
import org.knora.webapi.util.ConstructResponseUtilV2
import org.knora.webapi.util.ConstructResponseUtilV2.{MappingAndXSLTransformation, ResourceWithValueRdfData}
import org.knora.webapi.util.standoff.{StandoffTagUtilV2, XMLUtil}
import org.knora.webapi.{IRI, NotFoundException}

import scala.concurrent.Future

class ResourcesResponderV2 extends ResponderWithStandoffV2 {

    def receive = {
        case ResourcesGetRequestV2(resIris, requestingUser) => future2Message(sender(), getResources(resIris, requestingUser), log)
        case ResourcesPreviewGetRequestV2(resIris, requestingUser) => future2Message(sender(), getResourcePreview(resIris, requestingUser), log)
        case ResourceTEIGetRequestV2(resIri, requestingUser) => future2Message(sender(), getResourceAsTEI(resIri, requestingUser), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }


    /**
      * Gets the requested resources from the triplestore.
      *
      * @param resourceIris the Iris of the requested resources.
      * @return a [[Map[IRI, ResourceWithValueRdfData]]] representing the resources.
      */
    private def getResourcesFromTriplestore(resourceIris: Seq[IRI], preview: Boolean, requestingUser: UserADM): Future[Map[IRI, ResourceWithValueRdfData]] = {

        // eliminate duplicate Iris
        val resourceIrisDistinct: Seq[IRI] = resourceIris.distinct

        for {
            resourceRequestSparql <- Future(queries.sparql.v2.txt.getResourcePropertiesAndValues(
                triplestore = settings.triplestoreType,
                resourceIris = resourceIrisDistinct,
                preview
            ).toString())

            // _ = println(resourceRequestSparql)

            resourceRequestResponse: SparqlConstructResponse <- (storeManager ? SparqlConstructRequest(resourceRequestSparql)).mapTo[SparqlConstructResponse]

            // separate resources and values
            queryResultsSeparated: Map[IRI, ResourceWithValueRdfData] = ConstructResponseUtilV2.splitMainResourcesAndValueRdfData(constructQueryResults = resourceRequestResponse, requestingUser = requestingUser)

            // check if all the requested resources were returned
            requestedButMissing = resourceIrisDistinct.toSet -- queryResultsSeparated.keySet

            _ = if (requestedButMissing.nonEmpty) {
                throw NotFoundException(
                    s"""Not all the requested resources from ${resourceIrisDistinct.mkString(", ")} could not be found:
                        maybe you do not have the right to see all of them or some are marked as deleted.
                        Missing: ${requestedButMissing.mkString(", ")}""".stripMargin)

            }
        } yield queryResultsSeparated

    }

    /**
      * Get one or several resources and return them as a sequence.
      *
      * @param resourceIris   the resources to query for.
      * @param requestingUser the the client making the request.
      * @return a [[ReadResourcesSequenceV2]].
      */
    private def getResources(resourceIris: Seq[IRI], requestingUser: UserADM): Future[ReadResourcesSequenceV2] = {

        // eliminate duplicate Iris
        val resourceIrisDistinct: Seq[IRI] = resourceIris.distinct

        for {

            queryResultsSeparated: Map[IRI, ResourceWithValueRdfData] <- getResourcesFromTriplestore(resourceIris = resourceIris, preview = false, requestingUser = requestingUser)

            // get the mappings
            mappingsAsMap <- getMappingsFromQueryResultsSeparated(queryResultsSeparated, requestingUser)

            resourcesResponse: Vector[ReadResourceV2] = resourceIrisDistinct.map {
                (resIri: IRI) =>
                    ConstructResponseUtilV2.createFullResourceResponse(resIri, queryResultsSeparated(resIri), mappings = mappingsAsMap)
            }.toVector

        } yield ReadResourcesSequenceV2(numberOfResources = resourceIrisDistinct.size, resources = resourcesResponse)

    }

    /**
      * Get the preview of a resource.
      *
      * @param resourceIris   the resource to query for.
      * @param requestingUser the the client making the request.
      * @return a [[ReadResourcesSequenceV2]].
      */
    private def getResourcePreview(resourceIris: Seq[IRI], requestingUser: UserADM): Future[ReadResourcesSequenceV2] = {

        // eliminate duplicate Iris
        val resourceIrisDistinct: Seq[IRI] = resourceIris.distinct

        for {
            queryResultsSeparated: Map[IRI, ResourceWithValueRdfData] <- getResourcesFromTriplestore(resourceIris = resourceIris, preview = true, requestingUser = requestingUser)

            resourcesResponse: Vector[ReadResourceV2] = resourceIrisDistinct.map {
                (resIri: IRI) =>
                    ConstructResponseUtilV2.createFullResourceResponse(resIri, queryResultsSeparated(resIri), mappings = Map.empty[IRI, MappingAndXSLTransformation])
            }.toVector

        } yield ReadResourcesSequenceV2(numberOfResources = resourceIrisDistinct.size, resources = resourcesResponse)

    }

    private def getResourceAsTEI(resourceIri: IRI, requestingUser: UserADM) = {

        // proof of concept: works only for test resource:
        // http://rdfh.ch/0001/thing_with_richtext_with_markup

        for {

            // get requested resource
            queryResultsSeparated <- getResourcesFromTriplestore(resourceIris = Seq(resourceIri), preview = false, requestingUser = requestingUser)

            // _ = println(queryResultsSeparated)

            // get TEI mapping
            teiMapping: GetMappingResponseV2 <- (responderManager ? GetMappingRequestV2(mappingIri = "http://rdfh.ch/projects/0001/mappings/TEIMapping", userProfile = requestingUser)).mapTo[GetMappingResponseV2]

            // _ = println(teiMapping)

            // get value object representing the text value with standoff
            valueObject: ConstructResponseUtilV2.ValueRdfData = queryResultsSeparated(resourceIri).valuePropertyAssertions("http://www.knora.org/ontology/0001/anything#hasRichtext").head

            // convert standoff assertions to standoff tags
            standoffTags: Vector[StandoffTagV2] = StandoffTagUtilV2.createStandoffTagsV2FromSparqlResults(teiMapping.standoffEntities, valueObject.standoff)

            // create XML from standoff (temporary XML) that is going to be converted to TEI/XML
            tmpXml = StandoffTagUtilV2.convertStandoffTagV2ToXML(valueObject.assertions(KnoraBase.ValueHasString), standoffTags, teiMapping.mapping)

            teiXSLTFile: File = new File("src/main/resources/standoffToTEI.xsl")

            _ = if (!teiXSLTFile.canRead) throw NotFoundException("Cannot find XSL transformation for TEI: 'src/main/resources/standoffToTEI.xsl'")

            // apply XSL transformation to temporary XML to create the TEI/XML body
            xslt: String = FileUtils.readFileToString(teiXSLTFile, "UTF-8")

            teiXMLBody = XMLUtil.applyXSLTransformation(tmpXml, xslt)

            _ = println(tmpXml)

            _ = println("+++++")

            header =
            """
              |<teiHeader>
              |<fileDesc>
              |<titleStmt>
              |    <title>The shortest TEI Document Imaginable</title>
              |   </titleStmt>
              |<publicationStmt>
              |    <p>First published as part of TEI P2, this is the P5
              |         version using a name space.</p>
              |   </publicationStmt>
              |<sourceDesc>
              |    <p>No source: this is an original work.</p>
              |   </sourceDesc>
              |</fileDesc>
              |</teiHeader>
            """.stripMargin

            tei = ResourceTEIGetResponseV2(header = header, body = teiXMLBody)

            _ = println(tei.toXML)

        } yield
            ReadResourcesSequenceV2(numberOfResources = 0, resources = Vector.empty[ReadResourceV2])


    }

}

