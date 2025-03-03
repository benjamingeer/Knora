/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import com.typesafe.scalalogging.LazyLogging
import org.xml.sax.SAXException
import zio.*

import java.io.*
import java.util.UUID
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory
import scala.xml.Node
import scala.xml.NodeSeq
import scala.xml.XML

import dsp.errors.*
import dsp.valueobjects.Iri
import org.knora.webapi.*
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageHandler
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.*
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.store.sipimessages.SipiGetTextFileRequest
import org.knora.webapi.messages.twirl.MappingElement
import org.knora.webapi.messages.twirl.MappingStandoffDatatypeClass
import org.knora.webapi.messages.twirl.MappingXMLAttribute
import org.knora.webapi.messages.twirl.queries.sparql
import org.knora.webapi.messages.util.ConstructResponseUtilV2
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2.XMLTagItem
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality.*
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadClassInfoV2
import org.knora.webapi.messages.v2.responder.ontologymessages.StandoffEntityInfoGetRequestV2
import org.knora.webapi.messages.v2.responder.ontologymessages.StandoffEntityInfoGetResponseV2
import org.knora.webapi.messages.v2.responder.resourcemessages.*
import org.knora.webapi.messages.v2.responder.standoffmessages.*
import org.knora.webapi.messages.v2.responder.valuemessages.*
import org.knora.webapi.responders.IriLocker
import org.knora.webapi.responders.Responder
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.infrastructure.CacheManager
import org.knora.webapi.slice.infrastructure.EhCache
import org.knora.webapi.slice.ontology.domain.model.Cardinality.AtLeastOne
import org.knora.webapi.slice.ontology.domain.model.Cardinality.ExactlyOne
import org.knora.webapi.store.iiif.api.SipiService
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update
import org.knora.webapi.util.FileUtil

/**
 * Responds to requests relating to the creation of mappings from XML elements
 * and attributes to standoff classes and properties.
 */
