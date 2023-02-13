/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi
package responders

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.http.scaladsl.util.FastFuture
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import com.typesafe.scalalogging.Logger
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import dsp.errors._
import zio.Task
import zio.ZIO

import org.knora.webapi.messages.StringFormatter

/**
 * An abstract class providing values that are commonly used in responders.
 */
abstract class Responder(actorDeps: ActorDeps) extends LazyLogging {

  protected implicit val system: ActorSystem                = actorDeps.system
  protected implicit val timeout: Timeout                   = actorDeps.timeout
  protected implicit val executionContext: ExecutionContext = actorDeps.executionContext
  protected implicit val stringFormatter: StringFormatter   = StringFormatter.getGeneralInstance

  protected val log: Logger        = logger
  protected val appActor: ActorRef = actorDeps.appActor
  protected val iriService: EntityAndClassIriService =
    EntityAndClassIriService(actorDeps, stringFormatter)

  /**
   * A responder uses this method to handle unexpected request messages in a consistent way.
   *
   * @param message the message that was received.
   * @param log     a [[Logger]].
   * @param who     the responder receiving the message.
   */
  protected def handleUnexpectedMessage(message: Any, log: Logger, who: String): Future[Nothing] =
    Responder.handleUnexpectedMessageFuture(message, who)
}
object Responder {
  private def unexpectedException(message: Any, who: String) = UnexpectedMessageException(
    s"$who received an unexpected message $message of type ${message.getClass.getCanonicalName}"
  )
  private def handleUnexpectedMessageFuture(message: Any, who: String): Future[Nothing] =
    FastFuture.failed(unexpectedException(message, who))
  def handleUnexpectedMessageTask(message: Any, who: String): Task[Nothing] =
    ZIO.fail(unexpectedException(message, who))
}
