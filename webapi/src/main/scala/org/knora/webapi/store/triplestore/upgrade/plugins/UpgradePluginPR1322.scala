/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import dsp.errors.InconsistentRepositoryDataException
import dsp.valueobjects.UuidUtil
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.util.rdf.*
import org.knora.webapi.store.triplestore.upgrade.UpgradePlugin

/**
 * Transforms a repository for Knora PR 1322.
 */
class UpgradePluginPR1322() extends UpgradePlugin {
  private val nodeFactory: RdfNodeFactory = RdfFeatureFactory.getRdfNodeFactory()
  // IRI objects representing the IRIs used in this transformation.
  private val ValueHasUUIDIri: IriNode      = nodeFactory.makeIriNode(OntologyConstants.KnoraBase.ValueHasUUID)
  private val ValueCreationDateIri: IriNode = nodeFactory.makeIriNode(OntologyConstants.KnoraBase.ValueCreationDate)
  private val PreviousValueIri: IriNode     = nodeFactory.makeIriNode(OntologyConstants.KnoraBase.PreviousValue)

  override def transform(model: RdfModel): Unit =
    // Add a random UUID to each current value version.
    for (valueIri: IriNode <- collectCurrentValueIris(model)) {
      model.add(
        subj = valueIri,
        pred = ValueHasUUIDIri,
        obj = nodeFactory.makeStringLiteral(UuidUtil.makeRandomBase64EncodedUuid)
      )
    }

  /**
   * Collects the IRIs of all values that are current value versions.
   */
  private def collectCurrentValueIris(model: RdfModel): Iterator[IriNode] =
    model
      .find(None, Some(ValueCreationDateIri), None)
      .map(_.subj)
      .filter { (resource: RdfResource) =>
        model
          .find(
            subj = None,
            pred = Some(PreviousValueIri),
            obj = Some(resource)
          )
          .isEmpty
      }
      .map {
        case iriNode: IriNode => iriNode
        case other            => throw InconsistentRepositoryDataException(s"Unexpected subject for $ValueCreationDateIri: $other")
      }
}
