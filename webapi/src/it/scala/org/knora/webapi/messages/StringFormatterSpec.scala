/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages

import java.time.Instant
import java.util.UUID

import dsp.errors.AssertionException
import org.knora.webapi._
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.sharedtestdata.SharedOntologyTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataV1

/**
 * Tests [[StringFormatter]].
 */
class StringFormatterSpec extends CoreSpec {
  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  "The StringFormatter class" should {
    "recognize the url of the dhlab site as a valid IRI" in {
      val testUrl: String = "http://dhlab.unibas.ch/"
      val validIri        = stringFormatter.validateAndEscapeIri(testUrl, throw AssertionException(s"Invalid IRI $testUrl"))
      validIri should be(testUrl)
    }

    "recognize the url of the DaSCH site as a valid IRI" in {
      val testUrl  = "http://dasch.swiss"
      val validIri = stringFormatter.validateAndEscapeIri(testUrl, throw AssertionException(s"Invalid IRI $testUrl"))
      validIri should be(testUrl)
    }

    /////////////////////////////////////
    // Built-in ontologies

    "convert http://www.knora.org/ontology/knora-base to http://api.knora.org/ontology/knora-api/simple/v2" in {
      val internalOntologyIri = "http://www.knora.org/ontology/knora-base".toSmartIri
      assert(
        internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
          internalOntologyIri.isKnoraOntologyIri &&
          internalOntologyIri.isKnoraBuiltInDefinitionIri &&
          internalOntologyIri.getProjectCode.isEmpty
      )

      val externalOntologyIri = internalOntologyIri.toOntologySchema(ApiV2Simple)
      externalOntologyIri.toString should ===("http://api.knora.org/ontology/knora-api/simple/v2")
      assert(
        externalOntologyIri.getOntologySchema.contains(ApiV2Simple) &&
          externalOntologyIri.isKnoraOntologyIri &&
          externalOntologyIri.isKnoraBuiltInDefinitionIri &&
          externalOntologyIri.getProjectCode.isEmpty
      )
    }

    "convert http://www.knora.org/ontology/knora-base#Resource to http://api.knora.org/ontology/knora-api/simple/v2#Resource" in {
      val internalEntityIri = "http://www.knora.org/ontology/knora-base#Resource".toSmartIri
      assert(
        internalEntityIri.getOntologySchema.contains(InternalSchema) &&
          internalEntityIri.isKnoraInternalEntityIri &&
          internalEntityIri.isKnoraBuiltInDefinitionIri &&
          internalEntityIri.getProjectCode.isEmpty
      )

      val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2Simple)
      externalEntityIri.toString should ===("http://api.knora.org/ontology/knora-api/simple/v2#Resource")
      assert(
        externalEntityIri.getOntologySchema.contains(ApiV2Simple) &&
          externalEntityIri.isKnoraApiV2EntityIri &&
          externalEntityIri.isKnoraBuiltInDefinitionIri &&
          externalEntityIri.getProjectCode.isEmpty
      )
    }

    "convert http://www.knora.org/ontology/knora-base to http://api.knora.org/ontology/knora-api/v2" in {
      val internalOntologyIri = "http://www.knora.org/ontology/knora-base".toSmartIri
      assert(
        internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
          internalOntologyIri.isKnoraOntologyIri &&
          internalOntologyIri.isKnoraBuiltInDefinitionIri &&
          internalOntologyIri.getProjectCode.isEmpty
      )

      val externalOntologyIri = internalOntologyIri.toOntologySchema(ApiV2Complex)
      externalOntologyIri.toString should ===("http://api.knora.org/ontology/knora-api/v2")
      assert(
        externalOntologyIri.getOntologySchema.contains(ApiV2Complex) &&
          externalOntologyIri.isKnoraOntologyIri &&
          externalOntologyIri.isKnoraBuiltInDefinitionIri &&
          externalOntologyIri.getProjectCode.isEmpty
      )
    }

