/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import zio.*
import zio.macros.accessible

import org.knora.webapi.core.domain.AppState

@accessible
trait State {
  def set(v: AppState): UIO[Unit]
  val getAppState: UIO[AppState]
}

object State {
  val layer: ZLayer[Any, Nothing, State] =
    ZLayer {
      for {
        ref <- Ref.make[AppState](AppState.Stopped)
      } yield new StateImpl(ref) {}

    }.tap(_ => ZIO.logInfo(">>> AppStateService initialized <<<"))

  sealed abstract private class StateImpl(state: Ref[AppState]) extends State {

    override def set(v: AppState): UIO[Unit] =
      state.set(v) *> ZIO.logInfo(s"AppState set to ${v.toString()}")

    override val getAppState: UIO[AppState] =
      state.get

  }
}
