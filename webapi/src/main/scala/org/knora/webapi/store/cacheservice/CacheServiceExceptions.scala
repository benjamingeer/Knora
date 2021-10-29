/*
 * Copyright © 2021 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.cacheservice

import org.knora.webapi.exceptions.CacheServiceException

case class EmptyKey(message: String) extends CacheServiceException(message)

case class EmptyValue(message: String) extends CacheServiceException(message)

case class UnsupportedValueType(message: String) extends CacheServiceException(message)
