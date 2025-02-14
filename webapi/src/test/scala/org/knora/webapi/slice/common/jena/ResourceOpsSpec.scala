/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.jena

import zio.*
import zio.test.*

import java.time.Instant
import scala.language.implicitConversions

import org.knora.webapi.slice.common
import org.knora.webapi.slice.common.jena.JenaConversions.given
import org.knora.webapi.slice.common.jena.ModelOps.*
import org.knora.webapi.slice.common.jena.ResourceOps.*

object ResourceOpsSpec extends ZIOSpecDefault {

  private val integerValue =
    """
      | @prefix ex: <https://example.com/test#> .
      | @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
      | @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
      |
      | <https://example.com/a-thing>
      |     a           ex:Thing ;
      |     ex:int      "4"^^xsd:integer  ;
      |     ex:str      "Foo" ;
      |     ex:decimal  "42.0"^^xsd:decimal ;
      |     ex:bool     "true"^^xsd:boolean ;
      |     ex:dts      "1879-03-14T00:00:00Z"^^xsd:dateTimeStamp ;
      |     ex:date     "1879-03-14"^^xsd:date ;
      |     ex:list     "Foo" ;
      |     ex:list     "Bar" .
      |""".stripMargin

  private def resource() =
    ModelOps
      .fromTurtle(integerValue)
      .map(_.resource("https://example.com/a-thing"))
      .flatMap(ZIO.fromEither(_))
      .mapError(msg => s"Failing to parse model: $msg")

  private val objectBooleanSuite = suite("Getting Boolean values of Objects")(
    suite("objectBoolean")(
      test("should succeed if value is present") {
        for {
          res   <- resource()
          actual = res.objectBoolean("https://example.com/test#bool")
        } yield assertTrue(actual == Right(true))
      },
      test("should fail if value is the wrong type") {
        for {
          res   <- resource()
          actual = res.objectBoolean("https://example.com/test#str")
        } yield assertTrue(actual == Left("Invalid boolean value for property https://example.com/test#str"))
      },
      test("should fail if property is not present ") {
        for {
          res   <- resource()
          actual = res.objectBoolean("https://example.com/test#notPresent")
        } yield assertTrue(actual == Left("Required property not found https://example.com/test#notPresent"))
      },
    ),
    suite("objectBooleanOption")(
      test("should succeed if value is present") {
        for {
          res   <- resource()
          actual = res.objectBooleanOption("https://example.com/test#bool")
        } yield assertTrue(actual == Right(Some(true)))
      },
      test("should fail if value is the wrong type") {
        for {
          res   <- resource()
          actual = res.objectBooleanOption("https://example.com/test#str")
        } yield assertTrue(actual == Left("Invalid boolean value for property https://example.com/test#str"))
      },
      test("should succeed if property is not present ") {
        for {
          res   <- resource()
          actual = res.objectBooleanOption("https://example.com/test#notPresent")
        } yield assertTrue(actual == Right(None))
      },
    ),
  )

  private val objectBigDecimalSuite = suite("Getting BigDecimal of Objects")(
    suite("objectBigDecimal")(
      test("should succeed if value is present") {
        for {
          res   <- resource()
          actual = res.objectBigDecimal("https://example.com/test#decimal")
        } yield assertTrue(actual == Right(BigDecimal(42)))
      },
      test("should fail if value is the wrong type") {
        for {
          res   <- resource()
          actual = res.objectBigDecimal("https://example.com/test#str")
        } yield assertTrue(
          actual == Left(
            "Invalid datatype for property https://example.com/test#str, http://www.w3.org/2001/XMLSchema#decimal expected",
          ),
        )
      },
      test("should fail if property is not present ") {
        for {
          res   <- resource()
          actual = res.objectBigDecimal("https://example.com/test#notPresent")
        } yield assertTrue(actual == Left("Required property not found https://example.com/test#notPresent"))
      },
    ),
    suite("objectBigDecimalOption")(
      test("should succeed if value is present") {
        for {
          res   <- resource()
          actual = res.objectBigDecimalOption("https://example.com/test#decimal")
        } yield assertTrue(actual == Right(Some(BigDecimal(42))))
      },
      test("should fail if value is the wrong type") {
        for {
          res   <- resource()
          actual = res.objectBigDecimalOption("https://example.com/test#str")
        } yield assertTrue(
          actual == Left(
            "Invalid datatype for property https://example.com/test#str, http://www.w3.org/2001/XMLSchema#decimal expected",
          ),
        )
      },
      test("should succeed if property is not present ") {
        for {
          res   <- resource()
          actual = res.objectBigDecimalOption("https://example.com/test#notPresent")
        } yield assertTrue(actual == Right(None))
      },
    ),
  )

