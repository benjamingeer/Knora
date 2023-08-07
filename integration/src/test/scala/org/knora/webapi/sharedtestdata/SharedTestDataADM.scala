/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.sharedtestdata

import java.time.Instant

import dsp.constants.SalsahGui
import dsp.valueobjects.V2
import org.knora.webapi.IRI
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.util.KnoraSystemInstances

/**
 * This object holds the same user which are loaded with 'test_data/project_data/admin-data.ttl'. Using this object
 * in tests, allows easier updating of details as they change over time.
 */
object SharedTestDataADM {

  /**
   * **********************************
   */
  /** System Admin Data                * */
  /**
   * **********************************
   */
  val systemProjectIri: IRI = OntologyConstants.KnoraAdmin.SystemProject // built-in project

  val testPass: String = java.net.URLEncoder.encode("test", "utf-8")

  /* represents the user profile of 'root' as found in admin-data.ttl */
  def rootUser: UserADM =
    UserADM(
      id = "http://rdfh.ch/users/root",
      username = "root",
      email = "root@example.com",
      givenName = "System",
      familyName = "Administrator",
      status = true,
      lang = "de",
      password = Option("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"),
      token = None,
      groups = Seq.empty[GroupADM],
      projects = Seq.empty[ProjectADM],
      permissions = PermissionsDataADM(
        groupsPerProject = Map(
          systemProjectIri -> List(OntologyConstants.KnoraAdmin.SystemAdmin)
        ),
        administrativePermissionsPerProject = Map.empty[IRI, Set[PermissionADM]]
      )
    )

  /* represents the user profile of 'superuser' as found in admin-data.ttl */
  def superUser: UserADM =
    UserADM(
      id = "http://rdfh.ch/users/superuser",
      username = "superuser",
      email = "super.user@example.com",
      givenName = "Super",
      familyName = "User",
      status = true,
      lang = "de",
      password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"),
      token = None,
      groups = Seq.empty[GroupADM],
      projects = Seq.empty[ProjectADM],
      permissions = PermissionsDataADM(
        groupsPerProject = Map(
          systemProjectIri -> List(OntologyConstants.KnoraAdmin.SystemAdmin)
        )
      )
    )

  /* represents the user profile of 'superuser' as found in admin-data.ttl */
  def normalUser: UserADM =
    UserADM(
      id = "http://rdfh.ch/users/normaluser",
      username = "normaluser",
      email = "normal.user@example.com",
      givenName = "Normal",
      familyName = "User",
      status = true,
      lang = "de",
      password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"),
      token = None,
      groups = Seq.empty[GroupADM],
      projects = Seq.empty[ProjectADM],
      permissions = PermissionsDataADM()
    )

  /* represents the user profile of 'inactive user' as found in admin-data.ttl */
  def inactiveUser: UserADM =
    UserADM(
      id = "http://rdfh.ch/users/inactiveuser",
      username = "inactiveuser",
      email = "inactive.user@example.com",
      givenName = "Inactive",
      familyName = "User",
      status = false,
      lang = "de",
      password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"),
      token = None,
      groups = Seq.empty[GroupADM],
      projects = Seq.empty[ProjectADM],
      permissions = PermissionsDataADM()
    )

  /* represents an anonymous user */
  def anonymousUser: UserADM = KnoraSystemInstances.Users.AnonymousUser

  /* represents the 'multiuser' as found in admin-data.ttl */
  def multiuserUser: UserADM =
    UserADM(
      id = "http://rdfh.ch/users/multiuser",
      username = "multiuser",
      email = "multi.user@example.com",
      givenName = "Multi",
      familyName = "User",
      status = true,
      lang = "de",
      password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"),
      token = None,
      groups = Seq(imagesReviewerGroup),
      projects = Seq(incunabulaProject, imagesProject),
      permissions = PermissionsDataADM(
        groupsPerProject = Map(
          incunabulaProjectIri -> List(
            OntologyConstants.KnoraAdmin.ProjectMember,
            OntologyConstants.KnoraAdmin.ProjectAdmin
          ),
          imagesProjectIri -> List(
            "http://rdfh.ch/groups/00FF/images-reviewer",
            OntologyConstants.KnoraAdmin.ProjectMember,
            OntologyConstants.KnoraAdmin.ProjectAdmin
          )
        ),
        administrativePermissionsPerProject = Map(
          incunabulaProjectIri -> Set(
            PermissionADM.ProjectAdminAllPermission,
            PermissionADM.ProjectResourceCreateAllPermission
          ),
          imagesProjectIri -> Set(
            PermissionADM.ProjectAdminAllPermission,
            PermissionADM.ProjectResourceCreateAllPermission
          )
        )
      )
    )

