package org.knora.webapi.slice
import zio.ZLayer

trait SliceModule[RIn, E, ROut] {
  type Dependencies = RIn
  type Provided     = ROut
  inline def layer: ZLayer[RIn, E, ROut]
}

type RModule[RIn, ROut]  = SliceModule[RIn, Throwable, ROut]
type URModule[RIn, ROut] = SliceModule[RIn, Nothing, ROut]
type Module[E, ROut]     = SliceModule[Any, E, ROut]
type UModule[ROut]       = SliceModule[Any, Nothing, ROut]
type TaskModule[ROut]    = SliceModule[Any, Throwable, ROut]
