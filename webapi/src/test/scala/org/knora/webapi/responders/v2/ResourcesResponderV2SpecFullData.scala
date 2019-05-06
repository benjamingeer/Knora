package org.knora.webapi.responders.v2

import java.time.Instant

import org.knora.webapi.messages.v2.responder.resourcemessages._
import org.knora.webapi.messages.v2.responder.valuemessages._
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.PermissionUtilADM._
import org.knora.webapi.util.StringFormatter
import org.knora.webapi.util.date.{CalendarNameJulian, DatePrecisionYear}
import org.knora.webapi.{InternalSchema, SharedTestDataADM}

class ResourcesResponderV2SpecFullData(implicit stringFormatter: StringFormatter) {

    val expectedReadResourceV2ForZeitgloecklein = ReadResourceV2(
        label = "Zeitgl\u00F6cklein des Lebens und Leidens Christi",
        resourceIri = "http://rdfh.ch/0803/c5058f3a",
        permissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        userPermission = ChangeRightsPermission,
        attachedToUser = "http://rdfh.ch/users/91e19f1e01",
        resourceClassIri = "http://www.knora.org/ontology/0803/incunabula#book".toSmartIri,
        projectADM = SharedTestDataADM.incunabulaProject,
        creationDate = Instant.parse("2016-03-02T15:05:10Z"),
        versionDate = None,
        values = Map(
            "http://www.knora.org/ontology/0803/incunabula#physical_desc".toSmartIri -> Vector(ReadTextValueV2(
                valueContent = TextValueContentV2(
                    ontologySchema = InternalSchema,
                    valueHasLanguage = None,
                    comment = None,
                    maybeValueHasString = Some("Dimension: 8\u00B0")
                ),
                valueHasMaxStandoffStartIndex = None,
                valueIri = "http://rdfh.ch/0803/c5058f3a/values/5524469101",
                permissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:UnknownUser,knora-admin:KnownUser",
                userPermission = ChangeRightsPermission,
                valueCreationDate = Instant.parse("2016-03-02T15:05:10Z"),
                attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                deletionInfo = None
            )),
            "http://www.knora.org/ontology/0803/incunabula#citation".toSmartIri -> Vector(
                ReadTextValueV2(
                    valueContent = TextValueContentV2(
                        ontologySchema = InternalSchema,
                        valueHasLanguage = None,
                        comment = None,
                        maybeValueHasString = Some("Schramm Bd. XXI, S. 27")
                    ),
                    valueHasMaxStandoffStartIndex = None,
                    valueIri = "http://rdfh.ch/0803/c5058f3a/values/184e99ca01",
                    permissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
                    userPermission = ChangeRightsPermission,
                    valueCreationDate = Instant.parse("2016-03-02T15:05:10Z"),
                    attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                    deletionInfo = None
                ),
                ReadTextValueV2(
                    valueContent = TextValueContentV2(
                        ontologySchema = InternalSchema,
                        valueHasLanguage = None,
                        comment = None,
                        maybeValueHasString = Some("GW 4168")
                    ),
                    valueHasMaxStandoffStartIndex = None,
                    valueIri = "http://rdfh.ch/0803/c5058f3a/values/db77ec0302",
                    permissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:UnknownUser,knora-admin:KnownUser",
                    userPermission = ChangeRightsPermission,
                    valueCreationDate = Instant.parse("2016-03-02T15:05:10Z"),
                    attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                    deletionInfo = None
                ),
                ReadTextValueV2(
                    valueContent = TextValueContentV2(
                        ontologySchema = InternalSchema,
                        valueHasLanguage = None,
                        comment = None,
                        maybeValueHasString = Some("ISTC ib00512000")
                    ),
                    valueHasMaxStandoffStartIndex = None,
                    valueIri = "http://rdfh.ch/0803/c5058f3a/values/9ea13f3d02",
                    permissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
                    userPermission = ChangeRightsPermission,
                    valueCreationDate = Instant.parse("2016-03-02T15:05:10Z"),
                    attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                    deletionInfo = None
                )
            ),
            "http://www.knora.org/ontology/0803/incunabula#publisher".toSmartIri -> Vector(ReadTextValueV2(
                valueContent = TextValueContentV2(
                    ontologySchema = InternalSchema,
                    valueHasLanguage = None,
                    comment = None,
                    maybeValueHasString = Some("Johann Amerbach")
                ),
                valueHasMaxStandoffStartIndex = None,
                valueIri = "http://rdfh.ch/0803/c5058f3a/values/497df9ab",
                permissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
                userPermission = ChangeRightsPermission,
                valueCreationDate = Instant.parse("2016-03-02T15:05:10Z"),
                attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                deletionInfo = None
            )),
            "http://www.knora.org/ontology/0803/incunabula#hasAuthor".toSmartIri -> Vector(ReadTextValueV2(
                valueContent = TextValueContentV2(
                    ontologySchema = InternalSchema,
                    valueHasLanguage = None,
                    comment = None,
                    maybeValueHasString = Some("Berthold, der Bruder")
                ),
                valueHasMaxStandoffStartIndex = None,
                valueIri = "http://rdfh.ch/0803/c5058f3a/values/8653a672",
                permissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser",
                userPermission = ChangeRightsPermission,
                valueCreationDate = Instant.parse("2016-03-02T15:05:10Z"),
                attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                deletionInfo = None
            )),
            "http://www.knora.org/ontology/0803/incunabula#url".toSmartIri -> Vector(ReadTextValueV2(
                valueContent = TextValueContentV2(
                    ontologySchema = InternalSchema,
                    valueHasLanguage = None,
                    comment = None,
                    maybeValueHasString = Some("http://www.ub.uni-koeln.de/cdm/compoundobject/collection/inkunabeln/id/1878/rec/1")
                ),
                valueHasMaxStandoffStartIndex = None,
                valueIri = "http://rdfh.ch/0803/c5058f3a/values/10e00c7acc2704",
                permissions = "CR knora-admin:Creator|D knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
                userPermission = ChangeRightsPermission,
                valueCreationDate = Instant.parse("2016-03-02T15:05:10Z"),
                attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                deletionInfo = None
            )),
            "http://www.knora.org/ontology/0803/incunabula#location".toSmartIri -> Vector(ReadTextValueV2(
                valueContent = TextValueContentV2(
                    ontologySchema = InternalSchema,
                    valueHasLanguage = None,
                    comment = None,
                    maybeValueHasString = Some("Universit\u00E4ts- und Stadtbibliothek K\u00F6ln, Sign: AD+S167")
                ),
                valueHasMaxStandoffStartIndex = None,
                valueIri = "http://rdfh.ch/0803/c5058f3a/values/92faf25701",
                permissions = "CR knora-admin:Creator|D knora-admin:ProjectMember|V knora-admin:UnknownUser,knora-admin:KnownUser",
                userPermission = ChangeRightsPermission,
                valueCreationDate = Instant.parse("2016-03-02T15:05:10Z"),
                attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                deletionInfo = None
            )),
            "http://www.knora.org/ontology/0803/incunabula#publoc".toSmartIri -> Vector(ReadTextValueV2(
                valueContent = TextValueContentV2(
                    ontologySchema = InternalSchema,
                    valueHasLanguage = None,
                    comment = None,
                    maybeValueHasString = Some("Basel")
                ),
                valueHasMaxStandoffStartIndex = None,
                valueIri = "http://rdfh.ch/0803/c5058f3a/values/0ca74ce5",
                permissions = "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
                userPermission = ChangeRightsPermission,
                valueCreationDate = Instant.parse("2016-03-02T15:05:10Z"),
                attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                deletionInfo = None
            )),
            "http://www.knora.org/ontology/0803/incunabula#pubdate".toSmartIri -> Vector(ReadOtherValueV2(
                valueContent = DateValueContentV2(
                    valueHasEndJDN = 2266376,
                    valueHasStartJDN = 2266011,
                    ontologySchema = InternalSchema,
                    valueHasStartPrecision = DatePrecisionYear,
                    valueHasCalendar = CalendarNameJulian,
                    comment = None,
                    valueHasEndPrecision = DatePrecisionYear
                ),
                valueIri = "http://rdfh.ch/0803/c5058f3a/values/cfd09f1e01",
                permissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
                userPermission = ChangeRightsPermission,
                valueCreationDate = Instant.parse("2016-03-02T15:05:10Z"),
                attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                deletionInfo = None
            )),
            "http://www.knora.org/ontology/0803/incunabula#title".toSmartIri -> Vector(ReadTextValueV2(
                valueContent = TextValueContentV2(
                    ontologySchema = InternalSchema,
                    valueHasLanguage = None,
                    comment = None,
                    maybeValueHasString = Some("Zeitgl\u00F6cklein des Lebens und Leidens Christi")
                ),
                valueHasMaxStandoffStartIndex = None,
                valueIri = "http://rdfh.ch/0803/c5058f3a/values/c3295339",
                permissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
                userPermission = ChangeRightsPermission,
                valueCreationDate = Instant.parse("2016-03-02T15:05:10Z"),
                attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                deletionInfo = None
            ))
        ),
        lastModificationDate = None,
        deletionInfo = None
    )