  /* represents the full ProjectADM of the Knora System project */
  def systemProject: ProjectADM = ProjectADM(
    id = OntologyConstants.KnoraAdmin.SystemProject,
    shortname = "SystemProject",
    shortcode = "FFFF",
    longname = Some("Knora System Project"),
    description = Seq(V2.StringLiteralV2(value = "Knora System Project", language = Some("en"))),
    keywords = Seq.empty[String],
    logo = None,
    ontologies = Seq(
      OntologyConstants.KnoraBase.KnoraBaseOntologyIri,
      OntologyConstants.KnoraAdmin.KnoraAdminOntologyIri,
      SalsahGui.SalsahGuiOntologyIri,
      OntologyConstants.Standoff.StandoffOntologyIri
    ),
    status = true,
    selfjoin = false
  )

  /* represents the full ProjectADM of the default shared ontologies project */
  def defaultSharedOntologiesProject: ProjectADM = ProjectADM(
    id = OntologyConstants.KnoraAdmin.DefaultSharedOntologiesProject,
    shortname = "DefaultSharedOntologiesProject",
    shortcode = "0000",
    longname = Some("Default Knora Shared Ontologies Project"),
    description = Seq(V2.StringLiteralV2(value = "Default Knora Shared Ontologies Project", language = Some("en"))),
    keywords = Seq.empty[String],
    logo = None,
    ontologies = Seq.empty[IRI],
    status = true,
    selfjoin = false
  )

  /**
   * **********************************
   */
  /** Images Demo Project Admin Data  * */
  /**
   * **********************************
   */
  val imagesProjectIri = "http://rdfh.ch/projects/00FF"

  /* represents 'user01' as found in admin-data.ttl  */
  def imagesUser01: UserADM =
    UserADM(
      id = "http://rdfh.ch/users/c266a56709",
      username = "user01.user1",
      email = "user01.user1@example.com",
      givenName = "User01",
      familyName = "User",
      status = true,
      lang = "de",
      password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"),
      token = None,
      groups = Seq.empty[GroupADM],
      projects = Seq(imagesProject),
      permissions = PermissionsDataADM(
        groupsPerProject = Map(
          imagesProjectIri -> List(
            OntologyConstants.KnoraAdmin.ProjectMember,
            OntologyConstants.KnoraAdmin.ProjectAdmin
          )
        ),
        administrativePermissionsPerProject = Map(
          imagesProjectIri -> Set(
            PermissionADM.ProjectAdminAllPermission,
            PermissionADM.ProjectResourceCreateAllPermission
          )
        )
      )
    )

  /* represents 'user02' as found in admin-data.ttl  */
  def imagesUser02: UserADM =
    UserADM(
      id = "http://rdfh.ch/users/97cec4000f",
      username = "user02.user",
      email = "user02.user@example.com",
      givenName = "User02",
      familyName = "User",
      status = true,
      lang = "de",
      password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"),
      token = None,
      groups = Seq.empty[GroupADM],
      projects = Seq(imagesProject),
      permissions = PermissionsDataADM(
        groupsPerProject = Map(
          imagesProjectIri -> List(OntologyConstants.KnoraAdmin.ProjectMember)
        ),
        administrativePermissionsPerProject = Map(
          imagesProjectIri -> Set(
            PermissionADM.ProjectResourceCreateAllPermission
          )
        )
      )
    )

