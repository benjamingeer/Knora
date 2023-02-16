package org.knora.webapi.core

import org.knora.webapi.core.MessageRelaySpec.SomeRelayedMessage
import org.knora.webapi.responders.{ActorToZioBridge, ActorToZioBridgeMock}
import zio._
import zio.mock.Expectation
import zio.test.Spec.empty.ZSpec
import zio.test._

object AppRouterRelayingMessageHandlerSpec extends ZIOSpecDefault {

  private val actorToZioBridgeMockExpectation: ULayer[ActorToZioBridge] = ActorToZioBridgeMock.AskAppActor
    .of[String]
    .apply(
      assertion = Assertion.equalTo(MessageRelaySpec.NotARelayedMessage()),
      result = Expectation.value("handled by zio bridge")
    )
    .toLayer

  val spec: ZSpec[Any, Throwable, TestSuccess] = suite("AppRouterRelayingMessageHandler")(
    suite("given a message handled by a different handler")(test("it should not relay to the ActorToZioBridge") {
      for {
        _      <- ZIO.service[MessageRelaySpec.TestHandler]
        _      <- ZIO.service[AppRouterRelayingMessageHandler]
        actual <- ZIO.serviceWithZIO[MessageRelay](_.ask[String](SomeRelayedMessage()))
      } yield assertTrue(actual == "handled")
    }).provide(
      MessageRelayLive.layer,
      AppRouterRelayingMessageHandler.layer,
      ActorToZioBridgeMock.empty,
      MessageRelaySpec.TestHandler.layer
    ),
    suite("given a message handled by a different handler")(
      test("it should relay the message to the ActorToZioBridge") {
        for {
          _      <- ZIO.service[MessageRelaySpec.TestHandler]
          _      <- ZIO.service[AppRouterRelayingMessageHandler]
          actual <- ZIO.serviceWithZIO[MessageRelay](_.ask[String](MessageRelaySpec.NotARelayedMessage()))
        } yield assertTrue(actual == "handled by zio bridge")
      }
    ).provide(
      MessageRelayLive.layer,
      AppRouterRelayingMessageHandler.layer,
      actorToZioBridgeMockExpectation,
      MessageRelaySpec.TestHandler.layer
    )
  )
}