final case class StandoffResponderV2(
  appConfig: AppConfig,
  messageRelay: MessageRelay,
  triplestore: TriplestoreService,
  constructResponseUtilV2: ConstructResponseUtilV2,
  standoffTagUtilV2: StandoffTagUtilV2,
  projectService: ProjectService,
  xsltCache: EhCache[String, String],
  mappingCache: EhCache[String, MappingXMLtoStandoff],
  sipiService: SipiService,
)(implicit val stringFormatter: StringFormatter)
    extends MessageHandler
    with LazyLogging {

  private val xmlMimeTypes = Set("text/xml", "application/xml", "application/xslt+xml")

  override def isResponsibleFor(message: ResponderRequest): Boolean = message.isInstanceOf[StandoffResponderRequestV2]

  /**
   * Receives a message of type [[StandoffResponderRequestV2]], and returns an appropriate response message.
   */
  override def handle(msg: ResponderRequest): Task[Any] = msg match {
    case CreateMappingRequestV2(metadata, xml, requestingUser, uuid) =>
      createMappingV2(
        xml.xml,
        metadata.label,
        metadata.projectIri,
        metadata.mappingName,
        requestingUser,
        uuid,
      )
    case GetMappingRequestV2(mappingIri) => getMappingV2(mappingIri)
    case GetXSLTransformationRequestV2(xsltTextReprIri, requestingUser) =>
      getXSLTransformation(xsltTextReprIri, requestingUser)
    case other => Responder.handleUnexpectedMessage(other, this.getClass.getName)
  }

  /**
   * If not already in the cache, retrieves a `knora-base:XSLTransformation` in the triplestore and requests the corresponding XSL transformation file from Sipi.
   *
   * @param xslTransformationIri the IRI of the resource representing the XSL Transformation (a [[OntologyConstants.KnoraBase.XSLTransformation]]).
   * @param requestingUser       the user making the request.
   * @return a [[GetXSLTransformationResponseV2]].
   */
  private def getXSLTransformation(
    xslTransformationIri: IRI,
    requestingUser: User,
  ): Task[GetXSLTransformationResponseV2] = {

    val xsltUrlFuture = for {

      textRepresentationResponseV2 <-
        messageRelay
          .ask[ReadResourcesSequenceV2](
            ResourcesGetRequestV2(
              resourceIris = Vector(xslTransformationIri),
              targetSchema = ApiV2Complex,
              requestingUser = requestingUser,
            ),
          )

      resource = textRepresentationResponseV2.toResource(xslTransformationIri)

      _ = if (resource.resourceClassIri.toString != OntologyConstants.KnoraBase.XSLTransformation) {
            throw BadRequestException(
              s"Resource $xslTransformationIri is not a ${OntologyConstants.KnoraBase.XSLTransformation}",
            )
          }

      (fileValueIri, xsltFileValueContent) =
        resource.values.get(
          OntologyConstants.KnoraBase.HasTextFileValue.toSmartIri,
        ) match {
          case Some(values: Seq[ReadValueV2]) if values.size == 1 =>
            values.head match {
              case value: ReadValueV2 =>
                value.valueContent match {
                  case textRepr: TextFileValueContentV2 => (value.valueIri, textRepr)
                  case _ =>
                    throw InconsistentRepositoryDataException(
                      s"${OntologyConstants.KnoraBase.XSLTransformation} $xslTransformationIri is supposed to have exactly one value of type ${OntologyConstants.KnoraBase.TextFileValue}",
                    )
                }
            }

          case _ =>
            throw InconsistentRepositoryDataException(
              s"${OntologyConstants.KnoraBase.XSLTransformation} has no or more than one property ${OntologyConstants.KnoraBase.HasTextFileValue}",
            )
        }

      // check if xsltFileValueContent represents an XSL transformation
      _ = if (!xmlMimeTypes.contains(xsltFileValueContent.fileValue.internalMimeType)) {
            throw BadRequestException(
              s"Expected $fileValueIri to be an XML file referring to an XSL transformation, but it has MIME type ${xsltFileValueContent.fileValue.internalMimeType}",
            )
          }

      xsltUrl: String =
        s"${appConfig.sipi.internalBaseUrl}/${resource.projectADM.shortcode}/${xsltFileValueContent.fileValue.internalFilename}/file"

    } yield xsltUrl

    val recoveredXsltUrlFuture = xsltUrlFuture.mapError { case notFound: NotFoundException =>
      BadRequestException(s"XSL transformation $xslTransformationIri not found: ${notFound.message}")
    }

    for {

      // check if the XSL transformation is in the cache
      xsltFileUrl <- recoveredXsltUrlFuture

      xsltMaybe: Option[String] = xsltCache.get(xsltFileUrl)

      xslt <-
        if (xsltMaybe.nonEmpty) {
          // XSL transformation is cached
          ZIO.attempt(xsltMaybe.get)
        } else {
          for {
            response <-
              sipiService
                .getTextFileRequest(
                  SipiGetTextFileRequest(
                    fileUrl = xsltFileUrl,
                    requestingUser = KnoraSystemInstances.Users.SystemUser,
                    senderName = this.getClass.getName,
                  ),
                )
            _ = xsltCache.put(xsltFileUrl, response.content)
          } yield response.content
        }

    } yield GetXSLTransformationResponseV2(xslt)
  }

  /**
   * Creates a mapping between XML elements and attributes to standoff classes and properties.
   * The mapping is used to convert XML documents to texts with standoff and back.
   *
   * @param xml                  the provided mapping.
   * @param requestingUser       the client that made the request.
   */
  private def createMappingV2(
    xml: String,
    label: String,
    projectIri: ProjectIri,
    mappingName: String,
    requestingUser: User,
    apiRequestID: UUID,
  ): Task[CreateMappingResponseV2] = {

    def createMappingAndCheck(
      xml: String,
      label: String,
      mappingIri: IRI,
      namedGraph: String,
      requestingUser: User,
    ): Task[CreateMappingResponseV2] = {

      val createMappingFuture = for {

        factory <- ZIO.attempt(SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI))

        // get the schema the mapping has to be validated against
        schemaFile = FileUtil.readTextResource("mappingXMLToStandoff.xsd")

        schemaSource = new StreamSource(new StringReader(schemaFile))

        // create a schema instance
        schemaInstance = factory.newSchema(schemaSource)
        validator      = schemaInstance.newValidator()

        // validate the provided mapping
        _ = validator.validate(new StreamSource(new StringReader(xml)))

        // the mapping conforms to the XML schema "src/main/resources/mappingXMLToStandoff.xsd"
        mappingXML = XML.loadString(xml)

        // get the default XSL transformation, if given (optional)
        defaultXSLTransformation <-
          mappingXML \ "defaultXSLTransformation" match {
            case defaultTrans: NodeSeq if defaultTrans.length == 1 =>
              // check if the IRI is valid
              val transIri = Iri
                .validateAndEscapeIri(
                  defaultTrans.headOption
                    .getOrElse(throw BadRequestException("could not access <defaultXSLTransformation>"))
                    .text,
                )
                .getOrElse(
                  throw BadRequestException(s"XSL transformation ${defaultTrans.head.text} is not a valid IRI"),
                )

              // try to obtain the XSL transformation to make sure that it really exists
              for {
                _ <- getXSLTransformation(
                       xslTransformationIri = transIri,
                       requestingUser = requestingUser,
                     )
              } yield Some(transIri)
            case _ => ZIO.none
          }

        // create a collection of a all elements mappingElement
        mappingElementsXML = mappingXML \ "mappingElement"

        mappingElements: Seq[MappingElement] =
          mappingElementsXML.map { (curMappingEle: Node) =>
            // get the name of the XML tag
            val tagName = (curMappingEle \ "tag" \ "name").headOption
              .getOrElse(throw BadRequestException(s"no '<name>' given for node $curMappingEle"))
              .text

            // get the namespace the tag is defined in
            val tagNamespace = (curMappingEle \ "tag" \ "namespace").headOption
              .getOrElse(throw BadRequestException(s"no '<namespace>' given for node $curMappingEle"))
              .text

            // get the class the tag is combined with
            val className = (curMappingEle \ "tag" \ "class").headOption
              .getOrElse(throw BadRequestException(s"no '<classname>' given for node $curMappingEle"))
              .text

            // get the boolean indicating if the element requires a separator in the text once it is converted to standoff
            val separatorBooleanAsString =
              (curMappingEle \ "tag" \ "separatesWords").headOption
                .getOrElse(throw BadRequestException(s"no '<separatesWords>' given for node $curMappingEle"))
                .text

            val separatorRequired: Boolean = ValuesValidator
              .validateBoolean(separatorBooleanAsString)
              .getOrElse(
                throw BadRequestException(
                  s"<separatesWords> could not be converted to Boolean: $separatorBooleanAsString",
                ),
              )

            // get the standoff class IRI
            val standoffClassIri =
              (curMappingEle \ "standoffClass" \ "classIri").headOption
                .getOrElse(throw BadRequestException(s"no '<classIri>' given for node $curMappingEle"))
                .text

            // get a collection containing all the attributes
            val attributeNodes: NodeSeq =
              curMappingEle \ "standoffClass" \ "attributes" \ "attribute"

            val attributes: Seq[MappingXMLAttribute] = attributeNodes.map { curAttributeNode =>
              // get the current attribute's name
              val attrName = (curAttributeNode \ "attributeName").headOption
                .getOrElse(throw BadRequestException(s"no '<attributeName>' given for attribute $curAttributeNode"))
                .text

              val attributeNamespace =
                (curAttributeNode \ "namespace").headOption
                  .getOrElse(throw BadRequestException(s"no '<namespace>' given for attribute $curAttributeNode"))
                  .text

              // get the standoff property IRI for the current attribute
              val propIri = (curAttributeNode \ "propertyIri").headOption
                .getOrElse(throw BadRequestException(s"no '<propertyIri>' given for attribute $curAttributeNode"))
                .text

              MappingXMLAttribute(
                attributeName = Iri
                  .toSparqlEncodedString(attrName)
                  .getOrElse(
                    throw BadRequestException(s"tagname $attrName contains invalid characters"),
                  ),
                namespace = Iri
                  .toSparqlEncodedString(attributeNamespace)
                  .getOrElse(
                    throw BadRequestException(s"tagname $attributeNamespace contains invalid characters"),
                  ),
                standoffProperty = Iri
                  .validateAndEscapeIri(propIri)
                  .getOrElse(
                    throw BadRequestException(s"standoff class IRI $standoffClassIri is not a valid IRI"),
                  ),
                mappingXMLAttributeElementIri = stringFormatter.makeRandomMappingElementIri(mappingIri),
              )

            }

            // get the optional element datatype
            val datatypeMaybe: NodeSeq =
              curMappingEle \ "standoffClass" \ "datatype"

            // if "datatype" is given, get the the standoff class data type and the name of the XML data type attribute
            val standoffDataTypeOption: Option[MappingStandoffDatatypeClass] =
              if (datatypeMaybe.nonEmpty) {
                val dataTypeXML = (datatypeMaybe \ "type").headOption
                  .getOrElse(
                    throw BadRequestException(s"no '<type>' given for datatype"),
                  )
                  .text

                val dataType: StandoffDataTypeClasses.Value =
                  StandoffDataTypeClasses.lookup(
                    dataTypeXML,
                    throw BadRequestException(s"Invalid data type provided for $tagName"),
                  )
                val dataTypeAttribute: String =
                  (datatypeMaybe \ "attributeName").headOption
                    .getOrElse(throw BadRequestException(s"no '<attributeName>' given for datatype"))
                    .text

                Some(
                  MappingStandoffDatatypeClass(
                    datatype = dataType.toString, // safe because it is an enumeration
                    attributeName = Iri
                      .toSparqlEncodedString(dataTypeAttribute)
                      .getOrElse(
                        throw BadRequestException(s"tagname $dataTypeAttribute contains invalid characters"),
                      ),
                    mappingStandoffDataTypeClassElementIri = stringFormatter.makeRandomMappingElementIri(mappingIri),
                  ),
                )
              } else {
                None
              }

            MappingElement(
              tagName = Iri
                .toSparqlEncodedString(tagName)
                .getOrElse(
                  throw BadRequestException(
                    s"tagname $tagName contains invalid characters",
                  ),
                ),
              namespace = Iri
                .toSparqlEncodedString(tagNamespace)
                .getOrElse(
                  throw BadRequestException(
                    s"namespace $tagNamespace contains invalid characters",
                  ),
                ),
              className = Iri
                .toSparqlEncodedString(className)
                .getOrElse(
                  throw BadRequestException(
                    s"classname $className contains invalid characters",
                  ),
                ),
              standoffClass = Iri
                .validateAndEscapeIri(standoffClassIri)
                .getOrElse(
                  throw BadRequestException(
                    s"standoff class IRI $standoffClassIri is not a valid IRI",
                  ),
                ),
              attributes = attributes,
              standoffDataTypeClass = standoffDataTypeOption,
              mappingElementIri = stringFormatter.makeRandomMappingElementIri(mappingIri),
              separatorRequired = separatorRequired,
            )

          }

        // transform mappingElements to the structure that is used internally to convert to or from standoff
        // in order to check for duplicates (checks are done during transformation)
        mappingXMLToStandoff: MappingXMLtoStandoff = transformMappingElementsToMappingXMLtoStandoff(
                                                       mappingElements,
                                                       None,
                                                     )

        // get the standoff entities used in the mapping
        // checks if the standoff classes exist in the ontology
        // checks if the standoff properties exist in the ontology
        // checks if the attributes defined for XML elements have cardinalities for the standoff properties defined on the standoff class
        _ <- getStandoffEntitiesFromMappingV2(mappingXMLToStandoff)

        // check if the mapping IRI already exists
        getExistingMappingSparql = sparql.v2.txt.getMapping(mappingIri)
        existingMappingResponse <- triplestore.query(Construct(getExistingMappingSparql))

        _ = if (existingMappingResponse.statements.nonEmpty) {
              throw BadRequestException(s"mapping IRI $mappingIri already exists")
            }

        createNewMappingSparql = sparql.v2.txt.createNewMapping(
                                   dataNamedGraph = namedGraph,
                                   mappingIri = mappingIri,
                                   label = label,
                                   defaultXSLTransformation = defaultXSLTransformation,
                                   mappingElements = mappingElements,
                                 )
        _ <- triplestore.query(Update(createNewMappingSparql))

        // check if the mapping has been created
        newMappingResponse <- triplestore.query(Construct(getExistingMappingSparql))

        _ = if (newMappingResponse.statements.isEmpty) {
              logger.error(
                s"Attempted a SPARQL update to create a new resource, but it inserted no rows:\n\n$newMappingResponse",
              )
              throw UpdateNotPerformedException(
                s"Resource $mappingIri was not created. Please report this as a possible bug.",
              )
            }

        // get the mapping from the triplestore and cache it thereby
        _ <- getMappingFromTriplestore(mappingIri)
      } yield CreateMappingResponseV2(mappingIri, label, projectIri)

      createMappingFuture.mapError {
        case validationException: SAXException =>
          BadRequestException(s"the provided mapping is invalid: ${validationException.getMessage}")

        case _: IOException => NotFoundException(s"The schema could not be found")

        case unknown: Exception =>
          BadRequestException(s"the provided mapping could not be handled correctly: ${unknown.getMessage}")

      }
    }

    for {
      // Don't allow anonymous users to create a mapping.
      _ <-
        ZIO.attempt {
          if (requestingUser.isAnonymousUser) {
            throw ForbiddenException("Anonymous users aren't allowed to create mappings")
          } else {
            requestingUser.id
          }
        }

      // check if the given project IRI represents an actual project
      projectId <- ZIO
                     .fromEither(KnoraProject.ProjectIri.from(projectIri.toString))
                     .mapError(BadRequestException.apply)
      project <- projectService
                   .findById(projectId)
                   .someOrFail(BadRequestException(s"Project with Iri ${projectIri.toString} does not exist"))

      // TODO: make sure that has sufficient permissions to create a mapping in the given project

      // create the mapping IRI from the project IRI and the name provided by the user
      mappingIri = stringFormatter.makeProjectMappingIri(projectIri.toString, mappingName)
      // put the mapping into the named graph of the project
      namedGraph = ProjectService.projectDataNamedGraphV2(project).value

      result <-
        IriLocker.runWithIriLock(
          apiRequestID,
          s"${projectIri.toString}/mappings",
          createMappingAndCheck(
            xml = xml,
            label = label,
            mappingIri = mappingIri,
            namedGraph = namedGraph,
            requestingUser = requestingUser,
          ),
        )

    } yield result
  }

  /**
   * Transforms a mapping represented as a Seq of [[MappingElement]] to a [[MappingXMLtoStandoff]].
   * This method is called when reading a mapping back from the triplestore.
   *
   * @param mappingElements the Seq of MappingElement to be transformed.
   * @return a [[MappingXMLtoStandoff]].
   */
  private def transformMappingElementsToMappingXMLtoStandoff(
    mappingElements: Seq[MappingElement],
    defaultXSLTransformation: Option[IRI],
  ): MappingXMLtoStandoff = {

    val mappingXMLToStandoff = mappingElements.foldLeft(
      MappingXMLtoStandoff(
        namespace = Map.empty[String, Map[String, Map[String, XMLTag]]],
        defaultXSLTransformation = None,
      ),
    ) { case (acc: MappingXMLtoStandoff, curEle: MappingElement) =>
      // get the name of the XML tag
      val tagname = curEle.tagName

      // get the namespace the tag is defined in
      val namespace = curEle.namespace

      // get the class the tag is combined with
      val classname = curEle.className

      // get tags from this namespace if already existent, otherwise create an empty map
      val namespaceMap: Map[String, Map[String, XMLTag]] =
        acc.namespace.getOrElse(namespace, Map.empty[String, Map[String, XMLTag]])

      // get the standoff class IRI
      val standoffClassIri = curEle.standoffClass

      // get a collection containing all the attributes
      val attributeNodes: Seq[MappingXMLAttribute] = curEle.attributes

      // group attributes by their namespace
      val attributeNodesByNamespace: Map[String, Seq[MappingXMLAttribute]] = attributeNodes.groupBy {
        (attr: MappingXMLAttribute) => attr.namespace
      }

      // create attribute entries for each given namespace
      val attributes: Map[String, Map[String, IRI]] = attributeNodesByNamespace.map {
        case (namespace: String, attrNodes: Seq[MappingXMLAttribute]) =>
          // collect all the attributes for the current namespace
          val attributesInNamespace: Map[String, IRI] = attrNodes.foldLeft(Map.empty[String, IRI]) {
            case (acc: Map[String, IRI], attrEle: MappingXMLAttribute) =>
              // get the current attribute's name
              val attrName = attrEle.attributeName

              // check if the current attribute already exists in this namespace
              if (acc.contains(attrName)) {
                throw BadRequestException("Duplicate attribute name in namespace")
              }

              // get the standoff property IRI for the current attribute
              val propIri = attrEle.standoffProperty

              // add the current attribute to the collection
              acc + (attrName -> propIri)
          }

          namespace -> attributesInNamespace
      }

      // if "datatype" is given, create a `XMLStandoffDataTypeClass`
      val dataTypeOption: Option[XMLStandoffDataTypeClass] = curEle.standoffDataTypeClass match {

        case Some(dataTypeClass: MappingStandoffDatatypeClass) =>
          val dataType =
            StandoffDataTypeClasses.lookup(
              dataTypeClass.datatype,
              throw BadRequestException(s"Invalid data type provided for $tagname"),
            )

          val dataTypeAttribute = dataTypeClass.attributeName

          Some(
            XMLStandoffDataTypeClass(
              standoffDataTypeClass = dataType,
              dataTypeXMLAttribute = dataTypeAttribute,
            ),
          )

        case None => None
      }

      // add the current tag to the map
      val newNamespaceMap: Map[String, Map[String, XMLTag]] = namespaceMap.get(tagname) match {
        case Some(tagMap: Map[String, XMLTag]) =>
          tagMap.get(classname) match {
            case Some(_) => throw BadRequestException("Duplicate tag and classname combination in the same namespace")
            case None    =>
              // create the definition for the current element
              val xmlElementDef = XMLTag(
                name = tagname,
                mapping = XMLTagToStandoffClass(
                  standoffClassIri = standoffClassIri,
                  attributesToProps = attributes,
                  dataType = dataTypeOption,
                ),
                separatorRequired = curEle.separatorRequired,
              )

              // combine the definition for the this classname with the existing definitions beloning to the same element
              val combinedClassDef: Map[String, XMLTag] = namespaceMap(tagname) + (classname -> xmlElementDef)

              // combine all elements for this namespace
              namespaceMap + (tagname -> combinedClassDef)

          }
        case None =>
          namespaceMap + (tagname -> Map(
            classname -> XMLTag(
              name = tagname,
              mapping = XMLTagToStandoffClass(
                standoffClassIri = standoffClassIri,
                attributesToProps = attributes,
                dataType = dataTypeOption,
              ),
              separatorRequired = curEle.separatorRequired,
            ),
          ))
      }

      // recreate the whole structure for all namespaces
      MappingXMLtoStandoff(
        namespace = acc.namespace + (namespace -> newNamespaceMap),
        defaultXSLTransformation = defaultXSLTransformation,
      )

    }

    // invert mapping in order to run checks for duplicate use of
    // standoff class Iris and property Iris in the attributes for a standoff class
    StandoffTagUtilV2.invertXMLToStandoffMapping(mappingXMLToStandoff)

    mappingXMLToStandoff

  }

  /**
   * Gets a mapping either from the cache or by making a request to the triplestore.
   *
   * @param mappingIri           the IRI of the mapping to retrieve.
   * @return a [[MappingXMLtoStandoff]].
   */
  private def getMappingV2(mappingIri: IRI): Task[GetMappingResponseV2] = {

    val mappingFuture: Task[GetMappingResponseV2] =
      mappingCache.get(mappingIri) match {
        case Some(mapping: MappingXMLtoStandoff) =>
          for {
            entities <- getStandoffEntitiesFromMappingV2(mapping)
          } yield GetMappingResponseV2(
            mappingIri = mappingIri,
            mapping = mapping,
            standoffEntities = entities,
          )

        case None =>
          for {
            mapping  <- getMappingFromTriplestore(mappingIri = mappingIri)
            entities <- getStandoffEntitiesFromMappingV2(mapping)
          } yield GetMappingResponseV2(
            mappingIri = mappingIri,
            mapping = mapping,
            standoffEntities = entities,
          )
      }

    val mappingRecovered: Task[GetMappingResponseV2] = mappingFuture.mapError { case e: Exception =>
      BadRequestException(s"An error occurred when requesting mapping $mappingIri: ${e.getMessage}")
    }

    for {
      mapping <- mappingRecovered
    } yield mapping

  }

  /**
   * Gets a mapping from the triplestore.
   *
   * @param mappingIri           the IRI of the mapping to retrieve.
   * @return a [[MappingXMLtoStandoff]].
   */
  private def getMappingFromTriplestore(mappingIri: IRI) =
    for {
      mappingResponse <- triplestore.query(Construct(sparql.v2.txt.getMapping(mappingIri)))

      // if the result is empty, the mapping does not exist
      _ = if (mappingResponse.statements.isEmpty) {
            throw BadRequestException(s"mapping $mappingIri does not exist in triplestore")
          }

      // separate MappingElements from other statements (attributes and datatypes)
      (mappingElementStatements, otherStatements) =
        mappingResponse.statements.partition { case (_: IRI, assertions: Seq[(IRI, String)]) =>
          assertions.contains((OntologyConstants.Rdf.Type, OntologyConstants.KnoraBase.MappingElement))
        }

      mappingElements =
        mappingElementStatements.map { case (subjectIri: IRI, assertions: Seq[(IRI, String)]) =>
          // for convenience (works only for props with cardinality one)
          val assertionsAsMap: Map[IRI, String] = assertions.toMap

          // check for attributes
          val attributes: Seq[MappingXMLAttribute] = assertions.filter { case (propIri, _) =>
            propIri == OntologyConstants.KnoraBase.MappingHasXMLAttribute
          }.map { case (_: IRI, attributeElementIri: String) =>
            val attributeStatementsAsMap: Map[IRI, String] =
              otherStatements(attributeElementIri).toMap

            MappingXMLAttribute(
              attributeName = attributeStatementsAsMap(
                OntologyConstants.KnoraBase.MappingHasXMLAttributename,
              ),
              namespace = attributeStatementsAsMap(
                OntologyConstants.KnoraBase.MappingHasXMLNamespace,
              ),
              standoffProperty = attributeStatementsAsMap(
                OntologyConstants.KnoraBase.MappingHasStandoffProperty,
              ),
              mappingXMLAttributeElementIri = attributeElementIri,
            )
          }

          // check for standoff data type class
          val dataTypeOption: Option[IRI] =
            assertionsAsMap.get(
              OntologyConstants.KnoraBase.MappingHasStandoffDataTypeClass,
            )

          MappingElement(
            tagName = assertionsAsMap(OntologyConstants.KnoraBase.MappingHasXMLTagname),
            namespace = assertionsAsMap(
              OntologyConstants.KnoraBase.MappingHasXMLNamespace,
            ),
            className = assertionsAsMap(OntologyConstants.KnoraBase.MappingHasXMLClass),
            standoffClass = assertionsAsMap(
              OntologyConstants.KnoraBase.MappingHasStandoffClass,
            ),
            mappingElementIri = subjectIri,
            standoffDataTypeClass = dataTypeOption match {
              case Some(dataTypeElementIri: IRI) =>
                val dataTypeAssertionsAsMap: Map[IRI, String] =
                  otherStatements(dataTypeElementIri).toMap

                Some(
                  MappingStandoffDatatypeClass(
                    datatype = dataTypeAssertionsAsMap(
                      OntologyConstants.KnoraBase.MappingHasStandoffClass,
                    ),
                    attributeName = dataTypeAssertionsAsMap(
                      OntologyConstants.KnoraBase.MappingHasXMLAttributename,
                    ),
                    mappingStandoffDataTypeClassElementIri = dataTypeElementIri,
                  ),
                )
              case None => None
            },
            attributes = attributes,
            separatorRequired = assertionsAsMap(
              OntologyConstants.KnoraBase.MappingElementRequiresSeparator,
            ).toBoolean,
          )

        }.toSeq

      // check if there is a default XSL transformation
      defaultXSLTransformationOption =
        otherStatements(mappingIri).find { case (pred: IRI, _: String) =>
          pred == OntologyConstants.KnoraBase.MappingHasDefaultXSLTransformation
        }.map { case (_: IRI, xslTransformationIri: IRI) =>
          xslTransformationIri
        }

      mappingXMLToStandoff =
        transformMappingElementsToMappingXMLtoStandoff(
          mappingElements,
          defaultXSLTransformationOption,
        )

      // add the mapping to the cache
      _ = mappingCache.put(mappingIri, mappingXMLToStandoff)

    } yield mappingXMLToStandoff

  /**
   * Gets the required standoff entities (classes and properties) from the mapping and requests information about these entities from the ontology responder.
   *
   * @param mappingXMLtoStandoff the mapping to be used.
   * @return a [[StandoffEntityInfoGetResponseV2]] holding information about standoff classes and properties.
   */
  private def getStandoffEntitiesFromMappingV2(
    mappingXMLtoStandoff: MappingXMLtoStandoff,
  ): Task[StandoffEntityInfoGetResponseV2] = {

    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    // invert the mapping so standoff class Iris become keys
    val mappingStandoffToXML: Map[IRI, XMLTagItem] = StandoffTagUtilV2.invertXMLToStandoffMapping(mappingXMLtoStandoff)

    // collect standoff class Iris from the mapping
    val standoffTagIrisFromMapping: Set[IRI] = mappingStandoffToXML.keySet

    // collect all the standoff property Iris from the mapping
    val standoffPropertyIrisFromMapping: Set[IRI] = mappingStandoffToXML.values.foldLeft(Set.empty[IRI]) {
      (acc: Set[IRI], xmlTag: XMLTagItem) =>
        acc ++ xmlTag.attributes.keySet
    }

    // make sure that the mapping does not contain system or data type standoff properties as attributes
    // these standoff properties can only be used via the standoff base tag and standoff data type classes
    val systemOrDatatypePropsAsAttr: Set[IRI] = standoffPropertyIrisFromMapping.intersect(
      StandoffProperties.systemProperties ++ StandoffProperties.dataTypeProperties,
    )
    if (systemOrDatatypePropsAsAttr.nonEmpty)
      throw InvalidStandoffException(
        s"attempt to define attributes for system or data type properties: ${systemOrDatatypePropsAsAttr.mkString(", ")}",
      )

    for {

      // request information about standoff classes that should be created
      standoffClassEntities <-
        messageRelay
          .ask[StandoffEntityInfoGetResponseV2](
            StandoffEntityInfoGetRequestV2(
              standoffClassIris = standoffTagIrisFromMapping.map(_.toSmartIri),
            ),
          )

      // check that the ontology responder returned the information for all the standoff classes it was asked for
      // if the ontology responder does not return a standoff class it was asked for, then this standoff class does not exist
      _ = if (standoffTagIrisFromMapping.map(_.toSmartIri) != standoffClassEntities.standoffClassInfoMap.keySet) {
            throw NotFoundException(
              s"the ontology responder could not find information about these standoff classes: ${(standoffTagIrisFromMapping
                  .map(_.toSmartIri) -- standoffClassEntities.standoffClassInfoMap.keySet).mkString(", ")}",
            )
          }

      // get the property Iris that are defined on the standoff classes returned by the ontology responder
      standoffPropertyIrisFromOntologyResponder: Set[SmartIri] =
        standoffClassEntities.standoffClassInfoMap.foldLeft(
          Set.empty[SmartIri],
        ) { case (acc, (_, standoffClassEntity: ReadClassInfoV2)) =>
          val props = standoffClassEntity.allCardinalities.keySet
          acc ++ props
        }

      // request information about the standoff properties
      standoffPropertyEntities <-
        messageRelay
          .ask[StandoffEntityInfoGetResponseV2](
            StandoffEntityInfoGetRequestV2(
              standoffPropertyIris = standoffPropertyIrisFromOntologyResponder,
            ),
          )

      // check that the ontology responder returned the information for all the standoff properties it was asked for
      // if the ontology responder does not return a standoff property it was asked for, then this standoff property does not exist
      propertyDefinitionsFromMappingFoundInOntology: Set[SmartIri] =
        standoffPropertyEntities.standoffPropertyInfoMap.keySet
          .intersect(standoffPropertyIrisFromMapping.map(_.toSmartIri))

      _ <-
        ZIO.fail {
          NotFoundException(
            s"the ontology responder could not find information about these standoff properties: " +
              s"${(standoffPropertyIrisFromMapping.map(_.toSmartIri) -- propertyDefinitionsFromMappingFoundInOntology)
                  .mkString(", ")}",
          )
        }.when(standoffPropertyIrisFromMapping.map(_.toSmartIri) != propertyDefinitionsFromMappingFoundInOntology)

      // check that for each standoff property defined in the mapping element for a standoff class, a corresponding cardinality exists in the ontology
      _ <- ZIO.attempt {
             mappingStandoffToXML.foreach { case (standoffClass: IRI, xmlTag: XMLTagItem) =>
               // collect all the standoff properties defined for this standoff class
               val standoffPropertiesForStandoffClass: Set[SmartIri] = xmlTag.attributes.keySet.map(_.toSmartIri)

               // check that the current standoff class has cardinalities for all the properties defined
               val cardinalitiesFound = standoffClassEntities
                 .standoffClassInfoMap(standoffClass.toSmartIri)
                 .allCardinalities
                 .keySet
                 .intersect(standoffPropertiesForStandoffClass)

               if (standoffPropertiesForStandoffClass != cardinalitiesFound) {
                 throw NotFoundException(
                   s"the following standoff properties have no cardinality for $standoffClass: ${(standoffPropertiesForStandoffClass -- cardinalitiesFound)
                       .mkString(", ")}",
                 )
               }

               // collect the required standoff properties for the standoff class
               val requiredPropsForClass: Set[SmartIri] = standoffClassEntities
                 .standoffClassInfoMap(standoffClass.toSmartIri)
                 .allCardinalities
                 .filter { case (_: SmartIri, card: KnoraCardinalityInfo) =>
                   card.cardinality == ExactlyOne || card.cardinality == AtLeastOne
                 }
                 .keySet -- StandoffProperties.systemProperties.map(
                 _.toSmartIri,
               ) -- StandoffProperties.dataTypeProperties
                 .map(_.toSmartIri)

               // check that all the required standoff properties exist in the mapping
               if (standoffPropertiesForStandoffClass.intersect(requiredPropsForClass) != requiredPropsForClass) {
                 throw NotFoundException(
                   s"the following required standoff properties are not defined for the standoff class $standoffClass: ${(requiredPropsForClass -- standoffPropertiesForStandoffClass)
                       .mkString(", ")}",
                 )
               }

               // check if the standoff class's data type is correct in the mapping
               standoffClassEntities.standoffClassInfoMap(standoffClass.toSmartIri).standoffDataType match {
                 case Some(dataType: StandoffDataTypeClasses.Value) =>
                   // check if this corresponds to the datatype in the mapping
                   val dataTypeFromMapping: XMLStandoffDataTypeClass = xmlTag.tagItem.mapping.dataType.getOrElse(
                     throw InvalidStandoffException(s"no data type provided for $standoffClass, but $dataType required"),
                   )
                   if (dataTypeFromMapping.standoffDataTypeClass != dataType) {
                     throw InvalidStandoffException(
                       s"wrong data type ${dataTypeFromMapping.standoffDataTypeClass} provided for $standoffClass, but $dataType required",
                     )
                   }
                 case None =>
                   if (xmlTag.tagItem.mapping.dataType.nonEmpty) {
                     throw InvalidStandoffException(
                       s"no data type expected for $standoffClass, but ${xmlTag.tagItem.mapping.dataType.get.standoffDataTypeClass} given",
                     )
                   }
               }
             }
           }

    } yield StandoffEntityInfoGetResponseV2(
      standoffClassInfoMap = standoffClassEntities.standoffClassInfoMap,
      standoffPropertyInfoMap = standoffPropertyEntities.standoffPropertyInfoMap,
    )
  }
}

object StandoffResponderV2 {
  val layer =
    ZLayer.fromZIO {
      for {
        ac      <- ZIO.service[AppConfig]
        mr      <- ZIO.service[MessageRelay]
        ts      <- ZIO.service[TriplestoreService]
        cru     <- ZIO.service[ConstructResponseUtilV2]
        stu     <- ZIO.service[StandoffTagUtilV2]
        ps      <- ZIO.service[ProjectService]
        xc      <- ZIO.serviceWithZIO[CacheManager](_.createCache[String, String]("xsltCache"))
        mc      <- ZIO.serviceWithZIO[CacheManager](_.createCache[String, MappingXMLtoStandoff]("mappingCache"))
        sf      <- ZIO.service[StringFormatter]
        ssl     <- ZIO.service[SipiService]
        handler <- mr.subscribe(StandoffResponderV2(ac, mr, ts, cru, stu, ps, xc, mc, ssl)(sf))
      } yield handler
    }
}
