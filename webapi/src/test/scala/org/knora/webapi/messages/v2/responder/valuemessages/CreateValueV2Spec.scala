/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder.valuemessages
import zio.ZIO
import zio.test.Spec
import zio.test.TestResult
import zio.test.ZIOSpecDefault
import zio.test.assertTrue
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.core.MessageRelayLive
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.responder.valuemessages.TextValueType.UnformattedText
import org.knora.webapi.routing.v2.AssetIngestState.AssetIngested
import org.knora.webapi.sharedtestdata.SharedTestDataADM.rootUser
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.store.iiif.api.SipiService
import org.knora.webapi.store.iiif.impl.SipiServiceMock
import org.knora.webapi.store.iiif.impl.SipiServiceMock

object CreateValueV2Spec extends ZIOSpecDefault {

  private val unformattedTextValueWithLanguage =
    """
      |{
      |  "@id": "http://rdfh.ch/0001/a-thing",
      |  "@type": "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing",
      |  "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText":{
      |    "@type":"http://api.knora.org/ontology/knora-api/v2#TextValue",
      |    "http://api.knora.org/ontology/knora-api/v2#valueAsString":"This is English",
      |    "http://api.knora.org/ontology/knora-api/v2#textValueHasLanguage":"en"
      |  }
      |}""".stripMargin

  override def spec: Spec[Any, Throwable] =
    suite("CreateValueV2Spec")(test("UnformattedText TextValue fromJsonLd should contain the language") {
      for {
        sf    <- ZIO.service[StringFormatter]
        value <- CreateValueV2.fromJsonLd(AssetIngested, unformattedTextValueWithLanguage, rootUser)
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
          ingestState = AssetIngested,
        ),
      )
    }).provide(StringFormatter.test, MessageRelayLive.layer, IriConverter.layer, SipiServiceMock.layer)

}
