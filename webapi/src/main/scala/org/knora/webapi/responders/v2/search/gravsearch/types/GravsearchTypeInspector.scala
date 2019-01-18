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

package org.knora.webapi.responders.v2.search.gravsearch.types

import akka.util.Timeout
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.responders.ResponderData
import org.knora.webapi.responders.v2.search._
import org.knora.webapi.{KnoraDispatchers, Settings, SettingsImpl}

import scala.concurrent.{ExecutionContext, Future}

/**
  * An trait whose implementations can get type information from a parsed Gravsearch query in different ways.
  * Type inspectors are run in a pipeline. Each inspector tries to determine the types of all the typeable
  * entities in the WHERE clause of a Gravsearch query, then runs the next inspector in the pipeline.
  *
  * @param nextInspector the next type inspector in the pipeline, or `None` if this is the last one.
  * @param system        the Akka actor system.
  */
abstract class GravsearchTypeInspector(protected val nextInspector: Option[GravsearchTypeInspector],
                                       responderData: ResponderData) {

    protected val system = responderData.system
    protected val settings: SettingsImpl = Settings(system)
    protected implicit val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)
    protected implicit val timeout: Timeout = settings.defaultTimeout

    /**
      * Given the WHERE clause from a parsed Gravsearch query, returns information about the types found
      * in the query. Each implementation must end by calling `runNextInspector`.
      *
      * @param previousResult the result of previous type inspection.
      * @param whereClause    the Gravsearch WHERE clause.
      * @param requestingUser the requesting user.
      * @return the result returned by the pipeline.
      */
    def inspectTypes(previousResult: IntermediateTypeInspectionResult,
                     whereClause: WhereClause,
                     requestingUser: UserADM): Future[IntermediateTypeInspectionResult]

    /**
      * Runs the next type inspector in the pipeline.
      *
      * @param intermediateResult the intermediate result produced by this type inspector.
      * @param whereClause        the Gravsearch WHERE clause.
      * @return the result returned by the pipeline.
      */
    protected def runNextInspector(intermediateResult: IntermediateTypeInspectionResult,
                                   whereClause: WhereClause,
                                   requestingUser: UserADM): Future[IntermediateTypeInspectionResult] = {
        // Is there another inspector in the pipeline?
        nextInspector match {
            case Some(next) =>
                // Yes. Run that inspector.
                next.inspectTypes(
                    previousResult = intermediateResult,
                    whereClause = whereClause,
                    requestingUser = requestingUser
                )

            case None =>
                // There are no more inspectors. Return the result we have.
                Future(intermediateResult)
        }
    }
}
