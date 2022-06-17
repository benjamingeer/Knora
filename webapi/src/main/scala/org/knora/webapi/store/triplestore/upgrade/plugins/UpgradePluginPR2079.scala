/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import com.typesafe.scalalogging.Logger
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.store.triplestore.upgrade.UpgradePlugin

/**
 * Transforms a repository for Knora PR 2079.
 * Adds missing datatype ^^<http://www.w3.org/2001/XMLSchema#anyURI> and/or value to valueHasUri
 */
class UpgradePluginPR2079(featureFactoryConfig: FeatureFactoryConfig, log: Logger) extends UpgradePlugin {
  private val nodeFactory: RdfNodeFactory = RdfFeatureFactory.getRdfNodeFactory(featureFactoryConfig)

  override def transform(model: RdfModel): Unit = {
    val statementsToRemove: collection.mutable.Set[Statement] = collection.mutable.Set.empty
    val statementsToAdd: collection.mutable.Set[Statement]    = collection.mutable.Set.empty

    for (statement: Statement <- model) {
      if (statement.pred.iri == OntologyConstants.KnoraBase.ValueHasUri) {
        statement.obj match {
          case literal: DatatypeLiteral =>
            if (literal.datatype != OntologyConstants.Xsd.Uri) {
              statementsToRemove += statement

              statementsToAdd += nodeFactory.makeStatement(
                subj = statement.subj,
                pred = statement.pred,
                obj = nodeFactory.makeDatatypeLiteral(literal.value, OntologyConstants.Xsd.Uri),
                context = statement.context
              )

              log.info(
                s"Transformed valueHasIri: $literal to ${nodeFactory
                  .makeDatatypeLiteral(literal.value, OntologyConstants.Xsd.Uri)}."
              )
            }

          case node: IriNode =>
            statementsToRemove += statement

            statementsToAdd += nodeFactory.makeStatement(
              subj = statement.subj,
              pred = statement.pred,
              obj = nodeFactory.makeDatatypeLiteral(node.iri, OntologyConstants.Xsd.Uri),
              context = statement.context
            )

            log.info(
              s"Transformed valueHasIri $node to ${nodeFactory.makeDatatypeLiteral(node.iri, OntologyConstants.Xsd.Uri)}."
            )

          case _ => ()
        }
      }
    }

    model.removeStatements(statementsToRemove.toSet)
    model.addStatements(statementsToAdd.toSet)
  }
}
