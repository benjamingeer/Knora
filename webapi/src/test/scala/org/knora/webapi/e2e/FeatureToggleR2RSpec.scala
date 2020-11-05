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

package org.knora.webapi.e2e

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives.{get, path}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.http.scaladsl.util.FastFuture
import com.typesafe.config.ConfigFactory
import org.knora.webapi.{R2RSpec, TestContainers}
import org.knora.webapi.feature._
import org.knora.webapi.routing.{KnoraRoute, KnoraRouteData, KnoraRouteFactory}

import scala.concurrent.ExecutionContextExecutor

/**
 * Tests feature toggles that replace implementations of API routes.
 */
class FeatureToggleR2RSpec extends R2RSpec {
    // Don't take feature toggles from application.conf, just take them from the config in this spec.
    override implicit lazy val _system: ActorSystem = ActorSystem(actorSystemNameFrom(getClass),
        TestContainers.PortConfig.withFallback(ConfigFactory.parseString(testConfigSource).withFallback(ConfigFactory.load().withoutPath("app.feature-toggles"))))

    // Some feature toggles for testing.
    override def testConfigSource: String =
        """app {
          |    feature-toggles {
          |        new-foo {
          |            description = "Replace the old foo routes with new ones."
          |
          |            available-versions = [ 1, 2 ]
          |            default-version = 1
          |            enabled-by-default = yes
          |            override-allowed = yes
          |
          |            developer-emails = [
          |                "Benjamin Geer <benjamin.geer@dasch.swiss>"
          |            ]
          |        }
          |
          |        new-bar {
          |            description = "Replace the old bar routes with new ones."
          |
          |            available-versions = [ 1 ]
          |            default-version = 1
          |            enabled-by-default = yes
          |            override-allowed = yes
          |
          |            developer-emails = [
          |                "Benjamin Geer <benjamin.geer@dasch.swiss>"
          |            ]
          |        }
          |
          |        new-baz {
          |            description = "Replace the old baz routes with new ones."
          |
          |            available-versions = [ 1 ]
          |            default-version = 1
          |            enabled-by-default = no
          |            override-allowed = no
          |
          |            developer-emails = [
          |                "Benjamin Geer <benjamin.geer@dasch.swiss>"
          |            ]
          |        }
          |    }
          |}
        """.stripMargin

    /**
     * A test implementation of a route feature that handles HTTP GET requests.
     *
     * @param pathStr     the route path.
     * @param featureName the name of the feature.
     * @param routeData   a [[KnoraRouteData]] providing access to the application.
     */
    class TestRouteFeature(pathStr: String, featureName: String, routeData: KnoraRouteData) extends KnoraRoute(routeData) with Feature {
        override def makeRoute(featureFactoryConfig: FeatureFactoryConfig): Route = path(pathStr) {
            get {
                requestContext =>
                    // Return an HTTP response that says which feature implementation is being used.
                    val httpResponse = FastFuture.successful {
                        featureFactoryConfig.addHeaderToHttpResponse(
                            HttpResponse(
                                status = StatusCodes.OK,
                                entity = HttpEntity(
                                    contentType = ContentTypes.`application/json`,
                                    string = s"You are using $featureName"
                                )
                            )
                        )
                    }

                    requestContext.complete(httpResponse)
            }
        }
    }

