/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util

import zio._

object LogAspect {

  /**
   * Add the correlation id from as a log annotation.
   */
  def logAnnotateCorrelationId(): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      override def apply[R, E, A](
        zio: ZIO[R, E, A]
      )(implicit trace: Trace): ZIO[R, E, A] =
        Random.nextUUID.map(_.toString).flatMap(id => ZIO.logAnnotate("correlation-id", id)(zio))
    }

  /**
   * Creates a span log annotation based on the provided label.
   *
   * @param label
   */
  def logSpan(
    label: String
  ): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      override def apply[R, E, A](zio: ZIO[R, E, A])(implicit
        trace: Trace
      ): ZIO[R, E, A] =
        ZIO.logSpan(label)(zio)
    }
}
