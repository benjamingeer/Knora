/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common

import org.apache.jena.rdf.model.RDFNode
import zio.*
import zio.json.DecoderOps
import zio.json.EncoderOps
import zio.json.ast.Json
import zio.test.*

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.common.KnoraIris.*
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

object KnoraApiValueModelSpec extends ZIOSpecDefault {
  private val sf = StringFormatter.getInitializedTestInstance

  private val createIntegerValue = """
    {
      "@id": "http://rdfh.ch/0001/a-thing",
      "@type": "anything:Thing",
      "anything:hasInteger": {
        "@type": "knora-api:IntValue",
        "knora-api:intValueAsInt": 4
      },
      "@context": {
        "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
        "anything": "http://0.0.0.0:3333/ontology/0001/anything/v2#"
      }
    }
  """.fromJson[Json].getOrElse(throw new Exception("Invalid JSON"))

  private val createLinkValue =
    s"""{
         "@id" : "http://rdfh.ch/0001/a-thing",
         "@type" : "anything:Thing",
         "anything:hasOtherThingValue" : {
           "@id" : "http://rdfh.ch/0001/a-thing/values/mr9i2aUUJolv64V_9hYdTw",
           "@type" : "knora-api:LinkValue",
           "knora-api:valueHasUUID": "mr9i2aUUJolv64V_9hYdTw",
           "knora-api:linkValueHasTargetIri" : {
             "@id" : "http://rdfh.ch/0001/CNhWoNGGT7iWOrIwxsEqvA"
           },
           "knora-api:valueCreationDate" : {
               "@type" : "xsd:dateTimeStamp",
               "@value" : "2020-06-04T11:36:54.502951Z"
           }
         },
         "@context" : {
           "xsd" : "http://www.w3.org/2001/XMLSchema#",
           "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
         }
       }""".fromJson[Json].getOrElse(throw new Exception("Invalid JSON"))

  val spec = suite("KnoraApiValueModel")(
    test("getResourceIri should get the id") {
      check(Gen.fromIterable(Seq(createIntegerValue, createLinkValue).map(_.toJsonPretty))) { json =>
        for {
          model <- KnoraApiValueModel.fromJsonLd(json)
        } yield assertTrue(model.resourceIri.toString == "http://rdfh.ch/0001/a-thing")
      }
    },
    test("rootResourceClassIri should get the rdfs:type") {
      check(Gen.fromIterable(Seq(createIntegerValue, createLinkValue).map(_.toJsonPretty))) { json =>
        for {
          model <- KnoraApiValueModel.fromJsonLd(json)
        } yield assertTrue(model.resourceClassIri.toString == "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing")
      }
    },
    test("rootResourceProperties should get the props") {
      check(Gen.fromIterable(Seq(createIntegerValue, createLinkValue).map(_.toJsonPretty))) { json =>
        for {
          model <- KnoraApiValueModel.fromJsonLd(json)
          node   = model.valueNode
        } yield assertTrue(node != null)
      }
    },
    test("valueNode properties should be present") {
      for {
        model       <- KnoraApiValueModel.fromJsonLd(createIntegerValue.toJsonPretty)
        propertyIri  = model.valueNode.propertyIri
        valueType    = model.valueNode.valueType
        foo: RDFNode = model.valueNode.node

      } yield assertTrue(
        propertyIri == PropertyIri.unsafeFrom(
          sf.toSmartIri("http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger"),
        ),
        valueType == sf.toSmartIri("http://api.knora.org/ontology/knora-api/v2#IntValue"),
        model.valueNode.shortcode == Shortcode.unsafeFrom("0001"),
      )
    },
  ).provideSome[Scope](IriConverter.layer, StringFormatter.test)
}
