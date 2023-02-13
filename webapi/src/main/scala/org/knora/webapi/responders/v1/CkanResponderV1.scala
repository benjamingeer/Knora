/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v1

import akka.actor.ActorRef
import akka.pattern._
import akka.util.Timeout
import zio.ZIO
import java.net.URLEncoder
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._

import org.knora.webapi.IRI
import org.knora.webapi.core.MessageHandler
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages.SparqlSelectRequest
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.util.ResponderData
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
import org.knora.webapi.messages.util.rdf.VariableResultsRow
import org.knora.webapi.messages.v1.responder.ckanmessages._
import org.knora.webapi.messages.v1.responder.listmessages.NodePathGetRequestV1
import org.knora.webapi.messages.v1.responder.listmessages.NodePathGetResponseV1
import org.knora.webapi.messages.v1.responder.projectmessages.ProjectInfoByShortnameGetRequestV1
import org.knora.webapi.messages.v1.responder.projectmessages.ProjectInfoResponseV1
import org.knora.webapi.messages.v1.responder.projectmessages.ProjectInfoV1
import org.knora.webapi.messages.v1.responder.resourcemessages._
import org.knora.webapi.messages.v1.responder.valuemessages.DateValueV1
import org.knora.webapi.messages.v1.responder.valuemessages.HierarchicalListValueV1
import org.knora.webapi.messages.v1.responder.valuemessages.LinkV1
import org.knora.webapi.messages.v1.responder.valuemessages.TextValueV1
import org.knora.webapi.messages.ResponderRequest
import org.knora.webapi.responders.Responder

/**
 * This responder is used by the Ckan route, for serving data to the Ckan harverster, which is published
 * under http://data.humanities.ch
 */
