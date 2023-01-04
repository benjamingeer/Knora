/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.domain.model
import dsp.schema.domain
import dsp.schema.domain.Cardinality.MayHaveMany
import dsp.schema.domain.Cardinality.MayHaveOne
import dsp.schema.domain.Cardinality.MustHaveOne
import dsp.schema.domain.Cardinality.MustHaveSome

import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality.KnoraCardinalityInfo

sealed trait Cardinality {
  def min: Int
  def max: Option[Int]

  val oldCardinality: dsp.schema.domain.Cardinality

  def isStricter(other: KnoraCardinalityInfo): Boolean =
    Cardinality.get(other).isStricter(this)

  def isStricter(other: Cardinality): Boolean =
    (other.min, other.max) match {
      case (otherMin, _) if otherMin < this.min => true
      case (_, otherMax) if this.max.nonEmpty   => otherMax.forall(_ > this.max.get)
      case _                                    => false
    }

  override def toString: String = (min, max) match {
    case (min, None)      => s"$min-n"
    case (min, Some(max)) => if (min == max) s"$min" else s"$min-$max"
  }
}

object Cardinality {
  case object AtLeastOne extends Cardinality {
    override val min: Int                           = 1
    override val max: Option[Int]                   = None
    override val oldCardinality: domain.Cardinality = MustHaveSome
  }

  case object ExactlyOne extends Cardinality {
    override val min: Int                           = 1
    override val max: Option[Int]                   = Some(1)
    override val oldCardinality: domain.Cardinality = MustHaveOne
  }

  case object Unbounded extends Cardinality {
    override val min: Int                           = 0
    override val max: Option[Int]                   = None
    override val oldCardinality: domain.Cardinality = MayHaveMany
  }

  case object ZeroOrOne extends Cardinality {
    override val min: Int                           = 0
    override val max: Option[Int]                   = Some(1)
    override val oldCardinality: domain.Cardinality = MayHaveOne
  }

  val allCardinalities: Array[Cardinality] = Array(AtLeastOne, ExactlyOne, Unbounded, ZeroOrOne)
  def get(cardinalityInfo: KnoraCardinalityInfo): Cardinality =
    allCardinalities.find(_.oldCardinality == cardinalityInfo.cardinality).getOrElse(throw new IllegalStateException)
}
