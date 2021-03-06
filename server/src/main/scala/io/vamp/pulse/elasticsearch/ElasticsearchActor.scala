package io.vamp.pulse.elasticsearch

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

import akka.actor._
import akka.util.Timeout
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s._
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.Bootstrap.{Shutdown, Start}
import io.vamp.common.akka._
import io.vamp.common.http.{OffsetEnvelope, OffsetRequestEnvelope, OffsetResponseEnvelope, RestClient}
import io.vamp.common.vitals.InfoRequest
import io.vamp.pulse.http.PulseSerializationFormat
import io.vamp.pulse.model._
import io.vamp.pulse.notification._
import org.elasticsearch.index.mapper.MapperParsingException
import org.elasticsearch.search.aggregations.bucket.filter.InternalFilter
import org.elasticsearch.search.aggregations.metrics.InternalNumericMetricsAggregation
import org.elasticsearch.search.sort.SortOrder
import org.elasticsearch.transport.RemoteTransportException
import org.json4s.native.JsonMethods._

import scala.collection.JavaConverters._
import scala.collection.Seq
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

object ElasticsearchActor extends ActorDescription {

  val configuration = ConfigFactory.load().getConfig("vamp.pulse.elasticsearch")

  val timeout = Timeout(configuration.getInt("response-timeout") seconds)

  val defaultIndexName = configuration.getString("index.name-prefix")

  val restApiUrl = configuration.getString("rest-api-url")

  def props(args: Any*): Props = Props[ElasticsearchActor]


  case class EventRequestEnvelope(request: EventQuery, page: Int, perPage: Int) extends OffsetRequestEnvelope[EventQuery]

  case class EventResponseEnvelope(response: List[Event], total: Long, page: Int, perPage: Int) extends OffsetResponseEnvelope[Event]


  object StartIndexing

  case class Index(event: Event)

  case class BatchIndex(events: Seq[Event])

  case class Search(query: EventRequestEnvelope)

}

class ElasticsearchActor extends CommonSupportForActors with PulseNotificationProvider {

  import CustomObjectSource._
  import ElasticsearchActor._

  implicit val timeout = ElasticsearchActor.timeout

  private val indexTimeFormat: Map[String, String] = configuration.getConfig("index.time-format").entrySet.asScala.map { entry =>
    entry.getKey -> entry.getValue.unwrapped.toString
  } toMap

  private lazy val elasticsearch = new ElasticsearchServer(configuration)

  private var indexingAllowed = false

  def receive: Receive = {

    case InfoRequest => info()

    case StartIndexing =>
      indexingAllowed = true
      log.info(s"Starting with indexing.")

    case Index(event) => if (indexingAllowed) replyWith(insertEvent(event) map { _ => event })

    case BatchIndex(events) => if (indexingAllowed) replyWith(insertEvent(events) map { _ => events })

    case Search(query) => replyWith(queryEvents(query))

    case Start => start()

    case Shutdown => shutdown()
  }

  private def info() = {
    val receiver = sender()
    RestClient.get[Any](s"$restApiUrl").map(response => receiver ! response)
  }

  private def start() = {
    elasticsearch.start()
    actorFor(ElasticsearchInitializationActor) ! ElasticsearchInitializationActor.Initialize
  }

  private def shutdown() = {
    RestClient.post[Any](s"$restApiUrl/$defaultIndexName-*/_flush", "").map {
      response => elasticsearch.shutdown()
    }
  }

  private def replyWith(callback: => Future[_]): Unit = try {
    sender ! offload(callback)
  } catch {
    case e: Exception => sender ! e
  }

  private def insertEvent(event: Event) = {
    if (event.tags.isEmpty) throwException(EmptyEventError)

    val (indexName, typeName) = indexTypeName(event)

    elasticsearch.client.execute {
      indexEvent(indexName, typeName, event)
    } map { response =>
      if (!response.isCreated) throwException(EventIndexError) else response
    } recoverWith {
      case e: RemoteTransportException => e.getCause match {
        case t: MapperParsingException => throwException(MappingErrorNotification(e.getCause, event.`type`))
      }
    }
  }

  private def insertEvent(eventList: Seq[Event]) = {
    elasticsearch.client.execute {
      bulk(
        eventList.filter(_.tags.nonEmpty).map { event =>
          val (indexName, typeName) = indexTypeName(event)
          indexEvent(indexName, typeName, event)
        }
      )
    }
  }

  private def indexEvent(indexName: String, typeName: String, event: Event) = {
    index into(indexName, typeName) doc Event.expandTags(event)
  }

