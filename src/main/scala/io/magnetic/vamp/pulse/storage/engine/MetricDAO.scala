package io.magnetic.vamp.pulse.storage.engine

import com.sksamuel.elastic4s.{SearchType, FilterDefinition, QueryDefinition, ElasticClient}
import com.sksamuel.elastic4s.mappings.FieldType._
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.source.ObjectSource
import io.magnetic.vamp.pulse.api.MetricQuery
import io.magnetic.vamp.pulse.eventstream.producer.Metric
import io.magnetic.vamp.pulse.mapper.CustomObjectSource
import io.magnetic.vamp.pulse.eventstream.decoder.MetricDecoder
import org.elasticsearch.search.aggregations.bucket.filter.InternalFilter
import org.elasticsearch.search.aggregations.metrics.avg.InternalAvg

import scala.collection.mutable.Queue
import scala.concurrent.{Future, ExecutionContext}


class MetricDAO(implicit client: ElasticClient, implicit val executionContext: ExecutionContext) {
  private val entity = "metric"
  private val ind = "metrics"
  private val decoder = new MetricDecoder()

  def insert(metric: Metric) = {
    client.execute {
      index into s"$ind/$entity" doc CustomObjectSource(metric)
    } await
  }

  //TODO: Figure out timestamp issues with elastic: We can only use epoch now + we get epoch as a double from elastic.
  def getMetrics(metricQuery: MetricQuery): Future[Any] = {
    if(metricQuery.aggregator.isEmpty) {
      getPlainMetrics(metricQuery)
    } else {
      aggregateMetrics(metricQuery)
    }
  }

  private def getPlainMetrics(metricQuery: MetricQuery) = {
    val tagNum = metricQuery.tags.length

    val queries: Queue[QueryDefinition] = Queue(
      rangeQuery("timestamp") from metricQuery.time.from.toEpochSecond to metricQuery.time.to.toEpochSecond
    )

    if(tagNum > 0) queries += termsQuery("tags", metricQuery.tags:_*) minimumShouldMatch(tagNum)

    client.execute {
      search in ind -> entity query {
        must  (
          queries
        )
      } start 0 limit 30
    } map {
      resp => List(resp.getHits.hits().map((hit) =>  decoder.fromString(hit.sourceAsString())): _*)
    }
  }

  //TODO: Implement different aggregators here, for now we blindly do average.
  def aggregateMetrics(metricQuery: MetricQuery) = {

    val filters: Queue[FilterDefinition] = Queue(
      rangeFilter("timestamp") from metricQuery.time.from.toEpochSecond to metricQuery.time.to.toEpochSecond
    )

    if(!metricQuery.tags.isEmpty) filters += termsFilter("tags", metricQuery.tags :_*) execution("and")

    client.execute {
      search  in ind -> entity searchType SearchType.Count aggs {
        aggregation filter "filter_agg" filter {
          must(filters)
        } aggs {
          aggregation avg "val_agg" field "value"
        }
      }
    } map {
      resp => var value: Double = resp.getAggregations
        .get("filter_agg").asInstanceOf[InternalFilter]
        .getAggregations.get("val_agg").asInstanceOf[InternalAvg]
        .getValue
        //TODO: Wrapper for result types to check corner-cases
        if(value.compareTo(Double.NaN) == 0) value = 0D

        Map("value" -> value)
    }
  }
  
  def createIndex = client.execute {
      create index ind mappings (
          entity as (
            "tags" typed StringType,
            "timestamp" typed DateType,
            "value" typed DoubleType
          )
      )
    }
}
