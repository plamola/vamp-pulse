package io.vamp.pulse.api

import io.vamp.pulse.eventstream.message.{ElasticEvent, Event, Metric}
import ElasticEvent._
import io.vamp.pulse.eventstream.message.Event
import io.vamp.pulse.storage.engine.{AggregationResult, ResultList, ElasticEventDAO}
import io.vamp.pulse.eventstream.message.{Event, Metric}
import io.vamp.pulse.util.Serializers
import org.elasticsearch.action.index.IndexResponse
import org.json4s._
import spray.http.CacheDirectives.`no-store`
import spray.http.HttpHeaders.{RawHeader, `Cache-Control`}
import spray.http.MediaTypes._
import spray.http.StatusCodes._
import scala.util.{Failure, Success}
import spray.http.StatusCodes.{Success => SuccessCode}
import spray.httpx.Json4sSupport
import spray.routing.Directives._
import spray.routing.Route
import org.json4s.native.JsonMethods._

import scala.concurrent.ExecutionContext

class Routes(val eventDao: ElasticEventDAO)(implicit val executionContext: ExecutionContext) extends Json4sSupport {

  protected def jsonResponse = respondWithMediaType(`application/json`) | respondWithHeaders(`Cache-Control`(`no-store`), RawHeader("Pragma", "no-cache"))

  override implicit def json4sFormats: Formats =  Serializers.formats

  def route: Route = jsonResponse {
    pathPrefix("api" / "v1") {
      path("events" / "reset") {
        pathEndOrSingleSlash {
          get {
            requestEntityEmpty {
              ctx => {
                eventDao.cleanupEvents
                ctx.complete(OK)
              }
            }
          }
        }
      } ~
      path("events" / "get") {
        pathEndOrSingleSlash {
          post {
            entity(as[EventQuery]) {
              request =>
                onSuccess(eventDao.getEvents(request)){
                case ResultList(list) => complete(OK, list.map(_.convertOutput))
                case AggregationResult(map) => complete(OK, map)
                case _ => complete(BadRequest)
              }
            }
          }
        }
      }  ~
      path("events") {
        pathEndOrSingleSlash {
          post {
            entity(as[Metric]) {
              request => onSuccess(eventDao.insert(request)){
                case resp: IndexResponse => complete(Created, request)
              }
            }
          } ~
          post {
              entity(as[Event]) {
                request => onSuccess(eventDao.insert(request)){
                  case resp: IndexResponse => complete(Created, request)           }
              }
          }
        }

      }
    }
  }

}