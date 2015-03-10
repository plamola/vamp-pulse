package io.magnetic.vamp.pulse.eventstream.driver

import akka.actor.{ActorRef, ActorSystem}
import com.sclasen.akka.kafka.{AkkaConsumer, AkkaConsumerProps}
import io.magnetic.vamp.pulse.eventstream.decoder.ElasticEventDecoder
import io.magnetic.vamp.pulse.eventstream.producer.{ElasticEvent, Event}
import kafka.serializer.DefaultDecoder
import org.glassfish.jersey.client.{JerseyClient, JerseyClientBuilder}
import org.glassfish.jersey.media.sse.{EventSource, InboundEvent}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait Driver {
  def start(config: Map[String, String], ref: ActorRef, system: ActorSystem)
  def stop()
}

object SseDriver extends Driver{
  lazy private val client: JerseyClient = JerseyClientBuilder.createClient()

  private var eventSource: EventSource = _
  
  lazy private val decoder = new ElasticEventDecoder()

  override def start(config: Map[String, String], ref: ActorRef, system: ActorSystem): Unit = {
    Future {
      val target = client.target(config("url"))
      eventSource = new EventSource(target) {
        override def onEvent(inboundEvent: InboundEvent): Unit = inboundEvent.getName match {
          case "metric" => ref ! decoder.fromString(inboundEvent.readData(classOf[String]))
          case _ => println(s"Received event ${inboundEvent.getName}, ignoring")
        }
      }
    }
  }

  override def stop(): Unit = {
    eventSource.close()
  }
}

object KafkaDriver extends Driver {
  var consumer: Option[AkkaConsumer[Array[Byte], ElasticEvent]] = Option.empty
  
  override def start(config: Map[String, String], ref: ActorRef, system: ActorSystem): Unit = {
    val consumerProps = AkkaConsumerProps.forSystem(
      system = system,
      zkConnect = config("url"),
      topic = config("topic"),
      group = config("group"),
      streams = config("partitions").toInt, //one per partition
      keyDecoder = new DefaultDecoder(),
      msgDecoder = new ElasticEventDecoder(),
      receiver = ref
    )
    consumer = Option(new AkkaConsumer(consumerProps))
    consumer.get.start()
  }

  override def stop(): Unit = {
    consumer match {
      case Some(cons) => cons.stop()
      case None => println("Nothing to stop")
    }
  }
}

