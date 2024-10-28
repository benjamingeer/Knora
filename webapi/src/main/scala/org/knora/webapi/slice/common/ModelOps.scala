/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common

import org.apache.jena.rdf.model.*
import org.apache.jena.vocabulary.RDF
import zio.*

import java.time.Instant
import java.util.UUID
import scala.jdk.CollectionConverters.*

import dsp.valueobjects.UuidUtil
import dsp.valueobjects.UuidUtil.base64Decode
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex.ValueCreationDate
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex.ValueHasUUID
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.common.KnoraIris.*
import org.knora.webapi.slice.common.ModelError.MoreThanOneRootResource
import org.knora.webapi.slice.common.ModelError.ParseError
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

enum ModelError(val msg: String) {
  case ParseError(override val msg: String)              extends ModelError(msg)
  case InvalidIri(override val msg: String)              extends ModelError(msg)
  case MoreThanOneRootResource(override val msg: String) extends ModelError(msg)
  case NoRootResource(override val msg: String)          extends ModelError(msg)
  case MissingValueProp(override val msg: String)        extends ModelError(msg)
  case MultipleValueProp(override val msg: String)       extends ModelError(msg)
  case NoRootResourceClassIri(override val msg: String)  extends ModelError(msg)
}
object ModelError {
  def parseError(ex: Throwable): ParseError            = ParseError(ex.getMessage)
  def invalidIri(msg: String): InvalidIri              = InvalidIri(msg)
  def invalidIri(e: Throwable): InvalidIri             = InvalidIri(e.getMessage)
  def moreThanOneRootResource: MoreThanOneRootResource = MoreThanOneRootResource("More than one root resource found")
  def noRootResource: NoRootResource                   = NoRootResource("No root resource found")
  def missingValueProp: MissingValueProp               = MissingValueProp("No value property found in root resource")
  def multipleValueProp: MultipleValueProp             = MultipleValueProp("Multiple value properties found in root resource")
  def noRootResourceClassIri: NoRootResourceClassIri   = NoRootResourceClassIri("No root resource class IRI found")
}

/*
 * The KnoraApiModel represents any incoming value models from our v2 API.
 */
final case class KnoraApiValueModel(
  resourceIri: ResourceIri,
  resourceClassIri: ResourceClassIri,
  valueNode: KnoraApiValueNode,
) {
  lazy val shortcode: Shortcode = resourceIri.shortcode
}

object KnoraApiValueModel { self =>
  import StatementOps.*
  import ResourceOps.*

  // available for ease of use in tests
  def fromJsonLd(str: String): ZIO[Scope & IriConverter, ModelError, KnoraApiValueModel] =
    ZIO.service[IriConverter].flatMap(self.fromJsonLd(str, _))

  def fromJsonLd(str: String, converter: IriConverter): ZIO[Scope & IriConverter, ModelError, KnoraApiValueModel] =
    for {
      model            <- ModelOps.fromJsonLd(str)
      resourceIri      <- resourceIri(model, converter)
      resource          = model.getResource(resourceIri.smartIri.toString)
      resourceClassIri <- resourceClassIri(resource, converter)
      valueProp        <- valueNode(resource, resourceIri.shortcode, converter)
    } yield KnoraApiValueModel(
      resourceIri,
      resourceClassIri,
      valueProp,
    )

  private def resourceIri(model: Model, convert: IriConverter): IO[ModelError, ResourceIri] =
    val iter    = model.listStatements()
    var objSeen = Set.empty[String]
    var subSeen = Set.empty[String]
    while (iter.hasNext) {
      val stmt = iter.nextStatement()
      val _    = stmt.objectUri().foreach(iri => objSeen += iri)
      val _    = stmt.subjectUri().foreach(iri => subSeen += iri)
    }
    val result: IO[ModelError, ResourceIri] = subSeen -- objSeen match {
      case result if result.size == 1 =>
        convert
          .asSmartIri(result.head)
          .mapError(ModelError.parseError)
          .flatMap(iri => ZIO.fromEither(ResourceIri.from(iri)).mapError(ModelError.invalidIri))
      case result if result.isEmpty => ZIO.fail(ModelError.noRootResource)
      case _                        => ZIO.fail(ModelError.moreThanOneRootResource)
    }
    result

