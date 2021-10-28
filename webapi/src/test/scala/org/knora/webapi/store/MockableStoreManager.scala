/*
 * Copyright © 2021 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store

import akka.actor.{ActorRef, Props}
import org.knora.webapi.core.LiveActorMaker
import org.knora.webapi.settings.{KnoraDispatchers, _}
import org.knora.webapi.store.cacheservice.CacheService
import org.knora.webapi.store.iiif.MockableIIIFManager

class MockableStoreManager(mockStoreConnectors: Map[String, ActorRef], appActor: ActorRef, cs: CacheService)
    extends StoreManager(appActor, cs)
    with LiveActorMaker {

  /**
   * Starts the MockableIIIFManager
   */
  override lazy val iiifManager: ActorRef = makeActor(
    Props(new MockableIIIFManager(mockStoreConnectors) with LiveActorMaker)
      .withDispatcher(KnoraDispatchers.KnoraActorDispatcher),
    IIIFManagerActorName
  )

}
