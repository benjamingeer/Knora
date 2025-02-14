/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.tapir.Codec
import sttp.tapir.CodecFormat
import zio.json.JsonCodec

import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.slice.admin.api.model.MaintenanceRequests.AssetId
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.admin.domain.model.ListProperties.Comments
import org.knora.webapi.slice.admin.domain.model.ListProperties.Labels
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListName
import org.knora.webapi.slice.admin.domain.model.ListProperties.Position
import org.knora.webapi.slice.admin.domain.model.Password
import org.knora.webapi.slice.admin.domain.model.RestrictedView
import org.knora.webapi.slice.admin.domain.model.SystemAdmin
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.UserStatus
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.common.Value.BooleanValue
import org.knora.webapi.slice.common.Value.IntValue
import org.knora.webapi.slice.common.Value.StringValue
import org.knora.webapi.slice.common.domain.SparqlEncodedString

object Codecs {
  object TapirCodec {

    private type StringCodec[A] = Codec[String, A, CodecFormat.TextPlain]
    private def stringCodec[A <: StringValue](from: String => Either[String, A]): StringCodec[A] =
      stringCodec(from, _.value)
    private def stringCodec[A](from: String => Either[String, A], to: A => String): StringCodec[A] =
      Codec.string.mapEither(from)(to)

    private def booleanCodec[A <: BooleanValue](from: Boolean => A): StringCodec[A] =
      booleanCodec(from, _.value)
    private def booleanCodec[A](from: Boolean => A, to: A => Boolean): StringCodec[A] =
      Codec.boolean.map(from)(to)

    implicit val assetId: StringCodec[AssetId]                         = stringCodec(AssetId.from, _.value)
    implicit val keyword: StringCodec[Keyword]                         = stringCodec(Keyword.from)
    implicit val logo: StringCodec[Logo]                               = stringCodec(Logo.from)
    implicit val longname: StringCodec[Longname]                       = stringCodec(Longname.from)
    implicit val selfJoin: StringCodec[SelfJoin]                       = booleanCodec(SelfJoin.from)
    implicit val projectIri: StringCodec[ProjectIri]                   = stringCodec(ProjectIri.from)
    implicit val shortcode: StringCodec[Shortcode]                     = stringCodec(Shortcode.from)
    implicit val shortname: StringCodec[Shortname]                     = stringCodec(Shortname.from)
    implicit val sparqlEncodedString: StringCodec[SparqlEncodedString] = stringCodec(SparqlEncodedString.from)
    implicit val status: StringCodec[Status]                           = booleanCodec(Status.from)

    // list properties
    implicit val listIri: StringCodec[ListIri] = stringCodec(ListIri.from)

    // user value objects
    implicit val userIri: StringCodec[UserIri]   = stringCodec(UserIri.from)
    implicit val userEmail: StringCodec[Email]   = stringCodec(Email.from)
    implicit val username: StringCodec[Username] = stringCodec(Username.from)
  }

  object ZioJsonCodec {

    type StringCodec[A] = JsonCodec[A]
    def stringCodec[A <: StringValue](from: String => Either[String, A]): StringCodec[A] =
      stringCodec(from, _.value)
    def stringCodec[A](from: String => Either[String, A], to: A => String): StringCodec[A] =
      JsonCodec[String].transformOrFail(from, to)

    private def booleanCodec[A <: BooleanValue](from: Boolean => A): StringCodec[A] =
      booleanCodec(from, _.value)
    private def booleanCodec[A](from: Boolean => A, to: A => Boolean): StringCodec[A] =
      JsonCodec[Boolean].transform(from, to)

    private def intCodec[A <: IntValue](from: Int => Either[String, A]): StringCodec[A] =
      JsonCodec.int.transformOrFail(from, _.value)

    // list properties
    implicit val comments: StringCodec[Comments] =
      JsonCodec[Seq[StringLiteralV2]].transformOrFail(Comments.from, _.value)
    implicit val description: StringCodec[Description] =
      JsonCodec[StringLiteralV2].transformOrFail(Description.from, _.value)
    implicit val labels: StringCodec[Labels] =
      JsonCodec[Seq[StringLiteralV2]].transformOrFail(Labels.from, _.value)
    implicit val listIri: StringCodec[ListIri]   = stringCodec(ListIri.from)
    implicit val listName: StringCodec[ListName] = stringCodec(ListName.from)
    implicit val position: StringCodec[Position] = intCodec(Position.from)

    // maintenance
    implicit val assetId: StringCodec[AssetId] = stringCodec(AssetId.from, _.value)

    // project
    implicit val keyword: StringCodec[Keyword]                        = stringCodec(Keyword.from)
    implicit val logo: StringCodec[Logo]                              = stringCodec(Logo.from)
    implicit val longname: StringCodec[Longname]                      = stringCodec(Longname.from)
    implicit val projectIri: StringCodec[ProjectIri]                  = stringCodec(ProjectIri.from)
    implicit val restrictedViewSize: StringCodec[RestrictedView.Size] = stringCodec(RestrictedView.Size.from)
    implicit val restrictedViewWatermark: StringCodec[RestrictedView.Watermark] = booleanCodec(
      RestrictedView.Watermark.from,
    )
    implicit val selfJoin: StringCodec[SelfJoin]                       = booleanCodec(SelfJoin.from)
    implicit val shortcode: StringCodec[Shortcode]                     = stringCodec(Shortcode.from)
    implicit val shortname: StringCodec[Shortname]                     = stringCodec(Shortname.from)
    implicit val sparqlEncodedString: StringCodec[SparqlEncodedString] = stringCodec(SparqlEncodedString.from)
    implicit val status: StringCodec[Status]                           = booleanCodec(Status.from)

    // user
    implicit val userIri: StringCodec[UserIri]         = stringCodec(UserIri.from)
    implicit val userEmail: StringCodec[Email]         = stringCodec(Email.from)
    implicit val username: StringCodec[Username]       = stringCodec(Username.from)
    implicit val givenName: StringCodec[GivenName]     = stringCodec(GivenName.from)
    implicit val familyName: StringCodec[FamilyName]   = stringCodec(FamilyName.from)
    implicit val password: StringCodec[Password]       = stringCodec(Password.from)
    implicit val userStatus: StringCodec[UserStatus]   = booleanCodec(UserStatus.from)
    implicit val systemAdmin: StringCodec[SystemAdmin] = booleanCodec(SystemAdmin.from)

    // group
    implicit val groupIri: StringCodec[GroupIri]   = stringCodec(GroupIri.from)
    implicit val groupName: StringCodec[GroupName] = stringCodec(GroupName.from)
    implicit val groupDescriptions: StringCodec[GroupDescriptions] =
      JsonCodec[Seq[StringLiteralV2]].transformOrFail(GroupDescriptions.from, _.value)
    implicit val groupStatus: StringCodec[GroupStatus]     = booleanCodec(GroupStatus.from)
    implicit val groupSelfJoin: StringCodec[GroupSelfJoin] = booleanCodec(GroupSelfJoin.from)
  }
}
