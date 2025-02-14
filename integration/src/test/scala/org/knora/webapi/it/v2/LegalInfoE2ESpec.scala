/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.it.v2

import cats.syntax.traverse.*
import com.apicatalog.jsonld.JsonLd
import com.apicatalog.jsonld.document.JsonDocument
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.Property
import org.apache.jena.rdf.model.Resource
import org.apache.jena.vocabulary.RDF
import zio.*
import zio.http.Body
import zio.http.Response
import zio.json.ast.Json
import zio.test.*
import zio.test.Assertion.*

import java.io.ByteArrayInputStream
import java.net.URLEncoder
import scala.jdk.CollectionConverters.IteratorHasAsScala
import scala.language.implicitConversions

import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex as KA
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.models.filemodels.FileType
import org.knora.webapi.models.filemodels.UploadFileRequest
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.KnoraIris.ResourceIri
import org.knora.webapi.slice.common.KnoraIris.ValueIri
import org.knora.webapi.slice.common.jena.JenaConversions.given
import org.knora.webapi.slice.common.jena.ModelOps
import org.knora.webapi.slice.common.jena.ModelOps.*
import org.knora.webapi.slice.common.jena.ResourceOps.*
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

object LegalInfoE2ESpec extends E2EZSpec {

  private implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance
  private val aCopyrightHolder             = CopyrightHolder.unsafeFrom("Universität Basel")
  private val someAuthorship               = List("Hans Müller", "Gigi DAgostino").map(Authorship.unsafeFrom)
  private val aLicenseIri                  = LicenseIri.makeNew

  private val createResourceSuite = suite("Creating Resources")(
    test("when creating a resource without legal info the creation response should not contain it") {
      for {
        createResourceResponseModel <- createStillImageResource()
        info                        <- copyrightAndLicenseInfo(createResourceResponseModel)
      } yield assertTrue(
        info.copyrightHolder.isEmpty,
        info.authorship.isEmpty,
        info.licenseIri.isEmpty,
      )
    },
    test("when creating a resource with legal info the creation response should contain it") {
      for {
        createResourceResponseModel <- createStillImageResourceWithInfos
        info                        <- copyrightAndLicenseInfo(createResourceResponseModel)
      } yield assertTrue(
        info.copyrightHolder.contains(aCopyrightHolder),
        info.licenseIri.contains(aLicenseIri),
      ) && assert(info.authorship.getOrElse(List.empty))(hasSameElements(someAuthorship))
    },
    test("when creating a resource with legal info the response when getting the created resource should contain it") {
      for {
        createResourceResponseModel <- createStillImageResourceWithInfos
        resourceId                  <- resourceId(createResourceResponseModel)
        getResponseModel            <- getResourceFromApi(resourceId)
        info                        <- copyrightAndLicenseInfo(getResponseModel)
      } yield assertTrue(
        info.copyrightHolder.contains(aCopyrightHolder),
        info.licenseIri.contains(aLicenseIri),
      ) && assert(info.authorship.getOrElse(List.empty))(hasSameElements(someAuthorship))
    },
    test("when creating a resource with legal info the response when getting the created value should contain it") {
      for {
        createResourceResponseModel <- createStillImageResourceWithInfos
        valueResponseModel          <- getValueFromApi(createResourceResponseModel)
        info                        <- copyrightAndLicenseInfo(valueResponseModel)
      } yield assertTrue(
        info.copyrightHolder.contains(aCopyrightHolder),
        info.licenseIri.contains(aLicenseIri),
      ) && assert(info.authorship.getOrElse(List.empty))(hasSameElements(someAuthorship))
    },
  )

