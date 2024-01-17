/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.domain

import dsp.valueobjects.Iri
import org.knora.webapi.slice.common.{Value, WithFrom, WithJsonCodec, WithTapirCodec}

final case class SparqlEncodedString private (value: String) extends Value[String]

object SparqlEncodedString
    extends WithFrom[String, SparqlEncodedString]
    with WithJsonCodec[SparqlEncodedString]
    with WithTapirCodec[SparqlEncodedString] {

  def from(str: String): Either[String, SparqlEncodedString] =
    Iri
      .toSparqlEncodedString(str)
      .map(SparqlEncodedString.apply)
      .toRight(s"May not be empty or contain a line break: '$str'")
}