    /**
     * A feature factory that constructs implementations of [[FooRoute]].
     */
    class FooRouteFeatureFactory(routeData: KnoraRouteData) extends KnoraRouteFactory(routeData)
        with FeatureFactory {

        // A trait for version numbers of the new 'foo' feature.
        sealed trait NewFooVersion extends Version

        // Represents version 1 of the new 'foo' feature.
        case object NEW_FOO_1 extends NewFooVersion

        // Represents version 2 of the new 'foo' feature.
        case object NEW_FOO_2 extends NewFooVersion

        // The old 'foo' feature implementation.
        private val oldFoo = new TestRouteFeature(pathStr = "foo", featureName = "the old foo", routeData = routeData)

        // The new 'foo' feature implementation, version 1.
        private val newFoo1 = new TestRouteFeature(pathStr = "foo", featureName = "the new foo, version 1", routeData = routeData)

        // The new 'foo' feature implementation, version 2.
        private val newFoo2 = new TestRouteFeature(pathStr = "foo", featureName = "the new foo, version 2", routeData = routeData)

        /**
         * Constructs an implementation of the 'foo' route according to the feature factory
         * configuration.
         *
         * @param featureFactoryConfig the per-request feature factory configuration.
         * @return a route configured with the features enabled by the feature factory configuration.
         */
        def makeRoute(featureFactoryConfig: FeatureFactoryConfig): Route = {
            // Get the 'new-foo' feature toggle.
            val fooToggle: FeatureToggle = featureFactoryConfig.getToggle("new-foo")

            // Choose a route according to the toggle state.
            val route: KnoraRoute = fooToggle.getMatchableState(NEW_FOO_1, NEW_FOO_2) match {
                case Off => oldFoo
                case On(NEW_FOO_1) => newFoo1
                case On(NEW_FOO_2) => newFoo2
            }

            // Ask the route implementation for its routing function, and return that function.
            route.makeRoute(featureFactoryConfig)
        }
    }

    /**
     * A feature factory that constructs implementations of [[BarRoute]].
     */
    class BarRouteFeatureFactory(routeData: KnoraRouteData) extends KnoraRouteFactory(routeData)
        with FeatureFactory {

        // The old 'bar' feature implementation.
        private val oldBar = new TestRouteFeature(pathStr = "bar", featureName = "the old bar", routeData = routeData)

        // The new 'bar' feature implementation.
        private val newBar = new TestRouteFeature(pathStr = "bar", featureName = "the new bar", routeData = routeData)

        def makeRoute(featureFactoryConfig: FeatureFactoryConfig): Route = {
            // Is the 'new-bar' feature toggle enabled?
            val route: KnoraRoute = if (featureFactoryConfig.getToggle("new-bar").isEnabled) {
                // Yes. Use the new implementation.
                newBar
            } else {
                // No. Use the old implementation.
                oldBar
            }

            // Ask the route implementation for its routing function, and return that function.
            route.makeRoute(featureFactoryConfig)
        }
    }

    /**
     * A feature factory that constructs implementations of [[BazRoute]].
     */
    class BazRouteFeatureFactory(routeData: KnoraRouteData) extends KnoraRouteFactory(routeData)
        with FeatureFactory {

        // The old 'baz' feature implementation.
        private val oldBaz = new TestRouteFeature(pathStr = "baz", featureName = "the old baz", routeData = routeData)

        // The new 'baz' feature implementation.
        private val newBaz = new TestRouteFeature(pathStr = "baz", featureName = "the new baz", routeData = routeData)

        def makeRoute(featureFactoryConfig: FeatureFactoryConfig): Route = {
            // Is the 'new-baz' feature toggle enabled?
            val route: KnoraRoute = if (featureFactoryConfig.getToggle("new-baz").isEnabled) {
                // Yes. Use the new implementation.
                newBaz
            } else {
                // No. Use the old implementation.
                oldBaz
            }

            route.makeRoute(featureFactoryConfig)
        }
    }

    /**
     * A façade route that uses implementations constructed by [[FooRouteFeatureFactory]].
     */
    class FooRoute(routeData: KnoraRouteData) extends KnoraRoute(routeData) {
        private val featureFactory = new FooRouteFeatureFactory(routeData)

        override def makeRoute(featureFactoryConfig: FeatureFactoryConfig): Route = {
            featureFactory.makeRoute(featureFactoryConfig)
        }
    }

    /**
     * A façade route that uses implementations constructed by [[BarRouteFeatureFactory]].
     */
    class BarRoute(routeData: KnoraRouteData) extends KnoraRoute(routeData) {
        private val featureFactory = new BarRouteFeatureFactory(routeData)

        override def makeRoute(featureFactoryConfig: FeatureFactoryConfig): Route = {
            featureFactory.makeRoute(featureFactoryConfig)
        }
    }

    /**
     * A façade route that uses implementations constructed by [[BazRouteFeatureFactory]].
     */
    class BazRoute(routeData: KnoraRouteData) extends KnoraRoute(routeData) {
        private val featureFactory = new BazRouteFeatureFactory(routeData)

        override def makeRoute(featureFactoryConfig: FeatureFactoryConfig): Route = {
            featureFactory.makeRoute(featureFactoryConfig)
        }
    }

