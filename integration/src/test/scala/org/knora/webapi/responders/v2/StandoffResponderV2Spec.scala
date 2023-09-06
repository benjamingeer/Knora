/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import akka.testkit.ImplicitSender

import scala.concurrent.duration._

import dsp.errors._
import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.twirl.queries.sparql
import org.knora.webapi.messages.v2.responder.standoffmessages._
import org.knora.webapi.models.standoffmodels.DefineStandoffMapping
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct

class StandoffResponderV2Spec extends CoreSpec with ImplicitSender {

  // The default timeout for receiving reply messages from actors.
  override implicit val timeout: FiniteDuration = 30.seconds

  private def getMapping(iri: String): SparqlConstructResponse =
    UnsafeZioRun.runOrThrow(TriplestoreService.query(Construct(sparql.v2.txt.getMapping(iri))))

  "The standoff responder" should {
    "create a standoff mapping" in {
      val mappingName = "customMapping"
      val mapping     = DefineStandoffMapping.make(mappingName)
      val xmlContent =
        s"""<?xml version="1.0" encoding="UTF-8"?>
           |<mapping xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
           |         xsi:noNamespaceSchemaLocation="../../../webapi/src/main/resources/mappingXMLToStandoff.xsd">
           |
           |    <mappingElement>
           |        <tag>
           |            <name>text</name>
           |            <class>noClass</class>
           |            <namespace>noNamespace</namespace>
           |            <separatesWords>false</separatesWords>
           |        </tag>
           |        <standoffClass>
           |            <classIri>http://www.knora.org/ontology/standoff#StandoffRootTag</classIri>
           |            <attributes>
           |                <attribute>
           |                    <attributeName>documentType</attributeName>
           |                    <namespace>noNamespace</namespace>
           |                    <propertyIri>http://www.knora.org/ontology/standoff#standoffRootTagHasDocumentType</propertyIri>
           |                </attribute>
           |            </attributes>
           |        </standoffClass>
           |    </mappingElement>
           |
           |    <mappingElement>
           |        <tag>
           |            <name>section</name>
           |            <class>noClass</class>
           |            <namespace>noNamespace</namespace>
           |            <separatesWords>false</separatesWords>
           |        </tag>
           |        <standoffClass>
           |            <classIri>http://www.knora.org/ontology/standoff#StandoffParagraphTag</classIri>
           |        </standoffClass>
           |    </mappingElement>
           |
           |    <mappingElement>
           |        <tag>
           |            <name>italic</name>
           |            <class>noClass</class>
           |            <namespace>noNamespace</namespace>
           |            <separatesWords>false</separatesWords>
           |        </tag>
           |        <standoffClass>
           |            <classIri>http://www.knora.org/ontology/standoff#StandoffItalicTag</classIri>
           |        </standoffClass>
           |    </mappingElement>
           |
           |</mapping>
           |""".stripMargin
      val message = mapping.toMessage(
        xml = xmlContent,
        user = SharedTestDataADM.rootUser
      )
      appActor ! message
      val response = expectMsgPF(timeout) {
        case res: CreateMappingResponseV2 => res
        case _                            => throw AssertionException("Could not create a mapping")
      }

      val expectedMappingIRI = f"${mapping.projectIRI}/mappings/$mappingName"
      response.mappingIri should equal(expectedMappingIRI)
      val mappingFromDB: SparqlConstructResponse = getMapping(response.mappingIri)
      println(mappingFromDB)
      mappingFromDB.statements should not be Map.empty
      mappingFromDB.statements.get(expectedMappingIRI) should not be Map.empty
    }

  }
}
