/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.store.triplestoremessages

import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import org.eclipse.rdf4j.sparqlbuilder.rdf.RdfLiteral.StringLiteral
import spray.json.*
import sttp.tapir.Schema
import zio.*
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

import java.time.Instant
import scala.collection.mutable
import scala.reflect.ClassTag

import dsp.errors.*
import org.knora.webapi.*
import org.knora.webapi.messages.*
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.util.ErrorHandlingMap
import org.knora.webapi.messages.util.rdf.*

/**
 * A response to a [[org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct]] query.
 *
 * @param statements a map of subject IRIs to statements about each subject.
 */
case class SparqlConstructResponse(statements: Map[IRI, Seq[(IRI, String)]], rdfModel: RdfModel) {
  def asExtended(implicit sf: StringFormatter): IO[DataConversionException, SparqlExtendedConstructResponse] =
    SparqlExtendedConstructResponse.make(rdfModel)
}
object SparqlConstructResponse {
  def make(rdfModel: RdfModel): SparqlConstructResponse = {
    val statementMap = mutable.Map.empty[IRI, Seq[(IRI, String)]]
    for (st: Statement <- rdfModel) {
      val subjectIri                  = st.subj.stringValue
      val predicateIri                = st.pred.stringValue
      val objectIri                   = st.obj.stringValue
      val currentStatementsForSubject = statementMap.getOrElse(subjectIri, Vector.empty[(IRI, String)])
      statementMap += (subjectIri -> (currentStatementsForSubject :+ (predicateIri, objectIri)))
    }
    SparqlConstructResponse(statementMap.toMap, rdfModel)
  }
}

case class SparqlExtendedConstructResponse(
  statements: Map[SubjectV2, SparqlExtendedConstructResponse.ConstructPredicateObjects],
)
object SparqlExtendedConstructResponse {

  /**
   * A map of predicate IRIs to literal objects.
   */
  type ConstructPredicateObjects = Map[SmartIri, Seq[LiteralV2]]

  def make(turtle: String)(implicit
    stringFormatter: StringFormatter,
  ): IO[DataConversionException, SparqlExtendedConstructResponse] =
    make(RdfFormatUtil.parseToRdfModel(turtle, Turtle))

  def make(
    rdfModel: RdfModel,
  )(implicit sf: StringFormatter): IO[DataConversionException, SparqlExtendedConstructResponse] =
    ZIO.attempt {
      val statementMap = mutable.Map.empty[SubjectV2, ConstructPredicateObjects]

      for (st: Statement <- rdfModel) {
        val subject: SubjectV2 = st.subj match {
          case iriNode: IriNode     => IriSubjectV2(iriNode.iri)
          case blankNode: BlankNode => BlankNodeSubjectV2(blankNode.id)
        }

        val predicateIri: SmartIri = st.pred.iri.toSmartIri

        val objectLiteral: LiteralV2 = st.obj match {
          case iriNode: IriNode     => IriLiteralV2(value = iriNode.iri)
          case blankNode: BlankNode => BlankNodeLiteralV2(value = blankNode.id)

          case literal: RdfLiteral =>
            literal match {
              case datatypeLiteral: DatatypeLiteral =>
                datatypeLiteral.datatype match {
                  case datatypeIri if OntologyConstants.Xsd.integerTypes.contains(datatypeIri) =>
                    IntLiteralV2(
                      datatypeLiteral
                        .integerValue(
                          throw InconsistentRepositoryDataException(s"Invalid integer: ${datatypeLiteral.value}"),
                        )
                        .toInt,
                    )

                  case OntologyConstants.Xsd.DateTime =>
                    DateTimeLiteralV2(
                      ValuesValidator
                        .xsdDateTimeStampToInstant(datatypeLiteral.value)
                        .getOrElse(
                          throw InconsistentRepositoryDataException(s"Invalid xsd:dateTime: ${datatypeLiteral.value}"),
                        ),
                    )

                  case OntologyConstants.Xsd.Boolean =>
                    BooleanLiteralV2(
                      datatypeLiteral.booleanValue(
                        throw InconsistentRepositoryDataException(s"Invalid xsd:boolean: ${datatypeLiteral.value}"),
                      ),
                    )

                  case OntologyConstants.Xsd.String =>
                    StringLiteralV2.from(value = datatypeLiteral.value, language = None)

                  case OntologyConstants.Xsd.Decimal =>
                    DecimalLiteralV2(
                      datatypeLiteral.decimalValue(
                        throw InconsistentRepositoryDataException(s"Invalid xsd:decimal: ${datatypeLiteral.value}"),
                      ),
                    )

                  case OntologyConstants.Xsd.Uri => IriLiteralV2(datatypeLiteral.value)

                  case unknown => throw NotImplementedException(s"The literal type '$unknown' is not implemented.")
                }

              case stringWithLanguage: StringWithLanguage =>
                StringLiteralV2.from(value = stringWithLanguage.value, language = Some(stringWithLanguage.language))
            }
        }

        val currentStatementsForSubject   = statementMap.getOrElse(subject, Map.empty[SmartIri, Seq[LiteralV2]])
        val currentStatementsForPredicate = currentStatementsForSubject.getOrElse(predicateIri, Seq.empty[LiteralV2])

        val updatedPredicateStatements = currentStatementsForPredicate :+ objectLiteral
        val updatedSubjectStatements   = currentStatementsForSubject + (predicateIri -> updatedPredicateStatements)

        statementMap += (subject -> updatedSubjectStatements)
      }

      SparqlExtendedConstructResponse(statementMap.toMap)
    }.foldZIO(
      err => ZIO.fail(DataConversionException(s"Couldn't parse Turtle document ${err.getMessage}")),
      ZIO.succeed(_),
    )
}

