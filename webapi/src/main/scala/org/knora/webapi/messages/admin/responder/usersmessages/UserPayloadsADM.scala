package org.knora.webapi.messages.admin.responder.usersmessages

import org.knora.webapi.IRI
import org.knora.webapi.messages.admin.responder.valueObjects.{
  Username,
  Email,
  GivenName,
  FamilyName,
  Password,
  Status,
  LanguageCode,
  SystemAdmin
}

/**
 * User creation payload
 */
final case class UserCreatePayloadADM(
  id: Option[IRI] = None,
  username: Username,
  email: Email,
  givenName: GivenName,
  familyName: FamilyName,
  password: Password,
  status: Status,
  lang: LanguageCode,
  systemAdmin: SystemAdmin,
//  TODO-mpro: fields below not reflected in docs - why are these here Ivan?
  projects: Option[Seq[IRI]] = None,
  projectsAdmin: Option[Seq[IRI]] = None,
  groups: Option[Seq[IRI]] = None
)
