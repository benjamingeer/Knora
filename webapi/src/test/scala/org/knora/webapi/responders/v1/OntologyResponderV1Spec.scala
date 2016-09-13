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


import akka.actor.Props
import akka.testkit._
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.ontologymessages._
import org.knora.webapi.messages.v1.responder.usermessages.{UserDataV1, UserProfileV1}
import org.knora.webapi.messages.v1.store.triplestoremessages.{RdfDataObject, ResetTriplestoreContent, ResetTriplestoreContentACK}
import org.knora.webapi.responders._
import org.knora.webapi.store._
import org.knora.webapi.util.MessageUtil

import scala.concurrent.duration._

/**
  * Static data for testing [[OntologyResponderV1]].
  */
object OntologyResponderV1Spec {

    // A test user that prefers responses in German.
    private val userProfileWithGerman = UserProfileV1(
        projects = Vector("http://data.knora.org/projects/77275339"),
        groups = Nil,
        userData = UserDataV1(
            email = Some("test@test.ch"),
            lastname = Some("Test"),
            firstname = Some("User"),
            username = Some("testuser"),
            token = None,
            user_id = Some("http://data.knora.org/users/b83acc5f05"),
            lang = "de"
        )
    )

    // A test user that prefers responses in French.
    private val userProfileWithFrench = userProfileWithGerman.copy(userData = userProfileWithGerman.userData.copy(lang = "fr"))

    // A test user that prefers responses in English.
    private val userProfileWithEnglish = userProfileWithGerman.copy(userData = userProfileWithGerman.userData.copy(lang = "en"))

}


/**
  * Tests [[OntologyResponderV1]].
  */
class OntologyResponderV1Spec extends CoreSpec() with ImplicitSender {