/**
 * Indicates whether the repository is up to date.
 *
 * @param message a message providing details of what was done.
 */
case class RepositoryUpdatedResponse(message: String)

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
 * Contains the path to the 'ttl' file and the name of the named graph it should be loaded in.
 *
 * @param path to the 'ttl' file
 * @param name of the named graph the data will be load into.
 */
case class RdfDataObject(path: String, name: String)
object RdfDataObject {
  implicit val jsonCodec: JsonCodec[RdfDataObject] = DeriveJsonCodec.gen[RdfDataObject]
}

/**
 * Represents the subject of a statement read from the triplestore.
 */
sealed trait SubjectV2 {
  def value: String
}

/**
 * Represents an IRI used as the subject of a statement.
 */
case class IriSubjectV2(value: IRI) extends SubjectV2 {
  override def toString: IRI = value
}

/**
 * Represents a blank node identifier used as the subject of a statement.
 */
case class BlankNodeSubjectV2(value: String) extends SubjectV2 {
  override def toString: String = value
}

/**
 * Represents a literal read from the triplestore. There are different subclasses
 * representing literals with the extended type information stored in the triplestore.
 */
sealed trait LiteralV2 { self =>
  def as[A <: LiteralV2]()(implicit tag: ClassTag[A]): Option[A] =
    this match {
      case a: A => Some(a)
      case _    => None
    }
}

/**
 * Represents a literal read from an ontology in the triplestore.
 */
sealed trait OntologyLiteralV2

/**
 * Represents an IRI literal.
 *
 * @param value the IRI.
 */
case class IriLiteralV2(value: IRI) extends LiteralV2 {
  override def toString: IRI = value
}

/**
 * Represents an IRI literal as a [[SmartIri]].
 *
 * @param value the IRI.
 */
case class SmartIriLiteralV2(value: SmartIri) extends OntologyLiteralV2 {
  override def toString: IRI = value.toString

  def toOntologySchema(targetSchema: OntologySchema): SmartIriLiteralV2 =
    SmartIriLiteralV2(value.toOntologySchema(targetSchema))
}

/**
 * Represents a blank node identifier.
 *
 * @param value the identifier of the blank node.
 */
case class BlankNodeLiteralV2(value: String) extends LiteralV2 {
  override def toString: String = value
}

