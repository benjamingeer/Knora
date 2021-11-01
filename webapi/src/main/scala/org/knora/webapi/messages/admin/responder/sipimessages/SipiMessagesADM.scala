/*
 * Copyright © 2021 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.sipimessages

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.admin.responder.projectsmessages.{
  ProjectRestrictedViewSettingsADM,
  ProjectsADMJsonProtocol
}
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.admin.responder.{KnoraRequestADM, KnoraResponseADM}
import spray.json.{DefaultJsonProtocol, JsValue, NullOptions, RootJsonFormat}

/**
 * An abstract trait representing a Knora v1 API request message that can be sent to `SipiResponderV2`.
 */
sealed trait SipiResponderRequestADM extends KnoraRequestADM

/**
 * A Knora v1 API request message that requests information about a `FileValue`.
 *
 * @param projectID            the project shortcode.
 * @param filename             the name of the file belonging to the file value to be queried.
 * @param featureFactoryConfig the feature factory configuration.
 * @param requestingUser       the profile of the user making the request.
 */
case class SipiFileInfoGetRequestADM(
  projectID: String,
  filename: String,
  featureFactoryConfig: FeatureFactoryConfig,
  requestingUser: UserADM
) extends SipiResponderRequestADM

/**
 * Represents the JSON response to a request for a information about a `FileValue`.
 *
 * @param permissionCode         a code representing the user's maximum permission on the file.
 * @param restrictedViewSettings the project's restricted view settings.
 */
case class SipiFileInfoGetResponseADM(
  permissionCode: Int,
  restrictedViewSettings: Option[ProjectRestrictedViewSettingsADM]
) extends KnoraResponseADM {
  def toJsValue: JsValue = SipiResponderResponseADMJsonProtocol.sipiFileInfoGetResponseADMFormat.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
 * A spray-json protocol for generating Knora API v1 JSON providing data about representations of a resource.
 */
object SipiResponderResponseADMJsonProtocol
    extends SprayJsonSupport
    with DefaultJsonProtocol
    with NullOptions
    with ProjectsADMJsonProtocol {

  implicit val sipiFileInfoGetResponseADMFormat: RootJsonFormat[SipiFileInfoGetResponseADM] = jsonFormat2(
    SipiFileInfoGetResponseADM
  )
}