  /* represents 'images-reviewer-user' as found in admin-data.ttl  */
  def imagesReviewerUser: UserADM =
    UserADM(
      id = "http://rdfh.ch/users/images-reviewer-user",
      username = "images-reviewer-user",
      email = "images-reviewer-user@example.com",
      givenName = "User03",
      familyName = "User",
      status = true,
      lang = "de",
      password = Some("$2a$10$fTEr/xVjPq7UBAy1O6KWKOM1scLhKGeRQdR4GTA997QPqHzXv0MnW"),
      token = None,
      groups = Seq(imagesReviewerGroup),
      projects = Seq(imagesProject),
      permissions = PermissionsDataADM(
        groupsPerProject = Map(
          imagesProjectIri -> List(
            "http://rdfh.ch/groups/00FF/images-reviewer",
            OntologyConstants.KnoraAdmin.ProjectMember
          )
        ),
        administrativePermissionsPerProject = Map(
          imagesProjectIri -> Set(
            PermissionADM.projectResourceCreateRestrictedPermission(
              s"${SharedOntologyTestDataADM.IMAGES_ONTOLOGY_IRI}#bild"
            ),
            PermissionADM.projectResourceCreateRestrictedPermission(
              s"${SharedOntologyTestDataADM.IMAGES_ONTOLOGY_IRI}#bildformat"
            )
          )
        )
      )
    )

  /* represents the full ProjectADM of the images project */
  def imagesProject: ProjectADM = ProjectADM(
    id = imagesProjectIri,
    shortname = "images",
    shortcode = "00FF",
    longname = Some("Image Collection Demo"),
    description = Seq(V2.StringLiteralV2(value = "A demo project of a collection of images", language = Some("en"))),
    keywords = Seq("images", "collection").sorted,
    logo = None,
    ontologies = Seq(SharedOntologyTestDataADM.IMAGES_ONTOLOGY_IRI),
    status = true,
    selfjoin = false
  )

  /* represents the full ProjectADM of the images project in the external format */
  def imagesProjectExternal: ProjectADM = ProjectADM(
    id = imagesProjectIri,
    shortname = "images",
    shortcode = "00FF",
    longname = Some("Image Collection Demo"),
    description = Seq(V2.StringLiteralV2(value = "A demo project of a collection of images", language = Some("en"))),
    keywords = Seq("images", "collection").sorted,
    logo = None,
    ontologies = Seq(SharedOntologyTestDataADM.IMAGES_ONTOLOGY_IRI_LocalHost),
    status = true,
    selfjoin = false
  )

  /* represents the full GroupADM of the images ProjectAdmin group */
  def imagesProjectAdminGroup: GroupADM = GroupADM(
    id = "-",
    name = "ProjectAdmin",
    descriptions = Seq(StringLiteralV2(value = "Default Project Admin Group", language = Some("en"))),
    project = imagesProject,
    status = true,
    selfjoin = false
  )

  /* represents the full GroupADM of the images ProjectMember group */
  def imagesProjectMemberGroup: GroupADM = GroupADM(
    id = "-",
    name = "ProjectMember",
    descriptions = Seq(StringLiteralV2(value = "Default Project Member Group", language = Some("en"))),
    project = imagesProject,
    status = true,
    selfjoin = false
  )

  /* represents the full GroupADM of the images project reviewer group */
  def imagesReviewerGroup: GroupADM = GroupADM(
    id = "http://rdfh.ch/groups/00FF/images-reviewer",
    name = "Image reviewer",
    descriptions = Seq(StringLiteralV2(value = "A group for image reviewers.", language = Some("en"))),
    project = imagesProject,
    status = true,
    selfjoin = false
  )

  /* represents the full GroupADM of the images project reviewer group in the external format*/
  def imagesReviewerGroupExternal: GroupADM = GroupADM(
    id = "http://rdfh.ch/groups/00FF/images-reviewer",
    name = "Image reviewer",
    descriptions = Seq(StringLiteralV2(value = "A group for image reviewers.", language = Some("en"))),
    project = imagesProjectExternal,
    status = true,
    selfjoin = false
  )

  /**
   * **********************************
   */
  /** Incunabula Project Admin Data   * */
  /**
   * **********************************
   */
  val incunabulaProjectIri = "http://rdfh.ch/projects/0803"

