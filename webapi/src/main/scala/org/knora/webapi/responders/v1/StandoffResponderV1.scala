/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

package org.knora.webapi.responders.v1

import akka.actor.Status
import akka.stream.ActorMaterializer
import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.ontologymessages.{Cardinality, StandoffEntityInfoGetRequestV1, StandoffEntityInfoGetResponseV1}
import org.knora.webapi.messages.v1.responder.standoffmessages.{CreateStandoffResponseV1, _}
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v1.responder.valuemessages.{KnoraCalendarV1, KnoraPrecisionV1}
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.ScalaPrettyPrinter
import org.knora.webapi.util.standoff._
import org.knora.webapi.twirl._

import scala.concurrent.Future
import scala.xml.NodeSeq

/**
  * Responds to requests for information about binary representations of resources, and returns responses in Knora API
  * v1 format.
  */
class StandoffResponderV1 extends ResponderV1 {

    implicit val materializer = ActorMaterializer()

    // Converts SPARQL query results to ApiValueV1 objects.
    val valueUtilV1 = new ValueUtilV1(settings)

    /**
      * Receives a message of type [[StandoffResponderRequestV1]], and returns an appropriate response message, or
      * [[Status.Failure]]. If a serious error occurs (i.e. an error that isn't the client's fault), this
      * method first returns `Failure` to the sender, then throws an exception.
      */
    def receive = {
        case CreateStandoffRequestV1(xml, userProfile) => future2Message(sender(), createStandoff(xml, userProfile), log)
        case other => sender ! Status.Failure(UnexpectedMessageException(s"Unexpected message $other of type ${other.getClass.getCanonicalName}"))
    }

    // represents the standoff properties defined on the base standoff tag
    val systemStandoffProperties = Set(
        OntologyConstants.KnoraBase.StandoffTagHasStart,
        OntologyConstants.KnoraBase.StandoffTagHasEnd,
        OntologyConstants.KnoraBase.StandoffTagHasStartIndex,
        OntologyConstants.KnoraBase.StandoffTagHasEndIndex,
        OntologyConstants.KnoraBase.StandoffTagHasStartParentIndex,
        OntologyConstants.KnoraBase.StandoffTagHasEndParentIndex,
        OntologyConstants.KnoraBase.StandoffTagHasUUID
    )