  private val createValueSuite = suite("Values with legal info") {
    test("when updating a value with legal info the created value should contain the update") {
      val newLicenseIri = LicenseIri.makeNew
      for {
        resourceCreated <- createStillImageResource()
        resourceId      <- resourceId(resourceCreated)
        valueIdOld      <- valueId(resourceCreated)
        _ <- sendPutRequestAsRoot(
               s"/v2/values",
               UpdateStillImageFileValueRequest(
                 resourceId,
                 valueIdOld,
                 ResourceClassIri.unsafeFrom("http://0.0.0.0:3333/ontology/0001/anything/v2#ThingPicture".toSmartIri),
                 aCopyrightHolder,
                 someAuthorship,
                 newLicenseIri,
               ).jsonLd,
             ).filterOrElseWith(_.status.isSuccess)(failResponse(s"Failed to create value"))
        createdResourceModel <- getResourceFromApi(resourceId)
        info                 <- copyrightAndLicenseInfo(createdResourceModel)
      } yield assertTrue(
        info.licenseIri.contains(newLicenseIri),
      ) && assert(info.authorship.getOrElse(List.empty))(hasSameElements(someAuthorship))
    }
  }

  final case class UpdateStillImageFileValueRequest(
    resourceId: ResourceIri,
    valueId: ValueIri,
    resourceClass: ResourceClassIri,
    copyrightHolder: CopyrightHolder,
    authorship: List[Authorship],
    licenseIri: LicenseIri,
  ) {
    def jsonLd: String =
      JsonLd.expand(JsonDocument.of(new ByteArrayInputStream(toJson.toString().getBytes))).get.toString

    private def toJson: Json = {
      def ldType(typ: Any) = ("@type", Json.Str(typ.toString))
      def ldId(id: Any)    = ("@id", Json.Str(id.toString))
      Json.Obj(
        ldId(resourceId),
        ldType(resourceClass),
        (
          KA.HasStillImageFileValue,
          Json.Obj(
            ldId(valueId),
            ldType(KA.StillImageFileValue),
            (KA.FileValueHasFilename, Json.Str("test.jpx")),
            (KA.HasCopyrightHolder, Json.Str(copyrightHolder.value)),
            (KA.HasAuthorship, Json.Arr(authorship.map(_.value).map(Json.Str.apply): _*)),
            (KA.HasLicense, Json.Obj(ldId(licenseIri.value))),
          ),
        ),
      )
    }
  }

  val e2eSpec: Spec[Scope & env, Any] =
    suite("Copyright Attribution and Licenses")(createResourceSuite, createValueSuite)

  private def failResponse(msg: String)(response: Response) =
    response.body.asString.flatMap(bodyStr => ZIO.fail(Exception(s"$msg\nstatus: ${response.status}\nbody: $bodyStr")))

  private def createStillImageResourceWithInfos =
    createStillImageResource(
      Some(aCopyrightHolder),
      Some(someAuthorship),
      Some(aLicenseIri),
    )

  private def createStillImageResource(
    copyrightHolder: Option[CopyrightHolder] = None,
    authorship: Option[List[Authorship]] = None,
    licenseIri: Option[LicenseIri] = None,
  ): ZIO[env, Throwable, Model] = {
    val jsonLd = UploadFileRequest
      .make(
        FileType.StillImageFile(),
        "internalFilename.jpg",
        copyrightHolder = copyrightHolder,
        authorship = authorship,
        licenseIri = licenseIri,
      )
      .toJsonLd(className = Some("ThingPicture"), ontologyName = "anything")
    for {
      responseBody <- sendPostRequestAsRoot("/v2/resources", jsonLd)
                        .mapError(Exception(_))
                        .filterOrElseWith(_.status.isSuccess)(failResponse(s"Failed to create resource"))
                        .flatMap(_.body.asString)
      createResourceResponseModel <- ModelOps.fromJsonLd(responseBody).mapError(Exception(_))
    } yield createResourceResponseModel
  }

  private def getResourceFromApi(resourceId: ResourceIri) = for {
    responseBody <- sendGetRequest(s"/v2/resources/${URLEncoder.encode(resourceId.toString, "UTF-8")}")
                      .filterOrElseWith(_.status.isSuccess)(failResponse(s"Failed to get resource $resourceId."))
                      .flatMap(_.body.asString)
    model <- ModelOps.fromJsonLd(responseBody).mapError(Exception(_))
  } yield model