  /* represents 'testuser' (Incunabula ProjectAdmin) as found in admin-data.ttl  */
  def incunabulaProjectAdminUser: UserADM =
    UserADM(
      id = "http://rdfh.ch/users/b83acc5f05",
      username = "user.test",
      email = "user.test@example.com",
      givenName = "User",
      familyName = "Test",
      status = true,
      lang = "de",
      password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"),
      token = None,
      groups = Seq.empty[GroupADM],
      projects = Seq(incunabulaProject),
      permissions = PermissionsDataADM(
        groupsPerProject = Map(
          incunabulaProjectIri -> List(
            OntologyConstants.KnoraAdmin.ProjectMember,
            OntologyConstants.KnoraAdmin.ProjectAdmin
          )
        ),
        administrativePermissionsPerProject = Map(
          incunabulaProjectIri -> Set(
            PermissionADM.ProjectAdminAllPermission,
            PermissionADM.ProjectResourceCreateAllPermission
          )
        )
      )
    )

  /* represents 'root_alt' (Incunabula ProjectMember) as found in admin-data.ttl  */
  def incunabulaCreatorUser: UserADM =
    UserADM(
      id = "http://rdfh.ch/users/91e19f1e01",
      username = "root_alt",
      email = "root-alt@example.com",
      givenName = "Administrator-alt",
      familyName = "Admin-alt",
      status = true,
      lang = "de",
      password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"),
      token = None,
      groups = Seq.empty[GroupADM],
      projects = Seq(incunabulaProject),
      permissions = PermissionsDataADM(
        groupsPerProject = Map(
          incunabulaProjectIri -> List(OntologyConstants.KnoraAdmin.ProjectMember)
        ),
        administrativePermissionsPerProject = Map(
          incunabulaProjectIri -> Set(
            PermissionADM.ProjectResourceCreateAllPermission
          )
        )
      )
    )

  /* represents 'root_alt' (Incunabula Creator and ProjectMember) as found in admin-data.ttl  */
  def incunabulaMemberUser: UserADM =
    UserADM(
      id = "http://rdfh.ch/users/incunabulaMemberUser",
      username = "incunabulaMemberUser",
      email = "test.user2@test.ch",
      givenName = "User",
      familyName = "Test2",
      status = true,
      lang = "de",
      password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"),
      token = None,
      groups = Seq.empty[GroupADM],
      projects = Seq(incunabulaProject),
      permissions = PermissionsDataADM(
        groupsPerProject = Map(
          incunabulaProjectIri -> List(OntologyConstants.KnoraAdmin.ProjectMember)
        ),
        administrativePermissionsPerProject = Map(
          incunabulaProjectIri -> Set(
            PermissionADM.ProjectResourceCreateAllPermission
          )
        )
      )
    )

  /* represents the ProjectADM of the incunabula project */
  def incunabulaProject: ProjectADM = ProjectADM(
    id = incunabulaProjectIri,
    shortname = "incunabula",
    shortcode = "0803",
    longname = Some("Bilderfolgen Basler Frühdrucke"),
    description = Seq(
      V2.StringLiteralV2(
        value =
          "<p>Das interdisziplinäre Forschungsprojekt \"<b><em>Die Bilderfolgen der Basler Frühdrucke: Spätmittelalterliche Didaxe als Bild-Text-Lektüre</em></b>\" verbindet eine umfassende kunstwissenschaftliche Analyse der Bezüge zwischen den Bildern und Texten in den illustrierten Basler Inkunabeln mit der Digitalisierung der Bestände der Universitätsbibliothek und der Entwicklung einer elektronischen Edition in der Form einer neuartigen Web-0.2-Applikation.\n</p>\n<p>Das Projekt wird durchgeführt vom <a href=\"http://kunsthist.unibas.ch\">Kunsthistorischen Seminar</a> der Universität Basel (Prof. B. Schellewald) und dem <a href=\"http://www.dhlab.unibas.ch\">Digital Humanities Lab</a> der Universität Basel (PD Dr. L. Rosenthaler).\n</p>\n<p>\nDas Kernstück der digitalen Edition besteht aus rund zwanzig reich bebilderten Frühdrucken aus vier verschiedenen Basler Offizinen. Viele davon sind bereits vor 1500 in mehreren Ausgaben erschienen, einige fast gleichzeitig auf Deutsch und Lateinisch. Es handelt sich um eine ausserordentlich vielfältige Produktion; neben dem Heilsspiegel finden sich ein Roman, die Melusine,  die Reisebeschreibungen des Jean de Mandeville, einige Gebets- und Erbauungsbüchlein, theologische Schriften, Fastenpredigten, die Leben der Heiligen Fridolin und Meinrad, das berühmte Narrenschiff  sowie die Exempelsammlung des Ritters vom Thurn.\n</p>\nDie Internetpublikation macht das digitalisierte Korpus dieser Frühdrucke  durch die Möglichkeiten nichtlinearer Verknüpfung und Kommentierung der Bilder und Texte, für die wissenschaftliche Edition sowie für die Erforschung der Bilder und Texte nutzbar machen. Auch können bereits bestehende und entstehende Online-Editionen damit verknüpft  werden , wodurch die Nutzung von Datenbanken anderer Institutionen im Hinblick auf unser Corpus optimiert wird.\n</p>",
        language = None
      )
    ),
    keywords = Seq(
      "Basler Frühdrucke",
      "Inkunabel",
      "Narrenschiff",
      "Wiegendrucke",
      "Sebastian Brant",
      "Bilderfolgen",
      "early print",
      "incunabula",
      "ship of fools",
      "Kunsthistorisches Seminar Universität Basel",
      "Late Middle Ages",
      "Letterpress Printing",
      "Basel",
      "Contectualisation of images"
    ).sorted,
    logo = Some("incunabula_logo.png"),
    ontologies = Seq(SharedOntologyTestDataADM.INCUNABULA_ONTOLOGY_IRI),
    status = true,
    selfjoin = false
  )