    // Construct the actors needed for this test.
    private val actorUnderTest = TestActorRef[OntologyResponderV1]
    private val responderManager = system.actorOf(Props(new ResponderManagerV1 with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    val rdfDataObjects = List(
        RdfDataObject(path = "../knora-ontologies/knora-base.ttl", name = "http://www.knora.org/ontology/knora-base"),
        RdfDataObject(path = "../knora-ontologies/knora-dc.ttl", name = "http://www.knora.org/ontology/dc"),
        RdfDataObject(path = "../knora-ontologies/salsah-gui.ttl", name = "http://www.knora.org/ontology/salsah-gui"),
        RdfDataObject(path = "_test_data/ontologies/incunabula-onto.ttl", name = "http://www.knora.org/ontology/incunabula")
    )

    // The default timeout for receiving reply messages from actors.
    private val timeout = 10.seconds

    private val page = ResourceTypeResponseV1(
        userdata = UserDataV1(
            password = None,
            email = Some("test@test.ch"),
            lastname = Some("Test"),
            firstname = Some("User"),
            username = Some("testuser"),
            token = None,
            user_id = Some("http://data.knora.org/users/b83acc5f05"),
            lang = "de"
        ),
        restype_info = ResTypeInfoV1(
            properties = Set(
                PropertyDefinitionV1(
                    gui_name = Some("searchbox"),
                    attributes = Some("numprops=1;restypeid=http://www.knora.org/ontology/incunabula#Sideband"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#LinkValue",
                    occurrence = "0-1",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Verweis auf einen Randleistentyp"),
                    label = Some("Randleistentyp links"),
                    name = "http://www.knora.org/ontology/incunabula#hasLeftSideband",
                    id = "http://www.knora.org/ontology/incunabula#hasLeftSideband"
                ),
                PropertyDefinitionV1(
                    gui_name = Some("richtext"),
                    attributes = None,
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "0-1",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Beschreibung"),
                    label = Some("Beschreibung (Richtext)"),
                    name = "http://www.knora.org/ontology/incunabula#description",
                    id = "http://www.knora.org/ontology/incunabula#description"
                ),
                PropertyDefinitionV1(
                    gui_name = Some("textarea"),
                    attributes = Some("wrap=soft;width=95%;rows=7"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "0-n",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Unstrukturierte Bemerkungen zu einem Objekt"),
                    label = Some("Kommentar"),
                    name = "http://www.knora.org/ontology/incunabula#page_comment",
                    id = "http://www.knora.org/ontology/incunabula#page_comment"
                ),
                PropertyDefinitionV1(
                    gui_name = Some("searchbox"),
                    attributes = Some("restypeid=http://www.knora.org/ontology/incunabula#book"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#LinkValue",
                    occurrence = "1",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Diese Property bezeichnet eine Verbindung zu einer anderen Resource, in dem ausgesagt wird, dass die vorliegende Resource ein integraler Teil der anderen Resource ist. Zum Beispiel ist eine Buchseite ein integraler Bestandteil genau eines Buches."),
                    label = Some("ist ein Teil von"),
                    name = "http://www.knora.org/ontology/incunabula#partOf",
                    id = "http://www.knora.org/ontology/incunabula#partOf"
                ),
                PropertyDefinitionV1(
                    gui_name = Some("spinbox"),
                    attributes = Some("min=0;max=-1"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#IntValue",
                    occurrence = "0-1",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Diese Property bezeichnet die Position in einer geordneten Reihenfolge"),
                    label = Some("Sequenznummer"),
                    name = "http://www.knora.org/ontology/incunabula#seqnum",
                    id = "http://www.knora.org/ontology/incunabula#seqnum"
                ),
                PropertyDefinitionV1(
                    gui_name = Some("text"),
                    attributes = Some("size=54;maxlength=128"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "1",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Der originale Dateiname"),
                    label = Some("Urspr\u00FCnglicher Dateiname"),
                    name = "http://www.knora.org/ontology/incunabula#origname",
                    id = "http://www.knora.org/ontology/incunabula#origname"
                ),
                PropertyDefinitionV1(
                    gui_name = Some("text"),
                    attributes = Some("min=4;max=8"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "0-1",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Eine eindeutige numerische Bezeichnung einer Buchseite"),
                    label = Some("Seitenbezeichnung"),
                    name = "http://www.knora.org/ontology/incunabula#pagenum",
                    id = "http://www.knora.org/ontology/incunabula#pagenum"
                ),
                PropertyDefinitionV1(
                    gui_name = Some("searchbox"),
                    attributes = Some("numprops=1;restypeid=http://www.knora.org/ontology/incunabula#Sideband"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#LinkValue",
                    occurrence = "0-1",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Verweis auf einen Randleistentyp"),
                    label = Some("Randleistentyp rechts"),
                    name = "http://www.knora.org/ontology/incunabula#hasRightSideband",
                    id = "http://www.knora.org/ontology/incunabula#hasRightSideband"
                ),
                PropertyDefinitionV1(
                    gui_name = Some("textarea"),
                    attributes = Some("cols=60;wrap=soft;rows=3"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "0-n",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Stellt einen Verweis dar."),
                    label = Some("Verweis"),
                    name = "http://www.knora.org/ontology/incunabula#citation",
                    id = "http://www.knora.org/ontology/incunabula#citation"
                ),
                PropertyDefinitionV1(
                    gui_name = Some("pulldown"),
                    attributes = Some("hlist=<http://data.knora.org/lists/4b6d86ce03>"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "0-n",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Transkription"),
                    label = Some("Transkription"),
                    name = "http://www.knora.org/ontology/incunabula#transcription",
                    id = "http://www.knora.org/ontology/incunabula#transcription"
                ),
                PropertyDefinitionV1(
                    gui_name = Some("fileupload"),
                    attributes = None,
                    valuetype_id = "http://www.knora.org/ontology/knora-base#StillImageFileValue",
                    occurrence = "1-n",
                    vocabulary = "http://www.knora.org/ontology/knora-base",
                    description = Some("Connects a Representation to an image file"),
                    label = Some("hat Bilddatei"),
                    name = "http://www.knora.org/ontology/knora-base#hasStillImageFileValue",
                    id = "http://www.knora.org/ontology/knora-base#hasStillImageFileValue"
                ),
                PropertyDefinitionV1(
                    gui_name = None,
                    attributes = Some("restypeid=http://www.knora.org/ontology/knora-base#Resource"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#LinkValue",
                    occurrence = "0-n",
                    vocabulary = "http://www.knora.org/ontology/knora-base",
                    description = None,
                    label = Some("hat Standoff Link zu"),
                    name = "http://www.knora.org/ontology/knora-base#hasStandoffLinkTo",
                    id = "http://www.knora.org/ontology/knora-base#hasStandoffLinkTo"
                )
            ),
            iconsrc = Some("page.gif"),
            description = Some("Eine Seite ist ein Teil eines Buchs"),
            label = Some("Seite"),
            name = "http://www.knora.org/ontology/incunabula#page"
        )
    )

    private val book = ResourceTypeResponseV1(
        userdata = OntologyResponderV1Spec.userProfileWithGerman.userData,
        restype_info = ResTypeInfoV1(
            properties = Set(
                PropertyDefinitionV1(
                    gui_name = Some("textarea"),
                    attributes = Some("wrap=soft;width=95%;rows=7"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "0-n",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Unstrukturierte Bemerkungen zu einem Objekt"),
                    label = Some("Kommentar"),
                    name = "http://www.knora.org/ontology/incunabula#book_comment",
                    id = "http://www.knora.org/ontology/incunabula#book_comment"
                ),
                PropertyDefinitionV1(
                    gui_name = Some("textarea"),
                    attributes = Some("cols=60;rows=4;wrap=soft"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "0-1",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Der Ort wo sich das physische Original befindet"),
                    label = Some("Standort"),
                    name = "http://www.knora.org/ontology/incunabula#location",
                    id = "http://www.knora.org/ontology/incunabula#location"
                ),
                PropertyDefinitionV1(
                    gui_name = Some("richtext"),
                    attributes = None,
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "0-1",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Beschreibung"),
                    label = Some("Beschreibung (Richtext)"),
                    name = "http://www.knora.org/ontology/incunabula#description",
                    id = "http://www.knora.org/ontology/incunabula#description"
                ),
                PropertyDefinitionV1(
                    gui_name = Some("textarea"),
                    attributes = Some("cols=60;wrap=soft;rows=3"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "0-n",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Eine Anmerkung zum Objekt"),
                    label = Some("Anmerkung"),
                    name = "http://www.knora.org/ontology/incunabula#note",
                    id = "http://www.knora.org/ontology/incunabula#note"
                ),
                PropertyDefinitionV1(
                    gui_name = Some("textarea"),
                    attributes = Some("cols=60;wrap=soft;rows=3"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "0-1",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Generelle physische Beschreibung des Objektes wie Material, Gr\u00F6sse etc."),
                    label = Some("Physische Beschreibung"),
                    name = "http://www.knora.org/ontology/incunabula#physical_desc",
                    id = "http://www.knora.org/ontology/incunabula#physical_desc"
                ),
                PropertyDefinitionV1(
                    gui_name = Some("text"),
                    attributes = Some("maxlength=255;size=60"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "0-n",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Erzeuger/Autor"),
                    label = Some("Creator"),
                    name = "http://www.knora.org/ontology/incunabula#hasAuthor",
                    id = "http://www.knora.org/ontology/incunabula#hasAuthor"
                ),
                PropertyDefinitionV1(
                    gui_name = Some("text"),
                    attributes = Some("size=60;maxlength=200"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "0-1",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Uniform Resource Identifier"),
                    label = Some("URI"),
                    name = "http://www.knora.org/ontology/incunabula#url",
                    id = "http://www.knora.org/ontology/incunabula#url"
                ),
                PropertyDefinitionV1(
                    gui_name = Some("date"),
                    attributes = Some("size=16;maxlength=32"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#DateValue",
                    occurrence = "0-1",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Datum der Herausgabe"),
                    label = Some("Datum der Herausgabe"),
                    name = "http://www.knora.org/ontology/incunabula#pubdate",
                    id = "http://www.knora.org/ontology/incunabula#pubdate"
                ),
                PropertyDefinitionV1(
                    gui_name = Some("text"),
                    attributes = Some("size=80;maxlength=255"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "1-n",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Titel"),
                    label = Some("Titel"),
                    name = "http://www.knora.org/ontology/incunabula#title",
                    id = "http://www.knora.org/ontology/incunabula#title"
                ),
                PropertyDefinitionV1(
                    gui_name = Some("textarea"),
                    attributes = Some("cols=60;wrap=soft;rows=3"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "0-n",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Stellt einen Verweis dar."),
                    label = Some("Verweis"),
                    name = "http://www.knora.org/ontology/incunabula#citation",
                    id = "http://www.knora.org/ontology/incunabula#citation"
                ),
                PropertyDefinitionV1(
                    gui_name = Some("text"),
                    attributes = Some("maxlength=255;size=60"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "0-n",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Ein Verlag ist ein Medienunternehmen, das Werke der Literatur, Kunst, Musik oder Wissenschaft vervielf\u00E4ltigt und verbreitet. Der Verkauf kann \u00FCber den Handel (Kunst-, Buchhandel etc.) oder durch den Verlag selbst erfolgen. Das Wort \u201Everlegen\u201C bedeutet im Mittelhochdeutschen \u201EGeld ausgeben\u201C oder \u201Eetwas auf seine Rechnung nehmen\u201C. (Wikipedia http://de.wikipedia.org/wiki/Verlag)"),
                    label = Some("Verleger"),
                    name = "http://www.knora.org/ontology/incunabula#publisher",
                    id = "http://www.knora.org/ontology/incunabula#publisher"
                ),
                PropertyDefinitionV1(
                    gui_name = Some("text"),
                    attributes = Some("size=60;maxlength=100"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "0-1",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Ort der Herausgabe"),
                    label = Some("Ort der Herausgabe"),
                    name = "http://www.knora.org/ontology/incunabula#publoc",
                    id = "http://www.knora.org/ontology/incunabula#publoc"
                ),
                PropertyDefinitionV1(
                    gui_name = None,
                    attributes = Some("restypeid=http://www.knora.org/ontology/knora-base#Resource"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#LinkValue",
                    occurrence = "0-n",
                    vocabulary = "http://www.knora.org/ontology/knora-base",
                    description = None,
                    label = Some("hat Standoff Link zu"),
                    name = "http://www.knora.org/ontology/knora-base#hasStandoffLinkTo",
                    id = "http://www.knora.org/ontology/knora-base#hasStandoffLinkTo"
                )
            ),
            iconsrc = Some("book.gif"),
            description = Some("Diese Resource-Klasse beschreibt ein Buch"),
            label = Some("Buch"),
            name = "http://www.knora.org/ontology/incunabula#book"
        )
    )

    private val region = ResourceTypeResponseV1(
        userdata = OntologyResponderV1Spec.userProfileWithGerman.userData,
        restype_info = ResTypeInfoV1(
            properties = Set(
                PropertyDefinitionV1(
                    gui_name = Some("richtext"),
                    attributes = None,
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "1-n",
                    vocabulary = "http://www.knora.org/ontology/knora-base",
                    description = Some("Represents a comment"),
                    label = Some("Kommentar"),
                    name = "http://www.knora.org/ontology/knora-base#hasComment",
                    id = "http://www.knora.org/ontology/knora-base#hasComment"
                ),
                PropertyDefinitionV1(
                    gui_name = Some("colorpicker"),
                    attributes = Some("ncolors=8"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#ColorValue",
                    occurrence = "1",
                    vocabulary = "http://www.knora.org/ontology/knora-base",
                    description = Some("Represents a color."),
                    label = Some("Farbe"),
                    name = "http://www.knora.org/ontology/knora-base#hasColor",
                    id = "http://www.knora.org/ontology/knora-base#hasColor"
                ),
                PropertyDefinitionV1(
                    gui_name = Some("geometry"),
                    attributes = Some("width=95%;rows=4;wrap=soft"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#GeomValue",
                    occurrence = "1-n",
                    vocabulary = "http://www.knora.org/ontology/knora-base",
                    description = Some("Represents a geometrical shape."),
                    label = Some("Geometrie"),
                    name = "http://www.knora.org/ontology/knora-base#hasGeometry",
                    id = "http://www.knora.org/ontology/knora-base#hasGeometry"
                ),
                PropertyDefinitionV1(
                    gui_name = None,
                    attributes = Some("restypeid=http://www.knora.org/ontology/knora-base#Representation"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#LinkValue",
                    occurrence = "1",
                    vocabulary = "http://www.knora.org/ontology/knora-base",
                    description = Some("Region of interest within a digital object (e.g. an image)"),
                    label = Some("is Region von"),
                    name = "http://www.knora.org/ontology/knora-base#isRegionOf",
                    id = "http://www.knora.org/ontology/knora-base#isRegionOf"
                ),
                PropertyDefinitionV1(
                    gui_name = None,
                    attributes = Some("restypeid=http://www.knora.org/ontology/knora-base#Resource"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#LinkValue",
                    occurrence = "0-n",
                    vocabulary = "http://www.knora.org/ontology/knora-base",
                    description = None,
                    label = Some("hat Standoff Link zu"),
                    name = "http://www.knora.org/ontology/knora-base#hasStandoffLinkTo",
                    id = "http://www.knora.org/ontology/knora-base#hasStandoffLinkTo"
                )
            ),
            iconsrc = Some("region.gif"),
            description = Some("This Resource represents a geometric region of a resource. The geometry is represented currently as JSON string."),
            label = Some("Region"),
            name = "http://www.knora.org/ontology/knora-base#Region"
        )
    )

    private val linkObject = ResourceTypeResponseV1(
        userdata = UserDataV1(
            password = None,
            email = Some("test@test.ch"),
            lastname = Some("test"),
            firstname = Some("User"),
            username = Some("testuser"),
            token = None,
            user_id = Some("http://data.knora.org/users/b83acc5f05"),
            lang = "de"
        ),
        restype_info = ResTypeInfoV1(
            properties = Set(
                PropertyDefinitionV1(
                    gui_name = None,
                    attributes = Some("restypeid=http://www.knora.org/ontology/knora-base#Resource"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#LinkValue",
                    occurrence = "1-n",
                    vocabulary = "http://www.knora.org/ontology/knora-base",
                    description = Some("Represents a direct connection between two resources"),
                    label = Some("hat Link zu"),
                    name = "http://www.knora.org/ontology/knora-base#hasLinkTo",
                    id = "http://www.knora.org/ontology/knora-base#hasLinkTo"
                ),
                PropertyDefinitionV1(
                    gui_name = Some("richtext"),
                    attributes = None,
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "0-n",
                    vocabulary = "http://www.knora.org/ontology/knora-base",
                    description = Some("Represents a comment"),
                    label = Some("Kommentar"),
                    name = "http://www.knora.org/ontology/knora-base#hasComment",
                    id = "http://www.knora.org/ontology/knora-base#hasComment"
                ),
                PropertyDefinitionV1(
                    gui_name = None,
                    attributes = Some("restypeid=http://www.knora.org/ontology/knora-base#Resource"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#LinkValue",
                    occurrence = "0-n",
                    vocabulary = "http://www.knora.org/ontology/knora-base",
                    description = Some("Represents a direct connection between two resources"),
                    label = Some("hat Standoff Link zu"),
                    name = "http://www.knora.org/ontology/knora-base#hasStandoffLinkTo",
                    id = "http://www.knora.org/ontology/knora-base#hasStandoffLinkTo"
                )
            ),
            iconsrc = Some("link.gif"),
            description = Some("Verkn\u00FCpfung mehrerer Resourcen (Systemobject)"),
            label = Some("Verkn\u00FCpfungsobjekt"),
            name = "http://www.knora.org/ontology/knora-base#LinkObj"
        )
    )

    private def checkResourceTypeResponseV1(received: ResourceTypeResponseV1, expected: ResourceTypeResponseV1): Unit = {
        val sortedReceivedProperties = received.restype_info.properties.toList.sortBy(_.id)
        val sortedExpectedProperties: Seq[PropertyDefinitionV1] = expected.restype_info.properties.toList.sortBy(_.id)

        assert(sortedReceivedProperties.size == sortedExpectedProperties.size,
            s"\n********** received these properties:\n${MessageUtil.toSource(sortedReceivedProperties)}\n********** expected these properties:\n${MessageUtil.toSource(sortedExpectedProperties)}")

        sortedReceivedProperties.zip(sortedExpectedProperties).foreach {
            case (receivedProp: PropertyDefinitionV1, expectedProp: PropertyDefinitionV1) =>
                assert(receivedProp == receivedProp, s"These props do not match:\n*** Received:\n${MessageUtil.toSource(receivedProp)}\n*** Expected:\n${MessageUtil.toSource(expectedProp)}")
        }
    }

    private val resourceTypesForNamedGraphIncunabula = ResourceTypesForNamedGraphResponseV1(
        userdata = UserDataV1(
            projects_info = Nil,
            projects = None,
            active_project = None,
            password = None,
            email = Some("test@test.ch"),
            lastname = Some("Test"),
            firstname = Some("User"),
            username = Some("testuser"),
            token = None,
            user_id = Some("http://data.knora.org/users/b83acc5f05"),
            lang = "en"
        ),
        resourcetypes = Vector(
            ResourceTypeV1(
                properties = Vector(
                    PropertyTypeV1(
                        label = "Publication location",
                        id = "http://www.knora.org/ontology/incunabula#publoc"
                    ),
                    PropertyTypeV1(
                        label = "has Standoff Link to",
                        id = "http://www.knora.org/ontology/knora-base#hasStandoffLinkTo"
                    ),
                    PropertyTypeV1(
                        label = "Creator",
                        id = "http://www.knora.org/ontology/incunabula#hasAuthor"
                    ),
                    PropertyTypeV1(
                        label = "Location",
                        id = "http://www.knora.org/ontology/incunabula#location"
                    ),
                    PropertyTypeV1(
                        label = "Datum der Herausgabe",
                        id = "http://www.knora.org/ontology/incunabula#pubdate"
                    ),
                    PropertyTypeV1(
                        label = "Phyiscal description",
                        id = "http://www.knora.org/ontology/incunabula#physical_desc"
                    ),
                    PropertyTypeV1(
                        label = "Comment",
                        id = "http://www.knora.org/ontology/incunabula#book_comment"
                    ),
                    PropertyTypeV1(
                        label = "Note",
                        id = "http://www.knora.org/ontology/incunabula#note"
                    ),
                    PropertyTypeV1(
                        label = "URI",
                        id = "http://www.knora.org/ontology/incunabula#url"
                    ),
                    PropertyTypeV1(
                        label = "Citation/reference",
                        id = "http://www.knora.org/ontology/incunabula#citation"
                    ),
                    PropertyTypeV1(
                        label = "Publisher",
                        id = "http://www.knora.org/ontology/incunabula#publisher"
                    ),
                    PropertyTypeV1(
                        label = "Title",
                        id = "http://www.knora.org/ontology/incunabula#title"
                    ),
                    PropertyTypeV1(
                        label = "Beschreibung (Richtext)",
                        id = "http://www.knora.org/ontology/incunabula#description"
                    )
                ),
                label = "Book",
                id = "http://www.knora.org/ontology/incunabula#book"
            ),
            ResourceTypeV1(
                properties = Vector(
                    PropertyTypeV1(
                        label = "Randleistentyp rechts",
                        id = "http://www.knora.org/ontology/incunabula#hasRightSideband"
                    ),
                    PropertyTypeV1(
                        label = "Comment",
                        id = "http://www.knora.org/ontology/incunabula#page_comment"
                    ),
                    PropertyTypeV1(
                        label = "has Standoff Link to",
                        id = "http://www.knora.org/ontology/knora-base#hasStandoffLinkTo"
                    ),
                    PropertyTypeV1(
                        label = "Urspr\u00FCnglicher Dateiname",
                        id = "http://www.knora.org/ontology/incunabula#origname"
                    ),
                    PropertyTypeV1(
                        label = "Randleistentyp links",
                        id = "http://www.knora.org/ontology/incunabula#hasLeftSideband"
                    ),
                    PropertyTypeV1(
                        label = "Transkription",
                        id = "http://www.knora.org/ontology/incunabula#transcription"
                    ),
                    PropertyTypeV1(
                        label = "Page identifier",
                        id = "http://www.knora.org/ontology/incunabula#pagenum"
                    ),
                    PropertyTypeV1(
                        "http://www.knora.org/ontology/knora-base#hasStillImageFileValue",
                        "has image file"),
                    PropertyTypeV1(
                        label = "Citation/reference",
                        id = "http://www.knora.org/ontology/incunabula#citation"
                    ),
                    PropertyTypeV1(
                        label = "is a part of",
                        id = "http://www.knora.org/ontology/incunabula#partOf"
                    ),
                    PropertyTypeV1(
                        label = "Sequence number",
                        id = "http://www.knora.org/ontology/incunabula#seqnum"
                    ),
                    PropertyTypeV1(
                        label = "Beschreibung (Richtext)",
                        id = "http://www.knora.org/ontology/incunabula#description"
                    )
                ),
                label = "Page",
                id = "http://www.knora.org/ontology/incunabula#page"
            ),
            ResourceTypeV1(
                properties = Vector(
                    PropertyTypeV1(
                        label = "has Standoff Link to",
                        id = "http://www.knora.org/ontology/knora-base#hasStandoffLinkTo"
                    ),
                    PropertyTypeV1(
                        label = "Kommentar (Richtext)",
                        id = "http://www.knora.org/ontology/incunabula#sideband_comment"
                    ),
                    PropertyTypeV1(
                        label = "Title",
                        id = "http://www.knora.org/ontology/incunabula#sbTitle"
                    ),
                    PropertyTypeV1(
                        "http://www.knora.org/ontology/knora-base#hasStillImageFileValue",
                        "has image file"),
                    PropertyTypeV1(
                        label = "Beschreibung (Richtext)",
                        id = "http://www.knora.org/ontology/incunabula#description"
                    )
                ),
                label = "Randleiste",
                id = "http://www.knora.org/ontology/incunabula#Sideband"
            ),
            ResourceTypeV1(
                properties = Vector(
                    PropertyTypeV1(
                        label = "Farbe",
                        id = "http://www.knora.org/ontology/incunabula#miscHasColor"
                    ),
                    PropertyTypeV1(
                        label = "has Standoff Link to",
                        id = "http://www.knora.org/ontology/knora-base#hasStandoffLinkTo"
                    ),
                    PropertyTypeV1(
                        label = "Geometrie",
                        id = "http://www.knora.org/ontology/incunabula#miscHasGeometry"
                    )
                ),
                label = "Sonstiges",
                id = "http://www.knora.org/ontology/incunabula#misc"
            )
        )
    )

    private val vocabulariesResponseV1 = NamedGraphsResponseV1(
        vocabularies = Vector(
            NamedGraphV1(
                "http://www.knora.org/ontology/knora-base",
                "Knora-Base",
                "Knora-Base",
                "Knora-Base",
                "http://data.knora.org/projects/knora-base",
                "http://www.knora.org/ontology/knora-base",
                active = false),
            NamedGraphV1(
                "http://www.knora.org/ontology/incunabula",
                "Incunabula",
                "Incunabula",
                "Incunabula",
                "http://data.knora.org/projects/77275339",
                "http://www.knora.org/ontology/incunabula",
                active = false),
            NamedGraphV1(
                "http://www.knora.org/ontology/beol",
                "Bernoulli-Euler Online",
                "Bernoulli-Euler Online",
                "Bernoulli-Euler Online",
                "http://data.knora.org/projects/yTerZGyxjZVqFMNNKXCDPF",
                "http://www.knora.org/ontology/beol",
                active = false),
            NamedGraphV1(
                "http://www.knora.org/ontology/images",
                "Images Test Project",
                "Images Test Project",
                "Images Test Project",
                "http://data.knora.org/projects/images",
                "http://www.knora.org/ontology/images",
                active = false),
            NamedGraphV1(
                "http://www.knora.org/ontology/anything",
                "Anything Test Project",
                "Anything Test Project",
                "Anything Test Project",
                "http://data.knora.org/projects/anything",
                "http://www.knora.org/ontology/anything",
                active = false
            )
        ),
        userdata = UserDataV1(
            password = None,
            email = Some("test@test.ch"),
            lastname = Some("test"),
            firstname = Some("User"),
            username = Some("testuser"),
            token = None,
            user_id = Some("http://data.knora.org/users/b83acc5f05"),
            lang = "en")
    )

    private val propertyTypesForNamedGraphIncunabula = PropertyTypesForNamedGraphResponseV1(
        userdata = UserDataV1(
            projects_info = Nil,
            projects = None,
            active_project = None,
            password = None,
            email = Some("test@test.ch"),
            lastname = Some("Test"),
            firstname = Some("User"),
            username = Some("testuser"),
            token = None,
            user_id = Some("http://data.knora.org/users/b83acc5f05"),
            lang = "en"
        ),
        properties = Vector(
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("text"),
                attributes = Some("maxlength=255;size=60"),
                valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = Some("Erzeuger/Autor"),
                label = Some("Creator"),
                name = "http://www.knora.org/ontology/incunabula#hasAuthor",
                id = "http://www.knora.org/ontology/incunabula#hasAuthor"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("geometry"),
                attributes = None,
                valuetype_id = "http://www.knora.org/ontology/knora-base#GeomValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = None,
                label = Some("Geometrie"),
                name = "http://www.knora.org/ontology/incunabula#miscHasGeometry",
                id = "http://www.knora.org/ontology/incunabula#miscHasGeometry"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("text"),
                attributes = Some("size=80;maxlength=255"),
                valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = Some("Titel"),
                label = Some("Title"),
                name = "http://www.knora.org/ontology/incunabula#title",
                id = "http://www.knora.org/ontology/incunabula#title"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("textarea"),
                attributes = Some("cols=60;wrap=soft;rows=3"),
                valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = Some("Stellt einen Verweis dar."),
                label = Some("Citation/reference"),
                name = "http://www.knora.org/ontology/incunabula#citation",
                id = "http://www.knora.org/ontology/incunabula#citation"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("richtext"),
                attributes = None,
                valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = Some("Unstrukturierte Bemerkungen zu einem Objekt"),
                label = Some("Kommentar (Richtext)"),
                name = "http://www.knora.org/ontology/incunabula#sideband_comment",
                id = "http://www.knora.org/ontology/incunabula#sideband_comment"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("searchbox"),
                attributes = Some("restypeid=http://www.knora.org/ontology/incunabula#book"),
                valuetype_id = "http://www.knora.org/ontology/knora-base#LinkValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = Some("Diese Property bezeichnet eine Verbindung zu einer anderen Resource, in dem ausgesagt wird, dass die vorliegende Resource ein integraler Teil der anderen Resource ist. Zum Beispiel ist eine Buchseite ein integraler Bestandteil genau eines Buches."),
                label = Some("is a part of"),
                name = "http://www.knora.org/ontology/incunabula#partOf",
                id = "http://www.knora.org/ontology/incunabula#partOf"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("textarea"),
                attributes = Some("cols=60;rows=4;wrap=soft"),
                valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = Some("Der Ort wo sich das physische Original befindet"),
                label = Some("Location"),
                name = "http://www.knora.org/ontology/incunabula#location",
                id = "http://www.knora.org/ontology/incunabula#location"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("colorpicker"),
                attributes = None,
                valuetype_id = "http://www.knora.org/ontology/knora-base#ColorValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = None,
                label = Some("Farbe"),
                name = "http://www.knora.org/ontology/incunabula#miscHasColor",
                id = "http://www.knora.org/ontology/incunabula#miscHasColor"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("text"),
                attributes = Some("size=80;maxlength=255"),
                valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = None,
                label = Some("Title"),
                name = "http://www.knora.org/ontology/incunabula#sbTitle",
                id = "http://www.knora.org/ontology/incunabula#sbTitle"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("text"),
                attributes = Some("min=4;max=8"),
                valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = Some("A distinct identification of a book page"),
                label = Some("Page identifier"),
                name = "http://www.knora.org/ontology/incunabula#pagenum",
                id = "http://www.knora.org/ontology/incunabula#pagenum"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("pulldown"),
                attributes = Some("hlist=<http://data.knora.org/lists/4b6d86ce03>"),
                valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = Some("Transkription"),
                label = Some("Transkription"),
                name = "http://www.knora.org/ontology/incunabula#transcription",
                id = "http://www.knora.org/ontology/incunabula#transcription"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("text"),
                attributes = Some("size=60;maxlength=200"),
                valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = Some("Uniform Resource Identifier"),
                label = Some("URI"),
                name = "http://www.knora.org/ontology/incunabula#url",
                id = "http://www.knora.org/ontology/incunabula#url"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("searchbox"),
                attributes = Some("restypeid=http://www.knora.org/ontology/incunabula#book"),
                valuetype_id = "http://www.knora.org/ontology/knora-base#LinkValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = None,
                label = Some("Verbindung mit einem Buch"),
                name = "http://www.knora.org/ontology/incunabula#miscHasBook",
                id = "http://www.knora.org/ontology/incunabula#miscHasBook"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("searchbox"),
                attributes = Some("numprops=1;restypeid=http://www.knora.org/ontology/incunabula#Sideband"),
                valuetype_id = "http://www.knora.org/ontology/knora-base#LinkValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = Some("Verweis auf einen Randleistentyp"),
                label = Some("Randleistentyp rechts"),
                name = "http://www.knora.org/ontology/incunabula#hasRightSideband",
                id = "http://www.knora.org/ontology/incunabula#hasRightSideband"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("textarea"),
                attributes = Some("cols=60;wrap=soft;rows=3"),
                valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = Some("Generelle physische Beschreibung des Objektes wie Material, Gr\u00F6sse etc."),
                label = Some("Phyiscal description"),
                name = "http://www.knora.org/ontology/incunabula#physical_desc",
                id = "http://www.knora.org/ontology/incunabula#physical_desc"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("searchbox"),
                attributes = Some("numprops=1;restypeid=http://www.knora.org/ontology/incunabula#Sideband"),
                valuetype_id = "http://www.knora.org/ontology/knora-base#LinkValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = Some("Verweis auf einen Randleistentyp"),
                label = Some("Randleistentyp links"),
                name = "http://www.knora.org/ontology/incunabula#hasLeftSideband",
                id = "http://www.knora.org/ontology/incunabula#hasLeftSideband"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("textarea"),
                attributes = Some("wrap=soft;width=95%;rows=7"),
                valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = Some("Unstrukturierte Bemerkungen zu einem Objekt"),
                label = Some("Comment"),
                name = "http://www.knora.org/ontology/incunabula#book_comment",
                id = "http://www.knora.org/ontology/incunabula#book_comment"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("richtext"),
                attributes = None,
                valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = Some("Beschreibung"),
                label = Some("Beschreibung (Richtext)"),
                name = "http://www.knora.org/ontology/incunabula#description",
                id = "http://www.knora.org/ontology/incunabula#description"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("textarea"),
                attributes = Some("wrap=soft;width=95%;rows=7"),
                valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = Some("Unstrukturierte Bemerkungen zu einem Objekt"),
                label = Some("Comment"),
                name = "http://www.knora.org/ontology/incunabula#page_comment",
                id = "http://www.knora.org/ontology/incunabula#page_comment"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("text"),
                attributes = Some("size=60;maxlength=100"),
                valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = Some("Ort der Herausgabe"),
                label = Some("Publication location"),
                name = "http://www.knora.org/ontology/incunabula#publoc",
                id = "http://www.knora.org/ontology/incunabula#publoc"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("date"),
                attributes = Some("size=16;maxlength=32"),
                valuetype_id = "http://www.knora.org/ontology/knora-base#DateValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = Some("Datum der Herausgabe"),
                label = Some("Datum der Herausgabe"),
                name = "http://www.knora.org/ontology/incunabula#pubdate",
                id = "http://www.knora.org/ontology/incunabula#pubdate"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("text"),
                attributes = Some("size=54;maxlength=128"),
                valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = Some("Der originale Dateiname"),
                label = Some("Urspr\u00FCnglicher Dateiname"),
                name = "http://www.knora.org/ontology/incunabula#origname",
                id = "http://www.knora.org/ontology/incunabula#origname"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("text"),
                attributes = Some("maxlength=255;size=60"),
                valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = Some("Publishing is the process of production and dissemination of literature or information \u2013 the activity of making information available for public view. In some cases authors may be their own publishers, meaning: originators and developers of content also provide media to deliver and display the content. (Wikipedia http://en.wikipedia.org/wiki/Publisher)"),
                label = Some("Publisher"),
                name = "http://www.knora.org/ontology/incunabula#publisher",
                id = "http://www.knora.org/ontology/incunabula#publisher"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("spinbox"),
                attributes = Some("min=0;max=-1"),
                valuetype_id = "http://www.knora.org/ontology/knora-base#IntValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = Some("This property stands for the position within a set of rdered items (resoucres)"),
                label = Some("Sequence number"),
                name = "http://www.knora.org/ontology/incunabula#seqnum",
                id = "http://www.knora.org/ontology/incunabula#seqnum"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("textarea"),
                attributes = Some("cols=60;wrap=soft;rows=3"),
                valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = Some("A note concerning the object"),
                label = Some("Note"),
                name = "http://www.knora.org/ontology/incunabula#note",
                id = "http://www.knora.org/ontology/incunabula#note"
            )
        )
    )

    private def checkVocabularies(received: NamedGraphsResponseV1, expected: NamedGraphsResponseV1) = {

        assert(received.vocabularies.size == expected.vocabularies.size, "Vocabularies' sizes did not match.")

        received.vocabularies.sortBy(_.uri).zip(expected.vocabularies.sortBy(_.uri)).foreach {
            case (receivedVoc, expectedVoc) =>
                assert(receivedVoc.uri == expectedVoc.uri, "IRIs of vocabularies did not match")
                assert(receivedVoc.longname == expectedVoc.longname, "Names of vocabularies did not match")
        }
    }

    private def checkResourceTypesForNamedGraphResponseV1(received: ResourceTypesForNamedGraphResponseV1, expected: ResourceTypesForNamedGraphResponseV1) = {
        assert(received.resourcetypes.size == expected.resourcetypes.size, s"${expected.resourcetypes.size} were expected, but ${received.resourcetypes.size} given.")

        received.resourcetypes.sortBy(_.id).zip(expected.resourcetypes.sortBy(_.id)).foreach {
            case (receivedResType, expectedResType) =>
                assert(receivedResType.id == expectedResType.id, s"IRIs of restypes did not match.")
                assert(receivedResType.label == expectedResType.label, s"Labels of restypes did not match.")

                receivedResType.properties.sortBy(_.id).zip(expectedResType.properties.sortBy(_.id)).foreach {
                    case (receivedProp, expectedProp) =>
                        assert(receivedProp.id == expectedProp.id, "IRIs of properties did not match.")
                        assert(receivedProp.label == expectedProp.label, "Labels of properties did not match.")
                }
        }

    }

    private def checkPropertyTypesForNamedGraphIncunabula(received: PropertyTypesForNamedGraphResponseV1, expected: PropertyTypesForNamedGraphResponseV1) = {
        assert(received.properties.size == expected.properties.size, s"Sizes of properties did not match")

        val sortedReceivedProperties = received.properties.sortBy(_.id)
        val sortedExpectedProperties = expected.properties.sortBy(_.id)

        sortedReceivedProperties.zip(sortedExpectedProperties).foreach {
            case (receivedProp, expectedProp) =>
                assert(receivedProp.id == expectedProp.id, "The properties' IRIs did not match.")
                assert(receivedProp.valuetype_id == expectedProp.valuetype_id, "The properties' valuetypes did not match.")
                assert(receivedProp.attributes == expectedProp.attributes, "The properties' attributes did not match.")
        }

    }


    "Load test data" in {
        storeManager ! ResetTriplestoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTriplestoreContentACK())

        responderManager ! LoadOntologiesRequest(OntologyResponderV1Spec.userProfileWithGerman)
        expectMsg(10.seconds, LoadOntologiesResponse())
    }

    "The ontology responder" should {
        "return the ontology information for a incunabula:page" in {
            // http://localhost:3333/v1/resourcetypes/http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23page
            actorUnderTest ! ResourceTypeGetRequestV1(
                userProfile = OntologyResponderV1Spec.userProfileWithGerman,
                resourceTypeIri = "http://www.knora.org/ontology/incunabula#page"
            )

            expectMsgPF(timeout) {
                case msg: ResourceTypeResponseV1 => checkResourceTypeResponseV1(received = msg, expected = page)
            }
        }

        "return the ontology information for a incunabula:book" in {
            // http://localhost:3333/v1/resourcetypes/http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book
            actorUnderTest ! ResourceTypeGetRequestV1(
                userProfile = OntologyResponderV1Spec.userProfileWithGerman,
                resourceTypeIri = "http://www.knora.org/ontology/incunabula#book"
            )

            expectMsgPF(timeout) {
                case msg: ResourceTypeResponseV1 => checkResourceTypeResponseV1(received = msg, expected = book)
            }
        }

        "return the ontology information for a knora-base:Region" in {
            // http://localhost:3333/v1/resourcetypes/http%3A%2F%2Fwww.knora.org%2Fontology%2Fknora-base%23Region
            actorUnderTest ! ResourceTypeGetRequestV1(
                userProfile = OntologyResponderV1Spec.userProfileWithGerman,
                resourceTypeIri = "http://www.knora.org/ontology/knora-base#Region"
            )

            expectMsgPF(timeout) {
                case msg: ResourceTypeResponseV1 => checkResourceTypeResponseV1(received = msg, expected = region)
            }
        }

        "return the ontology information for a knora-base:LinkObj" in {
            // http://localhost:3333/v1/resourcetypes/http%3A%2F%2Fwww.knora.org%2Fontology%2Fknora-base%23LinkObj
            actorUnderTest ! ResourceTypeGetRequestV1(
                userProfile = OntologyResponderV1Spec.userProfileWithGerman,
                resourceTypeIri = "http://www.knora.org/ontology/knora-base#LinkObj"
            )

            expectMsgPF(timeout) {
                case msg: ResourceTypeResponseV1 => checkResourceTypeResponseV1(received = msg, expected = linkObject)
            }
        }

        "return labels in the user's preferred language" in {
            actorUnderTest ! EntityInfoGetRequestV1(
                propertyIris = Set("http://www.knora.org/ontology/incunabula#title"),
                userProfile = OntologyResponderV1Spec.userProfileWithGerman // irrelevant
            )

            expectMsgPF(timeout) {
                case msg: EntityInfoGetResponseV1 =>
                    msg.propertyEntityInfoMap("http://www.knora.org/ontology/incunabula#title").getPredicateObject(predicateIri = OntologyConstants.Rdfs.Label, preferredLangs = Some(("de", "en"))) should ===(Some("Titel"))
                    msg.propertyEntityInfoMap("http://www.knora.org/ontology/incunabula#title").getPredicateObject(predicateIri = OntologyConstants.Rdfs.Label, preferredLangs = Some(("fr", "en"))) should ===(Some("Titre"))
            }
        }

        "get all the vocabularies" in {
            actorUnderTest ! NamedGraphsGetRequestV1(
                userProfile = OntologyResponderV1Spec.userProfileWithEnglish
            )

            expectMsgPF(timeout) {
                case msg: NamedGraphsResponseV1 =>
                    checkVocabularies(received = msg, expected = vocabulariesResponseV1)

            }

        }

        "get all the resource classes with their property types for incunabula named graph" in {
            actorUnderTest ! ResourceTypesForNamedGraphGetRequestV1(
                namedGraph = Some("http://www.knora.org/ontology/incunabula"),
                userProfile = OntologyResponderV1Spec.userProfileWithEnglish
            )

            expectMsgPF(timeout) {
                case msg: ResourceTypesForNamedGraphResponseV1 =>
                    checkResourceTypesForNamedGraphResponseV1(received = msg, expected = resourceTypesForNamedGraphIncunabula)
            }

        }

        "get all the properties for the named graph incunabula" in {
            actorUnderTest ! PropertyTypesForNamedGraphGetRequestV1(
                namedGraph = Some("http://www.knora.org/ontology/incunabula"),
                userProfile = OntologyResponderV1Spec.userProfileWithEnglish
            )

            expectMsgPF(timeout) {
                case msg: PropertyTypesForNamedGraphResponseV1 =>
                    checkPropertyTypesForNamedGraphIncunabula(received = msg, expected = propertyTypesForNamedGraphIncunabula)
            }
        }
    }
}