    val expectedReadResourceV2ForZeitgloeckleinPreview = ReadResourceV2(
        label = "Zeitgl\u00F6cklein des Lebens und Leidens Christi",
        resourceIri = "http://rdfh.ch/0803/c5058f3a",
        permissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        userPermission = ChangeRightsPermission,
        attachedToUser = "http://rdfh.ch/users/91e19f1e01",
        resourceClassIri = "http://www.knora.org/ontology/0803/incunabula#book".toSmartIri,
        projectADM = SharedTestDataADM.incunabulaProject,
        creationDate = Instant.parse("2016-03-02T15:05:10Z"),
        values = Map(),
        lastModificationDate = None,
        versionDate = None,
        deletionInfo = None
    )

    val expectedReadResourceV2ForReiseInsHeiligeland = ReadResourceV2(
        label = "Reise ins Heilige Land",
        resourceIri = "http://rdfh.ch/0803/2a6221216701",
        permissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        userPermission = ChangeRightsPermission,
        attachedToUser = "http://rdfh.ch/users/91e19f1e01",
        resourceClassIri = "http://www.knora.org/ontology/0803/incunabula#book".toSmartIri,
        projectADM = SharedTestDataADM.incunabulaProject,
        creationDate = Instant.parse("2016-03-02T15:05:21Z"),
        versionDate = None,
        values = Map(
            "http://www.knora.org/ontology/0803/incunabula#physical_desc".toSmartIri -> Vector(ReadTextValueV2(
                valueContent = TextValueContentV2(
                    ontologySchema = InternalSchema,
                    valueHasLanguage = None,
                    comment = None,
                    maybeValueHasString = Some("Extent: 1 Bd.; Dimensions: f\u00B0")
                ),
                valueHasMaxStandoffStartIndex = None,
                valueIri = "http://rdfh.ch/0803/2a6221216701/values/e94fa8a09205",
                permissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
                userPermission = ChangeRightsPermission,
                valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
                attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                deletionInfo = None
            )),
            "http://www.knora.org/ontology/0803/incunabula#citation".toSmartIri -> Vector(
                ReadTextValueV2(
                    valueContent = TextValueContentV2(
                        ontologySchema = InternalSchema,
                        valueHasLanguage = None,
                        comment = None,
                        maybeValueHasString = Some("Van der Haegen I: 9,14")
                    ),
                    valueHasMaxStandoffStartIndex = None,
                    valueIri = "http://rdfh.ch/0803/2a6221216701/values/7b4a9bf89305",
                    permissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
                    userPermission = ChangeRightsPermission,
                    valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
                    attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                    deletionInfo = None
                ),
                ReadTextValueV2(
                    valueContent = TextValueContentV2(
                        ontologySchema = InternalSchema,
                        valueHasLanguage = None,
                        comment = None,
                        maybeValueHasString = Some("Goff M165")
                    ),
                    valueHasMaxStandoffStartIndex = None,
                    valueIri = "http://rdfh.ch/0803/2a6221216701/values/3e74ee319405",
                    permissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
                    userPermission = ChangeRightsPermission,
                    valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
                    attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                    deletionInfo = None
                ),
                ReadTextValueV2(
                    valueContent = TextValueContentV2(
                        ontologySchema = InternalSchema,
                        valueHasLanguage = None,
                        comment = None,
                        maybeValueHasString = Some("C 3833")
                    ),
                    valueHasMaxStandoffStartIndex = None,
                    valueIri = "http://rdfh.ch/0803/2a6221216701/values/019e416b9405",
                    permissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
                    userPermission = ChangeRightsPermission,
                    valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
                    attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                    deletionInfo = None
                ),
                ReadTextValueV2(
                    valueContent = TextValueContentV2(
                        ontologySchema = InternalSchema,
                        valueHasLanguage = None,
                        comment = None,
                        maybeValueHasString = Some("Klebs 651.2")
                    ),
                    valueHasMaxStandoffStartIndex = None,
                    valueIri = "http://rdfh.ch/0803/2a6221216701/values/c4c794a49405",
                    permissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
                    userPermission = ChangeRightsPermission,
                    valueCreationDate = Instant.parse("2016-03-02T15:05:21Z"),
                    attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                    deletionInfo = None
                ),
                ReadTextValueV2(
                    valueContent = TextValueContentV2(
                        ontologySchema = InternalSchema,
                        valueHasLanguage = None,
                        comment = None,
                        maybeValueHasString = Some("Schr 4799")
                    ),
                    valueHasMaxStandoffStartIndex = None,
                    valueIri = "http://rdfh.ch/0803/2a6221216701/values/87f1e7dd9405",
                    permissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
                    userPermission = ChangeRightsPermission,
                    valueCreationDate = Instant.parse("2016-03-02T15:05:21Z"),
                    attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                    deletionInfo = None
                ),
                ReadTextValueV2(
                    valueContent = TextValueContentV2(
                        ontologySchema = InternalSchema,
                        valueHasLanguage = None,
                        comment = None,
                        maybeValueHasString = Some("Schramm XXI p. 9 & 26")
                    ),
                    valueHasMaxStandoffStartIndex = None,
                    valueIri = "http://rdfh.ch/0803/2a6221216701/values/4a1b3b179505",
                    permissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
                    userPermission = ChangeRightsPermission,
                    valueCreationDate = Instant.parse("2016-03-02T15:05:21Z"),
                    attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                    deletionInfo = None
                ),
                ReadTextValueV2(
                    valueContent = TextValueContentV2(
                        ontologySchema = InternalSchema,
                        valueHasLanguage = None,
                        comment = None,
                        maybeValueHasString = Some("FairMur(G) 283")
                    ),
                    valueHasMaxStandoffStartIndex = None,
                    valueIri = "http://rdfh.ch/0803/2a6221216701/values/0d458e509505",
                    permissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
                    userPermission = ChangeRightsPermission,
                    valueCreationDate = Instant.parse("2016-03-02T15:05:21Z"),
                    attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                    deletionInfo = None
                ),
                ReadTextValueV2(
                    valueContent = TextValueContentV2(
                        ontologySchema = InternalSchema,
                        valueHasLanguage = None,
                        comment = None,
                        maybeValueHasString = Some("IBP 3556")
                    ),
                    valueHasMaxStandoffStartIndex = None,
                    valueIri = "http://rdfh.ch/0803/2a6221216701/values/d06ee1899505",
                    permissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
                    userPermission = ChangeRightsPermission,
                    valueCreationDate = Instant.parse("2016-03-02T15:05:21Z"),
                    attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                    deletionInfo = None
                ),
                ReadTextValueV2(
                    valueContent = TextValueContentV2(
                        ontologySchema = InternalSchema,
                        valueHasLanguage = None,
                        comment = None,
                        maybeValueHasString = Some("Borm 1751")
                    ),
                    valueHasMaxStandoffStartIndex = None,
                    valueIri = "http://rdfh.ch/0803/2a6221216701/values/939834c39505",
                    permissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
                    userPermission = ChangeRightsPermission,
                    valueCreationDate = Instant.parse("2016-03-02T15:05:21Z"),
                    attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                    deletionInfo = None
                )
            ),
            "http://www.knora.org/ontology/0803/incunabula#publisher".toSmartIri -> Vector(ReadTextValueV2(
                valueContent = TextValueContentV2(
                    ontologySchema = InternalSchema,
                    valueHasLanguage = None,
                    comment = None,
                    maybeValueHasString = Some("Bernhard Richel")
                ),
                valueHasMaxStandoffStartIndex = None,
                valueIri = "http://rdfh.ch/0803/2a6221216701/values/dda85bbb9105",
                permissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
                userPermission = ChangeRightsPermission,
                valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
                attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                deletionInfo = None
            )),
            "http://www.knora.org/ontology/0803/incunabula#hasAuthor".toSmartIri -> Vector(ReadTextValueV2(
                valueContent = TextValueContentV2(
                    ontologySchema = InternalSchema,
                    valueHasLanguage = None,
                    comment = None,
                    maybeValueHasString = Some("Jean Mandeville")
                ),
                valueHasMaxStandoffStartIndex = None,
                valueIri = "http://rdfh.ch/0803/2a6221216701/values/1a7f08829105",
                permissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser",
                userPermission = ChangeRightsPermission,
                valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
                attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                deletionInfo = None
            )),
            "http://www.knora.org/ontology/0803/incunabula#book_comment".toSmartIri -> Vector(ReadTextValueV2(
                valueContent = TextValueContentV2(
                    ontologySchema = InternalSchema,
                    valueHasLanguage = None,
                    comment = None,
                    maybeValueHasString = Some("Katalogaufnahme anhand ISTC und v.d.Haegen")
                ),
                valueHasMaxStandoffStartIndex = None,
                valueIri = "http://rdfh.ch/0803/2a6221216701/values/56c287fc9505",
                permissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
                userPermission = ChangeRightsPermission,
                valueCreationDate = Instant.parse("2016-03-02T15:05:21Z"),
                attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                deletionInfo = None
            )),
            "http://www.knora.org/ontology/0803/incunabula#url".toSmartIri -> Vector(ReadTextValueV2(
                valueContent = TextValueContentV2(
                    ontologySchema = InternalSchema,
                    valueHasLanguage = None,
                    comment = None,
                    maybeValueHasString = Some("http://aleph.unibas.ch/F/?local_base=DSV01&con_lng=GER&func=find-b&find_code=SYS&request=002610320")
                ),
                valueHasMaxStandoffStartIndex = None,
                valueIri = "http://rdfh.ch/0803/2a6221216701/values/f89173afca2704",
                permissions = "CR knora-admin:Creator|D knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
                userPermission = ChangeRightsPermission,
                valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
                attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                deletionInfo = None
            )),
            "http://www.knora.org/ontology/0803/incunabula#note".toSmartIri -> Vector(
                ReadTextValueV2(
                    valueContent = TextValueContentV2(
                        ontologySchema = InternalSchema,
                        valueHasLanguage = None,
                        comment = None,
                        maybeValueHasString = Some("deutsch von Otto von Diemeringen")
                    ),
                    valueHasMaxStandoffStartIndex = None,
                    valueIri = "http://rdfh.ch/0803/2a6221216701/values/ac79fbd99205",
                    permissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
                    userPermission = ChangeRightsPermission,
                    valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
                    attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                    deletionInfo = None
                ),
                ReadTextValueV2(
                    valueContent = TextValueContentV2(
                        ontologySchema = InternalSchema,
                        valueHasLanguage = None,
                        comment = None,
                        maybeValueHasString = Some("Rubr. mit Init. J zu Beginn")
                    ),
                    valueHasMaxStandoffStartIndex = None,
                    valueIri = "http://rdfh.ch/0803/2a6221216701/values/6fa34e139305",
                    permissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
                    userPermission = ChangeRightsPermission,
                    valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
                    attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                    deletionInfo = None
                ),
                ReadTextValueV2(
                    valueContent = TextValueContentV2(
                        ontologySchema = InternalSchema,
                        valueHasLanguage = None,
                        comment = None,
                        maybeValueHasString = Some("Holzschnitte nicht koloriert")
                    ),
                    valueHasMaxStandoffStartIndex = None,
                    valueIri = "http://rdfh.ch/0803/2a6221216701/values/32cda14c9305",
                    permissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
                    userPermission = ChangeRightsPermission,
                    valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
                    attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                    deletionInfo = None
                ),
                ReadTextValueV2(
                    valueContent = TextValueContentV2(
                        ontologySchema = InternalSchema,
                        valueHasLanguage = None,
                        comment = None,
                        maybeValueHasString = Some("Besitzervermerke: Kartause, H. Zscheckenb\u00FCrlin")
                    ),
                    valueHasMaxStandoffStartIndex = None,
                    valueIri = "http://rdfh.ch/0803/2a6221216701/values/f5f6f4859305",
                    permissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
                    userPermission = ChangeRightsPermission,
                    valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
                    attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                    deletionInfo = None
                ),
                ReadTextValueV2(
                    valueContent = TextValueContentV2(
                        ontologySchema = InternalSchema,
                        valueHasLanguage = None,
                        comment = None,
                        maybeValueHasString = Some("Zusammengebunden mit: Die zehen Gebote ; Was und wie man beten soll und Auslegung des hlg. Pater nosters / Hans von Warmont. Strassburg, 1516")
                    ),
                    valueHasMaxStandoffStartIndex = None,
                    valueIri = "http://rdfh.ch/0803/2a6221216701/values/b82048bf9305",
                    permissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
                    userPermission = ChangeRightsPermission,
                    valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
                    attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                    deletionInfo = None
                )
            ),
            "http://www.knora.org/ontology/0803/incunabula#location".toSmartIri -> Vector(ReadTextValueV2(
                valueContent = TextValueContentV2(
                    ontologySchema = InternalSchema,
                    valueHasLanguage = None,
                    comment = None,
                    maybeValueHasString = Some("Basel UB, Sign: Aleph D III 13:1")
                ),
                valueHasMaxStandoffStartIndex = None,
                valueIri = "http://rdfh.ch/0803/2a6221216701/values/262655679205",
                permissions = "CR knora-admin:Creator|D knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
                userPermission = ChangeRightsPermission,
                valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
                attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                deletionInfo = None
            )),
            "http://www.knora.org/ontology/0803/incunabula#publoc".toSmartIri -> Vector(ReadTextValueV2(
                valueContent = TextValueContentV2(
                    ontologySchema = InternalSchema,
                    valueHasLanguage = None,
                    comment = None,
                    maybeValueHasString = Some("Basel")
                ),
                valueHasMaxStandoffStartIndex = None,
                valueIri = "http://rdfh.ch/0803/2a6221216701/values/a0d2aef49105",
                permissions = "CR knora-admin:Creator|V knora-admin:ProjectMember,knora-admin:KnownUser,knora-admin:UnknownUser",
                userPermission = ChangeRightsPermission,
                valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
                attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                deletionInfo = None
            )),
            "http://www.knora.org/ontology/0803/incunabula#pubdate".toSmartIri -> Vector(ReadOtherValueV2(
                valueContent = DateValueContentV2(
                    valueHasEndJDN = 2262358,
                    valueHasStartJDN = 2261994,
                    ontologySchema = InternalSchema,
                    valueHasStartPrecision = DatePrecisionYear,
                    valueHasCalendar = CalendarNameJulian,
                    comment = None,
                    valueHasEndPrecision = DatePrecisionYear
                ),
                valueIri = "http://rdfh.ch/0803/2a6221216701/values/63fc012e9205",
                permissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
                userPermission = ChangeRightsPermission,
                valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
                attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                deletionInfo = None
            )),
            "http://www.knora.org/ontology/0803/incunabula#title".toSmartIri -> Vector(
                ReadTextValueV2(
                    valueContent = TextValueContentV2(
                        ontologySchema = InternalSchema,
                        valueHasLanguage = None,
                        comment = None,
                        maybeValueHasString = Some("Reise ins Heilige Land")
                    ),
                    valueHasMaxStandoffStartIndex = None,
                    valueIri = "http://rdfh.ch/0803/2a6221216701/values/d1010fd69005",
                    permissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
                    userPermission = ChangeRightsPermission,
                    valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
                    attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                    deletionInfo = None
                ),
                ReadTextValueV2(
                    valueContent = TextValueContentV2(
                        ontologySchema = InternalSchema,
                        valueHasLanguage = None,
                        comment = None,
                        maybeValueHasString = Some("Reysen und wanderschafften durch das Gelobte Land")
                    ),
                    valueHasMaxStandoffStartIndex = None,
                    valueIri = "http://rdfh.ch/0803/2a6221216701/values/942b620f9105",
                    permissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
                    userPermission = ChangeRightsPermission,
                    valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
                    attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                    deletionInfo = None
                ),
                ReadTextValueV2(
                    valueContent = TextValueContentV2(
                        ontologySchema = InternalSchema,
                        valueHasLanguage = None,
                        comment = None,
                        maybeValueHasString = Some("Itinerarius")
                    ),
                    valueHasMaxStandoffStartIndex = None,
                    valueIri = "http://rdfh.ch/0803/2a6221216701/values/5755b5489105",
                    permissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
                    userPermission = ChangeRightsPermission,
                    valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
                    attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                    deletionInfo = None
                )
            )
        ),
        lastModificationDate = None,
        deletionInfo = None
    )