  /* represents the ProjectADM of the incunabula project in the external format*/
  def incunabulaProjectExternal: ProjectADM = ProjectADM(
    id = incunabulaProjectIri,
    shortname = "incunabula",
    shortcode = "0803",
    longname = Some("Bilderfolgen Basler Frühdrucke"),
    description = Seq(
      V2.StringLiteralV2(
        value =
          "<p>Das interdisziplinäre Forschungsprojekt \"<b><em>Die Bilderfolgen der Basler Frühdrucke: Spätmittelalterliche Didaxe als Bild-Text-Lektüre</em></b>\" verbindet eine umfassende kunstwissenschaftliche Analyse der Bezüge zwischen den Bildern und Texten in den illustrierten Basler Inkunabeln mit der Digitalisierung der Bestände der Universitätsbibliothek und der Entwicklung einer elektronischen Edition in der Form einer neuartigen Web-0.2-Applikation.\n</p>\n<p>Das Projekt wird durchgeführt vom <a href=\"http://kunsthist.unibas.ch\">Kunsthistorischen Seminar</a> der Universität Basel (Prof. B. Schellewald) und dem <a href=\"http://www.dhlab.unibas.ch\">Digital Humanities Lab</a> der Universität Basel (PD Dr. L. Rosenthaler).\n</p>\n<p>\nDas Kernstück der digitalen Edition besteht aus rund zwanzig reich bebilderten Frühdrucken aus vier verschiedenen Basler Offizinen. Viele davon sind bereits vor 1500 in mehreren Ausgaben erschienen, einige fast gleichzeitig auf Deutsch und Lateinisch. Es handelt sich um eine ausserordentlich vielfältige Produktion; neben dem Heilsspiegel finden sich ein Roman, die Melusine,  die Reisebeschreibungen des Jean de Mandeville, einige Gebets- und Erbauungsbüchlein, theologische Schriften, Fastenpredigten, die Leben der Heiligen Fridolin und Meinrad, das berühmte Narrenschiff  sowie die Exempelsammlung des Ritters vom Thurn.\n</p>\nDie Internetpublikation macht das digitalisierte Korpus dieser Frühdrucke  durch die Möglichkeiten nichtlinearer Verknüpfung und Kommentierung der Bilder und Texte, für die wissenschaftliche Edition sowie für die Erforschung der Bilder und Texte nutzbar machen. Auch können bereits bestehende und entstehende Online-Editionen damit verknüpft  werden , wodurch die Nutzung von Datenbanken anderer Institutionen im Hinblick auf unser Corpus optimiert wird.\n</p>",
        language = None
      )
    ),
    keywords = Seq(
      "Basler Frühdrucke",
      "Inkunabel",
      "Narrenschiff",
      "Wiegendrucke",
      "Sebastian Brant",
      "Bilderfolgen",
      "early print",
      "incunabula",
      "ship of fools",
      "Kunsthistorisches Seminar Universität Basel",
      "Late Middle Ages",
      "Letterpress Printing",
      "Basel",
      "Contectualisation of images"
    ).sorted,
    logo = Some("incunabula_logo.png"),
    ontologies = Seq(SharedOntologyTestDataADM.INCUNABULA_ONTOLOGY_IRI_LocalHost),
    status = true,
    selfjoin = false
  )

