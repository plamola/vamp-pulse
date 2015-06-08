package io.vamp.pulse.http

import com.typesafe.config.ConfigFactory
import io.vamp.common.http.{InfoBaseRoute, InfoMessageBase, RestApiBase}
import io.vamp.common.vitals.JvmVitals
import io.vamp.pulse.elasticsearch.ElasticsearchActor
import io.vamp.pulse.eventstream.EventStreamActor

import scala.language.{existentials, postfixOps}

case class InfoMessage(message: String, jvm: JvmVitals, elasticSearch: Any, stream: Any) extends InfoMessageBase

trait InfoRoute extends InfoBaseRoute {
  this: RestApiBase =>

  val infoMessage = ConfigFactory.load().getString("vamp.pulse.hi-message")

  def info(jvm: JvmVitals): InfoMessage = {
    InfoMessage(infoMessage,
      jvm,
      info(ElasticsearchActor),
      info(EventStreamActor)
    )
  }
}