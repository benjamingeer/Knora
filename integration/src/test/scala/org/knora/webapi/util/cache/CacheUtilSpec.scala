/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util.cache

import org.knora.webapi.CoreSpec
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.sharedtestdata.SharedTestDataADM2

class CacheUtilSpec extends CoreSpec {

  private val cacheName = Authenticator.AUTHENTICATION_INVALIDATION_CACHE_NAME
  private val sessionId = java.lang.System.currentTimeMillis().toString

  "Caching" should {

    "allow to set and get the value " in {
      CacheUtil.removeAllCaches()
      CacheUtil.createCaches(appConfig.cacheConfigs)
      CacheUtil.put(cacheName, sessionId, SharedTestDataADM2.rootUser)
      CacheUtil.get(cacheName, sessionId) should be(Some(SharedTestDataADM2.rootUser))
    }

    "return none if key is not found " in {
      CacheUtil.removeAllCaches()
      CacheUtil.createCaches(appConfig.cacheConfigs)
      CacheUtil.get(cacheName, 213.toString) should be(None)
    }
  }
}
