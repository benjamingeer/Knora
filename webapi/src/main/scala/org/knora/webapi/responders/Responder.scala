/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi
package responders

import zio.*

import dsp.errors.*

object Responder {

  def handleUnexpectedMessage(message: Any, who: String): Task[Nothing] =
    ZIO.fail(
      UnexpectedMessageException(
        s"$who received an unexpected message $message of type ${message.getClass.getCanonicalName}"
      )
    )
}
