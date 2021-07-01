/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import com.typesafe.scalalogging.LazyLogging
import org.knora.webapi.exceptions.AssertionException
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.util.rdf._

class UpgradePluginPR1885Spec extends UpgradePluginSpec with LazyLogging {
  private val nodeFactory: RdfNodeFactory = RdfFeatureFactory.getRdfNodeFactory(defaultFeatureFactoryConfig)

  private def checkLiteral(model: RdfModel, subj: IriNode, pred: IriNode, expectedObj: RdfLiteral): Unit = {
    model
      .find(
        subj = Some(subj),
        pred = Some(pred),
        obj = None
      )
      .toSet
      .headOption match {
      case Some(statement: Statement) =>
        statement.obj match {
          case rdfLiteral: RdfLiteral => assert(rdfLiteral == expectedObj)
          case other                  => throw AssertionException(s"Unexpected object for $pred: $other")
        }

      case None => throw AssertionException(s"No statement found with subject $subj and predicate $pred")
    }
  }

  "Upgrade plugin PR1885" should {
    "add UUID to resources" in {
      // Parse the input file.
      val model: RdfModel = trigFileToModel("test_data/upgrade/pr1885.trig")

      // Use the plugin to transform the input.
      val plugin = new UpgradePluginPR1885(defaultFeatureFactoryConfig)
      plugin.transform(model)

      // Check that the UUID is added.
      val subj = nodeFactory.makeIriNode("http://rdfh.ch/0001/Lz7WEqJETJqqsUZQYexBQg")
      val pred = nodeFactory.makeIriNode(OntologyConstants.KnoraBase.ResourceHasUUID)

      model
        .find(
          subj = Some(subj),
          pred = Some(pred),
          obj = None
        )
        .toSet
        .headOption match {
        case Some(statement: Statement) =>
          statement.obj match {
            case datatypeLiteral: DatatypeLiteral =>
              assert(datatypeLiteral.datatype == OntologyConstants.Xsd.String)
              assert(datatypeLiteral.value == "Lz7WEqJETJqqsUZQYexBQg")
            case other =>
              throw AssertionException(s"Unexpected object for $pred: $other")
          }

        case None => throw AssertionException(s"No statement found with subject $subj and predicate $pred")
      }

    }
  }
}
