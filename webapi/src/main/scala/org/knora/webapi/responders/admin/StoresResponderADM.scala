/*
 * Copyright © 2021 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import akka.pattern._
import org.knora.webapi.exceptions.ForbiddenException
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.admin.responder.storesmessages.{
  ResetTriplestoreContentRequestADM,
  ResetTriplestoreContentResponseADM,
  StoreResponderRequestADM
}
import org.knora.webapi.messages.app.appmessages.GetAllowReloadOverHTTPState
import org.knora.webapi.messages.store.cacheservicemessages.{CacheServiceFlushDB, CacheServiceFlushDBACK}
import org.knora.webapi.messages.store.triplestoremessages.{
  RdfDataObject,
  ResetRepositoryContent,
  ResetRepositoryContentACK
}
import org.knora.webapi.messages.util.{KnoraSystemInstances, ResponderData}
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.ontologymessages.LoadOntologiesRequestV2
import org.knora.webapi.responders.Responder
import org.knora.webapi.responders.Responder.handleUnexpectedMessage

import scala.concurrent.Future

/**
 * This responder is used by [[org.knora.webapi.routing.admin.StoreRouteADM]], for piping through HTTP requests to the
 * 'Store Module'
 */
class StoresResponderADM(responderData: ResponderData) extends Responder(responderData) {

  /**
   * A user representing the Knora API server, used in those cases where a user is required.
   */
  private val systemUser = KnoraSystemInstances.Users.SystemUser

  /**
   * Receives a message extending [[StoreResponderRequestADM]], and returns an appropriate response message.
   */
  def receive(msg: StoreResponderRequestADM) = msg match {
    case ResetTriplestoreContentRequestADM(
          rdfDataObjects: Seq[RdfDataObject],
          prependDefaults: Boolean,
          featureFactoryConfig: FeatureFactoryConfig
        ) =>
      resetTriplestoreContent(rdfDataObjects, prependDefaults, featureFactoryConfig)
    case other => handleUnexpectedMessage(other, log, this.getClass.getName)
  }

  /**
   * This method send a [[ResetRepositoryContent]] message to the [[org.knora.webapi.store.triplestore.TriplestoreManager]].
   *
   * @param rdfDataObjects the payload consisting of a list of [[RdfDataObject]] send inside the message.
   * @return a future containing a [[ResetTriplestoreContentResponseADM]].
   */
  private def resetTriplestoreContent(
    rdfDataObjects: Seq[RdfDataObject],
    prependDefaults: Boolean = true,
    featureFactoryConfig: FeatureFactoryConfig
  ): Future[ResetTriplestoreContentResponseADM] = {

    log.debug(s"resetTriplestoreContent - called")

    for {
      value: Boolean <- (appActor ? GetAllowReloadOverHTTPState()).mapTo[Boolean]
      _ = if (!value) {
        throw ForbiddenException(
          "The ResetTriplestoreContent operation is not allowed. Did you start the server with the right flag?"
        )
      }

      resetResponse <- (storeManager ? ResetRepositoryContent(rdfDataObjects, prependDefaults))
        .mapTo[ResetRepositoryContentACK]
      _ = log.debug(s"resetTriplestoreContent - triplestore reset done - {}", resetResponse.toString)

      loadOntologiesResponse <- (responderManager ? LoadOntologiesRequestV2(
        featureFactoryConfig = featureFactoryConfig,
        requestingUser = systemUser
      )).mapTo[SuccessResponseV2]
      _ = log.debug(s"resetTriplestoreContent - load ontology done - {}", loadOntologiesResponse.toString)

      redisFlushDB <- (storeManager ? CacheServiceFlushDB(systemUser)).mapTo[CacheServiceFlushDBACK]
      _ = log.debug(s"resetTriplestoreContent - flushing Redis store done - {}", redisFlushDB.toString)

      result = ResetTriplestoreContentResponseADM(message = "success")

    } yield result
  }

}