    val expectedReadResourceV2ForReiseInsHeiligelandPreview = ReadResourceV2(
        resourceClassIri = "http://www.knora.org/ontology/0803/incunabula#book".toSmartIri,
        label = "Reise ins Heilige Land",
        creationDate = Instant.parse("2016-03-02T15:05:21Z"),
        resourceIri = "http://rdfh.ch/0803/2a6221216701",
        permissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        userPermission = ChangeRightsPermission,
        attachedToUser = "http://rdfh.ch/users/91e19f1e01",
        projectADM = SharedTestDataADM.incunabulaProject,
        values = Map(),
        lastModificationDate = None,
        versionDate = None,
        deletionInfo = None
    )

    val expectedFullResourceResponseForZeitgloecklein = ReadResourcesSequenceV2(
        resources = Vector(expectedReadResourceV2ForZeitgloecklein),
        numberOfResources = 1
    )

    val expectedPreviewResourceResponseForZeitgloecklein = ReadResourcesSequenceV2(
        resources = Vector(expectedReadResourceV2ForZeitgloeckleinPreview),
        numberOfResources = 1
    )

    val expectedFullResourceResponseForReise = ReadResourcesSequenceV2(
        resources = Vector(expectedReadResourceV2ForReiseInsHeiligeland),
        numberOfResources = 1
    )