class CkanResponderV1(responderData: ResponderData, messageRelay: MessageRelay)
    extends Responder(responderData.actorDeps)
    with MessageHandler {

  messageRelay.subscribe(this)

  override def handle(message: ResponderRequest): zio.Task[Any] =
    ZIO.fromFuture(_ => this.receive(message.asInstanceOf[CkanResponderRequestV1]))

  override def isResponsibleFor(message: ResponderRequest): Boolean = message match {
    case _: CkanResponderRequestV1 => true
    case _                           => false
  }

  /**
   * A user representing the Knora API server, used in those cases where a user is required.
   */
  private val systemUser = KnoraSystemInstances.Users.SystemUser.asUserProfileV1

  /**
   * Receives a message extending [[CkanResponderRequestV1]], and returns an appropriate response message.
   */
  def receive(msg: CkanResponderRequestV1) = msg match {
    case CkanRequestV1(projects, limit, info, userProfile) =>
      getCkanResponseV1(projects, limit, info, userProfile)
    case other => handleUnexpectedMessage(other, log, this.getClass.getName)
  }

  private def getCkanResponseV1(
    project: Option[Seq[String]],
    limit: Option[Int],
    info: Boolean,
    userProfile: UserADM
  ): Future[CkanResponseV1] = {

    log.debug("Ckan Endpoint:")
    log.debug(s"Project: $project")
    log.debug(s"Limit: $limit")
    log.debug(s"Info: $info")

    val defaultProjectList: Seq[String] = Vector("dokubib", "incunabula")

    val selectedProjectsFuture = project match {
      case Some(projectList) =>
        // look up resources only for these projects if allowed
        val allowedProjects = projectList.filter(defaultProjectList.contains(_))
        getProjectInfos(
          projectNames = allowedProjects,
          userProfile = userProfile
        )

      case None =>
        // return our default project map, containing all projects that we want to serve over the Ckan endpoint
        getProjectInfos(
          projectNames = defaultProjectList,
          userProfile = userProfile
        )
    }

    for {
      projects <- selectedProjectsFuture
      ckanProjects: Seq[Future[CkanProjectV1]] = projects flatMap {
                                                   case ("dokubib", projectFullInfo) =>
                                                     Some(
                                                       getDokubibCkanProject(
                                                         pinfo = projectFullInfo,
                                                         limit = limit,
                                                         userProfile = userProfile
                                                       )
                                                     )

                                                   case ("incunabula", projectFullInfo) =>
                                                     Some(
                                                       getIncunabulaCkanProject(
                                                         pinfo = projectFullInfo,
                                                         limit = limit,
                                                         userProfile = userProfile
                                                       )
                                                     )

                                                   case _ => None
                                                 }
      result  <- Future.sequence(ckanProjects)
      response = CkanResponseV1(projects = result)
    } yield response
  }

  ///////////////////////////////////////////////////////////////////////////////////////////
  // DOKUBIB
  ///////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Dokubib specific Ckan export stuff
   *
   * @param pinfo
   * @param limit
   * @param userProfile
   * @return
   */
  private def getDokubibCkanProject(
    pinfo: ProjectInfoV1,
    limit: Option[Int],
    userProfile: UserADM
  ): Future[CkanProjectV1] = {

    /*
         - datasets
            - bild 1
                - files
                    - bild 1
            - bild 2
     */

    val pIri = pinfo.id

    val ckanPInfo =
      CkanProjectInfoV1(
        shortname = pinfo.shortname,
        longname = pinfo.longname.getOrElse(pinfo.shortname),
        ckan_tags = Vector("Kulturanthropologie"),
        ckan_license_id = "CC-BY-NC-SA-4.0"
      )

    val datasetsFuture: Future[Seq[CkanProjectDatasetV1]] = for {
      bilder <- getDokubibBilderIRIs(pIri, limit)

      bilderMitPropsFuture = getResources(
                               iris = bilder,
                               userProfile = userProfile
                             )

      bilderMitProps <- bilderMitPropsFuture
      dataset = bilderMitProps.map { case (iri, info, props) =>
                  flattenInfo(info)
                  val propsMap = flattenProps(props)
                  CkanProjectDatasetV1(
                    ckan_title = propsMap.getOrElse("Description", ""),
                    ckan_tags = propsMap.getOrElse("Title", "").split("/").toIndexedSeq.map(_.trim),
                    files = Vector(
                      CkanProjectDatasetFileV1(
                        ckan_title = propsMap.getOrElse("preview_loc_origname", ""),
                        data_url = "http://localhost:3333/v1/assets/" + URLEncoder.encode(iri, "UTF-8"),
                        data_mimetype = "",
                        source_url = "http://salsah.org/resources/" + URLEncoder.encode(iri, "UTF-8"),
                        source_mimetype = ""
                      )
                    ),
                    other_props = propsMap
                  )
                }
    } yield dataset

    for {
      datasets <- datasetsFuture
      result    = CkanProjectV1(project_info = ckanPInfo, project_datasets = Some(datasets))
    } yield result

  }

  /**
   * Get all Bilder IRIs for Dokubib
   *
   * @param projectIri
   * @param limit
   * @return
   */
  private def getDokubibBilderIRIs(projectIri: IRI, limit: Option[Int]): Future[Seq[IRI]] = {

    implicit val timeout = Timeout(180.seconds)

    for {
      sparqlQuery <- Future(
                       org.knora.webapi.messages.twirl.queries.sparql.v1.txt
                         .ckanDokubib(projectIri, limit)
                         .toString()
                     )
      response                             <- appActor.ask(SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResult]
      responseRows: Seq[VariableResultsRow] = response.results.bindings

      bilder: Seq[String] = responseRows.groupBy(_.rowMap("bild")).keys.toVector

      result = limit match {
                 case Some(n) if n > 0 => bilder.take(n)
                 case _                => bilder
               }

    } yield result

  }

  ///////////////////////////////////////////////////////////////////////////////////////////
  // INCUNABULA
  ///////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Incunabula specific Ckan stuff
   *
   * @param pinfo
   * @param limit
   *
   * @param userProfile
   * @return
   */
  private def getIncunabulaCkanProject(
    pinfo: ProjectInfoV1,
    limit: Option[Int],
    userProfile: UserADM
  ): Future[CkanProjectV1] = {

    /*
         - datasets
            - book 1
                - files
                    - page 1
                    - page 2
                    ...
            - book 2
     */

    val pIri = pinfo.id

    val ckanPInfo =
      CkanProjectInfoV1(
        shortname = pinfo.shortname,
        longname = pinfo.longname.getOrElse(pinfo.shortname),
        ckan_tags = Vector("Kunstgeschichte"),
        ckan_license_id = "CC-BY-4.0"
      )

    // get book and page IRIs in project
    val booksWithPagesFuture = getIncunabulaBooksWithPagesIRIs(pIri, limit)

    val bookDatasetsFuture = booksWithPagesFuture.flatMap { singleBook =>
      val bookDataset = singleBook map { case (bookIri: IRI, pageIris: Seq[IRI]) =>
        val bookResourceFuture = getResource(
          iri = bookIri,
          userProfile = userProfile
        )

        bookResourceFuture flatMap { case (_, _, bProps) =>
          val bPropsMap = flattenProps(bProps)
          val files = pageIris map { pageIri =>
            getResource(
              iri = pageIri,
              userProfile = userProfile
            ) map { case (pIri, _, pProps) =>
              val pPropsMap = flattenProps(pProps)
              CkanProjectDatasetFileV1(
                ckan_title = pPropsMap.getOrElse("Page identifier", ""),
                ckan_description = Some(pPropsMap.getOrElse("Beschreibung (Richtext)", "")),
                data_url = "http://localhost:3333/v1/assets/" + URLEncoder.encode(pIri, "UTF-8"),
                data_mimetype = "",
                source_url = "http://salsah.org/resources/" + URLEncoder.encode(pIri, "UTF-8"),
                source_mimetype = "",
                other_props = Some(pPropsMap)
              )
            }
          }
          Future.sequence(files) map { filesList =>
            CkanProjectDatasetV1(
              ckan_title = bPropsMap.getOrElse("Title", ""),
              ckan_tags = Vector("Kunstgeschichte"),
              files = filesList,
              other_props = bPropsMap
            )
          }
        }
      }
      Future.sequence(bookDataset.toVector)
    }

    for {
      bookDatasets <- bookDatasetsFuture
      result        = CkanProjectV1(project_info = ckanPInfo, project_datasets = Some(bookDatasets))
    } yield result
  }

  /**
   * Get all book IRIs for Incunabula
   *
   * @param projectIri
   * @param limit
   * @return
   */
  private def getIncunabulaBooksWithPagesIRIs(projectIri: IRI, limit: Option[Int]): Future[Map[IRI, Seq[IRI]]] =
    for {
      sparqlQuery <- Future(
                       org.knora.webapi.messages.twirl.queries.sparql.v1.txt
                         .ckanIncunabula(projectIri, limit)
                         .toString()
                     )
      response                             <- appActor.ask(SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResult]
      responseRows: Seq[VariableResultsRow] = response.results.bindings

      booksWithPages: Map[String, Seq[String]] =
        responseRows.groupBy(_.rowMap("book")).map { case (bookIri: String, rows: Seq[VariableResultsRow]) =>
          (
            bookIri,
            rows.map { case row =>
              row.rowMap("page")
            }
          )
        }

      result = limit match {
                 case Some(n) if n > 0 => booksWithPages.take(n)
                 case _                => booksWithPages
               }

    } yield result

  ///////////////////////////////////////////////////////////////////////////////////////////
  // GENERAL
  ///////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Get detailed information about the projects
   *
   * @param projectNames
   *
   * @param userProfile
   * @return
   */
  private def getProjectInfos(
    projectNames: Seq[String],
    userProfile: UserADM
  ): Future[Seq[(String, ProjectInfoV1)]] =
    Future.sequence {
      for {
        pName <- projectNames

        projectInfoResponseFuture = appActor
                                      .ask(
                                        ProjectInfoByShortnameGetRequestV1(
                                          shortname = pName,
                                          userProfileV1 = Some(userProfile.asUserProfileV1)
                                        )
                                      )
                                      .mapTo[ProjectInfoResponseV1]

        result = projectInfoResponseFuture.map(_.project_info) map { pInfo =>
                   (pName, pInfo)
                 }
      } yield result
    }

  /**
   * Get all information there is about these resources
   *
   * @param iris
   *
   * @param userProfile
   * @return
   */
  private def getResources(
    iris: Seq[IRI],
    userProfile: UserADM
  ): Future[Seq[(String, Option[ResourceInfoV1], Option[PropsV1])]] =
    Future.sequence {
      for {
        iri <- iris

        resource = getResource(
                     iri = iri,
                     userProfile = userProfile
                   )
      } yield resource
    }

  /**
   * Get all information there is about this one resource
   *
   * @param iri
   *
   * @param userProfile
   * @return
   */
  private def getResource(
    iri: IRI,
    userProfile: UserADM
  ): Future[(String, Option[ResourceInfoV1], Option[PropsV1])] = {

    val resourceFullResponseFuture =
      appActor
        .ask(
          ResourceFullGetRequestV1(
            iri = iri,
            userADM = userProfile
          )
        )
        .mapTo[ResourceFullResponseV1]

    resourceFullResponseFuture map { case ResourceFullResponseV1(resInfo, _, props, _, _) =>
      (iri, resInfo, props)
    }

  }

  private def flattenInfo(maybeInfo: Option[ResourceInfoV1]): Map[String, String] = {
    def maybeTuple(key: String, maybeValue: Option[String]): Option[(String, String)] =
      maybeValue.map(value => (key, value))

    maybeInfo match {
      case None => Map()
      case Some(info) =>
        val listOfOptions = Vector(
          maybeTuple("project_id", Some(info.project_id)),
          maybeTuple("person_id", Some(info.person_id)),
          maybeTuple("restype_id", Some(info.restype_id)),
          maybeTuple("restype_label", info.restype_label),
          maybeTuple("restype_description", info.restype_description),
          maybeTuple("restype_iconsrc", info.restype_iconsrc),
          maybeTuple("firstproperty", info.firstproperty)
        ) ++ flattenLocation(info.preview)
        listOfOptions.flatten.toMap
    }

  }

  private def flattenLocation(location: Option[LocationV1]): Seq[Option[(String, String)]] =
    location match {
      case None => Vector(None)
      case Some(loc) =>
        Vector(Some(("preview_loc_origname", loc.origname.getOrElse(""))))
    }

  private def flattenProps(props: Option[PropsV1]): Map[String, String] =
    if (props.nonEmpty) {
      val properties = props.get.properties

      val propMap = properties.foldLeft(Map.empty[String, String]) { case (acc, propertyV1: PropertyV1) =>
        val label = propertyV1.label.getOrElse("")

        val values: Seq[String] = propertyV1.valuetype_id.get match {
          case OntologyConstants.KnoraBase.TextValue =>
            propertyV1.values.map(literal => textValue2String(literal.asInstanceOf[TextValueV1]))

          case OntologyConstants.KnoraBase.DateValue =>
            propertyV1.values.map(literal => dateValue2String(literal.asInstanceOf[DateValueV1]))

          case OntologyConstants.KnoraBase.ListValue =>
            propertyV1.values.map(literal => listValue2String(literal.asInstanceOf[HierarchicalListValueV1], appActor))

          case OntologyConstants.KnoraBase.Resource => // TODO: this could actually be a subclass of knora-base:Resource.
            propertyV1.values.map(literal => resourceValue2String(literal.asInstanceOf[LinkV1], appActor))

          case _ => Vector()
        }

        if (label.nonEmpty && values.nonEmpty) {
          acc + (label -> values.mkString(","))
        } else {
          acc
        }
      }

      propMap
    } else {
      Map.empty[String, String]
    }

  private def textValue2String(text: TextValueV1): String =
    text.utf8str

  private def dateValue2String(date: DateValueV1): String =
    if (date.dateval1 == date.dateval2) {
      date.dateval1.toString + " " + date.era1 + ", " + date.calendar.toString + " " + date.era2
    } else {
      date.dateval1.toString + " " + date.era1 + ", " + date.dateval2 + ", " + date.calendar.toString + " " + date.era2
    }

  private def listValue2String(list: HierarchicalListValueV1, appActor: ActorRef): String = {

    val resultFuture = appActor.ask(NodePathGetRequestV1(list.hierarchicalListIri, systemUser))
    val nodePath     = Await.result(resultFuture, Duration(3, SECONDS)).asInstanceOf[NodePathGetResponseV1]

    val labels = nodePath.nodelist map { case element =>
      element.label.getOrElse("")
    }

    labels.mkString(" / ")
  }

  private def resourceValue2String(resource: LinkV1, appActor: ActorRef): String =
    resource.valueLabel.get

}
