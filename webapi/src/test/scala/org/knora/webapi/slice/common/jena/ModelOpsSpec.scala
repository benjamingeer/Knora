/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.jena

import zio.*
import zio.test.*

import org.knora.webapi.slice.common.jena.ModelOps.*

object ModelOpsSpec extends ZIOSpecDefault {

  private val jsonLd = """{
                     "@id" : "http://rdfh.ch/0001/a-thing",
                     "@type" : "anything:Thing",
                     "anything:hasInteger" : {
                       "@type" : "knora-api:IntValue",
                       "knora-api:intValueAsInt" : 4
                     },
                     "@context" : {
                       "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                       "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                     }
                   }
                   """.stripMargin
  private val jsonLd2 = """{
                     "@id" : "http://rdfh.ch/0001/a-thing",
                     "@type" : "anything:Thing",
                     "anything:hasInteger" : {
                       "@type" : "knora-api:IntValue",
                       "knora-api:intValueAsInt" : {
                          "@type" : "xsd",
                          "@value" : "4"
                        }
                     },
                     "@context" : {
                       "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                       "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#",
                       "xsd" : "http://www.w3.org/2001/XMLSchema#integer"
                     }
                   }
                   """.stripMargin

  private val singleRootResourceSuite = suite("singleRootResource")(
    test("should return the single root resource") {
      for {
        model <- ModelOps
                   .fromTurtle("""
                                 |@prefix ex: <http://example.org/> .
                                 |ex:root
                                 |  ex:hasChild ex:child ;
                                 |  ex:hasBlankNode [
                                 |    ex:hasOther ex:foo
                                 |  ] ;
                                 |  ex:hasOther ex:bar .
                                 |""".stripMargin)

      } yield assertTrue(model.singleRootResource.map(_.getURI) == Right("http://example.org/root"))
    },
    test("should fail with no resources given") {
      for {
        model <- ModelOps.fromTurtle("")
      } yield assertTrue(
        model.singleRootResource == Left("No root resource found in model"),
      )
    },
    test("should fail with more than a single root resource, uri resources") {
      for {
        model <- ModelOps
                   .fromTurtle("""
                                 |@prefix ex: <http://example.org/> .
                                 |ex:root1  ex:hasChild ex:child1 .
                                 |ex:root2  ex:hasChild ex:child2 .
                                 |""".stripMargin)
      } yield assertTrue(
        model.singleRootResource == Left(
          "Multiple root resources found in model: http://example.org/root1, http://example.org/root2",
        ),
      )
    },
    test("should fail with more than a single root resource, blank node") {
      for {
        model <- ModelOps
                   .fromTurtle("""
                                 |@prefix ex: <http://example.org/> .
                                 |[
                                 |  ex:hasChild ex:child1
                                 |] .
                                 |ex:root2  ex:hasChild ex:child2 .
                                 |""".stripMargin)
      } yield assertTrue(
        model.singleRootResource.left.map(_.startsWith("Multiple root resources found in model:")) == Left(true),
      )
    },
    test("should return blank node if that is the single root resource") {
      for {
        model <- ModelOps
                   .fromTurtle("""
                                 |@prefix ex: <http://example.org/> .
                                 |[
                                 |   ex:hasChild ex:child1
                                 |].
                                 |""".stripMargin)
      } yield assertTrue(
        model.singleRootResource.map(_.isAnon) == Right(true),
      )
    },
  )

  val spec = suite("ModelOps")(
    singleRootResourceSuite,
    suite("fromJsonLd")(
      test("should parse the json ld") {
        ModelOps.fromJsonLd(jsonLd).flatMap { model =>
          assertTrue(model.size() == 4)
        }
      },
      test("should produce isomorphic models") {
        for {
          model1 <- ModelOps.fromJsonLd(jsonLd)
          model2 <- ModelOps.fromJsonLd(jsonLd2)
        } yield assertTrue(model1.isIsomorphicWith(model2))
      },
      test("should fail on invalid json ld") {
        for {
          exit <- ModelOps.fromJsonLd("invalid json ld").exit
        } yield assertTrue(
          exit == Exit.Failure(
            Cause.fail("[line: 1, col: 1 ] The document could not be loaded or parsed [code=LOADING_DOCUMENT_FAILED]."),
          ),
        )
      },
    ),
  )
}
