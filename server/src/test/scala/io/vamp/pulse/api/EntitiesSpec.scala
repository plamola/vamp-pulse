package io.vamp.pulse.api

import org.scalatest.{FlatSpec, Matchers}

class EntitiesSpec extends FlatSpec with Matchers {
  //  implicit val formats = PulseSerializationFormat.api
  //  val decoder = new EventDecoder()
  //
  //  "MetricQuery" should "be able to be decoded from json" in {
  //    val str = Source.fromURL(getClass.getResource("/metricQuery.json")).mkString
  //    val eventQuery = parse(str).extract[model.EventQuery]
  //
  //    eventQuery shouldBe an[model.EventQuery]
  //  }
  //
  //  "Event" should "be able to be decoded from metric json" in {
  //    val str = Source.fromURL(getClass.getResource("/metric.json")).mkString
  //    val elasticEvent = decoder.fromString(str)
  //
  //    elasticEvent shouldBe an[Event]
  //  }
  //
  //  "Event" should "be able to be decoded from event json" in {
  //    val str = Source.fromURL(getClass.getResource("/event.json")).mkString
  //    val elasticEvent = decoder.fromString(str)
  //
  //    elasticEvent shouldBe an[Event]
  //  }

  //  "Metric" should "be able to be produced by Event decoded from Metric json" in {
  //    val str = Source.fromURL(getClass.getResource("/metric.json")).mkString
  //    val elasticEvent = decoder.fromString(str)
  //
  //    elasticEvent.convertOutput shouldBe a[Metric]
  //  }

  //  "Event" should "be able to be produced by Event decoded from Event json" in {
  //    val str = Source.fromURL(getClass.getResource("/event.json")).mkString
  //    val event = decoder.fromString(str)
  //
  //    event.convertOutput shouldBe an[Event]
  //  }
}