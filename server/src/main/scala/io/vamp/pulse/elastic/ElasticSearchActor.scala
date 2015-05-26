package io.vamp.pulse.elastic

import akka.actor._
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.Bootstrap.{Shutdown, Start}
import io.vamp.common.akka._
import io.vamp.common.vitals.InfoRequest
import io.vamp.pulse.elastic.ElasticsearchActor.{BatchIndex, Index, Search}
import io.vamp.pulse.model.{Event, EventQuery}
import io.vamp.pulse.notification.PulseNotificationProvider

import scala.language.postfixOps

object ElasticsearchActor extends ActorDescription {

  def props(args: Any*): Props = Props[ElasticsearchActor]

  case class Index(event: Event)

  case class BatchIndex(events: List[Event])

  case class Search(query: EventQuery)

}

class ElasticsearchActor extends CommonActorSupport with PulseNotificationProvider {

  private val configuration = ConfigFactory.load().getConfig("vamp.pulse.elasticsearch")

  private lazy val elasticsearch = if (configuration.getString("type").toLowerCase == "embedded")
    new EmbeddedElasticsearchServer(configuration.getConfig("embedded"))
  else
    new RemoteElasticsearchServer(configuration.getConfig("remote"))

  def receive: Receive = {
    case Start => elasticsearch.start()

    case Shutdown => elasticsearch.shutdown()

    case InfoRequest =>
      sender ! "You Know, for Search..."
    // http://localhost:9200/_nodes/process?pretty

    case BatchIndex(events) =>
      println(s"events: $events")
      sender ! events

    case Index(event) =>
      println(s"event: $event")
      sender ! event

    case Search(query) =>
      println(s"query: $query")
      sender ! query
  }
}
