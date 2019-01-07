package org.knora.webapi.app

import akka.actor.{Actor, ActorLogging, ActorRef, Timers}
import org.knora.webapi._
import org.knora.webapi.messages.app.appmessages.AppState.AppState
import org.knora.webapi.messages.app.appmessages._
import org.knora.webapi.messages.store.triplestoremessages.{CheckRepositoryRequest, CheckRepositoryResponse, RepositoryStatus}
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.ontologymessages.LoadOntologiesRequestV2
import org.knora.webapi.util.CacheUtil

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._

class ApplicationStateActor(responderManager: ActorRef, storeManager: ActorRef) extends Actor with Timers with ActorLogging {

    log.debug("entered the ApplicationStateActor constructor")

    val executionContext: ExecutionContext = context.system.dispatchers.lookup(KnoraDispatchers.KnoraBlockingDispatcher)

    // the prometheus, zipkin, jaeger, datadog, and printConfig flags can be set via application.conf and via command line parameter
    val settings: SettingsImpl = Settings(context.system)

    private var appState: AppState = AppState.Stopped
    private var allowReloadOverHTTPState = false
    private var prometheusReporterState = false
    private var zipkinReporterState = false
    private var jaegerReporterState = false
    private var printConfigState = false
    private var skipOntologies = true

    def receive: PartialFunction[Any, Unit] = {

        case ActorReady() => {
            sender ! ActorReadyAck()
        }

        case SetAllowReloadOverHTTPState(value) => {
            log.debug("ApplicationStateActor - SetAllowReloadOverHTTPState - value: {}", value)
            allowReloadOverHTTPState = value
        }
        case GetAllowReloadOverHTTPState() => {
            log.debug("ApplicationStateActor - GetAllowReloadOverHTTPState - value: {}", allowReloadOverHTTPState)
            sender ! allowReloadOverHTTPState
        }
        case SetPrometheusReporterState(value) => {
            log.debug("ApplicationStateActor - SetPrometheusReporterState - value: {}", value)
            prometheusReporterState = value
        }
        case GetPrometheusReporterState() => {
            log.debug("ApplicationStateActor - GetPrometheusReporterState - value: {}", prometheusReporterState)
            sender ! (prometheusReporterState | settings.prometheusReporter)
        }
        case SetZipkinReporterState(value) => {
            log.debug("ApplicationStateActor - SetZipkinReporterState - value: {}", value)
            zipkinReporterState = value
        }
        case GetZipkinReporterState() => {
            log.debug("ApplicationStateActor - GetZipkinReporterState - value: {}", zipkinReporterState)
            sender ! (zipkinReporterState | settings.zipkinReporter)
        }
        case SetJaegerReporterState(value) => {
            log.debug("ApplicationStateActor - SetJaegerReporterState - value: {}", value)
            jaegerReporterState = value
        }
        case GetJaegerReporterState() => {
            log.debug("ApplicationStateActor - GetJaegerReporterState - value: {}", jaegerReporterState)
            sender ! (jaegerReporterState | settings.jaegerReporter)
        }
        case SetPrintConfigExtendedState(value) => {
            log.debug("ApplicationStateActor - SetPrintConfigExtendedState - value: {}", value)
            printConfigState = value
        }
        case GetPrintConfigExtendedState() => {
            log.debug("ApplicationStateActor - GetPrintConfigExtendedState - value: {}", printConfigState)
            sender ! (printConfigState | settings.printExtendedConfig)
        }

        case SetAppState(value: AppState) => {

            appState = value

            log.info("appStateChanged - to state: {}", value)

            value match {
                // case AppState.Stopped => self ! SetAppState(AppState.StartingUp)
                case AppState.StartingUp => self ! SetAppState(AppState.WaitingForRepository)
                case AppState.WaitingForRepository => self ! CheckRepository() // check DB
                case AppState.RepositoryReady =>  self ! SetAppState(AppState.CreatingCaches)
                case AppState.CreatingCaches => self ! CreateCaches()
                case AppState.CachesReady => self ! SetAppState(AppState.LoadingOntologies)
                case AppState.LoadingOntologies if skipOntologies => self ! SetAppState(AppState.OntologiesReady)  // skipping loading of ontologies
                case AppState.LoadingOntologies if !skipOntologies => self ! LoadOntologies() // load ontologies
                case AppState.OntologiesReady => self ! SetAppState(AppState.Running)
                case AppState.Running => printWelcomeMsg()
                case value => throw UnsupportedValueException(s"The value: $value is not supported.")
            }
        }

        case GetAppState() => {
            log.debug("ApplicationStateActor - GetAppState - value: {}", appState)
            sender ! appState
        }

        case InitStartUp(skipLoadingOfOntologies) => {
            log.debug("ApplicationStateActor - InitApp")

            if (appState == AppState.Stopped) {
                skipOntologies = skipLoadingOfOntologies
                self ! SetAppState(AppState.StartingUp)
            }
        }

        case CheckRepository() => {
            storeManager ! CheckRepositoryRequest()
        }

        case CheckRepositoryResponse(status, message) => {
            status match {
                case RepositoryStatus.ServiceAvailable =>
                    self ! SetAppState(AppState.RepositoryReady)
                case RepositoryStatus.NotInitialized =>
                    log.info(s"ApplicationStateActor - checkRepository - status: {}, message: {}", status, message)
                    log.info("Please initialize repository.")
                    timers.startSingleTimer("CheckRepository", CheckRepository(), 5.seconds)
                case RepositoryStatus.ServiceUnavailable =>
                    timers.startSingleTimer("CheckRepository", CheckRepository(), 5.seconds)
            }
        }

        case CreateCaches() => {
            CacheUtil.createCaches(settings.caches)
            self ! SetAppState(AppState.CachesReady)
        }

        case LoadOntologies() => {
            responderManager !  LoadOntologiesRequestV2(KnoraSystemInstances.Users.SystemUser)
        }

        case SuccessResponseV2(message) => {
            self ! SetAppState(AppState.OntologiesReady)
        }
    }

    override def postStop(): Unit = {
        super.postStop()
        log.debug("ApplicationStateActor - postStop called")
    }

    /**
      * Prints the welcome message
      */
    private def printWelcomeMsg(): Unit = {

        println("")
        println("================================================================")
        println(s"Knora API Server started at http://${settings.internalKnoraApiHost}:${settings.internalKnoraApiPort}")
        println("----------------------------------------------------------------")

        if (allowReloadOverHTTPState) {
            println("WARNING: Resetting Triplestore Content over HTTP is turned ON.")
            println("----------------------------------------------------------------")
        }

        // which repository are we using
        println(s"DB-Name: ${settings.triplestoreDatabaseName}")
        println(s"DB-Type: ${settings.triplestoreType}")
        println(s"DB Server: ${settings.triplestoreHost}, DB Port: ${settings.triplestorePort}")


        if (printConfigState) {

            println(s"DB User: ${settings.triplestoreUsername}")
            println(s"DB Password: ${settings.triplestorePassword}")

            println(s"Swagger Json: ${settings.externalKnoraApiBaseUrl}/api-docs/swagger.json")
            println(s"Webapi internal URL: ${settings.internalKnoraApiBaseUrl}")
            println(s"Webapi external URL: ${settings.externalKnoraApiBaseUrl}")
            println(s"Sipi internal URL: ${settings.internalSipiBaseUrl}")
            println(s"Sipi external URL: ${settings.externalSipiBaseUrl}")
        }

        println("================================================================")
        println("")
    }
}
