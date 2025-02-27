/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import zio.*

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
   * [[A]] value or throws a [[FiberFailure]].
   *
   * This method is effectful and should only be used at the edges of your
   * application.
   */
  def runOrThrow[R, E, A](effect: ZIO[R, E, A])(implicit r: Runtime[R]): A =
    Unsafe.unsafe(implicit u => r.unsafe.run(effect).getOrThrowFiberFailure())

  /**
   * Executes the effect synchronously and returns its result as a
   * [[Future]].
   *
   * This method is effectful and should only be used at the edges of your
   * application.
   */
  def runToFuture[R, A](effect: ZIO[R, Throwable, A])(implicit r: Runtime[R]): Future[A] =
    Unsafe.unsafe(implicit u => r.unsafe.runToFuture(effect))

  def service[A: Tag](implicit r: Runtime[A]): A = runOrThrow(ZIO.service[A])
}
