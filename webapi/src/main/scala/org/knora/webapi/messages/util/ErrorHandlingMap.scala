/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util

import dsp.errors.InconsistentRepositoryDataException

/**
 * A [[Map]] that facilitates error-handling, by wrapping an ordinary [[Map]] and overriding the `default`
 * method to provide custom behaviour (by default, throwing an [[InconsistentRepositoryDataException]]) if a required
 * value is missing.
 *
 * @param toWrap           the [[Map]] to wrap.
 * @param errorTemplateFun a function that generates an appropriate error message if a required value is missing. The function's
 *                         argument is the key that was not found.
 * @param errorFun         an optional function that is called if a required value is missing. The function's argument is the
 *                         error message generated by `errorTemplateFun`.
 * @tparam A the type of keys in the map.
 * @tparam B the type of values in the map.
 */
class ErrorHandlingMap[A, B](
  toWrap: Map[A, B],
  private val errorTemplateFun: A => String,
  private val errorFun: String => B = { (errorMessage: String) =>
    throw InconsistentRepositoryDataException(errorMessage)
  }
) extends Map[A, B] {

  // As an optimization, if the Map we're supposed to wrap is another ErrorHandlingMap, wrap its underlying wrapped Map instead.
  private val wrapped: Map[A, B] = toWrap match {
    case errHandlingMap: ErrorHandlingMap[A, B] => errHandlingMap.wrapped
    case otherMap                               => otherMap
  }

  override def empty: ErrorHandlingMap[A, B] =
    new ErrorHandlingMap(Map.empty[A, B], errorTemplateFun, errorFun)

  override def get(key: A): Option[B] =
    wrapped.get(key)

  override def iterator: Iterator[(A, B)] =
    wrapped.iterator

  override def foreach[U](f: ((A, B)) => U): Unit =
    wrapped.foreach(f)

  override def size: Int =
    wrapped.size

  override def ++[V1 >: B](xs: IterableOnce[(A, V1)]): ErrorHandlingMap[A, V1] =
    new ErrorHandlingMap(wrapped ++ xs, errorTemplateFun, errorFun)

  /**
   * Called when a key is not found.
   *
   * @param key the given key value for which a binding is missing.
   */
  override def default(key: A): B = errorFun(errorTemplateFun(key))

  override def removed(key: A): Map[A, B] = new ErrorHandlingMap(wrapped - key, errorTemplateFun, errorFun)

  override def updated[V1 >: B](key: A, value: V1): Map[A, V1] =
    new ErrorHandlingMap(wrapped + (key -> value), errorTemplateFun, errorFun)
}
