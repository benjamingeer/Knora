/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo.rdf

import dsp.valueobjects.LanguageCode
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.KnoraAdminPrefix
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.KnoraAdminPrefixExpansion
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.common.repo.rdf.LangString
import org.knora.webapi.slice.resourceinfo.domain.InternalIri

object RdfConversions {

  def withPrefixExpansion[A](f: String => Either[String, A]): String => Either[String, A] =
    (str: String) => f(str.replace(KnoraAdminPrefix, KnoraAdminPrefixExpansion))

  implicit val internalIri: String => Either[String, InternalIri] = withPrefixExpansion(str => Right(InternalIri(str)))

  // Group properties
  implicit val groupIriConverter: String => Either[String, GroupIri] = withPrefixExpansion(GroupIri.from)

  // Project properties
  implicit val projectIriConverter: String => Either[String, ProjectIri] = ProjectIri.from
  implicit val shortcodeConverter: String => Either[String, Shortcode]   = Shortcode.from
  implicit val shortnameConverter: String => Either[String, Shortname]   = Shortname.from
  implicit val longnameConverter: String => Either[String, Longname]     = Longname.from
  implicit val keywordConverter: String => Either[String, Keyword]       = Keyword.from
  implicit val logoConverter: String => Either[String, Logo]             = Logo.from
  implicit val statusConverter: Boolean => Either[String, Status]        = value => Right(Status.from(value))
  implicit val selfjoinConverter: Boolean => Either[String, SelfJoin]    = value => Right(SelfJoin.from(value))
  implicit val descriptionConverter: LangString => Either[String, Description] = langString =>
    Description.from(StringLiteralV2.from(langString.value, langString.lang))

  // User properties
  implicit val usernameConverter: String => Either[String, Username]         = Username.from
  implicit val emailConverter: String => Either[String, Email]               = Email.from
  implicit val familyNameConverter: String => Either[String, FamilyName]     = FamilyName.from
  implicit val givenNameConverter: String => Either[String, GivenName]       = GivenName.from
  implicit val passwordHashConverter: String => Either[String, PasswordHash] = PasswordHash.from
  implicit val languageCodeConverter: String => Either[String, LanguageCode] = LanguageCode.from
  implicit val systemAdminConverter: Boolean => Either[String, SystemAdmin]  = Right(_).map(SystemAdmin.from)
  implicit val userStatusConverter: Boolean => Either[String, UserStatus]    = Right(_).map(UserStatus.from)

}
