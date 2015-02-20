package io.magnetic.vamp.pulse.eventstream.decoder

import org.scalatest.{WordSpec, FlatSpec, WordSpecLike}
import org.scalatest.Matchers
import io.magnetic.vamp.pulse.eventstream.producer.Metric
import scala.io.Source
/**
 * Created by lazycoder on 19/02/15.
 */
class MetricDecoderSpec extends FlatSpec  with Matchers {
  "MetricDecoder" should "be able to decode valid json into a Metric" in {
    val metricDecoder = new MetricDecoder()
    val str = Source.fromURL(getClass.getResource("/metric.json")).mkString
    metricDecoder.fromString(str) shouldBe an[Metric]
  }
}