/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.jena

import cats.instances.option.*
import cats.syntax.traverse.*
import org.apache.jena.rdf.model.Property
import org.apache.jena.rdf.model.Resource
import org.apache.jena.rdf.model.Statement
import org.apache.jena.vocabulary.RDF

import java.time.Instant
import java.util.UUID
import scala.jdk.CollectionConverters.IteratorHasAsScala

import org.knora.webapi.slice.common.jena.StatementOps.*

object ResourceOps {

  extension (res: Resource) {
    def statementOption(p: Property): Option[Statement] = Option(res.getProperty(p))
    def statement(p: Property): Either[String, Statement] =
      statementOption(p).toRight(s"Required property not found ${p.getURI}")

    def statements(p: Property): List[Statement] = res.listProperties(p).asScala.toList

    private inline def fromStatement[A](p: Property, f: Statement => Either[String, A]): Either[String, Option[A]] =
      statementOption(p) match
        case Some(stmt) => f.apply(stmt).map(Some(_))
        case None       => Right(None)

    def objectRdfClass(): Either[String, String] = statement(RDF.`type`).flatMap(_.objectAsUri)

    def objectBigDecimal(p: Property): Either[String, BigDecimal]               = statement(p).flatMap(_.objectAsBigDecimal)
    def objectBigDecimalOption(p: Property): Either[String, Option[BigDecimal]] = fromStatement(p, _.objectAsBigDecimal)

    def objectBoolean(p: Property): Either[String, Boolean]               = statement(p).flatMap(_.objectAsBoolean)
    def objectBooleanOption(p: Property): Either[String, Option[Boolean]] = fromStatement(p, _.objectAsBoolean)

    def objectInstant(p: Property): Either[String, Instant]               = statement(p).flatMap(_.objectAsInstant)
    def objectInstantOption(p: Property): Either[String, Option[Instant]] = fromStatement(p, _.objectAsInstant)

    def objectInt(p: Property): Either[String, Int]               = statement(p).flatMap(_.objectAsInt)
    def objectIntOption(p: Property): Either[String, Option[Int]] = fromStatement(p, _.objectAsInt)

    def objectString(p: Property): Either[String, String] =
      statement(p).flatMap(_.objectAsString)
    def objectString[A](p: Property, mapper: String => Either[String, A]): Either[String, A] =
      objectString(p).flatMap(mapper)

    def objectStringOption(p: Property): Either[String, Option[String]] =
      fromStatement(p, _.objectAsString)
    def objectStringOption[A](p: Property, mapper: String => Either[String, A]): Either[String, Option[A]] =
      objectStringOption(p).flatMap(_.traverse(mapper))

    def objectStringList(p: Property): Either[String, List[String]] =
      statements(p) match
        case Nil   => Left(s"Required property $p not found")
        case stmts => stmts.traverse(_.objectAsString)
    def objectStringList[A](p: Property, mapper: String => Either[String, A]): Either[String, List[A]] =
      objectStringList(p).flatMap(_.traverse(mapper))

    def objectStringListOption(p: Property): Either[String, Option[List[String]]] =
      statements(p) match
        case Nil   => Right(None)
        case stmts => stmts.traverse(_.objectAsString).map(Some(_))
    def objectStringListOption[A](p: Property, mapper: String => Either[String, A]): Either[String, Option[List[A]]] =
      objectStringListOption(p).flatMap(_.traverse(_.traverse(mapper)))

    def objectUri(p: Property): Either[String, String]                                    = statement(p).flatMap(stmt => stmt.objectAsUri)
    def objectUri[A](p: Property, mapper: String => Either[String, A]): Either[String, A] = objectUri(p).flatMap(mapper)
    def objectUriOption(p: Property): Either[String, Option[String]]                      = fromStatement(p, _.objectAsUri)
    def objectUriOption[A](p: Property, mapper: String => Either[String, A]): Either[String, Option[A]] =
      objectUriOption(p).flatMap(_.traverse(mapper))

    def objectUuid(p: Property): Either[String, UUID]               = statement(p).flatMap(stmt => stmt.objectAsUuid)
    def objectUuidOption(p: Property): Either[String, Option[UUID]] = fromStatement(p, _.objectAsUuid)

    def objectDataType(p: Property, dt: String): Either[String, String] =
      statement(p).flatMap(stmt => stmt.objectAsDataType(dt))

    def objectDataType[A](p: Property, dt: String, mapper: String => Either[String, A]): Either[String, A] =
      statement(p).flatMap(stmt => stmt.objectAsDataType(dt)).flatMap(mapper)

    def objectDataTypeOption(p: Property, dt: String): Either[String, Option[String]] =
      fromStatement(p, _.objectAsDataType(dt))
    def objectDataTypeOption[A](p: Property, dt: String, f: String => Either[String, A]): Either[String, Option[A]] =
      objectDataTypeOption(p, dt).flatMap(_.traverse(f))

    def rdfsType: Option[String] = Option(res.getPropertyResourceValue(RDF.`type`)).flatMap(_.uri)
    def uri: Option[String]      = Option(res.getURI)
  }
}
