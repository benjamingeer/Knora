/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import zio.json.JsonCodec

import org.knora.webapi.JsonLdRendering.Flat
import org.knora.webapi.slice.admin.api.Codecs.TapirCodec

/**
 * Indicates the schema that a Knora ontology or ontology entity conforms to
 * and its options that can be submitted to configure an ontology schema.
 */
case class SchemaRendering(schema: ApiV2Schema, rendering: Set[Rendering])
object SchemaRendering {
  def apiV2SchemaWithOption(option: Rendering): SchemaRendering        = apiV2SchemaWithOptions(Set(option))
  def apiV2SchemaWithOptions(options: Set[Rendering]): SchemaRendering = SchemaRendering(ApiV2Complex, options)
}

/**
 * Indicates the schema that a Knora ontology or ontology entity conforms to.
 */
sealed trait OntologySchema

/**
 * The schema of Knora ontologies and entities that are used in the triplestore.
 */
case object InternalSchema extends OntologySchema

/**
 * The schema of Knora ontologies and entities that are used in API v2.
 */
sealed trait ApiV2Schema extends OntologySchema {
  def name: String
}

/**
 * The simple schema for representing Knora ontologies and entities. This schema represents values as literals
 * when possible.
 */
case object ApiV2Simple extends ApiV2Schema {
  override val name: String = "simple"
}

/**
 * The default (or complex) schema for representing Knora ontologies and entities. This
 * schema always represents values as objects.
 */
case object ApiV2Complex extends ApiV2Schema {
  override val name: String = "complex"
}

object ApiV2Schema {

  given JsonCodec[ApiV2Schema]              = JsonCodec[String].transformOrFail(ApiV2Schema.from, _.name)
  given TapirCodec.StringCodec[ApiV2Schema] = TapirCodec.stringCodec(ApiV2Schema.from, _.name)

  val default: ApiV2Schema = ApiV2Complex

  def from(str: String): Either[String, ApiV2Schema] = str.toLowerCase match {
    case ApiV2Simple.name  => Right(ApiV2Simple)
    case ApiV2Complex.name => Right(ApiV2Complex)
    case _                 => Left(s"Unrecognised ontology schema name: $str")
  }
}

/**
 * A trait representing options that can be submitted to configure an ontology schema.
 */
sealed trait Rendering {
  def name: String
}

/**
 * A trait representing options that affect the rendering of markup when text values are returned.
 */
sealed trait MarkupRendering extends Rendering

object MarkupRendering {

  /**
   * Indicates that standoff markup should be returned as XML with text values.
   */
  case object Xml extends MarkupRendering {
    override val name: String = "xml"
  }

  /**
   * Indicates that markup should not be returned with text values, because it will be requested
   * separately as standoff.
   */
  case object Standoff extends MarkupRendering {
    override val name: String = "standoff"
  }

  def from(str: String): Either[String, MarkupRendering] = str.toLowerCase match {
    case Xml.name      => Right(Xml)
    case Standoff.name => Right(Standoff)
    case _             => Left(s"Unrecognised markup rendering name: $str")
  }
}

/**
 * A trait representing options that affect the format of JSON-LD responses.
 */
sealed trait JsonLdRendering extends Rendering

object JsonLdRendering {

  /**
   * Indicates that flat JSON-LD should be returned, i.e. objects with IRIs should be referenced by IRI
   * rather than nested. Blank nodes will still be nested in any case.
   * See https://w3c.github.io/json-ld-syntax/#flattened-document-formd
   */
  case object Flat extends JsonLdRendering {
    override val name: String = "flat"
  }

  /**
   * Indicates that hierarchical JSON-LD should be returned, i.e. objects with IRIs should be nested when
   * possible, rather than referenced by IRI.
   */
  case object Hierarchical extends JsonLdRendering {
    override val name: String = "hierarchical"
  }

  def from(str: String): Either[String, JsonLdRendering] = str.toLowerCase match {
    case Flat.name         => Right(Flat)
    case Hierarchical.name => Right(Hierarchical)
    case _                 => Left(s"Unrecognised JSON-LD rendering name: $str")
  }
}

/**
 * Utility functions for working with schema options.
 */
object SchemaOptions {

  /**
   * A set of schema options for querying all standoff markup along with text values.
   */
  val ForStandoffWithTextValues: Set[Rendering] = Set(MarkupRendering.Xml)

  /**
   * Determines whether standoff should be queried when a text value is queried.
   *
   * @param targetSchema  the target API schema.
   * @param schemaOptions the schema options submitted with the request.
   * @return `true` if standoff should be queried.
   */
  def queryStandoffWithTextValues(targetSchema: ApiV2Schema, schemaOptions: Set[Rendering]): Boolean =
    targetSchema == ApiV2Complex && !schemaOptions.contains(MarkupRendering.Standoff)

  /**
   * Determines whether markup should be rendered as XML.
   *
   * @param targetSchema  the target API schema.
   * @param schemaOptions the schema options submitted with the request.
   * @return `true` if markup should be rendered as XML.
   */
  def renderMarkupAsXml(targetSchema: ApiV2Schema, schemaOptions: Set[Rendering]): Boolean =
    targetSchema == ApiV2Complex && !schemaOptions.contains(MarkupRendering.Standoff)

  /**
   * Determines whether flat JSON-LD should be returned, i.e. objects with IRIs should be referenced by IRI
   * rather than nested.
   *
   * @param schemaOptions the schema options submitted with the request.
   * @return `true` if flat JSON-LD should be returned.
   */
  def returnFlatJsonLD(schemaOptions: Set[Rendering]): Boolean =
    schemaOptions.contains(Flat)
}