  /**
   * *********************************
   */
  /** Anything Admin Data            * */
  /**
   * *********************************
   */
  val anythingProjectIri = "http://rdfh.ch/projects/0001"

  val customResourceIRI: IRI                    = "http://rdfh.ch/0001/rYAMw7wSTbGw3boYHefByg"
  val customResourceIRI_resourceWithValues: IRI = "http://rdfh.ch/0001/4PnSvolsTEa86KJ2EG76SQ"
  val customValueIRI_withResourceIriAndValueIRIAndValueUUID: IRI =
    "http://rdfh.ch/0001/5zCt1EMJKezFUOW_RCB0Gw/values/fdqCOaqT6dP19pWI84X1XQ"
  val customValueUUID                     = "fdqCOaqT6dP19pWI84X1XQ"
  val customValueIRI: IRI                 = "http://rdfh.ch/0001/5zCt1EMJKezFUOW_RCB0Gw/values/tdWAtnWK2qUC6tr4uQLAHA"
  val customResourceCreationDate: Instant = Instant.parse("2019-01-09T15:45:54.502951Z")
  val customValueCreationDate: Instant    = Instant.parse("2020-06-09T17:04:54.502951Z")

  val customListIRI: IRI = "http://rdfh.ch/lists/0001/qq54wdGKR0S5zsbR5-9wtg"

  def anythingAdminUser: UserADM =
    UserADM(
      id = "http://rdfh.ch/users/AnythingAdminUser",
      username = "AnythingAdminUser",
      email = "anything.admin@example.org",
      givenName = "Anything",
      familyName = "Admin",
      status = true,
      lang = "de",
      password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"),
      token = None,
      groups = Seq.empty[GroupADM],
      projects = Seq(anythingProject),
      permissions = PermissionsDataADM(
        groupsPerProject = Map(
          anythingProjectIri -> List(
            OntologyConstants.KnoraAdmin.ProjectMember,
            OntologyConstants.KnoraAdmin.ProjectAdmin
          )
        ),
        administrativePermissionsPerProject = Map(
          anythingProjectIri -> Set(
            PermissionADM.ProjectAdminAllPermission,
            PermissionADM.ProjectResourceCreateAllPermission
          )
        )
      )
    )

  def anythingUser1: UserADM =
    UserADM(
      id = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q",
      username = "anything.user01",
      email = "anything.user01@example.org",
      givenName = "Anything",
      familyName = "User01",
      status = true,
      lang = "de",
      password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"),
      token = None,
      groups = Seq.empty[GroupADM],
      projects = Seq(anythingProject),
      permissions = PermissionsDataADM(
        groupsPerProject = Map(
          anythingProjectIri -> List(
            OntologyConstants.KnoraAdmin.ProjectMember,
            "http://rdfh.ch/groups/0001/thing-searcher"
          )
        ),
        administrativePermissionsPerProject = Map(
          anythingProjectIri -> Set(
            PermissionADM.ProjectResourceCreateAllPermission
          )
        )
      )
    )

  def anythingUser2: UserADM =
    UserADM(
      id = "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ",
      username = "anything.user02",
      email = "anything.user02@example.org",
      givenName = "Anything",
      familyName = "User02",
      status = true,
      lang = "de",
      password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"),
      token = None,
      groups = Seq.empty[GroupADM],
      projects = Seq(anythingProject),
      permissions = PermissionsDataADM(
        groupsPerProject = Map(
          anythingProjectIri -> List(OntologyConstants.KnoraAdmin.ProjectMember)
        ),
        administrativePermissionsPerProject = Map(
          anythingProjectIri -> Set(
            PermissionADM.ProjectResourceCreateAllPermission
          )
        )
      )
    )

