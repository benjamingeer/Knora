package org.knora.webapi.routing.v2

import java.util.UUID

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{PathMatcher, Route}
import org.apache.jena.graph.Graph
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.{ApiV2Complex, InternalSchema}
import org.knora.webapi.messages.v2.responder.metadatamessages.{MetadataGetRequestV2, MetadataPutRequestV2}
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilV2}

import scala.concurrent.Future

object MetadataRouteV2 {
    val MetadataBasePath: PathMatcher[Unit] = PathMatcher("v2" / "metadata")
}
/**
 * Provides a routing function for API v2 routes that deal with metadata.
 */
class MetadataRouteV2(routeData: KnoraRouteData) extends KnoraRoute(routeData) with Authenticator {

    import MetadataRouteV2._

    /**
     * Returns the route.
     */
    override def makeRoute(featureFactoryConfig: FeatureFactoryConfig): Route =
        getMetadata(featureFactoryConfig) ~
            setMetadata(featureFactoryConfig)

    /**
     * Route to get metadata.
     */
    private def getMetadata(featureFactoryConfig: FeatureFactoryConfig): Route = path(MetadataBasePath / Segment) { projectIri =>
        get {
            requestContext => {
                // Make the request message.
                val requestMessageFuture: Future[MetadataGetRequestV2] = for {
                    requestingUser <- getUserADM(requestContext)
                    project <- getProjectADM(projectIri, requestingUser)
                } yield MetadataGetRequestV2(
                    projectADM = project,
                    requestingUser = requestingUser
                )

                // Send it to the responder.
                RouteUtilV2.runRdfRouteWithFuture(
                    requestMessageF = requestMessageFuture,
                    requestContext = requestContext,
                    featureFactoryConfig = featureFactoryConfig,
                    settings = settings,
                    responderManager = responderManager,
                    log = log,
                    targetSchema = InternalSchema,
                    schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
                )
            }
        }
    }

    /**
     * Route to set a project's metadata, replacing any existing metadata for the project.
     */
    private def setMetadata(featureFactoryConfig: FeatureFactoryConfig): Route = path(MetadataBasePath / Segment) { projectIri =>
        put {
            entity(as[String]) { entityStr =>
                requestContext => {
                    // Parse the request to a Jena Graph.
                    val requestGraph: Graph = RouteUtilV2.requestToJenaGraph(
                        entityStr = entityStr,
                        requestContext = requestContext
                    )

                    // Make the request message.
                    val requestMessageFuture: Future[MetadataPutRequestV2] = for {
                        requestingUser <- getUserADM(requestContext)
                        project <- getProjectADM(projectIri, requestingUser)
                    } yield MetadataPutRequestV2(
                        graph = requestGraph,
                        projectADM = project,
                        requestingUser = requestingUser,
                        apiRequestID = UUID.randomUUID
                    )

                    // Send it to the responder.
                    RouteUtilV2.runRdfRouteWithFuture(
                        requestMessageF = requestMessageFuture,
                        requestContext = requestContext,
                        featureFactoryConfig = featureFactoryConfig,
                        settings = settings,
                        responderManager = responderManager,
                        log = log,
                        targetSchema = ApiV2Complex,
                        schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
                    )
                }
            }
        }
    }
}
