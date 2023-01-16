/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import zio._

import scala.concurrent.Future

object UnsafeZioRun {

  /**
   * Executes the effect synchronously and returns its result as a
   * [[zio.Exit]] value.
   *
   * This method is effectful and should only be used at the edges of your
   * application.
   */
  def run[R, E, A](effect: ZIO[R, E, A])(implicit r: Runtime[R]): Exit[E, A] =
    Unsafe.unsafe(implicit u => r.unsafe.run(effect))

  /**
   * Executes the effect synchronously and returns its result as a
   * [[Future]].
   *
   * This method is effectful and should only be used at the edges of your
   * application.
   */
  def runToFuture[R, A](effect: ZIO[R, Throwable, A])(implicit r: Runtime[R]): Future[A] =
    Unsafe.unsafe(implicit u => r.unsafe.runToFuture(effect))
}
