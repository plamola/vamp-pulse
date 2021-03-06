package io.vamp.pulse.client

import java.time.OffsetDateTime

import io.vamp.common.akka.ExecutionContextProvider
import io.vamp.common.http.RestClient
import io.vamp.common.json.OffsetDateTimeSerializer
import io.vamp.pulse.model._
import org.json4s.DefaultFormats
import org.json4s.ext.EnumNameSerializer

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag


class PulseClient(val url: String)(implicit executionContext: ExecutionContext) {

  def info: Future[Any] = RestClient.get[Any](s"$url/api/v1/info")

  def sendEvent(event: Event): Future[Event] = RestClient.post[Event](s"$url/api/v1/events", event)

  def getEvents(tags: Set[String], from: Option[OffsetDateTime] = None, to: Option[OffsetDateTime] = None, includeLower: Boolean = true, includeUpper: Boolean = true): Future[List[Event]] = {
    query[List[Event]](EventQuery(tags = tags, Some(TimeRange.from(from, to, includeLower, includeUpper))))
  }

  def query[T <: Any : ClassTag](query: EventQuery)(implicit mf: scala.reflect.Manifest[T]): Future[T] = {
    implicit val formats = DefaultFormats + new OffsetDateTimeSerializer() + new EnumNameSerializer(Aggregator)
    RestClient.post[T](s"$url/api/v1/events/get", query)
  }
}

trait PulseClientProvider {
  this: ExecutionContextProvider =>

  protected def pulseUrl: String

  lazy val pulseClient = new PulseClient(pulseUrl)
}
