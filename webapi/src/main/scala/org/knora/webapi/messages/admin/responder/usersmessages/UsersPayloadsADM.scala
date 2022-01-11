package org.knora.webapi.messages.admin.responder.usersmessages

import org.knora.webapi.messages.admin.responder.valueObjects._

//TODO-mpro: add change payload because below is used also for update user

/**
 * User creation payload
 */
final case class UserCreatePayloadADM(
  id: Option[UserIRI] = None,
  username: Username,
  email: Email,
  givenName: GivenName,
  familyName: FamilyName,
  password: Password,
  status: UserStatus,
  lang: LanguageCode,
  systemAdmin: SystemAdmin
  //  TODO-mpro: fields below not reflected in docs - why are these here Ivan?
  //  projects: Option[Seq[IRI]] = None,
  //  projectsAdmin: Option[Seq[IRI]] = None,
  //  groups: Option[Seq[IRI]] = None
)
