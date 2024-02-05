package org.knora.webapi.slice.common.repo.rdf

import org.apache.jena.riot.RiotException
import zio.*
import zio.test.Assertion.*
import zio.test.*

import org.knora.webapi.slice.common.repo.rdf.Errors.ConversionError
import org.knora.webapi.slice.common.repo.rdf.Errors.LiteralNotPresent
import org.knora.webapi.slice.common.repo.rdf.Errors.NotALiteral
import org.knora.webapi.slice.common.repo.rdf.Errors.ResourceNotPresent

object RdfModelSpec extends ZIOSpecDefault {

  private case class StringContainer(value: String)
  private implicit val stringContainerConverter: String => Either[String, StringContainer] =
    str => Right(StringContainer(str))

  private case class FailingStringContainer(value: String)
  private implicit val failingStringContainerConverter: String => Either[String, FailingStringContainer] =
    str => Left("Conversion failed")

  private case class LangStringContainer(value: LangString)
  private implicit val langStringContainerConverter: LangString => Either[String, LangStringContainer] =
    langString => Right(LangStringContainer(langString))

  private case class FailingLangStringContainer(value: LangString)
  private implicit val failingLangStringContainerConverter: LangString => Either[String, FailingLangStringContainer] =
    langString => Left("Conversion failed")

  private val rdfModelSuite = suite("RdfModel")(
    suite("Deserialize turtle")(
      test("Create an RdfModel from valid turtle") {
        val turtle =
          """|@prefix ex: <http://example.org/> .
             |ex:subject ex:predicate ex:object .
             |""".stripMargin
        for {
          rdfModel <- RdfModel.fromTurtle(turtle).exit
        } yield assertTrue(rdfModel.isSuccess)
      },
      test("Fail to create an RdfModel from invalid turtle") {
        for {
          rdfModel <- RdfModel.fromTurtle("Not turtle").exit
        } yield assert(rdfModel)(failsWithA[RiotException])
      }
    ),
    suite("Get resource")(
      test("Get a resource that exists") {
        val turtle =
          """|@prefix ex: <http://example.org/> .
             |ex:subject ex:predicate ex:object .
             |""".stripMargin
        for {
          rdfModel <- RdfModel.fromTurtle(turtle)
          resource <- rdfModel.getResource("http://example.org/subject").exit
        } yield assertTrue(resource.isSuccess)
      },
      test("Fail to get a resource that does not exist") {
        val turtle =
          """|@prefix ex: <http://example.org/> .
             |ex:subject ex:predicate ex:object .
             |""".stripMargin
        for {
          rdfModel <- RdfModel.fromTurtle(turtle)
          resource <- rdfModel.getResource("http://example.org/does-not-exist").exit
        } yield assert(resource)(failsWithA[ResourceNotPresent])
      }
    )
  )