  private val objectIntSuite = suite("Getting Integer values of Objects")(
    suite("objectInt")(
      test("should succeed if value is present") {
        for {
          res   <- resource()
          actual = res.objectInt("https://example.com/test#int")
        } yield assertTrue(actual == Right(4))
      },
      test("should fail if value is the wrong type") {
        for {
          res   <- resource()
          actual = res.objectInt("https://example.com/test#str")
        } yield assertTrue(actual == Left("Invalid integer value for property https://example.com/test#str"))
      },
      test("should fail if property is not present ") {
        for {
          res   <- resource()
          actual = res.objectInt("https://example.com/test#notPresent")
        } yield assertTrue(actual == Left("Required property not found https://example.com/test#notPresent"))
      },
    ),
    suite("objectIntOption")(
      test("should succeed if value is present") {
        for {
          res   <- resource()
          actual = res.objectIntOption("https://example.com/test#int")
        } yield assertTrue(actual == Right(Some(4)))
      },
      test("should fail if value is the wrong type") {
        for {
          res   <- resource()
          actual = res.objectIntOption("https://example.com/test#str")
        } yield assertTrue(actual == Left("Invalid integer value for property https://example.com/test#str"))
      },
      test("should succeed if property is not present ") {
        for {
          res   <- resource()
          actual = res.objectIntOption("https://example.com/test#notPresent")
        } yield assertTrue(actual == Right(None))
      },
    ),
  )

  private val objectInstantSuite = suite("Getting Instant values of Objects")(
    suite("objectInstant")(
      test("should succeed if value is present") {
        for {
          res   <- resource()
          actual = res.objectInstant("https://example.com/test#dts")
        } yield assertTrue(actual == Right(Instant.parse("1879-03-14T00:00:00Z")))
      },
      test("should fail if type is not correct") {
        for {
          res   <- resource()
          actual = res.objectInstant("https://example.com/test#int")
        } yield assertTrue(
          actual == Left(value =
            "Invalid datatype for property https://example.com/test#int, http://www.w3.org/2001/XMLSchema#dateTimeStamp expected",
          ),
        )
      },
      test("should fail if property is not present ") {
        for {
          res   <- resource()
          actual = res.objectInstant("https://example.com/test#notPresent")
        } yield assertTrue(actual == Left("Required property not found https://example.com/test#notPresent"))
      },
    ),
    suite("objectInstantOption")(
      test("should succeed if value is present") {
        for {
          res   <- resource()
          actual = res.objectInstantOption("https://example.com/test#dts")
        } yield assertTrue(actual == Right(Some(Instant.parse("1879-03-14T00:00:00Z"))))
      },
      test("should fail if type is not correct") {
        for {
          res   <- resource()
          actual = res.objectInstantOption("https://example.com/test#int")
        } yield assertTrue(
          actual == Left(
            "Invalid datatype for property https://example.com/test#int, http://www.w3.org/2001/XMLSchema#dateTimeStamp expected",
          ),
        )
      },
      test("should succeed if property is not present ") {
        for {
          res   <- resource()
          actual = res.objectInstantOption("https://example.com/test#notPresent")
        } yield assertTrue(actual == Right(None))
      },
    ),
  )

  private val objectStringSuite = suite("Getting String values of Objects")(
    suite("objectString")(
      test("should succeed if value is present") {
        for {
          res   <- resource()
          actual = res.objectString("https://example.com/test#str")
        } yield assertTrue(actual == Right("Foo"))
      },
      test("should succeed if value is not a string with the string representation") {
        for {
          res   <- resource()
          actual = res.objectString("https://example.com/test#int")
        } yield assertTrue(actual == Right("4"))
      },
      test("should fail if property is not present ") {
        for {
          res   <- resource()
          actual = res.objectString("https://example.com/test#notPresent")
        } yield assertTrue(actual == Left("Required property not found https://example.com/test#notPresent"))
      },
    ),
    suite("objectStringOption")(
      test("should succeed if value is present") {
        for {
          res   <- resource()
          actual = res.objectStringOption("https://example.com/test#str")
        } yield assertTrue(actual == Right(Some("Foo")))
      },
      test("should succeed if not value is not a string with the string representation") {
        for {
          res   <- resource()
          actual = res.objectStringOption("https://example.com/test#int")
        } yield assertTrue(actual == Right(Some("4")))
      },
      test("should succeed if property is not present ") {
        for {
          res   <- resource()
          actual = res.objectStringOption("https://example.com/test#notPresent")
        } yield assertTrue(actual == Right(None))
      },
    ),
  )