    val expectedFullResourceResponseForThingWithHistory = ReadResourcesSequenceV2(
        resources = Vector(
            ReadResourceV2(
                versionDate = Some(Instant.parse("2019-02-12T08:05:10Z")),
                label = "A thing with version history",
                resourceIri = "http://rdfh.ch/0001/thing-with-history",
                permissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:UnknownUser",
                userPermission = ModifyPermission,
                attachedToUser = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q",
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
                creationDate = Instant.parse("2019-02-08T15:05:10Z"),
                values = Map(
                    "http://www.knora.org/ontology/0001/anything#hasText".toSmartIri -> Vector(ReadTextValueV2(
                        valueContent = TextValueContentV2(
                            valueHasLanguage = None,
                            ontologySchema = InternalSchema,
                            comment = None,
                            maybeValueHasString = Some("two")
                        ),
                        valueHasMaxStandoffStartIndex = None,
                        valueIri = "http://rdfh.ch/0001/thing-with-history/values/2b",
                        permissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:UnknownUser",
                        userPermission = ChangeRightsPermission,
                        valueCreationDate = Instant.parse("2019-02-11T10:05:10Z"),
                        attachedToUser = "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ",
                        deletionInfo = None
                    )),
                    "http://www.knora.org/ontology/0001/anything#hasOtherThingValue".toSmartIri -> Vector(ReadLinkValueV2(
                        valueContent = LinkValueContentV2(
                            referredResourceIri = "http://rdfh.ch/0001/2qMtTWvVRXWMBcRNlduvCQ",
                            ontologySchema = InternalSchema,
                            comment = None,
                            nestedResource = None
                        ),
                        valueHasRefCount = 1,
                        valueIri = "http://rdfh.ch/0001/thing-with-history/values/3a",
                        permissions = "V knora-admin:UnknownUser|M knora-admin:ProjectMember",
                        userPermission = ModifyPermission,
                        valueCreationDate = Instant.parse("2019-02-10T10:30:10Z"),
                        attachedToUser = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q",
                        previousValueIri = None,
                        deletionInfo = None
                    )),
                    "http://www.knora.org/ontology/0001/anything#hasInteger".toSmartIri -> Vector(ReadOtherValueV2(
                        valueContent = IntegerValueContentV2(
                            ontologySchema = InternalSchema,
                            valueHasInteger = 1,
                            comment = None
                        ),
                        valueIri = "http://rdfh.ch/0001/thing-with-history/values/1a",
                        permissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:UnknownUser",
                        userPermission = ChangeRightsPermission,
                        valueCreationDate = Instant.parse("2019-02-11T09:05:10Z"),
                        attachedToUser = "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ",
                        deletionInfo = None
                    ))
                ),
                projectADM = SharedTestDataADM.anythingProject,
                lastModificationDate = Some(Instant.parse("2019-02-13T09:05:10Z")),
                deletionInfo = None
            )
        ),
        numberOfResources = 1
    )