  private val stringLiteralSuite =
    suite("String literal")(
      suite("getStringLiteral")(
        test("Get a string literal that exists") {
          val turtle =
            """|@prefix ex: <http://example.org/> .
               |ex:subject ex:predicate "object" .
               |""".stripMargin
          for {
            rdfModel <- RdfModel.fromTurtle(turtle)
            resource <- rdfModel.getResource("http://example.org/subject")
            literal  <- resource.getStringLiteral[StringContainer]("http://example.org/predicate")
          } yield assertTrue(literal.contains(StringContainer("object")))
        },
        test("Get None for a string literal that does not exist") {
          val turtle =
            """|@prefix ex: <http://example.org/> .
               |ex:subject ex:predicate "object" .
               |""".stripMargin
          for {
            rdfModel <- RdfModel.fromTurtle(turtle)
            resource <- rdfModel.getResource("http://example.org/subject")
            literal  <- resource.getStringLiteral[StringContainer]("http://example.org/does-not-exist")
          } yield assertTrue(literal.isEmpty)
        },
        test("Fail to get a string literal from a property that does not point to a literal") {
          val turtle =
            """|@prefix ex: <http://example.org/> .
               |ex:subject ex:predicate ex:object .
               |""".stripMargin
          for {
            rdfModel <- RdfModel.fromTurtle(turtle)
            resource <- rdfModel.getResource("http://example.org/subject")
            literal  <- resource.getStringLiteral[StringContainer]("http://example.org/predicate").exit
          } yield assert(literal)(failsWithA[NotALiteral])
        },
        test("Fail to get a string literal from a property that does not point to a string literal") {
          val turtle =
            """|@prefix ex: <http://example.org/> .
               |@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
               |ex:subject ex:predicate "1.2"^^xsd:float .
               |""".stripMargin
          for {
            rdfModel <- RdfModel.fromTurtle(turtle)
            resource <- rdfModel.getResource("http://example.org/subject")
            literal  <- resource.getStringLiteral[StringContainer]("http://example.org/predicate").exit
          } yield assert(literal)(failsWithA[ConversionError])
        },
        test("Fail to get a string literal, if the conversion to the domain object fails") {
          val turtle =
            """|@prefix ex: <http://example.org/> .
               |ex:subject ex:predicate "object" .
               |""".stripMargin
          for {
            rdfModel <- RdfModel.fromTurtle(turtle)
            resource <- rdfModel.getResource("http://example.org/subject")
            literal  <- resource.getStringLiteral[FailingStringContainer]("http://example.org/predicate").exit
          } yield assert(literal)(failsWithA[ConversionError])
        }
      ),
      suite("getStringLiteralOrFail")(
        test("Get a string literal that exists") {
          val turtle =
            """|@prefix ex: <http://example.org/> .
               |ex:subject ex:predicate "object" .
               |""".stripMargin
          for {
            rdfModel <- RdfModel.fromTurtle(turtle)
            resource <- rdfModel.getResource("http://example.org/subject")
            literal  <- resource.getStringLiteralOrFail[StringContainer]("http://example.org/predicate")
          } yield assertTrue(literal == StringContainer("object"))
        },
        test("Fail to get a string literal that does not exist") {
          val turtle =
            """|@prefix ex: <http://example.org/> .
               |ex:subject ex:predicate "object" .
               |""".stripMargin
          for {
            rdfModel <- RdfModel.fromTurtle(turtle)
            resource <- rdfModel.getResource("http://example.org/subject")
            literal  <- resource.getStringLiteralOrFail[StringContainer]("http://example.org/does-not-exist").exit
          } yield assert(literal)(failsWithA[LiteralNotPresent])
        },
        test("Fail to get a string literal from a property that does not point to a literal") {
          val turtle =
            """|@prefix ex: <http://example.org/> .
               |ex:subject ex:predicate ex:object .
               |""".stripMargin
          for {
            rdfModel <- RdfModel.fromTurtle(turtle)
            resource <- rdfModel.getResource("http://example.org/subject")
            literal  <- resource.getStringLiteralOrFail[StringContainer]("http://example.org/predicate").exit
          } yield assert(literal)(failsWithA[NotALiteral])
        },
        test("Fail to get a string literal from a property that does not point to a string literal") {
          val turtle =
            """|@prefix ex: <http://example.org/> .
               |@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
               |ex:subject ex:predicate "1.2"^^xsd:float .
               |""".stripMargin
          for {
            rdfModel <- RdfModel.fromTurtle(turtle)
            resource <- rdfModel.getResource("http://example.org/subject")
            literal  <- resource.getStringLiteralOrFail[StringContainer]("http://example.org/predicate").exit
          } yield assert(literal)(failsWithA[ConversionError])
        },
        test("Fail to get a string literal, if the conversion to the domain object fails") {
          val turtle =
            """|@prefix ex: <http://example.org/> .
               |ex:subject ex:predicate "object" .
               |""".stripMargin
          for {
            rdfModel <- RdfModel.fromTurtle(turtle)
            resource <- rdfModel.getResource("http://example.org/subject")
            literal  <- resource.getStringLiteralOrFail[FailingStringContainer]("http://example.org/predicate").exit
          } yield assert(literal)(failsWithA[ConversionError])
        }
      ),
      suite("getStringLiterals")(
        test("Get string literals that exist") {
          val turtle =
            """|@prefix ex: <http://example.org/> .
               |ex:subject ex:predicate "object1" .
               |ex:subject ex:predicate "object2" .
               |""".stripMargin
          for {
            rdfModel <- RdfModel.fromTurtle(turtle)
            resource <- rdfModel.getResource("http://example.org/subject")
            literals <- resource.getStringLiterals[StringContainer]("http://example.org/predicate")
          } yield assert(literals)(
            hasSameElementsDistinct(Chunk(StringContainer("object1"), StringContainer("object2")))
          )
        },
        test("Get an empty list for string literals that do not exist") {
          val turtle =
            """|@prefix ex: <http://example.org/> .
               |ex:subject ex:predicate "object" .
               |""".stripMargin
          for {
            rdfModel <- RdfModel.fromTurtle(turtle)
            resource <- rdfModel.getResource("http://example.org/subject")
            literals <- resource.getStringLiterals[StringContainer]("http://example.org/does-not-exist")
          } yield assertTrue(literals.isEmpty)
        },
        test("Fail to get string literals from a property that does not point to a literal") {
          val turtle =
            """|@prefix ex: <http://example.org/> .
               |ex:subject ex:predicate ex:object .
               |""".stripMargin
          for {
            rdfModel <- RdfModel.fromTurtle(turtle)
            resource <- rdfModel.getResource("http://example.org/subject")
            literals <- resource.getStringLiterals[StringContainer]("http://example.org/predicate").exit
          } yield assert(literals)(failsWithA[NotALiteral])
        },
        test("Fail to get string literals from a property that does not point to a string literal") {
          val turtle =
            """|@prefix ex: <http://example.org/> .
               |@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
               |ex:subject ex:predicate "1.2"^^xsd:float .
               |""".stripMargin
          for {
            rdfModel <- RdfModel.fromTurtle(turtle)
            resource <- rdfModel.getResource("http://example.org/subject")
            literals <- resource.getStringLiterals[StringContainer]("http://example.org/predicate").exit
          } yield assert(literals)(failsWithA[ConversionError])
        },
        test("Fail to get string literals, if the conversion to the domain object fails") {
          val turtle =
            """|@prefix ex: <http://example.org/> .
               |ex:subject ex:predicate "object" .
               |ex:subject ex:predicate "object" .
               |""".stripMargin
          for {
            rdfModel <- RdfModel.fromTurtle(turtle)
            resource <- rdfModel.getResource("http://example.org/subject")
            literals <- resource.getStringLiterals[FailingStringContainer]("http://example.org/predicate").exit
          } yield assert(literals)(failsWithA[ConversionError])
        }
      ),
      suite("getStringLiteralsOrFail")(
        test("Get string literals that exist") {
          val turtle =
            """|@prefix ex: <http://example.org/> .
               |ex:subject ex:predicate "object1" .
               |ex:subject ex:predicate "object2" .
               |""".stripMargin
          for {
            rdfModel <- RdfModel.fromTurtle(turtle)
            resource <- rdfModel.getResource("http://example.org/subject")
            literals <- resource.getStringLiteralsOrFail[StringContainer]("http://example.org/predicate")
          } yield assert(literals.toChunk)(
            hasSameElementsDistinct(Chunk(StringContainer("object1"), StringContainer("object2")))
          )
        },
        test("Fail to get string literals that do not exist") {
          val turtle =
            """|@prefix ex: <http://example.org/> .
               |ex:subject ex:predicate "object" .
               |""".stripMargin
          for {
            rdfModel <- RdfModel.fromTurtle(turtle)
            resource <- rdfModel.getResource("http://example.org/subject")
            literals <- resource.getStringLiteralsOrFail[StringContainer]("http://example.org/does-not-exist").exit
          } yield assert(literals)(failsWithA[LiteralNotPresent])
        },
        test("Fail to get string literals from a property that does not point to a literal") {
          val turtle =
            """|@prefix ex: <http://example.org/> .
               |ex:subject ex:predicate ex:object .
               |""".stripMargin
          for {
            rdfModel <- RdfModel.fromTurtle(turtle)
            resource <- rdfModel.getResource("http://example.org/subject")
            literals <- resource.getStringLiteralsOrFail[StringContainer]("http://example.org/predicate").exit
          } yield assert(literals)(failsWithA[NotALiteral])
        },
        test("Fail to get string literals from a property that does not point to a string literal") {
          val turtle =
            """|@prefix ex: <http://example.org/> .
               |@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
               |ex:subject ex:predicate "1.2"^^xsd:float .
               |""".stripMargin
          for {
            rdfModel <- RdfModel.fromTurtle(turtle)
            resource <- rdfModel.getResource("http://example.org/subject")
            literals <- resource.getStringLiteralsOrFail[StringContainer]("http://example.org/predicate").exit
          } yield assert(literals)(failsWithA[ConversionError])
        },
        test("Fail to get string literals, if the conversion to the domain object fails") {
          val turtle =
            """|@prefix ex: <http://example.org/> .
               |ex:subject ex:predicate "object" .
               |ex:subject ex:predicate "object" .
               |""".stripMargin
          for {
            rdfModel <- RdfModel.fromTurtle(turtle)
            resource <- rdfModel.getResource("http://example.org/subject")
            literals <- resource.getStringLiteralsOrFail[FailingStringContainer]("http://example.org/predicate").exit
          } yield assert(literals)(failsWithA[ConversionError])
        }
      )
    )