  private val objectStringListSuite = suite("Getting List of String values of Objects")(
    suite("objectStringList")(
      suite("without mapper")(
        test("should succeed if value is present") {
          for {
            res   <- resource()
            actual = res.objectStringList("https://example.com/test#list")
          } yield assert(actual.getOrElse(List.empty))(Assertion.hasSameElements(List("Foo", "Bar")))
        },
        test("should fail if property is not present ") {
          for {
            res   <- resource()
            actual = res.objectStringList("https://example.com/test#notPresent")
          } yield assertTrue(actual == Left("Required property https://example.com/test#notPresent not found"))
        },
      ),
      suite("with mapper")(
        test("should succeed if value is present and mapper succeeds") {
          for {
            res   <- resource()
            actual = res.objectStringList("https://example.com/test#list", s => Right(s.toUpperCase))
          } yield assert(actual.getOrElse(List.empty))(Assertion.hasSameElements(List("FOO", "BAR")))
        },
        test("should fail if value is present and mapper fails") {
          for {
            res   <- resource()
            actual = res.objectStringList("https://example.com/test#list", _ => Left("Error"))
          } yield assertTrue(actual == Left("Error"))
        },
      ),
    ),
    suite("objectStringListOption")(
      suite("without mapper")(
        test("should succeed if value is present") {
          for {
            res   <- resource()
            actual = res.objectStringListOption("https://example.com/test#list")
          } yield assert(actual.map(_.getOrElse(List.empty)).getOrElse(List.empty))(
            Assertion.hasSameElements(List("Foo", "Bar")),
          )
        },
        test("should succeed if property is not present ") {
          for {
            res   <- resource()
            actual = res.objectStringListOption("https://example.com/test#notPresent")
          } yield assertTrue(actual == Right(None))
        },
      ),
      suite("with mapper")(
        test("should succeed if value is present and mapper succeeds") {
          for {
            res   <- resource()
            actual = res.objectStringListOption("https://example.com/test#list", s => Right(s.toUpperCase))
          } yield assert(actual.map(_.getOrElse(List.empty)).getOrElse(List.empty))(
            Assertion.hasSameElements(List("FOO", "BAR")),
          )
        },
        test("should succeed if value is present and mapper succeeds") {
          for {
            res   <- resource()
            actual = res.objectStringListOption("https://example.com/test#list", _ => Left("Error"))
          } yield assertTrue(actual == Left("Error"))
        },
      ),
    ),
  )
  private val objectDataTypeOptionSuite = suite("objectDataTypeOption")(
    suite("date")(
      test("should return a date") {
        for {
          res   <- resource()
          actual = res.objectDataTypeOption("https://example.com/test#date", "http://www.w3.org/2001/XMLSchema#date")
        } yield assertTrue(actual == Right(Some("1879-03-14")))
      },
      test("should fail if it is not a date") {
        for {
          res   <- resource()
          actual = res.objectDataTypeOption("https://example.com/test#str", "http://www.w3.org/2001/XMLSchema#date")
        } yield assertTrue(
          actual == Left(
            "Invalid datatype for property https://example.com/test#str, http://www.w3.org/2001/XMLSchema#date expected",
          ),
        )
      },
    ),
  )

  private val rdfTypeTest = test("rdfsType should get the type") {
    for {
      res   <- resource()
      actual = res.rdfsType
    } yield assertTrue(actual.contains("https://example.com/test#Thing"))
  }

  val spec: Spec[Scope, String] = suite("ResourceOps")(
    objectBigDecimalSuite,
    objectBooleanSuite,
    objectIntSuite,
    objectInstantSuite,
    objectStringSuite,
    objectStringListSuite,
    objectDataTypeOptionSuite,
    rdfTypeTest,
  )
}
