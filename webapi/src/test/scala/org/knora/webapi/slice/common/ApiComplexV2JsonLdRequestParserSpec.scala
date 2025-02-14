/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common

import zio.*
import zio.json.DecoderOps
import zio.json.EncoderOps
import zio.json.ast.Json
import zio.test.*

import java.time.Instant

import org.knora.webapi.ApiV2Complex
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageRelayLive
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.CalendarNameGregorian
import org.knora.webapi.messages.util.DatePrecisionDay
import org.knora.webapi.messages.v2.responder.valuemessages.ArchiveFileValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.AudioFileValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.BooleanValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.ColorValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.CreateValueV2
import org.knora.webapi.messages.v2.responder.valuemessages.DateValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.DecimalValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.DocumentFileValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.FileValueV2
import org.knora.webapi.messages.v2.responder.valuemessages.GeomValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.GeonameValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.HierarchicalListValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.IntegerValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.IntervalValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.LinkValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.MovingImageFileValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.StillImageExternalFileValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.StillImageFileValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.TextFileValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.TextValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.TextValueType.UnformattedText
import org.knora.webapi.messages.v2.responder.valuemessages.TimeValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.UriValueContentV2
import org.knora.webapi.responders.IriService
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.slice.admin.domain.repo.*
import org.knora.webapi.slice.admin.domain.service.*
import org.knora.webapi.slice.admin.repo.service.*
import org.knora.webapi.slice.common.JsonLdTestUtil.JsonLdTransformations
import org.knora.webapi.slice.common.KnoraIris.*
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoInMemory
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.slice.resources.IiifImageRequestUrl
import org.knora.webapi.store.iiif.api.FileMetadataSipiResponse
import org.knora.webapi.store.iiif.api.SipiService
import org.knora.webapi.store.iiif.impl.SipiServiceMock
import org.knora.webapi.store.iiif.impl.SipiServiceMock.SipiMockMethodName.GetFileMetadataFromDspIngest
import org.knora.webapi.store.iiif.impl.SipiServiceMock.SipiMockMethodName.GetFileMetadataFromSipiTemp
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory

object ApiComplexV2JsonLdRequestParserSpec extends ZIOSpecDefault {
  private val sf = StringFormatter.getInitializedTestInstance

  private val service = ZIO.serviceWithZIO[ApiComplexV2JsonLdRequestParser]

  private val givenFileInfo =
    FileMetadataSipiResponse(
      Some("originalFilename.orig"),
      Some("originalMimeType"),
      "internalMimeType",
      Some(640),
      Some(480),
      Some(666),
      None,
      None,
    )

  private val expectedFileValue = FileValueV2(
    "internalFilename.ext",
    "internalMimeType",
    Some("originalFilename.orig"),
    Some("originalMimeType"),
  )

