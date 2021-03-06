package io.vamp.pulse.http

import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.http.{InfoBaseRoute, InfoMessageBase, RestApiBase}
import io.vamp.common.vitals.JvmVitals
import io.vamp.pulse.elasticsearch.ElasticsearchActor
import io.vamp.pulse.eventstream.EventStreamActor

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.language.{existentials, postfixOps}

case class InfoMessage(message: String, version: String, jvm: JvmVitals, elasticSearch: Any, stream: Any) extends InfoMessageBase

trait InfoRoute extends InfoBaseRoute {
  this: RestApiBase =>

  val infoMessage = ConfigFactory.load().getString("vamp.pulse.rest-api.info.message")

  val componentInfoTimeout = Timeout(ConfigFactory.load().getInt("vamp.pulse.rest-api.info.timeout") seconds)

  def info(jvm: JvmVitals): Future[InfoMessageBase] = info(Set(ElasticsearchActor, EventStreamActor)).map { result =>
    InfoMessage(infoMessage,
      getClass.getPackage.getImplementationVersion,
      jvm,
      result.get(ElasticsearchActor),
      result.get(EventStreamActor)
    )
  }
}