    /**
      * Creates standoff from a given XML.
      *
      * @param xml
      * @param userProfile
      * @return a [[CreateStandoffResponseV1]]
      */
    def createStandoff(xml: String, userProfile: UserProfileV1): Future[CreateStandoffResponseV1] = {

        val mappingXMLTags2StandoffTags: Map[String, IRI] = Map(
            "text" -> OntologyConstants.KnoraBase.StandoffRootTag,
            "p" -> OntologyConstants.KnoraBase.StandoffParagraphTag,
            "i" -> OntologyConstants.KnoraBase.StandoffItalicTag,
            "birthday" -> "http://www.knora.org/ontology/knora-base#StandoffBirthdayTag"
        )

        val standoffUtil = new StandoffUtil()

        val textWithStandoff: TextWithStandoff = standoffUtil.xml2TextWithStandoff(xml)

        //println(textWithStandoff.text)

        // get Iris of standoff classes that should be created
        val standoffTagIris = mappingXMLTags2StandoffTags.values.toSet

        for {
            // request information about standoff classes that should be created
            standoffClassEntities: StandoffEntityInfoGetResponseV1 <- (responderManager ? StandoffEntityInfoGetRequestV1(standoffClassIris = standoffTagIris, userProfile = userProfile)).mapTo[StandoffEntityInfoGetResponseV1]

            // get the property Iris that are defined on the standoff classes returned by the ontology responder
            standoffPropertyIris = standoffClassEntities.standoffClassEntityInfoMap.foldLeft(Set.empty[IRI]) {
                case (acc, (standoffClassIri, standoffClassEntity)) =>
                    val props = standoffClassEntity.cardinalities.keySet
                    acc ++ props
            }

            // request information about the standoff properties
            standoffPropertyEntities: StandoffEntityInfoGetResponseV1 <- (responderManager ? StandoffEntityInfoGetRequestV1(standoffPropertyIris = standoffPropertyIris, userProfile = userProfile)).mapTo[StandoffEntityInfoGetResponseV1]

            // loop over the standoff nodes returned by the StandoffUtil and map them to type safe case classes
            standoffNodesToCreate = textWithStandoff.standoff.map {
                case (standoffNodeFromXML: StandoffTag) =>
                    val standoffClassIri: IRI = mappingXMLTags2StandoffTags.getOrElse(standoffNodeFromXML.tagName, throw BadRequestException(s"the standoff class for $standoffNodeFromXML.tagName could not be found in the provided mapping"))

                    // get the cardinalities of the current standoff class
                    val cardinalities: Map[IRI, Cardinality.Value] = standoffClassEntities.standoffClassEntityInfoMap.getOrElse(standoffClassIri, throw NotFoundException(s"information about standoff class $standoffClassIri was not found in ontology")).cardinalities

                    // ignore the system properties since they are provided by StandoffUtil
                    val classSpecificProps: Map[IRI, Cardinality.Value] = cardinalities -- systemStandoffProperties

                    val standoffBaseTagV1: StandoffBaseTagV1 = standoffNodeFromXML match {
                        case hierarchicalStandoffTag: HierarchicalStandoffTag =>
                            StandoffBaseTagV1(
                                name = standoffClassIri,
                                startPosition = hierarchicalStandoffTag.startPosition,
                                endPosition = hierarchicalStandoffTag.endPosition,
                                uuid = hierarchicalStandoffTag.uuid,
                                startIndex = hierarchicalStandoffTag.index,
                                endIndex = None,
                                startParentIndex = hierarchicalStandoffTag.parentIndex,
                                endParentIndex = None,
                                attributes = Seq.empty[StandoffTagAttributeV1]
                            )
                        case freeStandoffTag: FreeStandoffTag =>
                            StandoffBaseTagV1(
                                name = standoffClassIri,
                                startPosition = freeStandoffTag.startPosition,
                                endPosition = freeStandoffTag.endPosition,
                                uuid = freeStandoffTag.uuid,
                                startIndex = freeStandoffTag.startIndex,
                                endIndex = Some(freeStandoffTag.endIndex),
                                startParentIndex = freeStandoffTag.startParentIndex,
                                endParentIndex = freeStandoffTag.endParentIndex,
                                attributes = Seq.empty[StandoffTagAttributeV1]
                            )
                    }

                    // check the data type of the given standoff class
                    val standoffTagV1: StandoffTagV1 = standoffClassEntities.standoffClassEntityInfoMap(standoffClassIri).dataType match {
                        case Some(standoffDateTag: IRI) =>
                            StandoffDateTagV1(
                                name = standoffBaseTagV1.name, // TODO: very ugly, would like to copy from standoffBaseTagV1
                                startPosition = standoffBaseTagV1.startPosition,
                                endPosition = standoffBaseTagV1.endPosition,
                                uuid = standoffBaseTagV1.uuid,
                                startIndex = standoffBaseTagV1.startIndex,
                                endIndex = standoffBaseTagV1.endIndex,
                                startParentIndex = standoffBaseTagV1.startParentIndex,
                                endParentIndex = standoffBaseTagV1.endParentIndex,
                                attributes = standoffBaseTagV1.attributes,
                                valueHasCalendar = KnoraCalendarV1.GREGORIAN, // TODO: get from XML attr
                                valueHasStartJDC = 0, // TODO: get from XML attr
                                valueHasEndJDC = 0, // TODO: get from XML attr
                                valueHasStartPrecision = KnoraPrecisionV1.DAY, // TODO: get from XML attr
                                valueHasEndPrecision = KnoraPrecisionV1.DAY // TODO: get from XML attr
                            )

                        case None => standoffBaseTagV1 // no typed standoff class

                        case unknownDataType => throw InconsistentTriplestoreDataException(s"the triplestore returned the data type $unknownDataType for $standoffClassIri that could be handled")

                    }


                    if (!classSpecificProps.isEmpty) {
                        // additional standoff properties are required

                        /*standoffBaseTagV1.copy(
                            attributes =
                        )*/
                    } else {
                        // only system props required
                        standoffBaseTagV1
                    }


            }

        } yield CreateStandoffResponseV1(userdata = userProfile.userData)

    }

}