/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import zio.Chunk
import zio.ZLayer
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

import org.knora.webapi.slice.admin.api.AdminPathVariables.projectShortcode
import org.knora.webapi.slice.admin.domain.model.Authorship
import org.knora.webapi.slice.admin.domain.model.License
import org.knora.webapi.slice.common.api.BaseEndpoints

final case class PageInfo(size: Int, `total-elements`: Int, pages: Int, number: Int)
object PageInfo {
  given JsonCodec[PageInfo]         = DeriveJsonCodec.gen[PageInfo]
  def single(seq: Seq[_]): PageInfo = PageInfo(seq.size, seq.size, 1, 1)
}

final case class PagedResponse[LicenseDto](data: Chunk[LicenseDto], page: PageInfo)
object PagedResponse {
  given JsonCodec[PagedResponse[LicenseDto]] = DeriveJsonCodec.gen[PagedResponse[LicenseDto]]
  def allInOnePage(licenses: Chunk[LicenseDto]): PagedResponse[LicenseDto] =
    PagedResponse(licenses, PageInfo.single(licenses))
}

final case class LicenseDto(id: String, uri: String, `label-en`: String)
object LicenseDto {
  given JsonCodec[LicenseDto]            = DeriveJsonCodec.gen[LicenseDto]
  def from(license: License): LicenseDto = LicenseDto(license.id.value, license.uri.toString, license.labelEn)
}

final case class AuthorshipAddRequest(data: Set[Authorship])
object AuthorshipAddRequest {
  given JsonCodec[AuthorshipAddRequest] = DeriveJsonCodec.gen[AuthorshipAddRequest]
}

final case class AuthorshipReplaceRequest(`old-value`: Authorship, `new-value`: Authorship)
object AuthorshipReplaceRequest {
  given JsonCodec[AuthorshipReplaceRequest] = DeriveJsonCodec.gen[AuthorshipReplaceRequest]
}

final case class ProjectsLegalInfoEndpoints(baseEndpoints: BaseEndpoints) {

  private final val base = "admin" / "projects" / "shortcode" / projectShortcode / "legal-info"

  val getProjectLicenses = baseEndpoints.securedEndpoint.get
    .in(base / "licenses")
    .out(
      jsonBody[PagedResponse[LicenseDto]].example(
        PagedResponse.allInOnePage(
          Chunk(
            LicenseDto(
              "http://rdfh.ch/licenses/cc-by-4.0",
              "https://creativecommons.org/licenses/by/4.0/",
              "CC BY 4.0",
            ),
            LicenseDto(
              "http://rdfh.ch/licenses/cc-by-sa-4.0",
              "https://creativecommons.org/licenses/by-sa/4.0/",
              "CC BY-SA 4.0",
            ),
          ),
        ),
      ),
    )
    .description("Get the allowed licenses of a project. The user must be a system or project admin.")

  val getProjectAuthorships = baseEndpoints.securedEndpoint.get
    .in(base / "authorships")
    .out(
      jsonBody[List[Authorship]].example(
        List(
          Authorship.unsafeFrom("DaSch"),
          Authorship.unsafeFrom("University of Zurich"),
        ),
      ),
    )

  val postProjectAuthorships = baseEndpoints.securedEndpoint.post
    .in(base / "authorships")
    .in(
      jsonBody[AuthorshipAddRequest]
        .example(AuthorshipAddRequest(Set("DaSch", "University of Zurich").map(Authorship.unsafeFrom))),
    )
    .description("Add a new predefined authorships to a project. The user must be a system or project admin.")

  val putProjectAuthorships = baseEndpoints.securedEndpoint.put
    .in(base / "authorships")
    .in(
      jsonBody[AuthorshipReplaceRequest]
        .example(
          AuthorshipReplaceRequest(Authorship.unsafeFrom("Alpert Einstain"), Authorship.unsafeFrom("Albert Einstein")),
        ),
    )
    .description(
      "Update a particular predefined authorships of a project, does not update existing authorships on assets. The user must be a system admin.",
    )

  val endpoints: Seq[AnyEndpoint] = Seq(
    getProjectLicenses,
    getProjectAuthorships,
    postProjectAuthorships,
    putProjectAuthorships,
  ).map(_.endpoint).map(_.tag("Admin Projects (Legal Info)"))
}

object ProjectsLegalInfoEndpoints {
  val layer = ZLayer.derive[ProjectsLegalInfoEndpoints]
}