/**
 * Represents a string with language iso. Allows sorting inside collections by the value.
 *
 * @param value    the string value.
 * @param language the language iso.
 */
case class StringLiteralV2 private (value: String, language: Option[String])
    extends LiteralV2
    with OntologyLiteralV2
    with Ordered[StringLiteralV2] {

  override def compare(that: StringLiteralV2): Int = this.value.compareTo(that.value)
  override def toString: String                    = value

  def toRdfLiteral: StringLiteral =
    language.map(Rdf.literalOfLanguage(value, _)).getOrElse(Rdf.literalOf(value))
}

object StringLiteralV2 {
  implicit val codec: JsonCodec[StringLiteralV2] = DeriveJsonCodec.gen[StringLiteralV2]
  implicit val schema: Schema[StringLiteralV2]   = Schema.derived[StringLiteralV2]

  def from(value: String, lang: LanguageCode): StringLiteralV2 =
    from(value, Some(lang.code))

  def from(value: String, language: Option[String]): StringLiteralV2 = language match {
    case Some(_) if value.isEmpty => throw BadRequestException("String value is missing.")
    case _                        => StringLiteralV2(value, language)
  }

  def unsafeFrom(value: String, language: Option[String]): StringLiteralV2 = StringLiteralV2(value, language)
}

/**
 * Represents a sequence of [[StringLiteralV2]].
 *
 * @param stringLiterals a sequence of [[StringLiteralV2]].
 */
case class StringLiteralSequenceV2(stringLiterals: Vector[StringLiteralV2]) {

  /**
   * Sort sequence of [[StringLiteralV2]] by their language value.
   *
   * @return a [[StringLiteralSequenceV2]] sorted by language value.
   */
  def sortByLanguage: StringLiteralSequenceV2 = StringLiteralSequenceV2(stringLiterals.sortBy(_.language))

  /**
   * Gets the string value of the [[StringLiteralV2]] corresponding to the preferred language.
   * If not available, returns the string value of the fallback language or any available language.
   *
   * @param preferredLang the preferred language.
   * @param fallbackLang  language to use if preferred language is not available.
   */
  def getPreferredLanguage(preferredLang: String, fallbackLang: String): Option[String] = {

    val stringLiteralMap: Map[Option[String], String] = stringLiterals.map { case StringLiteralV2(str, lang) =>
      lang -> str
    }.toMap

    stringLiteralMap.get(Some(preferredLang)) match {
      // Is the string value available in the user's preferred language?
      case Some(strVal: String) =>
        // Yes.
        Some(strVal)
      case None =>
        // The string value is not available in the user's preferred language. Is it available
        // in the system default language?
        stringLiteralMap.get(Some(fallbackLang)) match {
          case Some(strValFallbackLang) =>
            // Yes.
            Some(strValFallbackLang)
          case None =>
            // The string value is not available in the system default language. Is it available
            // without a language tag?
            stringLiteralMap.get(None) match {
              case Some(strValWithoutLang) =>
                // Yes.
                Some(strValWithoutLang)
              case None =>
                // The string value is not available without a language tag. Sort the
                // available `StringLiteralV2` by language code to get a deterministic result,
                // and return the object in the language with the lowest sort
                // order.
                stringLiteralMap.toVector.sortBy { case (lang, _) =>
                  lang
                }.headOption.map { case (_, obj) =>
                  obj
                }
            }

        }
    }
  }

  final def isEmpty: Boolean  = stringLiterals.isEmpty
  final def nonEmpty: Boolean = stringLiterals.nonEmpty
}

object StringLiteralSequenceV2 {
  implicit val codec: JsonCodec[StringLiteralSequenceV2] =
    JsonCodec[Vector[StringLiteralV2]].transform(StringLiteralSequenceV2.apply, _.stringLiterals)

  implicit val schema: Schema[StringLiteralSequenceV2] = Schema.derived[StringLiteralSequenceV2]

  val empty: StringLiteralSequenceV2 = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
}

