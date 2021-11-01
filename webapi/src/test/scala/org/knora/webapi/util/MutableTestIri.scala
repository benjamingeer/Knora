/*
 * Copyright © 2021 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util

import org.knora.webapi.IRI
import org.knora.webapi.messages.StringFormatter

/**
 * Holds an optional, mutable IRI for use in tests.
 */
class MutableTestIri {
  private val stringFormatter = StringFormatter.getGeneralInstance
  private var maybeIri: Option[IRI] = None

  /**
   * Checks whether an IRI is valid. If it's valid, stores the IRI, otherwise throws an exception.
   *
   * @param iri the IRI to be stored.
   */
  def set(iri: IRI): Unit =
    maybeIri = Some(stringFormatter.validateAndEscapeIri(iri, throw TestIriException(s"Got an invalid IRI: <$iri>")))

  /**
   * Removes any stored IRI.
   */
  def unset(): Unit =
    maybeIri = None

  /**
   * Gets the stored IRI, or throws an exception if the IRI is not set.
   *
   * @return the stored IRI.
   */
  def get: IRI =
    maybeIri.getOrElse(throw TestIriException("This test could not be run because a previous test failed"))
}

/**
 * Thrown if a stored IRI was needed but was not set.
 */
case class TestIriException(message: String) extends Exception(message)