  private val configureSipiServiceMock = for {
    sipiMock <- ZIO.service[SipiServiceMock]
    _        <- sipiMock.setReturnValue(GetFileMetadataFromSipiTemp, ZIO.fail(Exception("No interaction with sipi expected")))
    _        <- sipiMock.setReturnValue(GetFileMetadataFromDspIngest, ZIO.succeed(givenFileInfo))
  } yield ()

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
          actual <- service(_.createValueV2FromJsonLd(json))
        } yield assertTrue(actual.resourceIri == "http://rdfh.ch/0001/a-thing")
      }
    },
    test("rootResourceClassIri should get the rdfs:type") {
      check(Gen.fromIterable(Seq(createIntegerValue, createLinkValue).map(_.toJsonPretty))) { json =>
        for {
          actual <- service(_.createValueV2FromJsonLd(json))
        } yield assertTrue(actual.resourceClassIri.toString == "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing")
      }
    },
    test("value property should be present") {
      for {
        actual <- service(_.createValueV2FromJsonLd(createIntegerValue.toJsonPretty))
      } yield assertTrue(
        actual.propertyIri == sf.toSmartIri("http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger"),
      )
    },
    test("should parse integer value") {
      for {
        actual <- service(
                    _.createValueV2FromJsonLd(
                      """{
                        |  "@id": "http://rdfh.ch/0001/a-thing",
                        |  "@type": "ex:Thing",
                        |  "ex:someInt": {
                        |    "@type": "ka:IntValue",
                        |    "ka:intValueAsInt": {
                        |       "@type": "xsd:integer",
                        |       "@value": 4
                        |    }
                        |  },
                        |  "@context": {
                        |    "ka": "http://api.knora.org/ontology/knora-api/v2#",
                        |    "ex": "http://0.0.0.0:3333/ontology/0001/anything/v2#",
                        |    "xsd": "http://www.w3.org/2001/XMLSchema#"
                        |  }
                        |}""".stripMargin,
                    ),
                  )
      } yield assertTrue(actual.valueContent == IntegerValueContentV2(ApiV2Complex, 4, None))
    },
    test("should parse DecimalValueContentV2") {
      for {
        actual <- service(
                    _.createValueV2FromJsonLd(
                      """{
                        |  "@id": "http://rdfh.ch/0001/a-thing",
                        |  "@type": "ex:Thing",
                        |  "ex:someDec": {
                        |    "@type": "ka:DecimalValue",
                        |    "ka:decimalValueAsDecimal": {
                        |       "@type": "xsd:decimal",
                        |       "@value": "4"
                        |    }
                        |  },
                        |  "@context": {
                        |    "ka": "http://api.knora.org/ontology/knora-api/v2#",
                        |    "ex": "http://0.0.0.0:3333/ontology/0001/anything/v2#",
                        |    "xsd": "http://www.w3.org/2001/XMLSchema#"
                        |  }
                        |}""".stripMargin,
                    ),
                  )
      } yield assertTrue(actual.valueContent == DecimalValueContentV2(ApiV2Complex, BigDecimal(4), None))
    },
    test("should parse BooleanValueContentV2") {
      for {
        actual <- service(
                    _.createValueV2FromJsonLd(
                      """{
                        |  "@id": "http://rdfh.ch/0001/a-thing",
                        |  "@type": "ex:Thing",
                        |  "ex:someBool": {
                        |    "@type": "ka:BooleanValue",
                        |    "ka:booleanValueAsBoolean": {
                        |       "@type": "xsd:boolean",
                        |       "@value": "true"
                        |    }
                        |  },
                        |  "@context": {
                        |    "ka": "http://api.knora.org/ontology/knora-api/v2#",
                        |    "ex": "http://0.0.0.0:3333/ontology/0001/anything/v2#",
                        |    "xsd": "http://www.w3.org/2001/XMLSchema#"
                        |  }
                        |}""".stripMargin,
                    ),
                  )
      } yield assertTrue(actual.valueContent == BooleanValueContentV2(ApiV2Complex, true, None))
    },
    test("should parse GeomValueContentV2") {
      for {
        actual <- service(
                    _.createValueV2FromJsonLd(
                      """{
                        |  "@id": "http://rdfh.ch/0001/a-thing",
                        |  "@type": "ex:Thing",
                        |  "ex:someGeom": {
                        |    "@type": "ka:GeomValue",
                        |    "ka:geometryValueAsGeometry": "{}"
                        |  },
                        |  "@context": {
                        |    "ka": "http://api.knora.org/ontology/knora-api/v2#",
                        |    "ex": "http://0.0.0.0:3333/ontology/0001/anything/v2#",
                        |    "xsd": "http://www.w3.org/2001/XMLSchema#"
                        |  }
                        |}""".stripMargin,
                    ),
                  )
      } yield assertTrue(actual.valueContent == GeomValueContentV2(ApiV2Complex, "{}", None))
    },
    test("should parse IntervalValueContentV2") {
      for {
        actual <- service(
                    _.createValueV2FromJsonLd(
                      """{
                        |  "@id": "http://rdfh.ch/0001/a-thing",
                        |  "@type": "ex:Thing",
                        |  "ex:someInterval": {
                        |    "@type": "ka:IntervalValue",
                        |    "ka:intervalValueHasStart": {
                        |       "@type": "xsd:decimal",
                        |       "@value": 4
                        |    },
                        |    "ka:intervalValueHasEnd": {
                        |       "@type": "xsd:decimal",
                        |       "@value": 2
                        |    }
                        |  },
                        |  "@context": {
                        |    "ka": "http://api.knora.org/ontology/knora-api/v2#",
                        |    "ex": "http://0.0.0.0:3333/ontology/0001/anything/v2#",
                        |    "xsd": "http://www.w3.org/2001/XMLSchema#"
                        |  }
                        |}""".stripMargin,
                    ),
                  )
      } yield assertTrue(
        actual.valueContent == IntervalValueContentV2(ApiV2Complex, BigDecimal(4), BigDecimal(2), None),
      )
    },
    test("should parse TimeValueContentV2") {
      for {
        actual <- service(
                    _.createValueV2FromJsonLd(
                      """{
                        |  "@id": "http://rdfh.ch/0001/a-thing",
                        |  "@type": "ex:Thing",
                        |  "ex:someTimeValue": {
                        |    "@type": "ka:TimeValue",
                        |    "ka:timeValueAsTimeStamp": {
                        |       "@type": "xsd:dateTimeStamp",
                        |       "@value": "2020-06-04T11:36:54.502951Z"
                        |    }
                        |  },
                        |  "@context": {
                        |    "ka": "http://api.knora.org/ontology/knora-api/v2#",
                        |    "ex": "http://0.0.0.0:3333/ontology/0001/anything/v2#",
                        |    "xsd": "http://www.w3.org/2001/XMLSchema#"
                        |  }
                        |}""".stripMargin,
                    ),
                  )
      } yield assertTrue(
        actual.valueContent == TimeValueContentV2(ApiV2Complex, Instant.parse("2020-06-04T11:36:54.502951Z"), None),
      )
    },
    test("should parse LinkValueContentV2") {
      for {
        actual <-
          service(
            _.createValueV2FromJsonLd(
              s"""
                 |{
                 |  "@id" : "http://rdfh.ch/0001/a-thing",
                 |  "@type" : "ex:Thing",
                 |  "ex:hasOtherThingValue" : {
                 |    "@id" : "http://rdfh.ch/0001/a-thing/values/mr9i2aUUJolv64V_9hYdTw",
                 |    "@type" : "ka:LinkValue",
                 |    "ka:linkValueHasTargetIri" : {
                 |      "@id" : "http://rdfh.ch/0001/CNhWoNGGT7iWOrIwxsEqvA"
                 |    }
                 |  },
                 |  "@context": {
                 |    "ka": "http://api.knora.org/ontology/knora-api/v2#",
                 |    "ex": "http://0.0.0.0:3333/ontology/0001/anything/v2#",
                 |    "xsd": "http://www.w3.org/2001/XMLSchema#"
                 |  }
                 |}""".stripMargin,
            ),
          )
      } yield assertTrue(
        actual.valueContent == LinkValueContentV2(
          ApiV2Complex,
          referredResourceIri = "http://rdfh.ch/0001/CNhWoNGGT7iWOrIwxsEqvA",
          comment = None,
        ),
      )
    },
    test("should parse UriValueContentV2") {
      for {
        actual <-
          service(
            _.createValueV2FromJsonLd(
              s"""
                 |{
                 |  "@id" : "http://rdfh.ch/0001/a-thing",
                 |  "@type" : "ex:Thing",
                 |  "ex:hasOtherThingValue" : {
                 |    "@id" : "http://rdfh.ch/0001/a-thing/values/mr9i2aUUJolv64V_9hYdTw",
                 |    "@type" : "ka:UriValue",
                 |    "ka:uriValueAsUri" :  {
                 |      "@type" : "xsd:anyURI",
                 |      "@value" : "http://rdfh.ch/0001/CNhWoNGGT7iWOrIwxsEqvA"
                 |    }
                 |  },
                 |  "@context": {
                 |    "ka": "http://api.knora.org/ontology/knora-api/v2#",
                 |    "ex": "http://0.0.0.0:3333/ontology/0001/anything/v2#",
                 |    "xsd": "http://www.w3.org/2001/XMLSchema#"
                 |  }
                 |}""".stripMargin,
            ),
          )
      } yield assertTrue(
        actual.valueContent == UriValueContentV2(ApiV2Complex, "http://rdfh.ch/0001/CNhWoNGGT7iWOrIwxsEqvA", None),
      )
    },
    test("should parse GeonameValueContentV2") {
      for {
        actual <-
          service(
            _.createValueV2FromJsonLd(
              s"""
                 |{
                 |  "@id" : "http://rdfh.ch/0001/a-thing",
                 |  "@type" : "ex:Thing",
                 |  "ex:hasOtherThingValue" : {
                 |    "@id" : "http://rdfh.ch/0001/a-thing/values/mr9i2aUUJolv64V_9hYdTw",
                 |    "@type" : "ka:GeonameValue",
                 |    "ka:geonameValueAsGeonameCode" : "foo"
                 |  },
                 |  "@context": {
                 |    "ka": "http://api.knora.org/ontology/knora-api/v2#",
                 |    "ex": "http://0.0.0.0:3333/ontology/0001/anything/v2#",
                 |    "xsd": "http://www.w3.org/2001/XMLSchema#"
                 |  }
                 |}""".stripMargin,
            ),
          )
      } yield assertTrue(actual.valueContent == GeonameValueContentV2(ApiV2Complex, "foo", None))
    },
    test("should parse ColorValue") {
      for {
        actual <-
          service(
            _.createValueV2FromJsonLd(
              s"""
                 |{
                 |  "@id" : "http://rdfh.ch/0001/a-thing",
                 |  "@type" : "ex:Thing",
                 |  "ex:hasOtherThingValue" : {
                 |    "@id" : "http://rdfh.ch/0001/a-thing/values/mr9i2aUUJolv64V_9hYdTw",
                 |    "@type" : "ka:ColorValue",
                 |    "ka:colorValueAsColor" : "red"
                 |  },
                 |  "@context": {
                 |    "ka": "http://api.knora.org/ontology/knora-api/v2#",
                 |    "ex": "http://0.0.0.0:3333/ontology/0001/anything/v2#",
                 |    "xsd": "http://www.w3.org/2001/XMLSchema#"
                 |  }
                 |}""".stripMargin,
            ),
          )
      } yield assertTrue(actual.valueContent == ColorValueContentV2(ApiV2Complex, "red", None))
    },
    test("should parse StillImageFileValue") {
      for {
        _ <- configureSipiServiceMock
        actual <-
          service(
            _.createValueV2FromJsonLd(
              s"""
                 |{
                 |  "@id" : "http://rdfh.ch/0001/a-thing",
                 |  "@type" : "ex:Thing",
                 |  "ex:hasOtherThingValue" : {
                 |    "@id" : "http://rdfh.ch/0001/a-thing/values/mr9i2aUUJolv64V_9hYdTw",
                 |    "@type" : "ka:StillImageFileValue",
                 |    "ka:fileValueHasFilename": "internalFilename.ext"
                 |  },
                 |  "@context": {
                 |    "ka": "http://api.knora.org/ontology/knora-api/v2#",
                 |    "ex": "http://0.0.0.0:3333/ontology/0001/anything/v2#",
                 |    "xsd": "http://www.w3.org/2001/XMLSchema#"
                 |  }
                 |}""".stripMargin,
            ),
          )
      } yield assertTrue(
        actual.valueContent == StillImageFileValueContentV2(
          ApiV2Complex,
          expectedFileValue,
          givenFileInfo.width.getOrElse(throw new Exception("width is missing")),
          givenFileInfo.height.getOrElse(throw new Exception("height is missing")),
          None,
        ),
      )
    },
    test("should parse StillImageFileValue with copyright and license information") {
      for {
        _ <- configureSipiServiceMock
        actual <-
          service(
            _.createValueV2FromJsonLd(
              s"""
                 |{
                 |  "@id" : "http://rdfh.ch/0001/a-thing",
                 |  "@type" : "ex:Thing",
                 |  "ex:hasOtherThingValue" : {
                 |    "@id" : "http://rdfh.ch/0001/a-thing/values/mr9i2aUUJolv64V_9hYdTw",
                 |    "@type" : "ka:StillImageFileValue",
                 |    "ka:fileValueHasFilename": "internalFilename.ext",
                 |    "ka:hasCopyrightHolder" : "Jane Doe",
                 |    "ka:hasAuthorship" : [ "Mr. Smith", "Author McAuthorface" ],
                 |    "ka:hasLicense" : {
                 |      "@id" : "http://rdfh.ch/licenses/i6xBpZn4RVOdOIyTezEumw"
                 |    }
                 |  },
                 |  "@context": {
                 |    "ka": "http://api.knora.org/ontology/knora-api/v2#",
                 |    "ex": "http://0.0.0.0:3333/ontology/0001/anything/v2#",
                 |    "xsd": "http://www.w3.org/2001/XMLSchema#"
                 |  }
                 |}""".stripMargin,
            ),
          )
      } yield assertTrue(
        actual.valueContent == StillImageFileValueContentV2(
          ApiV2Complex,
          expectedFileValue.copy(
            copyrightHolder = Some(CopyrightHolder.unsafeFrom("Jane Doe")),
            authorship = Some(List(Authorship.unsafeFrom("Author McAuthorface"), Authorship.unsafeFrom("Mr. Smith"))),
            licenseIri = Some(LicenseIri.unsafeFrom("http://rdfh.ch/licenses/i6xBpZn4RVOdOIyTezEumw")),
          ),
          givenFileInfo.width.getOrElse(throw new Exception("width is missing")),
          givenFileInfo.height.getOrElse(throw new Exception("height is missing")),
          None,
        ),
      )
    },
    test("should parse StillImageExternalFileValue") {
      for {
        _ <- ZIO.serviceWithZIO[SipiServiceMock](_.assertNoInteraction)
        actual <-
          service(
            _.createValueV2FromJsonLd(
              s"""
                 |{
                 |  "@id" : "http://rdfh.ch/0001/a-thing",
                 |  "@type" : "ex:Thing",
                 |  "ex:hasOtherThingValue" : {
                 |    "@id" : "http://rdfh.ch/0001/a-thing/values/mr9i2aUUJolv64V_9hYdTw",
                 |    "@type" : "ka:StillImageExternalFileValue",
                 |    "ka:stillImageFileValueHasExternalUrl" : "http://www.example.org/prefix1/abcd1234/full/0/native.jpg"
                 |  },
                 |  "@context": {
                 |    "ka": "http://api.knora.org/ontology/knora-api/v2#",
                 |    "ex": "http://0.0.0.0:3333/ontology/0001/anything/v2#",
                 |    "xsd": "http://www.w3.org/2001/XMLSchema#"
                 |  }
                 |}""".stripMargin,
            ),
          )
      } yield assertTrue(
        actual.valueContent == StillImageExternalFileValueContentV2(
          ApiV2Complex,
          FileValueV2(
            "internalFilename",
            "internalMimeType",
            Some("originalFilename"),
            Some("originalMimeType"),
          ),
          IiifImageRequestUrl.unsafeFrom("http://www.example.org/prefix1/abcd1234/full/0/native.jpg"),
          None,
        ),
      )
    },
    test("should parse DocumentFileValue") {
      for {
        _ <- configureSipiServiceMock
        actual <- service(
                    _.createValueV2FromJsonLd(
                      s"""
                         |{
                         |  "@id" : "http://rdfh.ch/0001/a-thing",
                         |  "@type" : "ex:Thing",
                         |  "ex:hasOtherThingValue" : {
                         |    "@id" : "http://rdfh.ch/0001/a-thing/values/mr9i2aUUJolv64V_9hYdTw",
                         |    "@type" : "ka:DocumentFileValue",
                         |    "ka:fileValueHasFilename": "internalFilename.ext"
                         |  },
                         |  "@context": {
                         |    "ka": "http://api.knora.org/ontology/knora-api/v2#",
                         |    "ex": "http://0.0.0.0:3333/ontology/0001/anything/v2#",
                         |    "xsd": "http://www.w3.org/2001/XMLSchema#"
                         |  }
                         |}""".stripMargin,
                    ),
                  )
      } yield assertTrue(
        actual.valueContent == DocumentFileValueContentV2(
          ApiV2Complex,
          expectedFileValue,
          givenFileInfo.numpages,
          givenFileInfo.width,
          givenFileInfo.height,
          None,
        ),
      )
    },
    test("should parse TextFileValueContentV2") {
      for {
        _ <- configureSipiServiceMock
        actual <- service(
                    _.createValueV2FromJsonLd(
                      s"""
                         |{
                         |  "@id" : "http://rdfh.ch/0001/a-thing",
                         |  "@type" : "ex:Thing",
                         |  "ex:hasOtherThingValue" : {
                         |    "@id" : "http://rdfh.ch/0001/a-thing/values/mr9i2aUUJolv64V_9hYdTw",
                         |    "@type" : "ka:TextFileValue",
                         |    "ka:fileValueHasFilename": "internalFilename.ext"
                         |  },
                         |  "@context": {
                         |    "ka": "http://api.knora.org/ontology/knora-api/v2#",
                         |    "ex": "http://0.0.0.0:3333/ontology/0001/anything/v2#",
                         |    "xsd": "http://www.w3.org/2001/XMLSchema#"
                         |  }
                         |}""".stripMargin,
                    ),
                  )
      } yield assertTrue(
        actual.valueContent == TextFileValueContentV2(ApiV2Complex, expectedFileValue, None),
      )
    },
    test("should parse AudioFileValueContentV2") {
      for {
        _ <- configureSipiServiceMock
        actual <- service(
                    _.createValueV2FromJsonLd(
                      s"""
                         |{
                         |  "@id" : "http://rdfh.ch/0001/a-thing",
                         |  "@type" : "ex:Thing",
                         |  "ex:hasOtherThingValue" : {
                         |    "@id" : "http://rdfh.ch/0001/a-thing/values/mr9i2aUUJolv64V_9hYdTw",
                         |    "@type" : "ka:AudioFileValue",
                         |    "ka:fileValueHasFilename": "internalFilename.ext"
                         |  },
                         |  "@context": {
                         |    "ka": "http://api.knora.org/ontology/knora-api/v2#",
                         |    "ex": "http://0.0.0.0:3333/ontology/0001/anything/v2#",
                         |    "xsd": "http://www.w3.org/2001/XMLSchema#"
                         |  }
                         |}""".stripMargin,
                    ),
                  )
      } yield assertTrue(
        actual.valueContent == AudioFileValueContentV2(ApiV2Complex, expectedFileValue, None),
      )
    },
    test("should parse MovingImageFileValueContentV2") {
      for {
        _ <- configureSipiServiceMock
        actual <- service(
                    _.createValueV2FromJsonLd(
                      s"""
                         |{
                         |  "@id" : "http://rdfh.ch/0001/a-thing",
                         |  "@type" : "ex:Thing",
                         |  "ex:hasOtherThingValue" : {
                         |    "@id" : "http://rdfh.ch/0001/a-thing/values/mr9i2aUUJolv64V_9hYdTw",
                         |    "@type" : "ka:MovingImageFileValue",
                         |    "ka:fileValueHasFilename": "internalFilename.ext"
                         |  },
                         |  "@context": {
                         |    "ka": "http://api.knora.org/ontology/knora-api/v2#",
                         |    "ex": "http://0.0.0.0:3333/ontology/0001/anything/v2#",
                         |    "xsd": "http://www.w3.org/2001/XMLSchema#"
                         |  }
                         |}""".stripMargin,
                    ),
                  )
      } yield assertTrue(
        actual.valueContent == MovingImageFileValueContentV2(ApiV2Complex, expectedFileValue, None),
      )
    },
    test("should parse ArchiveFileValueContentV2") {
      for {
        _ <- configureSipiServiceMock
        actual <- service(
                    _.createValueV2FromJsonLd(
                      s"""
                         |{
                         |  "@id" : "http://rdfh.ch/0001/a-thing",
                         |  "@type" : "ex:Thing",
                         |  "ex:hasOtherThingValue" : {
                         |    "@id" : "http://rdfh.ch/0001/a-thing/values/mr9i2aUUJolv64V_9hYdTw",
                         |    "@type" : "ka:ArchiveFileValue",
                         |    "ka:fileValueHasFilename": "internalFilename.ext"
                         |  },
                         |  "@context": {
                         |    "ka": "http://api.knora.org/ontology/knora-api/v2#",
                         |    "ex": "http://0.0.0.0:3333/ontology/0001/anything/v2#",
                         |    "xsd": "http://www.w3.org/2001/XMLSchema#"
                         |  }
                         |}""".stripMargin,
                    ),
                  )
      } yield assertTrue(
        actual.valueContent == ArchiveFileValueContentV2(ApiV2Complex, expectedFileValue, None),
      )
    },
    test("should parse HierarchicalListValueContentV2") {
      for {
        actual <- service(
                    _.createValueV2FromJsonLd(
                      s"""
                         |{
                         |  "@id" : "http://rdfh.ch/0001/a-thing",
                         |  "@type" : "ex:Thing",
                         |  "ex:hasOtherThingValue" : {
                         |    "@id" : "http://rdfh.ch/0001/a-thing/values/mr9i2aUUJolv64V_9hYdTw",
                         |    "@type" : "ka:ListValue",
                         |    "ka:listValueAsListNode": {
                         |      "@id" : "http://rdfh.ch/0001/CNhWoNGGT7iWOrIwxsEqvA"
                         |    }
                         |  },
                         |  "@context": {
                         |    "ka": "http://api.knora.org/ontology/knora-api/v2#",
                         |    "ex": "http://0.0.0.0:3333/ontology/0001/anything/v2#",
                         |    "xsd": "http://www.w3.org/2001/XMLSchema#"
                         |  }
                         |}""".stripMargin,
                    ),
                  )
      } yield assertTrue(
        actual.valueContent == HierarchicalListValueContentV2(
          ApiV2Complex,
          "http://rdfh.ch/0001/CNhWoNGGT7iWOrIwxsEqvA",
          None,
          None,
        ),
      )
    },
    test("should parse DateValueContentV2") {
      for {
        actual <- service(
                    _.createValueV2FromJsonLd(
                      s"""
                         |{
                         |  "@id" : "http://rdfh.ch/0001/a-thing",
                         |  "@type" : "ex:Thing",
                         |  "ex:hasOtherThingValue" : {
                         |    "@id" : "http://rdfh.ch/0001/a-thing/values/mr9i2aUUJolv64V_9hYdTw",
                         |    "@type" : "ka:DateValue",
                         |    "ka:dateValueHasCalendar" : "GREGORIAN",
                         |    "ka:dateValueHasEndEra" : "CE",
                         |    "ka:dateValueHasEndYear" : 1489,
                         |    "ka:dateValueHasEndMonth" : 12,
                         |    "ka:dateValueHasEndDay" : 24,
                         |    "ka:dateValueHasStartEra" : "CE",
                         |    "ka:dateValueHasStartMonth" : 1,
                         |    "ka:dateValueHasStartDay" : 28,
                         |    "ka:dateValueHasStartYear" : 1488
                         |  },
                         |  "@context": {
                         |    "ka": "http://api.knora.org/ontology/knora-api/v2#",
                         |    "ex": "http://0.0.0.0:3333/ontology/0001/anything/v2#",
                         |    "xsd": "http://www.w3.org/2001/XMLSchema#"
                         |  }
                         |}""".stripMargin,
                    ),
                  )
      } yield assertTrue(
        actual.valueContent == DateValueContentV2(
          ApiV2Complex,
          2264568,
          2265264,
          DatePrecisionDay,
          DatePrecisionDay,
          CalendarNameGregorian,
          None,
        ),
      )
    },
    test("should handle transformed json-ld") {
      val transformations = JsonLdTransformations.all
      check(Gen.fromIterable(transformations)) { jsonLdTransform =>
        for {
          sf <- ZIO.service[StringFormatter]
          str = jsonLdTransform("""
                       {
                          "@id": "http://rdfh.ch/0001/a-thing",
                          "@type": "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing",
                          "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText":{
                            "@type":"http://api.knora.org/ontology/knora-api/v2#TextValue",
                            "http://api.knora.org/ontology/knora-api/v2#valueAsString":"This is English",
                            "http://api.knora.org/ontology/knora-api/v2#textValueHasLanguage":"en"
                          }
                       }""".stripMargin)
          value <- service(_.createValueV2FromJsonLd(str))
        } yield assertTrue(
          value == CreateValueV2(
            resourceIri = "http://rdfh.ch/0001/a-thing",
            resourceClassIri = sf.toSmartIri("http://0.0.0.0:3333/ontology/0001/anything/v2#Thing"),
            propertyIri = sf.toSmartIri("http://0.0.0.0:3333/ontology/0001/anything/v2#hasText"),
            valueContent = TextValueContentV2(
              ontologySchema = ApiV2Complex,
              maybeValueHasString = Some("This is English"),
              textValueType = UnformattedText,
              valueHasLanguage = Some("en"),
              standoff = Nil,
              mappingIri = None,
              mapping = None,
              xslt = None,
              comment = None,
            ),
            valueIri = None,
            valueUUID = None,
            valueCreationDate = None,
            permissions = None,
          ),
        )
      }
    },
  ).provideSome[Scope](
    AdministrativePermissionRepoInMemory.layer,
    AdministrativePermissionService.layer,
    ApiComplexV2JsonLdRequestParser.layer,
    GroupService.layer,
    IriConverter.layer,
    IriService.layer,
    KnoraGroupRepoInMemory.layer,
    KnoraGroupService.layer,
    KnoraProjectRepoInMemory.layer,
    KnoraProjectService.layer,
    KnoraUserRepoInMemory.layer,
    KnoraUserService.layer,
    KnoraUserToUserConverter.layer,
    MessageRelayLive.layer,
    OntologyRepoInMemory.emptyLayer,
    PasswordService.layer,
    ProjectService.layer,
    SipiServiceMock.layer,
    StringFormatter.test,
    TriplestoreServiceInMemory.emptyLayer,
    UserService.layer,
    AppConfig.layer,
  )
}