    "convert http://www.knora.org/ontology/knora-base#Resource to http://api.knora.org/ontology/knora-api/v2#Resource" in {
      val internalEntityIri = "http://www.knora.org/ontology/knora-base#Resource".toSmartIri
      assert(
        internalEntityIri.getOntologySchema.contains(InternalSchema) &&
          internalEntityIri.isKnoraInternalEntityIri &&
          internalEntityIri.isKnoraBuiltInDefinitionIri &&
          internalEntityIri.getProjectCode.isEmpty
      )

      val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2Complex)
      externalEntityIri.toString should ===("http://api.knora.org/ontology/knora-api/v2#Resource")
      assert(
        externalEntityIri.getOntologySchema.contains(ApiV2Complex) &&
          externalEntityIri.isKnoraApiV2EntityIri &&
          externalEntityIri.isKnoraBuiltInDefinitionIri &&
          externalEntityIri.getProjectCode.isEmpty
      )
    }

    "convert http://api.knora.org/ontology/knora-api/simple/v2 to http://www.knora.org/ontology/knora-base" in {
      val externalOntologyIri = "http://api.knora.org/ontology/knora-api/simple/v2".toSmartIri
      assert(
        externalOntologyIri.getOntologySchema.contains(ApiV2Simple) &&
          externalOntologyIri.isKnoraOntologyIri &&
          externalOntologyIri.isKnoraBuiltInDefinitionIri &&
          externalOntologyIri.getProjectCode.isEmpty
      )

      val internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
      internalOntologyIri.toString should ===("http://www.knora.org/ontology/knora-base")
      assert(
        internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
          internalOntologyIri.isKnoraOntologyIri &&
          internalOntologyIri.isKnoraBuiltInDefinitionIri &&
          internalOntologyIri.getProjectCode.isEmpty
      )
    }

    "convert http://api.knora.org/ontology/knora-api/simple/v2#Resource to http://www.knora.org/ontology/knora-base#Resource" in {
      val externalEntityIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri
      assert(
        externalEntityIri.getOntologySchema.contains(ApiV2Simple) &&
          externalEntityIri.isKnoraApiV2EntityIri &&
          externalEntityIri.isKnoraBuiltInDefinitionIri &&
          externalEntityIri.getProjectCode.isEmpty
      )

      val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
      internalEntityIri.toString should ===("http://www.knora.org/ontology/knora-base#Resource")
      assert(
        internalEntityIri.getOntologySchema.contains(InternalSchema) &&
          internalEntityIri.isKnoraInternalEntityIri &&
          internalEntityIri.isKnoraBuiltInDefinitionIri &&
          internalEntityIri.getProjectCode.isEmpty
      )
    }

    "convert http://api.knora.org/ontology/knora-api/v2 to http://www.knora.org/ontology/knora-base" in {
      val externalOntologyIri = "http://api.knora.org/ontology/knora-api/v2".toSmartIri
      assert(
        externalOntologyIri.getOntologySchema.contains(ApiV2Complex) &&
          externalOntologyIri.isKnoraOntologyIri &&
          externalOntologyIri.isKnoraBuiltInDefinitionIri &&
          externalOntologyIri.getProjectCode.isEmpty
      )

      val internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
      internalOntologyIri.toString should ===("http://www.knora.org/ontology/knora-base")
      assert(
        internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
          internalOntologyIri.isKnoraOntologyIri &&
          internalOntologyIri.isKnoraBuiltInDefinitionIri &&
          internalOntologyIri.getProjectCode.isEmpty
      )
    }

    "convert http://api.knora.org/ontology/knora-api/v2#Resource to http://www.knora.org/ontology/knora-base#Resource" in {
      val externalEntityIri = "http://api.knora.org/ontology/knora-api/v2#Resource".toSmartIri
      assert(
        externalEntityIri.getOntologySchema.contains(ApiV2Complex) &&
          externalEntityIri.isKnoraApiV2EntityIri &&
          externalEntityIri.isKnoraBuiltInDefinitionIri &&
          externalEntityIri.getProjectCode.isEmpty
      )

      val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
      internalEntityIri.toString should ===("http://www.knora.org/ontology/knora-base#Resource")
      assert(
        internalEntityIri.getOntologySchema.contains(InternalSchema) &&
          internalEntityIri.isKnoraInternalEntityIri &&
          internalEntityIri.isKnoraBuiltInDefinitionIri &&
          internalEntityIri.getProjectCode.isEmpty
      )
    }

    //////////////////////////////////////////
    // Non-shared, project-specific ontologies

    "convert http://www.knora.org/ontology/00FF/images to http://0.0.0.0:3333/ontology/00FF/images/simple/v2" in {
      val internalOntologyIri = "http://www.knora.org/ontology/00FF/images".toSmartIri
      assert(
        internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
          internalOntologyIri.isKnoraOntologyIri &&
          !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
          internalOntologyIri.getProjectCode.contains("00FF")
      )

      val externalOntologyIri = internalOntologyIri.toOntologySchema(ApiV2Simple)
      externalOntologyIri.toString should ===("http://0.0.0.0:3333/ontology/00FF/images/simple/v2")
      assert(
        externalOntologyIri.getOntologySchema.contains(ApiV2Simple) &&
          externalOntologyIri.isKnoraOntologyIri &&
          !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
          externalOntologyIri.getProjectCode.contains("00FF")
      )
    }

    "convert http://www.knora.org/ontology/00FF/images#bild to http://0.0.0.0:3333/ontology/00FF/images/simple/v2#bild" in {
      val internalEntityIri = "http://www.knora.org/ontology/00FF/images#bild".toSmartIri
      assert(
        internalEntityIri.getOntologySchema.contains(InternalSchema) &&
          internalEntityIri.isKnoraInternalEntityIri &&
          !internalEntityIri.isKnoraBuiltInDefinitionIri &&
          internalEntityIri.getProjectCode.contains("00FF")
      )

      val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2Simple)
      externalEntityIri.toString should ===("http://0.0.0.0:3333/ontology/00FF/images/simple/v2#bild")
      assert(
        externalEntityIri.getOntologySchema.contains(ApiV2Simple) &&
          externalEntityIri.isKnoraApiV2EntityIri &&
          !externalEntityIri.isKnoraBuiltInDefinitionIri &&
          externalEntityIri.getProjectCode.contains("00FF")
      )
    }

    "convert http://www.knora.org/ontology/00FF/images to http://0.0.0.0:3333/ontology/00FF/images/v2" in {
      val internalOntologyIri = "http://www.knora.org/ontology/00FF/images".toSmartIri
      assert(
        internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
          internalOntologyIri.isKnoraOntologyIri &&
          !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
          internalOntologyIri.getProjectCode.contains("00FF")
      )

      val externalOntologyIri = internalOntologyIri.toOntologySchema(ApiV2Complex)
      externalOntologyIri.toString should ===("http://0.0.0.0:3333/ontology/00FF/images/v2")
      assert(
        externalOntologyIri.getOntologySchema.contains(ApiV2Complex) &&
          externalOntologyIri.isKnoraOntologyIri &&
          !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
          externalOntologyIri.getProjectCode.contains("00FF")
      )
    }

    "convert http://www.knora.org/ontology/00FF/images#bild to http://0.0.0.0:3333/ontology/00FF/images/v2#bild" in {
      val internalEntityIri = "http://www.knora.org/ontology/00FF/images#bild".toSmartIri
      assert(
        internalEntityIri.getOntologySchema.contains(InternalSchema) &&
          internalEntityIri.isKnoraInternalEntityIri &&
          !internalEntityIri.isKnoraBuiltInDefinitionIri &&
          internalEntityIri.getProjectCode.contains("00FF")
      )

      val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2Complex)
      externalEntityIri.toString should ===("http://0.0.0.0:3333/ontology/00FF/images/v2#bild")
      assert(
        externalEntityIri.getOntologySchema.contains(ApiV2Complex) &&
          externalEntityIri.isKnoraApiV2EntityIri &&
          !externalEntityIri.isKnoraBuiltInDefinitionIri &&
          externalEntityIri.getProjectCode.contains("00FF")
      )
    }

    "convert http://0.0.0.0:3333/ontology/00FF/images/simple/v2 to http://www.knora.org/ontology/00FF/images" in {
      val externalOntologyIri = "http://0.0.0.0:3333/ontology/00FF/images/simple/v2".toSmartIri
      assert(
        externalOntologyIri.getOntologySchema.contains(ApiV2Simple) &&
          externalOntologyIri.isKnoraOntologyIri &&
          !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
          externalOntologyIri.getProjectCode.contains("00FF")
      )

      val internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
      internalOntologyIri.toString should ===("http://www.knora.org/ontology/00FF/images")
      assert(
        internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
          internalOntologyIri.isKnoraOntologyIri &&
          !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
          internalOntologyIri.getProjectCode.contains("00FF")
      )
    }

    "convert http://0.0.0.0:3333/ontology/00FF/images/simple/v2#bild to http://www.knora.org/ontology/00FF/images#bild" in {
      val externalEntityIri = "http://0.0.0.0:3333/ontology/00FF/images/simple/v2#bild".toSmartIri
      assert(
        externalEntityIri.getOntologySchema.contains(ApiV2Simple) &&
          externalEntityIri.isKnoraApiV2EntityIri &&
          !externalEntityIri.isKnoraBuiltInDefinitionIri &&
          externalEntityIri.getProjectCode.contains("00FF")
      )

      val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
      internalEntityIri.toString should ===("http://www.knora.org/ontology/00FF/images#bild")
      assert(
        internalEntityIri.getOntologySchema.contains(InternalSchema) &&
          internalEntityIri.isKnoraInternalEntityIri &&
          !internalEntityIri.isKnoraBuiltInDefinitionIri &&
          internalEntityIri.getProjectCode.contains("00FF")
      )
    }

    "convert http://0.0.0.0:3333/ontology/00FF/images/v2 to http://www.knora.org/ontology/00FF/images" in {
      val externalOntologyIri = "http://0.0.0.0:3333/ontology/00FF/images/v2".toSmartIri
      assert(
        externalOntologyIri.getOntologySchema.contains(ApiV2Complex) &&
          externalOntologyIri.isKnoraOntologyIri &&
          !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
          externalOntologyIri.getProjectCode.contains("00FF")
      )

      val internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
      internalOntologyIri.toString should ===("http://www.knora.org/ontology/00FF/images")
      assert(
        internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
          internalOntologyIri.isKnoraOntologyIri &&
          !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
          internalOntologyIri.getProjectCode.contains("00FF")
      )
    }

    "convert http://0.0.0.0:3333/ontology/00FF/images/v2#bild to http://www.knora.org/ontology/00FF/images#bild" in {
      val externalEntityIri = "http://0.0.0.0:3333/ontology/00FF/images/v2#bild".toSmartIri
      assert(
        externalEntityIri.getOntologySchema.contains(ApiV2Complex) &&
          externalEntityIri.isKnoraApiV2EntityIri &&
          !externalEntityIri.isKnoraBuiltInDefinitionIri &&
          externalEntityIri.getProjectCode.contains("00FF")
      )

      val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
      internalEntityIri.toString should ===("http://www.knora.org/ontology/00FF/images#bild")
      assert(
        internalEntityIri.getOntologySchema.contains(InternalSchema) &&
          internalEntityIri.isKnoraInternalEntityIri &&
          !internalEntityIri.isKnoraBuiltInDefinitionIri &&
          internalEntityIri.getProjectCode.contains("00FF")
      )
    }

    "convert http://www.knora.org/ontology/knora-base#TextValue to http://www.w3.org/2001/XMLSchema#string" in {
      val internalEntityIri = "http://www.knora.org/ontology/knora-base#TextValue".toSmartIri
      assert(
        internalEntityIri.getOntologySchema.contains(InternalSchema) &&
          internalEntityIri.isKnoraInternalEntityIri &&
          internalEntityIri.isKnoraBuiltInDefinitionIri &&
          internalEntityIri.getProjectCode.isEmpty
      )

      val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2Simple)
      assert(externalEntityIri.toString == "http://www.w3.org/2001/XMLSchema#string" && !externalEntityIri.isKnoraIri)
    }

    /////////////////////////////////////////////////////////////
    // Shared ontologies in the default shared ontologies project

    "convert http://www.knora.org/ontology/shared/example to http://api.knora.org/ontology/shared/example/simple/v2" in {
      val internalOntologyIri = "http://www.knora.org/ontology/shared/example".toSmartIri
      assert(
        internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
          internalOntologyIri.isKnoraOntologyIri &&
          !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
          internalOntologyIri.isKnoraSharedDefinitionIri &&
          internalOntologyIri.getProjectCode.contains("0000")
      )

      val externalOntologyIri = internalOntologyIri.toOntologySchema(ApiV2Simple)
      externalOntologyIri.toString should ===("http://api.knora.org/ontology/shared/example/simple/v2")
      assert(
        externalOntologyIri.getOntologySchema.contains(ApiV2Simple) &&
          externalOntologyIri.isKnoraOntologyIri &&
          !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
          externalOntologyIri.isKnoraSharedDefinitionIri &&
          externalOntologyIri.getProjectCode.contains("0000")
      )
    }

    "convert http://www.knora.org/ontology/shared/example#Person to http://api.knora.org/ontology/shared/example/simple/v2#Person" in {
      val internalEntityIri = "http://www.knora.org/ontology/shared/example#Person".toSmartIri
      assert(
        internalEntityIri.isKnoraInternalEntityIri &&
          !internalEntityIri.isKnoraBuiltInDefinitionIri &&
          internalEntityIri.isKnoraSharedDefinitionIri &&
          internalEntityIri.getProjectCode.contains("0000")
      )

      val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2Simple)
      externalEntityIri.toString should ===("http://api.knora.org/ontology/shared/example/simple/v2#Person")
      assert(
        externalEntityIri.getOntologySchema.contains(ApiV2Simple) &&
          externalEntityIri.isKnoraApiV2EntityIri &&
          !externalEntityIri.isKnoraBuiltInDefinitionIri &&
          externalEntityIri.isKnoraSharedDefinitionIri &&
          externalEntityIri.getProjectCode.contains("0000")
      )
    }

    "convert http://www.knora.org/ontology/shared/example to http://api.knora.org/ontology/shared/example/v2" in {
      val internalOntologyIri = "http://www.knora.org/ontology/shared/example".toSmartIri
      assert(
        internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
          internalOntologyIri.isKnoraOntologyIri &&
          !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
          internalOntologyIri.isKnoraSharedDefinitionIri &&
          internalOntologyIri.getProjectCode.contains("0000")
      )

      val externalOntologyIri = internalOntologyIri.toOntologySchema(ApiV2Complex)
      externalOntologyIri.toString should ===("http://api.knora.org/ontology/shared/example/v2")
      assert(
        externalOntologyIri.getOntologySchema.contains(ApiV2Complex) &&
          externalOntologyIri.isKnoraOntologyIri &&
          !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
          externalOntologyIri.isKnoraSharedDefinitionIri &&
          externalOntologyIri.getProjectCode.contains("0000")
      )
    }

    "convert http://www.knora.org/ontology/shared/example#Person to http://api.knora.org/ontology/shared/example/v2#Person" in {
      val internalEntityIri = "http://www.knora.org/ontology/shared/example#Person".toSmartIri
      assert(
        internalEntityIri.isKnoraInternalEntityIri &&
          !internalEntityIri.isKnoraBuiltInDefinitionIri &&
          internalEntityIri.isKnoraSharedDefinitionIri &&
          internalEntityIri.getProjectCode.contains("0000")
      )

      val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2Complex)
      externalEntityIri.toString should ===("http://api.knora.org/ontology/shared/example/v2#Person")
      assert(
        externalEntityIri.getOntologySchema.contains(ApiV2Complex) &&
          externalEntityIri.isKnoraApiV2EntityIri &&
          !externalEntityIri.isKnoraBuiltInDefinitionIri &&
          externalEntityIri.isKnoraSharedDefinitionIri &&
          externalEntityIri.getProjectCode.contains("0000")
      )
    }

    "convert http://api.knora.org/ontology/shared/example/simple/v2 to http://www.knora.org/ontology/shared/example" in {
      val externalOntologyIri = "http://api.knora.org/ontology/shared/example/simple/v2".toSmartIri
      assert(
        externalOntologyIri.getOntologySchema.contains(ApiV2Simple) &&
          externalOntologyIri.isKnoraOntologyIri &&
          !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
          externalOntologyIri.isKnoraSharedDefinitionIri &&
          externalOntologyIri.getProjectCode.contains("0000")
      )

      val internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
      internalOntologyIri.toString should ===("http://www.knora.org/ontology/shared/example")
      assert(
        internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
          internalOntologyIri.isKnoraOntologyIri &&
          !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
          internalOntologyIri.isKnoraSharedDefinitionIri &&
          internalOntologyIri.getProjectCode.contains("0000")
      )
    }

    "convert http://api.knora.org/ontology/shared/example/simple/v2#Person to http://www.knora.org/ontology/shared/example#Person" in {
      val externalEntityIri = "http://api.knora.org/ontology/shared/example/simple/v2#Person".toSmartIri
      assert(
        externalEntityIri.getOntologySchema.contains(ApiV2Simple) &&
          !externalEntityIri.isKnoraBuiltInDefinitionIri &&
          externalEntityIri.isKnoraSharedDefinitionIri &&
          externalEntityIri.getProjectCode.contains("0000")
      )

      val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
      internalEntityIri.toString should ===("http://www.knora.org/ontology/shared/example#Person")
      assert(
        internalEntityIri.getOntologySchema.contains(InternalSchema) &&
          !internalEntityIri.isKnoraBuiltInDefinitionIri &&
          internalEntityIri.isKnoraSharedDefinitionIri &&
          internalEntityIri.getProjectCode.contains("0000")
      )
    }

    "convert http://api.knora.org/ontology/shared/example/v2 to http://www.knora.org/ontology/shared/example" in {
      val externalOntologyIri = "http://api.knora.org/ontology/shared/example/v2".toSmartIri
      assert(
        externalOntologyIri.getOntologySchema.contains(ApiV2Complex) &&
          externalOntologyIri.isKnoraOntologyIri &&
          !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
          externalOntologyIri.isKnoraSharedDefinitionIri &&
          externalOntologyIri.getProjectCode.contains("0000")
      )

      val internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
      internalOntologyIri.toString should ===("http://www.knora.org/ontology/shared/example")
      assert(
        internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
          internalOntologyIri.isKnoraOntologyIri &&
          !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
          internalOntologyIri.isKnoraSharedDefinitionIri &&
          internalOntologyIri.getProjectCode.contains("0000")
      )
    }

    "convert http://api.knora.org/ontology/shared/example/v2#Person to http://www.knora.org/ontology/shared/example#Person" in {
      val externalEntityIri = "http://api.knora.org/ontology/shared/example/v2#Person".toSmartIri
      assert(
        externalEntityIri.getOntologySchema.contains(ApiV2Complex) &&
          !externalEntityIri.isKnoraBuiltInDefinitionIri &&
          externalEntityIri.isKnoraSharedDefinitionIri &&
          externalEntityIri.getProjectCode.contains("0000")
      )

      val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
      internalEntityIri.toString should ===("http://www.knora.org/ontology/shared/example#Person")
      assert(
        internalEntityIri.isKnoraInternalEntityIri &&
          !internalEntityIri.isKnoraBuiltInDefinitionIri &&
          internalEntityIri.isKnoraSharedDefinitionIri &&
          internalEntityIri.getProjectCode.contains("0000")
      )
    }

    ///////////////////////////////////////////////////////////////
    // Shared ontologies in a non-default shared ontologies project

    "convert http://www.knora.org/ontology/shared/0111/example to http://api.knora.org/ontology/shared/0111/example/simple/v2" in {
      val internalOntologyIri = "http://www.knora.org/ontology/shared/0111/example".toSmartIri
      assert(
        internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
          internalOntologyIri.isKnoraOntologyIri &&
          !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
          internalOntologyIri.isKnoraSharedDefinitionIri &&
          internalOntologyIri.getProjectCode.contains("0111")
      )

      val externalOntologyIri = internalOntologyIri.toOntologySchema(ApiV2Simple)
      externalOntologyIri.toString should ===("http://api.knora.org/ontology/shared/0111/example/simple/v2")
      assert(
        externalOntologyIri.getOntologySchema.contains(ApiV2Simple) &&
          externalOntologyIri.isKnoraOntologyIri &&
          !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
          externalOntologyIri.isKnoraSharedDefinitionIri &&
          externalOntologyIri.getProjectCode.contains("0111")
      )
    }

    "convert http://www.knora.org/ontology/shared/0111/example#Person to http://api.knora.org/ontology/shared/0111/example/simple/v2#Person" in {
      val internalEntityIri = "http://www.knora.org/ontology/shared/0111/example#Person".toSmartIri
      assert(
        internalEntityIri.isKnoraInternalEntityIri &&
          !internalEntityIri.isKnoraBuiltInDefinitionIri &&
          internalEntityIri.isKnoraSharedDefinitionIri &&
          internalEntityIri.getProjectCode.contains("0111")
      )

      val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2Simple)
      externalEntityIri.toString should ===("http://api.knora.org/ontology/shared/0111/example/simple/v2#Person")
      assert(
        externalEntityIri.getOntologySchema.contains(ApiV2Simple) &&
          externalEntityIri.isKnoraApiV2EntityIri &&
          !externalEntityIri.isKnoraBuiltInDefinitionIri &&
          externalEntityIri.isKnoraSharedDefinitionIri &&
          externalEntityIri.getProjectCode.contains("0111")
      )
    }

    "convert http://www.knora.org/ontology/shared/0111/example to http://api.knora.org/ontology/shared/0111/example/v2" in {
      val internalOntologyIri = "http://www.knora.org/ontology/shared/0111/example".toSmartIri
      assert(
        internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
          internalOntologyIri.isKnoraOntologyIri &&
          !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
          internalOntologyIri.isKnoraSharedDefinitionIri &&
          internalOntologyIri.getProjectCode.contains("0111")
      )

      val externalOntologyIri = internalOntologyIri.toOntologySchema(ApiV2Complex)
      externalOntologyIri.toString should ===("http://api.knora.org/ontology/shared/0111/example/v2")
      assert(
        externalOntologyIri.getOntologySchema.contains(ApiV2Complex) &&
          externalOntologyIri.isKnoraOntologyIri &&
          !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
          externalOntologyIri.isKnoraSharedDefinitionIri &&
          externalOntologyIri.getProjectCode.contains("0111")
      )
    }

    "convert http://www.knora.org/ontology/shared/0111/example#Person to http://api.knora.org/ontology/shared/0111/example/v2#Person" in {
      val internalEntityIri = "http://www.knora.org/ontology/shared/0111/example#Person".toSmartIri
      assert(
        internalEntityIri.isKnoraInternalEntityIri &&
          !internalEntityIri.isKnoraBuiltInDefinitionIri &&
          internalEntityIri.isKnoraSharedDefinitionIri &&
          internalEntityIri.getProjectCode.contains("0111")
      )

      val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2Complex)
      externalEntityIri.toString should ===("http://api.knora.org/ontology/shared/0111/example/v2#Person")
      assert(
        externalEntityIri.getOntologySchema.contains(ApiV2Complex) &&
          externalEntityIri.isKnoraApiV2EntityIri &&
          !externalEntityIri.isKnoraBuiltInDefinitionIri &&
          externalEntityIri.isKnoraSharedDefinitionIri &&
          externalEntityIri.getProjectCode.contains("0111")
      )
    }

    "convert http://api.knora.org/ontology/shared/0111/example/simple/v2 to http://www.knora.org/ontology/shared/0111/example" in {
      val externalOntologyIri = "http://api.knora.org/ontology/shared/0111/example/simple/v2".toSmartIri
      assert(
        externalOntologyIri.getOntologySchema.contains(ApiV2Simple) &&
          externalOntologyIri.isKnoraOntologyIri &&
          !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
          externalOntologyIri.isKnoraSharedDefinitionIri &&
          externalOntologyIri.getProjectCode.contains("0111")
      )

      val internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
      internalOntologyIri.toString should ===("http://www.knora.org/ontology/shared/0111/example")
      assert(
        internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
          internalOntologyIri.isKnoraOntologyIri &&
          !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
          internalOntologyIri.isKnoraSharedDefinitionIri &&
          internalOntologyIri.getProjectCode.contains("0111")
      )
    }

    "convert http://api.knora.org/ontology/shared/0111/example/simple/v2#Person to http://www.knora.org/ontology/shared/0111/example#Person" in {
      val externalEntityIri = "http://api.knora.org/ontology/shared/0111/example/simple/v2#Person".toSmartIri
      assert(
        externalEntityIri.getOntologySchema.contains(ApiV2Simple) &&
          !externalEntityIri.isKnoraBuiltInDefinitionIri &&
          externalEntityIri.isKnoraSharedDefinitionIri &&
          externalEntityIri.getProjectCode.contains("0111")
      )

      val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
      internalEntityIri.toString should ===("http://www.knora.org/ontology/shared/0111/example#Person")
      assert(
        internalEntityIri.getOntologySchema.contains(InternalSchema) &&
          !internalEntityIri.isKnoraBuiltInDefinitionIri &&
          internalEntityIri.isKnoraSharedDefinitionIri &&
          internalEntityIri.getProjectCode.contains("0111")
      )
    }

    "convert http://api.knora.org/ontology/shared/0111/example/v2 to http://www.knora.org/ontology/shared/0111/example" in {
      val externalOntologyIri = "http://api.knora.org/ontology/shared/0111/example/v2".toSmartIri
      assert(
        externalOntologyIri.getOntologySchema.contains(ApiV2Complex) &&
          externalOntologyIri.isKnoraOntologyIri &&
          !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
          externalOntologyIri.isKnoraSharedDefinitionIri &&
          externalOntologyIri.getProjectCode.contains("0111")
      )

      val internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
      internalOntologyIri.toString should ===("http://www.knora.org/ontology/shared/0111/example")
      assert(
        internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
          internalOntologyIri.isKnoraOntologyIri &&
          !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
          internalOntologyIri.isKnoraSharedDefinitionIri &&
          internalOntologyIri.getProjectCode.contains("0111")
      )
    }

    "convert http://api.knora.org/ontology/shared/0111/example/v2#Person to http://www.knora.org/ontology/shared/0111/example#Person" in {
      val externalEntityIri = "http://api.knora.org/ontology/shared/0111/example/v2#Person".toSmartIri
      assert(
        externalEntityIri.getOntologySchema.contains(ApiV2Complex) &&
          !externalEntityIri.isKnoraBuiltInDefinitionIri &&
          externalEntityIri.isKnoraSharedDefinitionIri &&
          externalEntityIri.getProjectCode.contains("0111")
      )

      val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
      internalEntityIri.toString should ===("http://www.knora.org/ontology/shared/0111/example#Person")
      assert(
        internalEntityIri.isKnoraInternalEntityIri &&
          !internalEntityIri.isKnoraBuiltInDefinitionIri &&
          internalEntityIri.isKnoraSharedDefinitionIri &&
          internalEntityIri.getProjectCode.contains("0111")
      )
    }

    /////////////////////

    "not change http://www.w3.org/2001/XMLSchema#string when converting to InternalSchema" in {
      val externalEntityIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri
      assert(!externalEntityIri.isKnoraIri)

      val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
      assert(internalEntityIri.toString == externalEntityIri.toString && !externalEntityIri.isKnoraIri)
    }

    "parse http://rdfh.ch/0000/Ef9heHjPWDS7dMR_gGax2Q" in {
      val dataIri = "http://rdfh.ch/0000/Ef9heHjPWDS7dMR_gGax2Q".toSmartIri
      assert(dataIri.isKnoraDataIri)
    }

    "parse http://rdfh.ch/Ef9heHjPWDS7dMR_gGax2Q" in {
      val dataIri = "http://rdfh.ch/Ef9heHjPWDS7dMR_gGax2Q".toSmartIri
      assert(dataIri.isKnoraDataIri)
    }

    "parse http://www.w3.org/2001/XMLSchema#integer" in {
      val xsdIri = "http://www.w3.org/2001/XMLSchema#integer".toSmartIri
      assert(
        !xsdIri.isKnoraOntologyIri &&
          !xsdIri.isKnoraDataIri &&
          xsdIri.getOntologySchema.isEmpty &&
          xsdIri.getProjectCode.isEmpty
      )
    }

    "validate import namespace with project shortcode" in {
      val defaultNamespace = "http://api.knora.org/ontology/0801/biblio/xml-import/v1#"
      stringFormatter
        .xmlImportNamespaceToInternalOntologyIriV1(defaultNamespace)
        .getOrElse(
          throw AssertionException("Invalid XML import namespace")
        )
        .toString should be("http://www.knora.org/ontology/0801/biblio")
    }

    "validate internal ontology path" in {
      val urlPath = "/ontology/knora-api/simple/v2"
      stringFormatter.isBuiltInApiV2OntologyUrlPath(urlPath) should be(true)
    }

    "reject an empty IRI string" in {
      assertThrows[AssertionException] {
        "".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject the IRI 'foo'" in {
      assertThrows[AssertionException] {
        "foo".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject http://" in {
      assertThrows[AssertionException] {
        "http://".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject ftp://www.knora.org/ontology/00FF/images (wrong URL scheme)" in {
      assertThrows[AssertionException] {
        "ftp://www.knora.org/ontology/00FF/images".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject https://www.knora.org/ontology/00FF/images (wrong URL scheme)" in {
      assertThrows[AssertionException] {
        "https://www.knora.org/ontology/00FF/images".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject http://www.knora.org/" in {
      assertThrows[AssertionException] {
        "http://www.knora.org/".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject http://api.knora.org/" in {
      assertThrows[AssertionException] {
        "http://api.knora.org/".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject http://0.0.0.0:3333/" in {
      assertThrows[AssertionException] {
        "http://api.knora.org/".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject http://www.knora.org/ontology" in {
      assertThrows[AssertionException] {
        "http://www.knora.org/ontology".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject http://api.knora.org/ontology" in {
      assertThrows[AssertionException] {
        "http://api.knora.org/".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject http://0.0.0.0:3333/ontology" in {
      assertThrows[AssertionException] {
        "http://api.knora.org/ontology".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject http://www.knora.org/ontology/00FF/images/v2 (wrong hostname)" in {
      assertThrows[AssertionException] {
        "http://www.knora.org/ontology/00FF/images/v2".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject http://www.knora.org/ontology/00FF/images/simple/v2 (wrong hostname)" in {
      assertThrows[AssertionException] {
        "http://www.knora.org/ontology/00FF/images/v2".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject http://api.knora.org/ontology/00FF/images/v2 (wrong hostname)" in {
      assertThrows[AssertionException] {
        "http://api.knora.org/ontology/00FF/images/v2".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject http://api.knora.org/ontology/00FF/images/simple/v2 (wrong hostname)" in {
      assertThrows[AssertionException] {
        "http://api.knora.org/ontology/00FF/images/simple/v2".toSmartIriWithErr(
          throw AssertionException(s"Invalid IRI")
        )
      }
    }

    "reject http://0.0.0.0:3333/ontology/v2 (invalid ontology name)" in {
      assertThrows[AssertionException] {
        "http://0.0.0.0:3333/ontology/v2".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject http://0.0.0.0:3333/ontology/0000/v2 (invalid ontology name)" in {
      assertThrows[AssertionException] {
        "http://0.0.0.0:3333/ontology/0000/v2".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject http://0.0.0.0:3333/ontology/ontology (invalid ontology name)" in {
      assertThrows[AssertionException] {
        "http://0.0.0.0:3333/ontology/ontology".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject http://0.0.0.0:3333/ontology/0000/ontology (invalid ontology name)" in {
      assertThrows[AssertionException] {
        "http://0.0.0.0:3333/ontology/0000/ontology".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject http://0.0.0.0:3333/ontology/0000/simple/simple/v2 (invalid ontology name)" in {
      assertThrows[AssertionException] {
        "http://0.0.0.0:3333/ontology/0000/simple/simple/v2".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject http://0.0.0.0:3333/ontology/00FF/images/v2#1234 (invalid entity name)" in {
      assertThrows[AssertionException] {
        "http://0.0.0.0:3333/ontology/00FF/images/v2#1234".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject http://0.0.0.0:3333/ontology/images/v2 (missing project shortcode in ontology IRI)" in {
      assertThrows[AssertionException] {
        "http://0.0.0.0:3333/ontology/0000/simple/simple/v2".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject http://0.0.0.0:3333/ontology/images/simple/v2 (missing project shortcode in ontology IRI)" in {
      assertThrows[AssertionException] {
        "http://0.0.0.0:3333/ontology/images/simple/v2".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject http://0.0.0.0:3333/ontology/images/v2#bild (missing project shortcode in entity IRI)" in {
      assertThrows[AssertionException] {
        "http://0.0.0.0:3333/ontology/images/v2#bild".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject http://0.0.0.0:3333/ontology/images/simple/v2#bild (missing project shortcode in entity IRI)" in {
      assertThrows[AssertionException] {
        "http://0.0.0.0:3333/ontology/images/simple/v2#bild".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject http://0.0.0.0:3333/ontology/shared/example/v2 (shared project code with local hostname in ontology IRI)" in {
      assertThrows[AssertionException] {
        "http://0.0.0.0:3333/ontology/shared/example/v2".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "enable pattern matching with SmartIri" in {
      val input: SmartIri = "http://www.knora.org/ontology/knora-base#Resource".toSmartIri

      val isResource = input match {
        case SmartIri(OntologyConstants.KnoraBase.Resource) => true
        case _                                              => false
      }

      assert(isResource)
    }

    "convert 100,000 IRIs" ignore {
      val totalIris = 100000

      val parseStart = System.currentTimeMillis

      for (i <- 1 to totalIris) {
        val iriStr = s"http://0.0.0.0:3333/ontology/00FF/images/v2#class$i"
        iriStr.toSmartIri.toOntologySchema(InternalSchema)
      }

      val parseEnd            = System.currentTimeMillis
      val parseDuration       = (parseEnd - parseStart).toDouble
      val parseDurationPerIri = parseDuration / totalIris.toDouble
      println(f"Parse and store $totalIris IRIs, $parseDuration ms, time per IRI $parseDurationPerIri%1.5f ms")

      val retrieveStart = System.currentTimeMillis

      for (i <- 1 to totalIris) {
        val iriStr = s"http://0.0.0.0:3333/ontology/00FF/images/v2#class$i"
        iriStr.toSmartIri.toOntologySchema(InternalSchema)
      }

      val retrieveEnd            = System.currentTimeMillis
      val retrieveDuration       = (retrieveEnd - retrieveStart).toDouble
      val retrieveDurationPerIri = retrieveDuration / totalIris.toDouble

      println(f"Retrieve time $retrieveDuration ms, time per IRI $retrieveDurationPerIri%1.5f ms")
    }

    "return the data named graph of a project with short code" in {
      val shortcode = SharedTestDataV1.imagesProjectInfo.shortcode
      val shortname = SharedTestDataV1.imagesProjectInfo.shortname
      val expected  = s"http://www.knora.org/data/$shortcode/$shortname"
      val result    = stringFormatter.projectDataNamedGraphV1(SharedTestDataV1.imagesProjectInfo)
      result should be(expected)

      // check consistency of our test data
      stringFormatter.projectDataNamedGraphV2(SharedTestDataADM.anythingProject) should be(
        SharedOntologyTestDataADM.ANYTHING_DATA_IRI
      )
      stringFormatter.projectDataNamedGraphV2(SharedTestDataADM.imagesProject) should be(
        SharedOntologyTestDataADM.IMAGES_DATA_IRI
      )
      stringFormatter.projectDataNamedGraphV2(SharedTestDataADM.beolProject) should be(
        SharedOntologyTestDataADM.BEOL_DATA_IRI
      )
      stringFormatter.projectDataNamedGraphV2(SharedTestDataADM.incunabulaProject) should be(
        SharedOntologyTestDataADM.INCUNABULA_DATA_IRI
      )
      stringFormatter.projectDataNamedGraphV2(SharedTestDataADM.dokubibProject) should be(
        SharedOntologyTestDataADM.DOKUBIB_DATA_IRI
      )
    }

    "generate an ARK URL for a resource IRI without a timestamp" in {
      val resourceIri: IRI = "http://rdfh.ch/0001/cmfk1DMHRBiR4-_6HXpEFA"
      val arkUrl           = resourceIri.toSmartIri.fromResourceIriToArkUrl()
      assert(arkUrl == "http://0.0.0.0:3336/ark:/72163/1/0001/cmfk1DMHRBiR4=_6HXpEFAn")
    }

    "generate an ARK URL for a resource IRI with a timestamp with a fractional part" in {
      val resourceIri: IRI = "http://rdfh.ch/0001/cmfk1DMHRBiR4-_6HXpEFA"
      val timestamp        = Instant.parse("2018-06-04T08:56:22.9876543Z")
      val arkUrl           = resourceIri.toSmartIri.fromResourceIriToArkUrl(maybeTimestamp = Some(timestamp))
      assert(arkUrl == "http://0.0.0.0:3336/ark:/72163/1/0001/cmfk1DMHRBiR4=_6HXpEFAn.20180604T0856229876543Z")
    }

    "generate an ARK URL for a resource IRI with a timestamp with a leading zero" in {
      val resourceIri: IRI = "http://rdfh.ch/0001/cmfk1DMHRBiR4-_6HXpEFA"
      val timestamp        = Instant.parse("2018-06-04T08:56:22.098Z")
      val arkUrl           = resourceIri.toSmartIri.fromResourceIriToArkUrl(maybeTimestamp = Some(timestamp))
      assert(arkUrl == "http://0.0.0.0:3336/ark:/72163/1/0001/cmfk1DMHRBiR4=_6HXpEFAn.20180604T085622098Z")
    }

    "generate an ARK URL for a resource IRI with a timestamp without a fractional part" in {
      val resourceIri: IRI = "http://rdfh.ch/0001/cmfk1DMHRBiR4-_6HXpEFA"
      val timestamp        = Instant.parse("2018-06-04T08:56:22Z")
      val arkUrl           = resourceIri.toSmartIri.fromResourceIriToArkUrl(maybeTimestamp = Some(timestamp))
      assert(arkUrl == "http://0.0.0.0:3336/ark:/72163/1/0001/cmfk1DMHRBiR4=_6HXpEFAn.20180604T085622Z")
    }

  }

  "The StringFormatter class for User and Project" should {
    "validate project IRI" in {
      stringFormatter.validateAndEscapeProjectIri(
        SharedTestDataADM.incunabulaProject.id,
        throw AssertionException("not valid")
      ) shouldBe SharedTestDataADM.incunabulaProject.id
      stringFormatter.validateAndEscapeProjectIri(
        SharedTestDataADM.systemProject.id,
        throw AssertionException("not valid")
      ) shouldBe SharedTestDataADM.systemProject.id
      stringFormatter.validateAndEscapeProjectIri(
        SharedTestDataADM.defaultSharedOntologiesProject.id,
        throw AssertionException("not valid")
      ) shouldBe SharedTestDataADM.defaultSharedOntologiesProject.id
    }

    "validate project shortname" in {
      // shortname with dash is valid
      assert(stringFormatter.validateAndEscapeProjectShortname("valid-shortname").contains("valid-shortname"))
      // shortname with numbers
      assert(stringFormatter.validateAndEscapeProjectShortname("valid_1111").contains("valid_1111"))
      // has special character colon
      assert(stringFormatter.validateAndEscapeProjectShortname("invalid:shortname").isEmpty)
      // begins with dash
      assert(stringFormatter.validateAndEscapeProjectShortname("-invalidshortname").isEmpty)
      // begins with dot
      assert(stringFormatter.validateAndEscapeProjectShortname(".invalidshortname").isEmpty)
      // includes slash
      assert(stringFormatter.validateAndEscapeProjectShortname("invalid/shortname").isEmpty)
      // includes @
      assert(stringFormatter.validateAndEscapeProjectShortname("invalid@shortname").isEmpty)
    }

    "validate project shortcode" in {
      stringFormatter.validateProjectShortcode("00FF", throw AssertionException("not valid")) should be("00FF")
      stringFormatter.validateProjectShortcode("00ff", throw AssertionException("not valid")) should be("00FF")
      stringFormatter.validateProjectShortcode("12aF", throw AssertionException("not valid")) should be("12AF")

      an[AssertionException] should be thrownBy {
        stringFormatter.validateProjectShortcode("000", throw AssertionException("not valid"))
      }

      an[AssertionException] should be thrownBy {
        stringFormatter.validateProjectShortcode("00000", throw AssertionException("not valid"))
      }

      an[AssertionException] should be thrownBy {
        stringFormatter.validateProjectShortcode("wxyz", throw AssertionException("not valid"))
      }
    }

    "validate username" in {
      // 4 - 50 characters long
      an[AssertionException] should be thrownBy {
        stringFormatter.validateAndEscapeUsername("abc", throw AssertionException("not valid"))
      }
      an[AssertionException] should be thrownBy {
        stringFormatter.validateAndEscapeUsername(
          "123456789012345678901234567890123456789012345678901",
          throw AssertionException("not valid")
        )
      }

      // only contain alphanumeric, underscore, and dot
      stringFormatter.validateAndEscapeUsername("a_2.3", throw AssertionException("not valid")) should be("a_2.3")

      an[AssertionException] should be thrownBy {
        stringFormatter.validateAndEscapeUsername("a_2.3-4", throw AssertionException("not valid"))
      }

      // not allow @
      an[AssertionException] should be thrownBy {
        stringFormatter.validateUsername("donald.duck@example.com", throw AssertionException("not valid"))
      }

      // Underscore and dot can't be at the end or start of a username
      an[AssertionException] should be thrownBy {
        stringFormatter.validateAndEscapeUsername("_username", throw AssertionException("not valid"))
      }
      an[AssertionException] should be thrownBy {
        stringFormatter.validateAndEscapeUsername("username_", throw AssertionException("not valid"))
      }
      an[AssertionException] should be thrownBy {
        stringFormatter.validateAndEscapeUsername(".username", throw AssertionException("not valid"))
      }
      an[AssertionException] should be thrownBy {
        stringFormatter.validateAndEscapeUsername("username.", throw AssertionException("not valid"))
      }

      // Underscore or dot can't be used multiple times in a row
      an[AssertionException] should be thrownBy {
        stringFormatter.validateAndEscapeUsername("user__name", throw AssertionException("not valid"))
      }
      an[AssertionException] should be thrownBy {
        stringFormatter.validateAndEscapeUsername("user..name", throw AssertionException("not valid"))
      }

    }

    "validate email" in {
      stringFormatter.validateEmailAndThrow("donald.duck@example.com", throw AssertionException("not valid")) should be(
        "donald.duck@example.com"
      )

      an[AssertionException] should be thrownBy {
        stringFormatter.validateEmailAndThrow("donald.duck", throw AssertionException("not valid"))
      }
    }

    "convert a UUID to Base-64 encoding and back again" in {
      val uuid              = UUID.randomUUID
      val base64EncodedUuid = stringFormatter.base64EncodeUuid(uuid)
      val base64DecodedUuid = stringFormatter.base64DecodeUuid(base64EncodedUuid)
      assert(base64DecodedUuid.toOption.contains(uuid))
    }

    "return TRUE for IRIs BEOL project IRI and other which contain UUID version 4 or 5, otherwise return FALSE" in {
      val iri3    = "http://rdfh.ch/0000/rKAU0FNjPUKWqOT8MEW_UQ"
      val iri4    = "http://rdfh.ch/0001/cmfk1DMHRBiR4-_6HXpEFA"
      val iri5    = "http://rdfh.ch/080C/Ef9heHjPWDS7dMR_gGax2Q"
      val beolIri = "http://rdfh.ch/projects/yTerZGyxjZVqFMNNKXCDPF"

      val testIRIFromVersion3UUID = stringFormatter.isUuidSupported(iri3)
      val testIRIFromVersion4UUID = stringFormatter.isUuidSupported(iri4)
      val testIRIFromVersion5UUID = stringFormatter.isUuidSupported(iri5)
      val testBeolIri             = stringFormatter.isUuidSupported(beolIri)

      testIRIFromVersion3UUID should be(false)
      testIRIFromVersion4UUID should be(true)
      testIRIFromVersion5UUID should be(true)
      testBeolIri should be(true)
    }
  }
}