  private def indexTypeName(event: Event): (String, String) = {
    val schema = event.`type`
    val format = indexTimeFormat.getOrElse(schema, indexTimeFormat.getOrElse("event", "YYYY-MM-dd"))
    val time = OffsetDateTime.now().format(DateTimeFormatter.ofPattern(format))

    s"$defaultIndexName-$schema-$time" -> schema
  }

  private def queryEvents(envelope: EventRequestEnvelope): Future[_] = {
    val eventQuery = envelope.request

    eventQuery.timestamp.foreach { time =>
      if ((time.lt.isDefined && time.lte.isDefined) || (time.gt.isDefined && time.gte.isDefined)) throwException(EventQueryTimeError)
    }

    try {
      eventQuery.aggregator match {
        case None => getEvents(envelope)
        case Some(Aggregator(Some(Aggregator.`count`), _)) => countEvents(eventQuery)
        case Some(aggregator) => aggregateEvents(eventQuery)
      }
    }
    catch {
      case e: Exception => throwException(EventQueryError)
    }
  }

  private def getEvents(envelope: EventRequestEnvelope) = {
    val (page, perPage) = OffsetEnvelope.normalize(envelope.page, envelope.perPage, 30)

    searchEvents(envelope.request, (page - 1) * perPage, perPage) map {
      response =>
        implicit val formats = PulseSerializationFormat.default
        EventResponseEnvelope(response.getHits.hits().map { hit =>
          parse(hit.sourceAsString()).extract[Event]
        } toList, response.getHits.totalHits, page, perPage)
    } recoverWith {
      case e: Exception => throwException(EventQueryError)
    }
  }

  private def countEvents(eventQuery: EventQuery) = {
    searchEvents(eventQuery, 0, 0) map {
      response => LongValueAggregationResult(response.getHits.totalHits())
    }
  }

  private def searchEvents(eventQuery: EventQuery, eventOffset: Int, eventLimit: Int) = {
    elasticsearch.client.execute {
      search in defaultIndexName query {
        must(constructQuery(eventQuery))
      } sort (by field "timestamp" order SortOrder.DESC) start eventOffset limit eventLimit
    }
  }

  private def constructQuery(eventQuery: EventQuery): List[QueryDefinition] = {
    val tagNum = eventQuery.tags.size
    val queries = constructTimeQuery(eventQuery.timestamp) :: Nil

    if (tagNum == 0) queries
    else
      queries :+ (termsQuery("tags", eventQuery.tags.toSeq: _*) minimumShouldMatch tagNum)
  }

  private def constructTimeQuery(timeRange: Option[TimeRange]) = {
    val range = rangeQuery("timestamp")

    timeRange match {
      case Some(tr) =>
        val addLt: (RangeQueryDefinition) => RangeQueryDefinition = { r => if (tr.lt.isDefined) r to tr.lt.get includeUpper false else r }
        val addLte: (RangeQueryDefinition) => RangeQueryDefinition = { r => if (tr.lte.isDefined) r to tr.lte.get includeUpper true else r }
        val addGt: (RangeQueryDefinition) => RangeQueryDefinition = { r => if (tr.gt.isDefined) r from tr.gt.get includeLower false else r }
        val addGte: (RangeQueryDefinition) => RangeQueryDefinition = { r => if (tr.gte.isDefined) r from tr.gte.get includeLower true else r }

        (addLt andThen addLte andThen addGt andThen addGte)(range)
      case _ => range
    }
  }

  private def aggregateEvents(eventQuery: EventQuery) = {
    val aggregator = eventQuery.aggregator.get
    val aggregationField = List("value", aggregator.field.getOrElse("")).filter(p => !p.isEmpty).mkString(".")

    elasticsearch.client.execute {
      search in defaultIndexName searchType SearchType.Count aggs {
        aggregation filter "filter_agg" filter {
          queryFilter(must(constructQuery(eventQuery)))
        } aggs {
          aggregator.`type` match {
            case Some(Aggregator.`average`) => aggregation avg "val_agg" field aggregationField
            case Some(Aggregator.`min`) => aggregation min "val_agg" field aggregationField
            case Some(Aggregator.`max`) => aggregation max "val_agg" field aggregationField
            case _ => throwException(AggregatorNotSupported())
          }
        }
      }
    } map {
      response =>
        val value: Double = response.getAggregations
          .get("filter_agg").asInstanceOf[InternalFilter]
          .getAggregations.get("val_agg").asInstanceOf[InternalNumericMetricsAggregation.SingleValue]
          .value()

        DoubleValueAggregationResult(if (value.isNaN || value.isInfinite) 0D else value)
    } recoverWith {
      case e: Exception => throwException(EventQueryError)
    }
  }
}
