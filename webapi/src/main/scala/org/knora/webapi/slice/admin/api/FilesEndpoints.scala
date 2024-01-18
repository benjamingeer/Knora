/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.tapir.EndpointInput.PathCapture
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.spray.jsonBody
import zio.ZLayer

import org.knora.webapi.messages.admin.responder.sipimessages.PermissionCodeAndProjectRestrictedViewSettings
import org.knora.webapi.messages.admin.responder.sipimessages.SipiResponderResponseADMJsonProtocol.*
import org.knora.webapi.slice.admin.api.AdminPathVariables.projectShortcode
import org.knora.webapi.slice.admin.api.Codecs.TapirCodec.sparqlEncodedString
import org.knora.webapi.slice.admin.api.FilesPathVar.filename
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.common.domain.SparqlEncodedString

object FilesPathVar {
  val filename: PathCapture[SparqlEncodedString] = path[SparqlEncodedString]("filename")
}

final case class FilesEndpoints(base: BaseEndpoints) {
  val getAdminFilesShortcodeFileIri = base.withUserEndpoint.get
    .in("admin" / "files" / projectShortcode / filename)
    .out(jsonBody[PermissionCodeAndProjectRestrictedViewSettings])
    .description(
      "Returns the permission code and the project's restricted view settings for a given shortcode and filename."
    )

  val endpoints: Seq[AnyEndpoint] = Seq(getAdminFilesShortcodeFileIri.endpoint)
}

object FilesEndpoints {
  val layer = ZLayer.derive[FilesEndpoints]
}
