/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.cache.api

import dsp.errors.CacheServiceException

case class EmptyKey(message: String) extends CacheServiceException(message)

case class EmptyValue(message: String) extends CacheServiceException(message)

case class UnsupportedValueType(message: String) extends CacheServiceException(message)