  private def getValueFromApi(createResourceResponse: Model): ZIO[env, Throwable, Model] = for {
    valueId    <- valueId(createResourceResponse)
    resourceId <- resourceId(createResourceResponse)
    model      <- getValueFromApi(valueId, resourceId)
  } yield model

  private def getValueFromApi(valueId: ValueIri, resourceId: ResourceIri): ZIO[env, Throwable, Model] = for {
    responseBody <- sendGetRequest(s"/v2/values/${URLEncoder.encode(resourceId.toString, "UTF-8")}/${valueId.valueId}")
                      .filterOrElseWith(_.status.isSuccess)(failResponse(s"Failed to get value $resourceId."))
                      .flatMap(_.body.asString)
    model <- ModelOps.fromJsonLd(responseBody).mapError(Exception(_))
  } yield model

  private def resourceId(model: Model): Task[ResourceIri] =
    ZIO
      .fromEither(
        for {
          root <- model.singleRootResource
          id   <- root.uri.toRight("No URI found for root resource")
        } yield id,
      )
      .map(_.toSmartIri)
      .mapBoth(Exception(_), ResourceIri.unsafeFrom)

  private def valueId(model: Model): ZIO[IriConverter, Throwable, ValueIri] = {
    val subs = model
      .listSubjectsWithProperty(RDF.`type`)
      .asScala
      .filter(_.getProperty(RDF.`type`).getObject.asResource().hasURI(KA.StillImageFileValue))
      .toList
    subs match
      case s :: Nil =>
        ZIO
          .fromEither(s.uri.toRight("No URI found for value"))
          .mapError(Exception(_))
          .flatMap(str => ZIO.serviceWithZIO[IriConverter](_.asSmartIri(str)))
          .flatMap(iri => ZIO.fromEither(ValueIri.from(iri)).mapError(Exception(_)))
      case Nil => ZIO.fail(Exception("No value found"))
      case _   => ZIO.fail(Exception("Multiple values found"))
  }

  final case class CopyrightAndLicenseInfo(
    copyrightHolder: Option[CopyrightHolder],
    authorship: Option[List[Authorship]],
    licenseIri: Option[LicenseIri],
  )

  private def copyrightAndLicenseInfo(model: Model) =
    for {
      copyright  <- copyrightValueOption(model).map(_.map(CopyrightHolder.unsafeFrom))
      authorship <- authorshipValuesOption(model).map(_.map(_.map(Authorship.unsafeFrom)))
      licenseIri <- licenseIriValueOption(model).map(_.map(LicenseIri.unsafeFrom))
    } yield CopyrightAndLicenseInfo(copyright, authorship, licenseIri)

  private def copyrightValueOption(model: Model) =
    singleStringValueOption(model, KA.HasCopyrightHolder)

  private def singleStringValueOption(model: Model, property: Property): Task[Option[String]] =
    ZIO
      .fromEither(
        model
          .singleSubjectWithPropertyOption(property)
          .flatMap(_.map(_.objectStringOption(property)).fold(Right(None))(identity)),
      )
      .mapError(Exception(_))

  private def authorshipValuesOption(model: Model): Task[Option[List[String]]] =
    ZIO
      .fromEither(
        model
          .singleSubjectWithPropertyOption(KA.HasAuthorship)
          .flatMap(_.traverse(_.objectStringList(KA.HasAuthorship))),
      )
      .mapError(Exception(_))

  private def licenseIriValueOption(model: Model) =
    singleObjectUriOption(model, KA.HasLicense)

  private def singleObjectUriOption(model: Model, property: Property): Task[Option[String]] =
    ZIO
      .fromEither(
        model
          .singleSubjectWithPropertyOption(property)
          .flatMap(_.map(_.objectUriOption(property)).fold(Right(None))(identity)),
      )
      .mapError(Exception(_))
}
