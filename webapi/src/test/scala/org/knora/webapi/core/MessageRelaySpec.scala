/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import zio.*
import zio.test.Assertion.anything
import zio.test.Assertion.dies
import zio.test.Assertion.isSubtype
import zio.test.*

import org.knora.webapi.messages.ResponderRequest

object MessageRelaySpec extends ZIOSpecDefault {

  case class TestHandler() extends MessageHandler {
    override def handle(message: ResponderRequest): Task[Any]         = ZIO.succeed("handled")
    override def isResponsibleFor(message: ResponderRequest): Boolean = message.isInstanceOf[SomeRelayedMessage]
  }

  object TestHandler {
    val layer: URLayer[MessageRelay, TestHandler] = ZLayer.fromZIO(MessageRelay.subscribe(TestHandler()))
  }

  case class SomeRelayedMessage() extends RelayedMessage
  case class NotARelayedMessage() extends ResponderRequest

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("MessageRelay")(
      test("when asked with an UnknownTestMessage then it should die with an IllegalStateException") {
        for {
          // need to include the TestHandler in the test otherwise the layer and hence its subscription is ignored
          _      <- ZIO.service[TestHandler]
          actual <- MessageRelay.ask(NotARelayedMessage()).exit
        } yield assert(actual)(dies(isSubtype[IllegalStateException](anything)))
      },
      test("when asked with a HandledTestMessage then it should relay it to the registered TestHandler") {
        for {
          // need to include the TestHandler in the test otherwise the layer and hence its subscription is ignored
          _      <- ZIO.service[TestHandler]
          actual <- MessageRelay.ask[String](SomeRelayedMessage())
        } yield assertTrue(actual == "handled")
      }
    ).provide(MessageRelayLive.layer, TestHandler.layer)
}
