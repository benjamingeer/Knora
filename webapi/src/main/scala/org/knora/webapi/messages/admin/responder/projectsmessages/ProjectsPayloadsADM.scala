package org.knora.webapi.messages.admin.responder.projectsmessages

import org.knora.webapi.IRI
import org.knora.webapi.messages.admin.responder.valueObjects.{
  Shortname,
  Longname,
  Shortcode,
  ProjectDescription,
  Keywords,
  Logo,
  Status,
  Selfjoin
}

/**
 * Project creation payload
 */
final case class ProjectsPayloadsADM(
  id: Option[IRI] = None,
  shortname: Shortname,
  shortcode: Shortcode,
  longname: Option[Longname],
  description: ProjectDescription,
  keywords: Keywords,
  logo: Option[Logo],
  status: Status,
  selfjoin: Selfjoin
)