/**
 * Represents a boolean value.
 *
 * @param value the boolean value.
 */
case class BooleanLiteralV2(value: Boolean) extends LiteralV2 with OntologyLiteralV2 {
  override def toString: String = value.toString
}

/**
 * Represents an integer value.
 *
 * @param value the integer value.
 */
case class IntLiteralV2(value: Int) extends LiteralV2 {
  override def toString: String = value.toString
}

/**
 * Represents a decimal value.
 *
 * @param value the decimal value.
 */
case class DecimalLiteralV2(value: BigDecimal) extends LiteralV2 {
  override def toString: String = value.toString
}

/**
 * Represents a timestamp.
 *
 * @param value the timestamp value.
 */
case class DateTimeLiteralV2(value: Instant) extends LiteralV2 {
  override def toString: String = value.toString
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
 * A ZIO json protocol that parses JSON returned by a SPARQL endpoint. Empty values and empty rows are
 * ignored.
 */
object SparqlResultProtocol extends DefaultJsonProtocol {
  import zio.json._
  import zio.json.ast.Json
  import cats.implicits._

  implicit val VariableResultsZioJsonFormat: JsonDecoder[VariableResultsRow] =
    JsonDecoder[Json.Obj].map { obj =>
      val mapToWrap: Map[String, String] = obj.fields.toList.foldMap { case (key, value) =>
        value.asObject.foldMap(_.get("value").flatMap(_.asString).filter(_.nonEmpty).foldMap(s => Map(key -> s)))
      }

      // Wrapped in an ErrorHandlingMap which gracefully reports errors about accessing missing values.
      val keyMissing = (key: String) => s"No value found for SPARQL query variable '$key' in query result row"
      VariableResultsRow(new ErrorHandlingMap(mapToWrap, keyMissing))
    }

  implicit val SparqlSelectResponseBodyFormatZ: JsonDecoder[SparqlSelectResultBody] =
    DeriveJsonDecoder.gen[SparqlSelectResultBodyUnchecked].map(_.asChecked)

  implicit val headerDecoder: JsonDecoder[SparqlSelectResultHeader] = DeriveJsonDecoder.gen[SparqlSelectResultHeader]
  implicit val responseDecoder: JsonDecoder[SparqlSelectResult]     = DeriveJsonDecoder.gen[SparqlSelectResult]
}

/**
 * A spray-json protocol for generating Knora API v1 JSON providing data about resources and their properties.
 */
trait TriplestoreJsonProtocol extends DefaultJsonProtocol with NullOptions {

  implicit object LiteralV2Format extends JsonFormat[StringLiteralV2] {

    /**
     * Converts a [[StringLiteralV2]] to a [[JsValue]].
     *
     * @param string a [[StringLiteralV2]].
     * @return a [[JsValue]].
     */
    def write(string: StringLiteralV2): JsValue =
      if (string.language.isDefined) {
        // have language tag
        JsObject(
          Map(
            "value"    -> string.value.toJson,
            "language" -> string.language.toJson,
          ),
        )
      } else {
        // no language tag
        JsObject(
          Map(
            "value" -> string.value.toJson,
          ),
        )
      }

    /**
     * Converts a [[JsValue]] to a [[StringLiteralV2]].
     *
     * @param json a [[JsValue]].
     * @return a [[StringLiteralV2]].
     */
    def read(json: JsValue): StringLiteralV2 = json match {
      case stringWithLang: JsObject =>
        stringWithLang.getFields("value", "language") match {
          case Seq(JsString(value), JsString(language)) =>
            StringLiteralV2.from(
              value = value,
              language = Some(language),
            )
          case Seq(JsString(value)) =>
            StringLiteralV2.from(
              value = value,
              language = None,
            )
          case _ =>
            throw DeserializationException("JSON object with 'value', or 'value' and 'language' fields expected.")
        }
      case JsString(value) => StringLiteralV2.from(value, None)
      case _               => throw DeserializationException("JSON object with 'value', or 'value' and 'language' expected. ")
    }
  }
}
