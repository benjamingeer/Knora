/*
/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

package org.knora.webapi.update.plugins

import org.eclipse.rdf4j.model.Model
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.knora.webapi.messages.store.triplestoremessages.{SparqlSelectResponse, SparqlSelectResponseBody}

class UpdatePluginPR1307Spec extends UpdatePluginSpec {
    "Update plugin PR1307" should {
        "update text values with standoff" in {
            // Parse the input file.
            val model: Model = trigFileToModel("src/test/resources/test-data/update/pr1307.trig")

            // Use the plugin to transform the input.
            val plugin = new UpdatePluginPR1307
            plugin.transform(model)

            // Make an in-memory repository containing the transformed model.
            val repository: SailRepository = makeRepository(model)
            val connection = repository.getConnection

            // Check that knora-base:valueHasMaxStandoffStartIndex was added.

            val query1: String =
                """
                  |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                  |
                  |SELECT ?s ?maxStartIndex WHERE {
                  |    ?s knora-base:valueHasMaxStandoffStartIndex ?maxStartIndex .
                  |}
                  |""".stripMargin

            val queryResult1: SparqlSelectResponse = doSelect(selectQuery = query1, connection = connection)

            val expectedResult1: SparqlSelectResponseBody = expectedResult(
                Seq(
                    Map(
                        "s" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ",
                        "maxStartIndex" -> "7"
                    )
                )
            )

            assert(queryResult1.results == expectedResult1)

            // Check that the standoff tags' IRIs were changed correctly.

            val query2: String =
                """
                  |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                  |
                  |SELECT ?tag WHERE {
                  |    <http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ> knora-base:valueHasStandoff ?tag .
                  |} ORDER BY ?tag
                  |""".stripMargin

            val queryResult2: SparqlSelectResponse = doSelect(selectQuery = query2, connection = connection)

            val expectedResult2: SparqlSelectResponseBody = expectedResult(
                Seq(
                    Map("tag" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/0"),
                    Map("tag" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/1"),
                    Map("tag" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/2"),
                    Map("tag" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/3"),
                    Map("tag" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/4"),
                    Map("tag" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/5"),
                    Map("tag" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/6"),
                    Map("tag" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/7")
                )
            )

            assert(queryResult2.results == expectedResult2)

            // Check that the objects of knora-base:standoffTagHasStartParent were changed correctly.

            val query3: String =
                """
                  |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                  |
                  |SELECT ?tag ?startIndex ?startParent WHERE {
                  |    ?tag knora-base:standoffTagHasStartIndex ?startIndex .
                  |
                  |    OPTIONAL {
                  |        ?tag knora-base:standoffTagHasStartParent ?startParent .
                  |    }
                  |} ORDER BY ?tag
                  |""".stripMargin

            val queryResult3: SparqlSelectResponse = doSelect(selectQuery = query3, connection = connection)

            val expectedResult3: SparqlSelectResponseBody = expectedResult(
                Seq(
                    Map(
                        "startIndex" -> "0",
                        "tag" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/0"
                    ),
                    Map(
                        "startIndex" -> "1",
                        "startParent" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/0",
                        "tag" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/1"
                    ),
                    Map(
                        "startIndex" -> "2",
                        "startParent" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/1",
                        "tag" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/2"
                    ),
                    Map(
                        "startIndex" -> "3",
                        "startParent" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/1",
                        "tag" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/3"
                    ),
                    Map(
                        "startIndex" -> "4",
                        "startParent" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/1",
                        "tag" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/4"
                    ),
                    Map(
                        "startIndex" -> "5",
                        "startParent" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/1",
                        "tag" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/5"
                    ),
                    Map(
                        "startIndex" -> "6",
                        "startParent" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/1",
                        "tag" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/6"
                    ),
                    Map(
                        "startIndex" -> "7",
                        "startParent" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/1",
                        "tag" -> "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g/values/xyUIf8QHS5aFrlt7Q4F1FQ/standoff/7"
                    )
                )
            )

            assert(queryResult3.results == expectedResult3)
            connection.close()
            repository.shutDown()
        }
    }
}
*/