    val expectedCompleteVersionHistoryResponse = ResourceVersionHistoryResponseV2(history = Vector(
        ResourceHistoryEntry(
            versionDate = Instant.parse("2019-02-13T09:05:10Z"),
            author = "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ"
        ),
        ResourceHistoryEntry(
            versionDate = Instant.parse("2019-02-13T09:00:10Z"),
            author = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q"
        ),
        ResourceHistoryEntry(
            versionDate = Instant.parse("2019-02-12T10:05:10Z"),
            author = "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ"
        ),
        ResourceHistoryEntry(
            versionDate = Instant.parse("2019-02-12T09:05:10Z"),
            author = "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ"
        ),
        ResourceHistoryEntry(
            versionDate = Instant.parse("2019-02-11T10:05:10Z"),
            author = "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ"
        ),
        ResourceHistoryEntry(
            versionDate = Instant.parse("2019-02-11T09:05:10Z"),
            author = "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ"
        ),
        ResourceHistoryEntry(
            versionDate = Instant.parse("2019-02-10T10:30:10Z"),
            author = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q"
        ),
        ResourceHistoryEntry(
            versionDate = Instant.parse("2019-02-10T10:05:10Z"),
            author = "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ"
        ),
        ResourceHistoryEntry(
            versionDate = Instant.parse("2019-02-08T15:05:10Z"),
            author = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q"
        )
    ))

