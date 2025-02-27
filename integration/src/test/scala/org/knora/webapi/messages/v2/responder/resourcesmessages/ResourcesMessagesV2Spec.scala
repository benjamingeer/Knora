/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder.resourcesmessages

import dsp.errors.BadRequestException
import org.knora.webapi.CoreSpec
import org.knora.webapi.messages.v2.responder.resourcemessages.*
import org.knora.webapi.sharedtestdata.*

/**
 * Tests [[ResourceMessagesV2]].
 */
class ResourcesMessagesV2Spec extends CoreSpec {
  "Get history events of all resources of a project" should {
    "fail if given project IRI is not valid" in {
      val projectIri = "invalid-project-IRI"
      val caught = intercept[BadRequestException](
        ProjectResourcesWithHistoryGetRequestV2(
          projectIri = projectIri,
          requestingUser = SharedTestDataADM.imagesUser01,
        ),
      )
      assert(caught.getMessage === s"Invalid project IRI: $projectIri")
    }

    "fail if given IRI is not a project Iri" in {
      val caught = intercept[BadRequestException](
        ProjectResourcesWithHistoryGetRequestV2(
          projectIri = "http://rdfh.ch/0001/thing-with-history", // resource IRI instead of project IRI
          requestingUser = SharedTestDataADM.imagesUser01,
        ),
      )
      assert(caught.getMessage === "Invalid project IRI: http://rdfh.ch/0001/thing-with-history")
    }
  }
}
