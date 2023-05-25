/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v1

import akka.http.scaladsl.model.StatusCodes
import zio._

import org.knora.webapi.E2ESpec
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol

/**
 * Causes an internal server error to see if logging is working correctly.
 */
class ErrorV1E2ESpec extends E2ESpec with TriplestoreJsonProtocol {

  "Make a request that causes an internal server error (unit type message)" in {
    val request  = Get(baseApiUrl + "/v1/error/unitMsg")
    val response = singleAwaitingRequest(request, 1.second)
    assert(response.status == StatusCodes.InternalServerError)
  }

  "Make a request that causes an internal server error (ise type message)" in {
    val request  = Get(baseApiUrl + "/v1/error/iseMsg")
    val response = singleAwaitingRequest(request, 1.second)
    assert(response.status == StatusCodes.Conflict)
  }

}
