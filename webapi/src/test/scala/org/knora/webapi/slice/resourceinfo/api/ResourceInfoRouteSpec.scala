/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resourceinfo.api

import zio.Chunk
import zio.ZIO
import zio.http._
import zio.test.ZIOSpecDefault
import zio.test._

import java.util.UUID.randomUUID

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.resourceinfo.api.RestResourceInfoServiceLive.ASC
import org.knora.webapi.slice.resourceinfo.api.RestResourceInfoServiceLive.DESC
import org.knora.webapi.slice.resourceinfo.api.RestResourceInfoServiceLive.creationDate
import org.knora.webapi.slice.resourceinfo.api.RestResourceInfoServiceLive.lastModificationDate
import org.knora.webapi.slice.resourceinfo.api.RestResourceInfoServiceSpy.orderingKey
import org.knora.webapi.slice.resourceinfo.api.RestResourceInfoServiceSpy.projectIriKey
import org.knora.webapi.slice.resourceinfo.api.RestResourceInfoServiceSpy.resourceClassKey
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.slice.resourceinfo.repo.ResourceInfoRepoFake

object ResourceInfoRouteSpec extends ZIOSpecDefault {

  private val testResourceClass = "http://test-resource-class/" + randomUUID
  private val testProjectIri    = "http://test-project/" + randomUUID
  private val baseUrl           = URL(Root / "v2" / "resources" / "info")
  private val projectHeader     = Headers("x-knora-accept-project", testProjectIri)

  private def sendRequest(req: Request) = ZIO.serviceWithZIO[ResourceInfoRoute](_.route.runZIO(req))

  def spec =
    suite("ResourceInfoRoute /v2/resources/info")(
      test("given no required params/headers were passed should respond with BadRequest") {
        val request = Request.get(url = baseUrl)
        for {
          response <- sendRequest(request)
        } yield assertTrue(response.status == Status.BadRequest)
      },
      test("given more than one resource class should respond with BadRequest") {
        val params  = QueryParams(("resourceClass", Chunk(testResourceClass, "http://anotherResourceClass")))
        val url     = baseUrl.withQueryParams(params)
        val request = Request.get(url = url).setHeaders(projectHeader)
        for {
          response <- sendRequest(request)
        } yield assertTrue(response.status == Status.BadRequest)
      },
      test("given no projectIri should respond with BadRequest") {
        val url     = baseUrl.withQueryParams(QueryParams(("resourceClass", testResourceClass)))
        val request = Request.get(url = url)
        for {
          response <- sendRequest(request)
        } yield assertTrue(response.status == Status.BadRequest)
      },
      test("given all mandatory parameters should respond with OK") {
        val url     = baseUrl.withQueryParams(QueryParams(("resourceClass", testResourceClass)))
        val request = Request.get(url = url).setHeaders(headers = projectHeader)
        for {
          response <- sendRequest(request)
        } yield assertTrue(response.status == Status.Ok)
      },
      test("given all parameters rest service should be called with default order") {
        val url     = baseUrl.withQueryParams(QueryParams(("resourceClass", testResourceClass)))
        val request = Request.get(url = url).setHeaders(projectHeader)
        for {
          expectedResourceClassIri <- IriConverter.asInternalIri(testResourceClass).map(_.value)
          expectedProjectIri       <- IriConverter.asInternalIri(testProjectIri).map(_.value)
          lastInvocation           <- sendRequest(request) *> RestResourceInfoServiceSpy.lastInvocation
        } yield assertTrue(
          lastInvocation ==
            Map(
              projectIriKey    -> expectedProjectIri,
              resourceClassKey -> expectedResourceClassIri,
              orderingKey      -> (lastModificationDate, ASC)
            )
        )
      },
      test("given all parameters rest service should be called with correct parameters") {
        val url = baseUrl.withQueryParams(
          QueryParams(
            ("resourceClass", testResourceClass),
            ("orderBy", "creationDate"),
            ("order", "DESC")
          )
        )
        val request = Request.get(url = url).setHeaders(projectHeader)
        for {
          expectedProjectIri       <- IriConverter.asInternalIri(testProjectIri).map(_.value)
          expectedResourceClassIri <- IriConverter.asInternalIri(testResourceClass).map(_.value)
          _                        <- sendRequest(request)
          lastInvocation           <- RestResourceInfoServiceSpy.lastInvocation
        } yield assertTrue(
          lastInvocation ==
            Map(
              projectIriKey    -> expectedProjectIri,
              resourceClassKey -> expectedResourceClassIri,
              orderingKey      -> (creationDate, DESC)
            )
        )
      }
    ).provide(
      IriConverter.layer,
      ResourceInfoRepoFake.layer,
      ResourceInfoRoute.layer,
      RestResourceInfoServiceSpy.layer,
      StringFormatter.test
    )
}