  private val langStringLiteralSuite =
    suite("Multi-language string")(
      suite("getLangStringLiteral")(
        test("Get a multi-language string that exists and has a language tag") {
          val turtle =
            """|@prefix ex: <http://example.org/> .
               |ex:subject ex:predicate "object"@en .
               |""".stripMargin
          for {
            rdfModel <- RdfModel.fromTurtle(turtle)
            resource <- rdfModel.getResource("http://example.org/subject")
            literal  <- resource.getLangStringLiteral[LangStringContainer]("http://example.org/predicate")
          } yield assertTrue(literal.contains(LangStringContainer(LangString("object", Some("en")))))
        },
        test("Get a multi-language string that exists and does not have a language tag") {
          val turtle =
            """|@prefix ex: <http://example.org/> .
               |ex:subject ex:predicate "object" .
               |""".stripMargin
          for {
            rdfModel <- RdfModel.fromTurtle(turtle)
            resource <- rdfModel.getResource("http://example.org/subject")
            literal  <- resource.getLangStringLiteral[LangStringContainer]("http://example.org/predicate")
          } yield assertTrue(literal.contains(LangStringContainer(LangString("object", None))))
        },
        test("Get None for a multi-language string that does not exist") {
          val turtle =
            """|@prefix ex: <http://example.org/> .
               |ex:subject ex:predicate "object"@en .
               |""".stripMargin
          for {
            rdfModel <- RdfModel.fromTurtle(turtle)
            resource <- rdfModel.getResource("http://example.org/subject")
            literal  <- resource.getLangStringLiteral[LangStringContainer]("http://example.org/does-not-exist")
          } yield assertTrue(literal.isEmpty)
        },
        test("Fail to get a multi-language string from a property that does not point to a literal") {
          val turtle =
            """|@prefix ex: <http://example.org/> .
               |ex:subject ex:predicate ex:object .
               |""".stripMargin
          for {
            rdfModel <- RdfModel.fromTurtle(turtle)
            resource <- rdfModel.getResource("http://example.org/subject")
            literal  <- resource.getLangStringLiteral[LangStringContainer]("http://example.org/predicate").exit
          } yield assert(literal)(failsWithA[NotALiteral])
        },
        test("Fail to get a multi-language string from a property that does not point to a string literal") {
          val turtle =
            """|@prefix ex: <http://example.org/> .
               |@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
               |ex:subject ex:predicate "1.2"^^xsd:float .
               |""".stripMargin
          for {
            rdfModel <- RdfModel.fromTurtle(turtle)
            resource <- rdfModel.getResource("http://example.org/subject")
            literal  <- resource.getLangStringLiteral[LangStringContainer]("http://example.org/predicate").exit
          } yield assert(literal)(failsWithA[ConversionError])
        },
        test("Fail to get a multi-language string, if the conversion to the domain object fails") {
          val turtle =
            """|@prefix ex: <http://example.org/> .
               |ex:subject ex:predicate "object"@en .
               |""".stripMargin
          for {
            rdfModel <- RdfModel.fromTurtle(turtle)
            resource <- rdfModel.getResource("http://example.org/subject")
            literal  <- resource.getLangStringLiteral[FailingLangStringContainer]("http://example.org/predicate").exit
          } yield assert(literal)(failsWithA[ConversionError])
        }
      ),
      suite("getLangStringLiteralOrFail")(
        test("Get a multi-language string that exists and has a language tag") {
          val turtle =
            """|@prefix ex: <http://example.org/> .
               |ex:subject ex:predicate "object"@en .
               |""".stripMargin
          for {
            rdfModel <- RdfModel.fromTurtle(turtle)
            resource <- rdfModel.getResource("http://example.org/subject")
            literal  <- resource.getLangStringLiteralOrFail[LangStringContainer]("http://example.org/predicate")
          } yield assertTrue(literal == LangStringContainer(LangString("object", Some("en"))))
        },
        test("Get a multi-language string that exists and does not have a language tag") {
          val turtle =
            """|@prefix ex: <http://example.org/> .
               |ex:subject ex:predicate "object" .
               |""".stripMargin
          for {
            rdfModel <- RdfModel.fromTurtle(turtle)
            resource <- rdfModel.getResource("http://example.org/subject")
            literal  <- resource.getLangStringLiteralOrFail[LangStringContainer]("http://example.org/predicate")
          } yield assertTrue(literal == LangStringContainer(LangString("object", None)))
        },
        test("Fail to get a multi-language string that does not exist") {
          val turtle =
            """|@prefix ex: <http://example.org/> .
               |ex:subject ex:predicate "object"@en .
               |""".stripMargin
          for {
            rdfModel <- RdfModel.fromTurtle(turtle)
            resource <- rdfModel.getResource("http://example.org/subject")
            literal <-
              resource.getLangStringLiteralOrFail[LangStringContainer]("http://example.org/does-not-exist").exit
          } yield assert(literal)(failsWithA[LiteralNotPresent])
        },
        test("Fail to get a multi-language string from a property that does not point to a literal") {
          val turtle =
            """|@prefix ex: <http://example.org/> .
               |ex:subject ex:predicate ex:object .
               |""".stripMargin
          for {
            rdfModel <- RdfModel.fromTurtle(turtle)
            resource <- rdfModel.getResource("http://example.org/subject")
            literal <-
              resource.getLangStringLiteralOrFail[LangStringContainer]("http://example.org/predicate").exit
          } yield assert(literal)(failsWithA[NotALiteral])
        },
        test("Fail to get a multi-language string from a property that does not point to a string literal") {
          val turtle =
            """|@prefix ex: <http://example.org/> .
               |@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
               |ex:subject ex:predicate "1.2"^^xsd:float .
               |""".stripMargin
          for {
            rdfModel <- RdfModel.fromTurtle(turtle)
            resource <- rdfModel.getResource("http://example.org/subject")
            literal <-
              resource.getLangStringLiteralOrFail[LangStringContainer]("http://example.org/predicate").exit
          } yield assert(literal)(failsWithA[ConversionError])
        },
        test("Fail to get a multi-language string, if the conversion to the domain object fails") {
          val turtle =
            """|@prefix ex: <http://example.org/> .
               |ex:subject ex:predicate "object"@en .
               |""".stripMargin
          for {
            rdfModel <- RdfModel.fromTurtle(turtle)
            resource <- rdfModel.getResource("http://example.org/subject")
            literal <-
              resource.getLangStringLiteralOrFail[FailingLangStringContainer]("http://example.org/predicate").exit
          } yield assert(literal)(failsWithA[ConversionError])
        }
      ),
      suite("getLangStringLiterals")(
        test("Get multi-language strings that exist") {
          val turtle =
            """|@prefix ex: <http://example.org/> .
               |ex:subject ex:predicate "object1"@en .
               |ex:subject ex:predicate "object2"@de .
               |ex:subject ex:predicate "object3" .
               |""".stripMargin
          for {
            rdfModel <- RdfModel.fromTurtle(turtle)
            resource <- rdfModel.getResource("http://example.org/subject")
            literals <- resource.getLangStringLiterals[LangStringContainer]("http://example.org/predicate")
          } yield assert(literals)(
            hasSameElementsDistinct(
              Chunk(
                LangStringContainer(LangString("object1", Some("en"))),
                LangStringContainer(LangString("object2", Some("de"))),
                LangStringContainer(LangString("object3", None))
              )
            )
          )
        },
        test("Get an empty list for multi-language strings that do not exist") {
          val turtle =
            """|@prefix ex: <http://example.org/> .
               |ex:subject ex:predicate "object"@en .
               |ex:subject ex:predicate "object"@de .
               |ex:subject ex:predicate "object" .
               |""".stripMargin
          for {
            rdfModel <- RdfModel.fromTurtle(turtle)
            resource <- rdfModel.getResource("http://example.org/subject")
            literals <- resource.getLangStringLiterals[LangStringContainer]("http://example.org/does-not-exist")
          } yield assertTrue(literals.isEmpty)
        },
        test("Fail to get multi-language strings from a property that does not point to a literal") {
          val turtle =
            """|@prefix ex: <http://example.org/> .
               |ex:subject ex:predicate ex:object .
               |ex:subject ex:predicate ex:object .
               |ex:subject ex:predicate ex:object .
               |""".stripMargin
          for {
            rdfModel <- RdfModel.fromTurtle(turtle)
            resource <- rdfModel.getResource("http://example.org/subject")
            literals <- resource.getLangStringLiterals[LangStringContainer]("http://example.org/predicate").exit
          } yield assert(literals)(failsWithA[NotALiteral])
        },
        test("Fail to get multi-language strings from a property that does not point to a string literal") {
          val turtle =
            """|@prefix ex: <http://example.org/> .
               |@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
               |ex:subject ex:predicate "1.2"^^xsd:float .
               |ex:subject ex:predicate "1.2"^^xsd:float .
               |ex:subject ex:predicate "1.2"^^xsd:float .
               |""".stripMargin
          for {
            rdfModel <- RdfModel.fromTurtle(turtle)
            resource <- rdfModel.getResource("http://example.org/subject")
            literals <- resource.getLangStringLiterals[LangStringContainer]("http://example.org/predicate").exit
          } yield assert(literals)(failsWithA[ConversionError])
        },
        test("Fail to get multi-language strings, if the conversion to the domain object fails") {
          val turtle =
            """|@prefix ex: <http://example.org/> .
               |ex:subject ex:predicate "object1"@en .
               |ex:subject ex:predicate "object2"@de .
               |ex:subject ex:predicate "object3" .
               |""".stripMargin
          for {
            rdfModel <- RdfModel.fromTurtle(turtle)
            resource <- rdfModel.getResource("http://example.org/subject")
            literals <- resource.getLangStringLiterals[FailingLangStringContainer]("http://example.org/predicate").exit
          } yield assert(literals)(failsWithA[ConversionError])
        }
      ),
      suite("getLangStringLiteralsOrFail")(
        test("Get multi-language strings that exist") {
          val turtle =
            """|@prefix ex: <http://example.org/> .
               |ex:subject ex:predicate "object1"@en .
               |ex:subject ex:predicate "object2"@de .
               |ex:subject ex:predicate "object3" .
               |""".stripMargin
          for {
            rdfModel <- RdfModel.fromTurtle(turtle)
            resource <- rdfModel.getResource("http://example.org/subject")
            literals <- resource.getLangStringLiteralsOrFail[LangStringContainer]("http://example.org/predicate")
          } yield assert(literals.toChunk)(
            hasSameElementsDistinct(
              Chunk(
                LangStringContainer(LangString("object1", Some("en"))),
                LangStringContainer(LangString("object2", Some("de"))),
                LangStringContainer(LangString("object3", None))
              )
            )
          )
        },
        test("Fail to get multi-language strings that do not exist") {
          val turtle =
            """|@prefix ex: <http://example.org/> .
               |ex:subject ex:predicate "object1"@en .
               |ex:subject ex:predicate "object2"@de .
               |ex:subject ex:predicate "object3" .
               |""".stripMargin
          for {
            rdfModel <- RdfModel.fromTurtle(turtle)
            resource <- rdfModel.getResource("http://example.org/subject")
            literals <-
              resource.getLangStringLiteralsOrFail[LangStringContainer]("http://example.org/does-not-exist").exit
          } yield assert(literals)(failsWithA[LiteralNotPresent])
        },
        test("Fail to get multi-language strings from a property that does not point to a literal") {
          val turtle =
            """|@prefix ex: <http://example.org/> .
               |ex:subject ex:predicate ex:object .
               |ex:subject ex:predicate ex:object .
               |ex:subject ex:predicate ex:object .
               |""".stripMargin
          for {
            rdfModel <- RdfModel.fromTurtle(turtle)
            resource <- rdfModel.getResource("http://example.org/subject")
            literals <-
              resource.getLangStringLiteralsOrFail[LangStringContainer]("http://example.org/predicate").exit
          } yield assert(literals)(failsWithA[NotALiteral])
        },
        test("Fail to get multi-language strings from a property that does not point to a string literal") {
          val turtle =
            """|@prefix ex: <http://example.org/> .
               |@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
               |ex:subject ex:predicate "1.2"^^xsd:float .
               |ex:subject ex:predicate "1.2"^^xsd:float .
               |ex:subject ex:predicate "1.2"^^xsd:float .
               |""".stripMargin
          for {
            rdfModel <- RdfModel.fromTurtle(turtle)
            resource <- rdfModel.getResource("http://example.org/subject")
            literals <-
              resource.getLangStringLiteralsOrFail[LangStringContainer]("http://example.org/predicate").exit
          } yield assert(literals)(failsWithA[ConversionError])
        },
        test("Fail to get multi-language strings, if the conversion to the domain object fails") {
          val turtle =
            """|@prefix ex: <http://example.org/> .
               |ex:subject ex:predicate "object1"@en .
               |ex:subject ex:predicate "object2"@de .
               |ex:subject ex:predicate "object3" .
               |""".stripMargin
          for {
            rdfModel <- RdfModel.fromTurtle(turtle)
            resource <- rdfModel.getResource("http://example.org/subject")
            literals <-
              resource.getLangStringLiteralsOrFail[FailingLangStringContainer]("http://example.org/predicate").exit
          } yield assert(literals)(failsWithA[ConversionError])
        }
      )
    )

  override def spec: Spec[TestEnvironment & Scope, Any] = suite("RdfModelSpec")(
    rdfModelSuite,
    suite("RdfResource")(
      stringLiteralSuite,
      langStringLiteralSuite
    )
  )
}
