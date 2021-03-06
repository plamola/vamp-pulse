package io.vamp.pulse

import akka.actor._
import io.vamp.common.akka.{ActorBootstrap, ActorSupport, Bootstrap}
import io.vamp.pulse.elasticsearch.{ElasticsearchActor, ElasticsearchInitializationActor}
import io.vamp.pulse.eventstream.EventStreamActor
import io.vamp.pulse.http.RestApiBootstrap

import scala.language.{implicitConversions, postfixOps}

trait VampPulse extends App {

  implicit val actorSystem = ActorSystem("vamp-pulse")

  val actorBootstrap = new ActorBootstrap {
    val actors = ActorSupport.actorOf(ElasticsearchInitializationActor) ::
      ActorSupport.actorOf(ElasticsearchActor) ::
      ActorSupport.actorOf(EventStreamActor) :: Nil
  }

  val bootstrap: List[Bootstrap] = actorBootstrap :: RestApiBootstrap :: Nil

  Runtime.getRuntime.addShutdownHook(new Thread() {
    override def run() = {
      bootstrap.foreach(_.shutdown)
      actorSystem.shutdown()
    }
  })

  bootstrap.foreach(_.run)
}