    val expectedPartialVersionHistoryResponse = ResourceVersionHistoryResponseV2(history = Vector(
        ResourceHistoryEntry(
            versionDate = Instant.parse("2019-02-13T09:00:10Z"),
            author = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q"
        ),
        ResourceHistoryEntry(
            versionDate = Instant.parse("2019-02-12T10:05:10Z"),
            author = "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ"
        ),
        ResourceHistoryEntry(
            versionDate = Instant.parse("2019-02-12T09:05:10Z"),
            author = "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ"
        ),
        ResourceHistoryEntry(
            versionDate = Instant.parse("2019-02-11T10:05:10Z"),
            author = "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ"
        ),
        ResourceHistoryEntry(
            versionDate = Instant.parse("2019-02-11T09:05:10Z"),
            author = "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ"
        ),
        ResourceHistoryEntry(
            versionDate = Instant.parse("2019-02-10T10:30:10Z"),
            author = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q"
        ),
        ResourceHistoryEntry(
            versionDate = Instant.parse("2019-02-10T10:05:10Z"),
            author = "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ"
        )
    ))

    val expectedFullResourceResponseForZeitgloeckleinAndReise = ReadResourcesSequenceV2(
        resources = Vector(expectedReadResourceV2ForZeitgloecklein, expectedReadResourceV2ForReiseInsHeiligeland),
        numberOfResources = 2
    )

    val expectedPreviewResourceResponseForZeitgloeckleinAndReise = ReadResourcesSequenceV2(
        resources = Vector(expectedReadResourceV2ForZeitgloeckleinPreview, expectedReadResourceV2ForReiseInsHeiligelandPreview),
        numberOfResources = 2
    )

    val expectedFullResourceResponseForReiseAndZeitgloeckleinInversedOrder = ReadResourcesSequenceV2(
        resources = Vector(expectedReadResourceV2ForReiseInsHeiligeland, expectedReadResourceV2ForZeitgloecklein),
        numberOfResources = 2
    )

}