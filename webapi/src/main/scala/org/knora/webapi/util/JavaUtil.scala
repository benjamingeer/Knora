/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.util

import java.util.function.{BiFunction, Function}

import scala.language.implicitConversions

/**
 * Utility functions for working with Java libraries.
 */
object JavaUtil {

    /**
     * Converts a 1-argument Scala function into a Java [[Function]].
     *
     * @param f the Scala function.
     * @return a [[Function]] that calls the Scala function.
     */
    def function[A, B](f: A => B): Function[A, B] =
        (a: A) => f(a)

    /**
     * Converts a 2-argument Scala function into a Java [[BiFunction]].
     *
     * @param f the Scala function.
     * @return a [[BiFunction]] that calls the Scala function.
     */
    def biFunction[A, B, C](f: (A, B) => C): BiFunction[A, B, C] =
        (a: A, b: B) => f(a, b)

    /**
     * Helps turn matches for optional regular expression groups, which can be null, into Scala Option objects. See
     * [[https://stackoverflow.com/a/18794646]].
     */
    object Optional {
        def unapply[T](a: T): Some[Option[T]] = if (null == a) Some(None) else Some(Some(a))
    }

    /**
     * Wraps a Java `Optional` and converts it to a Scala [[Option]].
     */
    class JavaOptional[T](opt: java.util.Optional[T]) {
        def toOption: Option[T] = if (opt.isPresent) Some(opt.get()) else None
    }

    implicit def toJavaOptional[T](optional: java.util.Optional[T]): JavaOptional[T] = new JavaOptional[T](optional)
}