  private def valueNode(
    rootResource: Resource,
    shortcode: Shortcode,
    converter: IriConverter,
  ): IO[ModelError, KnoraApiValueNode] =
    ZIO.succeed {
      rootResource
        .listProperties()
        .asScala
        .filter(_.getPredicate != RDF.`type`)
        .toList
    }
      .filterOrFail(_.nonEmpty)(ModelError.missingValueProp)
      .filterOrFail(_.size == 1)(ModelError.multipleValueProp)
      .map(_.head)
      .flatMap(s => KnoraApiValueNode.from(s, shortcode, converter))

  private def resourceClassIri(
    rootResource: Resource,
    convert: IriConverter,
  ): IO[ModelError, ResourceClassIri] = ZIO
    .fromOption(rootResource.rdfsType())
    .orElseFail(ModelError.noRootResourceClassIri)
    .flatMap(convert.asSmartIri(_).mapError(ModelError.invalidIri))
    .flatMap(iri => ZIO.fromEither(ResourceClassIri.from(iri)).mapError(ModelError.invalidIri))
}

final case class KnoraApiValueNode(
  node: RDFNode,
  propertyIri: PropertyIri,
  valueType: SmartIri,
  shortcode: Shortcode,
  convert: IriConverter,
) {
  import NodeOps.*
  import ResourceOps.*

  def getStringLiteral(property: String): Option[String]   = node.getStringLiteral(property)
  def getStringLiteral(property: Property): Option[String] = node.getStringLiteral(property)
  def getValueIri: IO[ModelError, Option[ValueIri]] =
    ZIO
      .fromOption(node.toResourceOption.flatMap(_.uri))
      .flatMap(convert.asSmartIri(_).mapError(ModelError.parseError).asSomeError)
      .flatMap(iri => ZIO.fromEither(ValueIri.from(iri)).mapError(ModelError.invalidIri).asSomeError)
      .unsome

  def getValueHasUuid: Either[String, Option[UUID]] =
    getStringLiteral(ValueHasUUID)
      .map(str => base64Decode(str).map(Some(_)).toEither.left.map(e => s"Invalid UUID '$str': ${e.getMessage}"))
      .fold(Right(None))(identity)

  def getValueCreationDate: Either[String, Option[Instant]] =
    node.getDateTimeProperty(ResourceFactory.createProperty(ValueCreationDate))

  def getHasPermissions: Option[String] =
    node.getStringLiteral(ResourceFactory.createProperty(OntologyConstants.KnoraApiV2Complex.HasPermissions))
}

object KnoraApiValueNode {
  import NodeOps.*
  import ResourceOps.*
  import StatementOps.*
  def from(
    stmt: Statement,
    shortcode: Shortcode,
    convert: IriConverter,
  ): IO[ModelError, KnoraApiValueNode] =
    for {
      propertyIri <- ZIO
                       .fromOption(stmt.getPredicate.getUri())
                       .orElseFail(ModelError.invalidIri(s"No property IRI found for Value."))
                       .flatMap(convert.asSmartIri(_).mapError(ModelError.invalidIri))
                       .flatMap(iri => ZIO.fromEither(PropertyIri.from(iri)).mapError(ModelError.invalidIri))
      valueType <- ZIO
                     .fromOption(stmt.objectAsResource().flatMap(_.rdfsType()))
                     .orElseFail(ModelError.invalidIri(s"No value type found for Value."))
                     .flatMap(convert.asSmartIri(_).mapError(ModelError.invalidIri))
    } yield KnoraApiValueNode(stmt.getObject, propertyIri, valueType, shortcode, convert)
}
