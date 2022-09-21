/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util

import org.apache.commons.text.StringEscapeUtils

import java.time.Instant
import scala.reflect.runtime.{universe => ru}

import dsp.schema.domain.Cardinality
import dsp.schema.domain.Cardinality._
import org.knora.webapi.OntologySchema
import org.knora.webapi.messages.SmartIri

/**
 * Utility functions for working with Akka messages.
 */
object MessageUtil {

  // Set of case class field names to skip.
  private val fieldsToSkip =
    Set(
      "stringFormatter",
      "base64Decoder",
      "knoraIdUtil",
      "standoffLinkTagTargetResourceIris"
    )

  /**
   * Recursively converts a Scala object to Scala source code for constructing the object (with named parameters). This is useful
   * for writing tests containing hard-coded Akka messages. It works with case classes, collections ([[Seq]], [[Set]],
   * and [[Map]]), [[Option]], enumerations (as long as the enumeration value's `toString` representation is the same
   * as its identifier), and primitive types. It doesn't work with classes defined inside methods.
   *
   * @param obj the object to convert.
   * @return a string that can be pasted into Scala source code to construct the object.
   */
  def toSource(obj: Any): String = {
    def maybeMakeNewLine(elemCount: Int): String = if (elemCount > 1) "\n" else ""

    obj match {
      // Handle primitive types.
      case null                   => "null"
      case short: Short           => short.toString
      case int: Int               => int.toString
      case long: Long             => long.toString
      case float: Float           => float.toString
      case double: Double         => double.toString
      case bigDecimal: BigDecimal => bigDecimal.toString
      case boolean: Boolean       => boolean.toString
      case char: Char             => char.toString
      case byte: Byte             => byte.toString
      case s: String              => "\"" + StringEscapeUtils.escapeJava(s) + "\""
      case Some(value)            => "Some(" + toSource(value) + ")"
      case smartIri: SmartIri     => "\"" + StringEscapeUtils.escapeJava(smartIri.toString) + "\".toSmartIri"
      case instant: Instant       => "Instant.parse(\"" + instant.toString + "\")"
      case None                   => "None"

      // Handle value objects.
      case cardinality: Cardinality =>
        cardinality match {
          case MayHaveOne   => "MayHaveOne"
          case MayHaveMany  => "MayHaveMany"
          case MustHaveOne  => "MustHaveOne"
          case MustHaveSome => "MustHaveSome"
        }

      // Handle enumerations.
      case enumVal if enumVal.getClass.getName == "scala.Enumeration$Val" => enumVal.toString

      case ontologySchema: OntologySchema =>
        ontologySchema.getClass.getSimpleName.stripSuffix("$")

      // Handle collections.
      case Nil => "Nil"

      case list: Seq[Any @unchecked] =>
        val maybeNewLine = maybeMakeNewLine(list.size)
        list.map(elem => toSource(elem)).mkString("Vector(" + maybeNewLine, ", " + maybeNewLine, maybeNewLine + ")")

      case set: Set[Any @unchecked] =>
        val maybeNewLine = maybeMakeNewLine(set.size)
        set.map(elem => toSource(elem)).mkString("Set(" + maybeNewLine, ", " + maybeNewLine, maybeNewLine + ")")

      case map: Map[Any @unchecked, Any @unchecked] =>
        val maybeNewLine = maybeMakeNewLine(map.size)

        map.map { case (key, value) =>
          toSource(key) + " -> " + toSource(value)
        }
          .mkString("Map(" + maybeNewLine, ", " + maybeNewLine, maybeNewLine + ")")

      // Handle case classes.
      case caseClass: Product =>
        val objClass     = obj.getClass
        val objClassName = objClass.getSimpleName

        val fieldMap: Map[String, Any] = caseClass.getClass.getDeclaredFields.foldLeft(Map[String, Any]()) {
          (acc, field) =>
            val fieldName = field.getName.trim

            if (fieldName.contains("$") || fieldName.endsWith("Format") || fieldsToSkip.contains(fieldName)) { // ridiculous hack
              acc
            } else {
              field.setAccessible(true)
              acc + (field.getName -> field.get(caseClass))
            }
        }

        val members: Iterable[String] = fieldMap.map { case (fieldName, fieldValue) =>
          val fieldValueString = toSource(fieldValue)
          s"$fieldName = $fieldValueString"
        }

        val maybeNewLine = maybeMakeNewLine(members.size)
        members.mkString(objClassName + "(" + maybeNewLine, ", " + maybeNewLine, maybeNewLine + ")")

      // Handle other classes.
      case _ =>
        // Generate a named parameter initializer for each of the class's non-method fields.

        val objClass     = obj.getClass
        val objClassName = objClass.getSimpleName

        val runtimeMirror: ru.Mirror = ru.runtimeMirror(objClass.getClassLoader)
        val instanceMirror           = runtimeMirror.reflect(obj)
        val objType: ru.Type         = runtimeMirror.classSymbol(objClass).toType

        val members: Iterable[String] = objType.members.filter(member => !member.isMethod).flatMap { member =>
          val memberName = member.name.toString.trim

          if (!(memberName.contains("$") || memberName.endsWith("Format") || fieldsToSkip.contains(memberName))) {
            val fieldMirror =
              try {
                instanceMirror.reflectField(member.asTerm)
              } catch {
                case e: Exception => throw new Exception(s"Can't format member $memberName in class $objClassName", e)
              }

            val memberValue       = fieldMirror.get
            val memberValueString = toSource(memberValue)
            Some(s"$memberName = $memberValueString")
          } else {
            None
          }
        }

        val maybeNewLine = maybeMakeNewLine(members.size)
        members.mkString(objClassName + "(" + maybeNewLine, ", " + maybeNewLine, maybeNewLine + ")")
    }
  }
}
