package io.magnetic.vamp.pulse.storage.engine

import io.magnetic.vamp.pulse.eventstream.message.Event

import scala.collection.JavaConversions._
import com.typesafe.config.ConfigFactory
import io.magnetic.vamp.pulse.api.EventQuery

import io.magnetic.vamp.pulse.storage.client.ESApi
import io.magnetic.vamp.pulse.util.Serializers
import org.elasticsearch.action.search.SearchResponse

import org.scalatest.{Matchers, FlatSpec}
import scala.concurrent.duration._
import org.json4s.native.JsonMethods._

import scala.concurrent.Await
import scala.io.Source
import io.magnetic.vamp.pulse.eventstream.decoder.ElasticEventDecoder
import scala.concurrent.ExecutionContext.Implicits.global


class ElasticEventDAOSpec extends FlatSpec with Matchers {
  implicit val formats = Serializers.formats

  val config = ConfigFactory.load()

  val esConf = config.getConfig("storage.es")
  implicit val esClient = ESApi.getClient(esConf.getString("cluster.name"), esConf.getString("host"), esConf.getInt("port"))
  val dao = new ElasticEventDAO


  val decoder = new ElasticEventDecoder()


  "MetricDAO" should "Should fetch records from elastic-search by tags and date-range" in {
    val str = Source.fromURL(getClass.getResource("/metricQuery.json")).mkString
    val metricQuery = parse(str).extract[EventQuery]
    val resp = Await.result(dao.getEvents(metricQuery), 10 seconds)
    println(resp)
    resp shouldBe an[List[Event]]

  }


  "MetricDAO" should "Should aggregate records from elastic-search by tags and date-range" in {
    val str = Source.fromURL(getClass.getResource("/metricQueryAgg.json")).mkString
    val metricQuery = parse(str).extract[EventQuery]
    val resp = Await.result(dao.getEvents(metricQuery), 10 seconds)
    resp shouldBe an[Map[String, Double]]

  }



}