  def anythingProject: ProjectADM = ProjectADM(
    id = anythingProjectIri,
    shortname = "anything",
    shortcode = "0001",
    longname = Some("Anything Project"),
    description = Seq(V2.StringLiteralV2(value = "Anything Project", language = None)),
    keywords = Seq("things", "arbitrary test data").sorted,
    logo = None,
    ontologies = Seq(SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI, SharedOntologyTestDataADM.SomethingOntologyIri),
    status = true,
    selfjoin = false
  )

  def anythingProjectExternal: ProjectADM = ProjectADM(
    id = anythingProjectIri,
    shortname = "anything",
    shortcode = "0001",
    longname = Some("Anything Project"),
    description = Seq(V2.StringLiteralV2(value = "Anything Project", language = None)),
    keywords = Seq("things", "arbitrary test data").sorted,
    logo = None,
    ontologies = Seq(
      SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost,
      SharedOntologyTestDataADM.SomethingOntologyIriLocalhost
    ),
    status = true,
    selfjoin = false
  )

  /* represents the full GroupADM of the Thing searcher group */
  def thingSearcherGroup: GroupADM = GroupADM(
    id = "http://rdfh.ch/groups/0001/thing-searcher",
    name = "Thing searcher",
    descriptions = Seq(StringLiteralV2(value = "A group for thing searchers.", language = Some("en"))),
    project = anythingProject,
    status = true,
    selfjoin = true
  )

  /**
   * *********************************
   */
  /** BEOL                           * */
  /**
   * *********************************
   */
  val beolProjectIri = "http://rdfh.ch/projects/yTerZGyxjZVqFMNNKXCDPF"

  def beolProject: ProjectADM = ProjectADM(
    id = beolProjectIri,
    shortname = "beol",
    shortcode = "0801",
    longname = Some("Bernoulli-Euler Online"),
    description = Seq(V2.StringLiteralV2(value = "Bernoulli-Euler Online", language = None)),
    keywords = Seq.empty[String],
    logo = None,
    ontologies = Seq(
      "http://www.knora.org/ontology/0801/beol",
      "http://www.knora.org/ontology/0801/biblio",
      "http://www.knora.org/ontology/0801/leibniz",
      "http://www.knora.org/ontology/0801/newton"
    ),
    status = true,
    selfjoin = false
  )

  /* represents the user profile of 'superuser' as found in admin-data.ttl */
  def beolUser: UserADM = UserADM(
    id = "http://rdfh.ch/users/PSGbemdjZi4kQ6GHJVkLGE",
    username = "beol",
    email = "beol@example.com",
    givenName = "BEOL",
    familyName = "BEOL",
    status = true,
    lang = "en",
    password = Some("$2a$10$fTEr/xVjPq7UBAy1O6KWKOM1scLhKGeRQdR4GTA997QPqHzXv0MnW"),
    token = None,
    groups = Seq.empty[GroupADM],
    projects = Seq(beolProject),
    permissions = PermissionsDataADM(
      groupsPerProject = Map(
        beolProjectIri -> List(OntologyConstants.KnoraAdmin.ProjectMember, OntologyConstants.KnoraAdmin.ProjectAdmin)
      ),
      administrativePermissionsPerProject = Map(
        beolProjectIri -> Set(
          PermissionADM.ProjectAdminAllPermission
        )
      )
    )
  )

  /**
   * *********************************
   */
  /** DOKUBIB                        * */
  /**
   * *********************************
   */
  val dokubibProjectIri = "http://rdfh.ch/projects/0804"

  def dokubibProject: ProjectADM = ProjectADM(
    id = dokubibProjectIri,
    shortname = "dokubib",
    shortcode = "0804",
    longname = Some("Dokubib"),
    description = Seq(V2.StringLiteralV2(value = "Dokubib", language = None)),
    keywords = Seq.empty[String],
    logo = None,
    ontologies = Seq("http://www.knora.org/ontology/0804/dokubib"),
    status = false,
    selfjoin = false
  )
}
