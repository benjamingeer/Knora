/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import zio.test._

import java.util.UUID

import dsp.valueobjects.Project.Shortcode

object IdSpec extends ZIOSpecDefault {

  private val shortcode = Shortcode.make("0001").fold(e => throw e.head, v => v)
  private val uuid      = UUID.randomUUID
  private val iri = Iri.ProjectIri
    .make(s"http://rdfh.ch/projects/${UUID.randomUUID}")
    .fold(e => throw e.head, v => v)

  override def spec = suite("ID Specs")(projectIdTests)

  // TODO: should have tests for other IDs too
  val projectIdTests = suite("ProjectId")(
    test("should create an ID from only a shortcode") {
      (for {
        projectId <- ProjectId.make(shortcode)
      } yield assertTrue(projectId.shortcode == shortcode) &&
        assertTrue(!projectId.iri.value.isEmpty()) &&
        assertTrue(!projectId.uuid.toString().isEmpty())).toZIO
    },
    test("should create an ID from a shortcode and a UUID") {
      val expectedIri = Iri.ProjectIri.make(s"http://rdfh.ch/projects/$uuid").fold(e => throw e.head, v => v)
      (for {
        projectId <- ProjectId.fromUuid(uuid, shortcode)
      } yield assertTrue(projectId.shortcode == shortcode) &&
        assertTrue(projectId.iri == expectedIri) &&
        assertTrue(projectId.uuid == uuid)).toZIO
    },
    test("should create an ID from a shortcode and an IRI") {
      (for {
        projectId <- ProjectId.fromIri(iri, shortcode)
      } yield assertTrue(projectId.shortcode == shortcode) &&
        assertTrue(projectId.iri == iri) &&
        assertTrue(projectId.uuid.toString().length() == 36)).toZIO
    }
  )
}
