/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.http.status

import org.apache.pekko

import dsp.errors._

import pekko.http.scaladsl.model.StatusCode
import pekko.http.scaladsl.model.StatusCodes

/**
 * The possible values for the HTTP status code that is returned as part of each Knora ADM response.
 */
object ApiStatusCodesADM {

  /**
   * Converts an exception to a similar HTTP status code.
   *
   * @param ex an exception.
   * @return an HTTP status code.
   */
  def fromException(ex: Throwable): StatusCode =
    ex match {
      // Subclasses of RequestRejectedException (which must be last in this group)
      case NotFoundException(_)           => StatusCodes.NotFound
      case ForbiddenException(_)          => StatusCodes.Forbidden
      case BadCredentialsException(_)     => StatusCodes.Unauthorized
      case DuplicateValueException(_)     => StatusCodes.BadRequest
      case OntologyConstraintException(_) => StatusCodes.BadRequest
      case EditConflictException(_)       => StatusCodes.Conflict
      case RequestRejectedException(_)    => StatusCodes.BadRequest

      // Subclasses of InternalServerException (which must be last in this group)
      case UpdateNotPerformedException(_) => StatusCodes.Conflict
      case InternalServerException(_)     => StatusCodes.InternalServerError
      case _                              => StatusCodes.InternalServerError
    }

}
