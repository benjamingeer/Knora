/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.jena

import org.apache.jena.rdf.model.*
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import zio.Console
import zio.Scope
import zio.Task
import zio.UIO
import zio.ZIO

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters.*

object ModelOps { self =>

  extension (model: Model) {
    def printTurtle: UIO[Unit] =
      asTurtle.flatMap(Console.printLine(_)).logError.ignore

    def printTriG: UIO[Unit] =
      Console.printLine("/// Model TRIG START ///").ignore *>
        asTriG.flatMap(Console.printLine(_)).logError.ignore *>
        Console.printLine("/// Model TRIG END ///").ignore

    def asTurtle: Task[String] = as(Lang.TURTLE)
    def asTriG: Task[String]   = as(Lang.TRIG)
    def as(lang: Lang): Task[String] =
      ZIO.attempt {
        val out = new java.io.ByteArrayOutputStream()
        RDFDataMgr.write(out, model, lang)
        out.toString(java.nio.charset.StandardCharsets.UTF_8)
      }

    def resourceOption(uri: String): Option[Resource] = Option(model.getResource(uri))
    def resource(uri: String): Either[String, Resource] =
      model.resourceOption(uri).toRight(s"Resource not found '$uri'")

    def statementOption(s: Resource, p: Property): Option[Statement] = Option(model.getProperty(s, p))
    def statement(s: Resource, p: Property): Either[String, Statement] =
      statementOption(s, p).toRight(s"Statement not found '${s.getURI} ${p.getURI} ?o .'")

    def singleRootResource: Either[String, Resource] =
      val subs       = model.listSubjects().asScala.toSet
      val objs       = model.listObjects().asScala.collect { case r: Resource => r }.toSet
      val candidates = subs -- objs
      candidates match {
        case iris if iris.size == 1 => Right(iris.head)
        case iris if iris.isEmpty   => Left("No root resource found in model")
        case iris                   => Left(s"Multiple root resources found in model: ${iris.mkString(", ")}")
      }

    def singleSubjectWithPropertyOption(property: Property): Either[String, Option[Resource]] =
      val subjects = model.listSubjectsWithProperty(property).asScala.toList
      subjects match {
        case s :: Nil => Right(Some(s))
        case Nil      => Right(None)
        case _        => Left(s"Multiple subjects found with property ${property.getURI}")
      }

    def singleSubjectWithProperty(property: Property): Either[String, Resource] =
      singleSubjectWithPropertyOption(property).flatMap(
        _.toRight(s"No resource found with property ${property.getURI}"),
      )
  }

  def fromJsonLd(str: String): ZIO[Scope, String, Model] = from(str, Lang.JSONLD)
  def fromTurtle(str: String): ZIO[Scope, String, Model] = from(str, Lang.TURTLE)

  private val createModel =
    ZIO.acquireRelease(ZIO.succeed(ModelFactory.createDefaultModel()))(m => ZIO.succeed(m.close()))

  def from(str: String, lang: Lang): ZIO[Scope, String, Model] =
    for {
      m <- createModel
      _ <- ZIO
             .attempt(RDFDataMgr.read(m, ByteArrayInputStream(str.getBytes(StandardCharsets.UTF_8)), lang))
             .mapError(_.getMessage)
    } yield m
}
