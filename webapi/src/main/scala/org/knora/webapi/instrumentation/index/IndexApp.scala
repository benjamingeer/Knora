package org.knora.webapi.instrumentation.index

import zhttp.html._
import zhttp.http._

/**
 * Provides the '/' endpoint serving a small index page.
 */
object IndexApp {

  def apply(): HttpApp[Any, Nothing] =
    Http.collect[Request] { case Method.GET -> !! => Response.html(Html.fromString(indexPage)) }

  private val indexPage =
    """<html>
      |<title>Simple Server</title>
      |<body>
      |<p><a href="/metrics">Prometheus Metrics</a></p>
      |</body
      |</html>""".stripMargin

}
