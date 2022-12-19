/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder

import org.knora.webapi.ApiV2Complex
import org.knora.webapi.OntologySchema
import org.knora.webapi.messages.traits.Jsonable

/**
 * A trait for Knora Admin response messages. Any response message can be converted into JSON.
 */
trait KnoraResponseADM extends Jsonable {
  def format(targetSchema: OntologySchema = ApiV2Complex): KnoraResponseADM
}
