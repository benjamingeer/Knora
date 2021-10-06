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
 * User entity representing the payload for the create user request
 */
sealed abstract case class UserCreatePayloadADM private (
  id: Option[IRI],
  username: Option[Username],
  email: Option[Email],
  givenName: Option[GivenName],
  familyName: Option[FamilyName],
  password: Option[Password],
  status: Option[Status],
  lang: Option[LanguageCode],
  projects: Option[Seq[IRI]],
  projectsAdmin: Option[Seq[IRI]],
  groups: Option[Seq[IRI]],
  systemAdmin: Option[SystemAdmin]
)

object UserCreatePayloadADM {

  /** The create constructor needs all attributes but id which is optional */
  def create(
    id: Option[IRI] = None,
    username: Username,
    email: Email,
    givenName: GivenName,
    familyName: FamilyName,
    password: Password,
    status: Status,
    lang: LanguageCode,
    systemAdmin: SystemAdmin
  ): UserCreatePayloadADM =
    new UserCreatePayloadADM(
      id = id,
      username = Some(username),
      email = Some(email),
      givenName = Some(givenName),
      familyName = Some(familyName),
      password = Some(password),
      status = Some(status),
      lang = Some(lang),
      projects = None,
      projectsAdmin = None,
      groups = None,
      systemAdmin = Some(systemAdmin)
    ) {}
}