    // The façade route instances that we are going to test.
    private val fooRoute = new FooRoute(routeData).knoraApiPath
    private val bazRoute = new BazRoute(routeData).knoraApiPath

    implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(settings.defaultTimeout)

    implicit val ec: ExecutionContextExecutor = system.dispatcher

    /**
     * Parses the HTTP response header that lists the configured feature toggles.
     *
     * @param response the HTTP response.
     * @return a string per toggle.
     */
    private def parseResponseHeader(response: HttpResponse): Set[String] = {
        response.headers.find(_.lowercaseName == FeatureToggle.RESPONSE_HEADER_LOWERCASE) match {
            case Some(header) => header.value.split(',').toSet
            case None => Set.empty
        }
    }

    "The feature toggle framework" should {
        "use default toggles" in {
            Get(s"/foo") ~> fooRoute ~> check {
                val responseStr = responseAs[String]
                assert(status == StatusCodes.OK, responseStr)
                assert(responseStr == "You are using the new foo, version 1")
                assert(parseResponseHeader(response) == Set("new-foo:1=on", "new-bar:1=on", "new-baz=off"))
            }
        }

        "turn off a toggle" in {
            Get(s"/foo").addHeader(RawHeader(FeatureToggle.REQUEST_HEADER, "new-foo=off")) ~> fooRoute ~> check {
                val responseStr = responseAs[String]
                assert(status == StatusCodes.OK, responseStr)
                assert(responseStr == "You are using the old foo")
                assert(parseResponseHeader(response) == Set("new-foo=off", "new-bar:1=on", "new-baz=off"))
            }
        }

        "override the default toggle version" in {
            Get(s"/foo").addHeader(RawHeader(FeatureToggle.REQUEST_HEADER, "new-foo:2=on")) ~> fooRoute ~> check {
                val responseStr = responseAs[String]
                assert(status == StatusCodes.OK, responseStr)
                assert(responseStr == "You are using the new foo, version 2")
                assert(parseResponseHeader(response) == Set("new-foo:2=on", "new-bar:1=on", "new-baz=off"))
            }
        }

        "not enable a toggle without specifying the version number" in {
            Get(s"/foo").addHeader(RawHeader(FeatureToggle.REQUEST_HEADER, "new-foo=on")) ~> fooRoute ~> check {
                val responseStr = responseAs[String]
                assert(status == StatusCodes.BadRequest, responseStr)
                assert(responseStr.contains("You must specify a version number to enable feature toggle new-foo"))
            }
        }

        "not enable a nonexistent version of a toggle" in {
            Get(s"/foo").addHeader(RawHeader(FeatureToggle.REQUEST_HEADER, "new-foo:3=on")) ~> fooRoute ~> check {
                val responseStr = responseAs[String]
                assert(status == StatusCodes.BadRequest, responseStr)
                assert(responseStr.contains("Feature toggle new-foo has no version 3"))
            }
        }

        "not accept a version number when disabling a toggle" in {
            Get(s"/foo").addHeader(RawHeader(FeatureToggle.REQUEST_HEADER, "new-foo:2=off")) ~> fooRoute ~> check {
                val responseStr = responseAs[String]
                assert(status == StatusCodes.BadRequest, responseStr)
                assert(responseStr.contains("You cannot specify a version number when disabling feature toggle new-foo"))
            }
        }

        "not override a default toggle if the base configuration doesn't allow it" in {
            Get(s"/baz").addHeader(RawHeader(FeatureToggle.REQUEST_HEADER, "new-baz=on")) ~> bazRoute ~> check {
                val responseStr = responseAs[String]
                assert(status == StatusCodes.BadRequest, responseStr)
                assert(responseStr.contains("Feature toggle new-baz cannot be overridden"))
            }
        }

        "not accept two settings for the same toggle" in {
            Get(s"/baz").addHeader(RawHeader(FeatureToggle.REQUEST_HEADER, "new-foo=off,new-foo:2=on")) ~> bazRoute ~> check {
                val responseStr = responseAs[String]
                assert(status == StatusCodes.BadRequest, responseStr)
                assert(responseStr.contains("You cannot set the same feature toggle more than once per request"))
            }
        }
    }
}
