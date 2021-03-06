package io.vamp.pulse.eventstream

import java.util.concurrent.TimeUnit

import akka.actor.{AbstractLoggingActor, ActorRef, Props}
import com.typesafe.scalalogging.Logger
import io.vamp.common.akka.{ActorDescription, CommonSupportForActors}
import io.vamp.pulse.configuration.TimeoutConfigurationProvider
import io.vamp.pulse.elasticsearch.ElasticsearchActor
import io.vamp.pulse.notification._
import org.glassfish.jersey.client.{ClientConfig, ClientProperties, JerseyClientBuilder}
import org.glassfish.jersey.media.sse.{EventListener, EventSource, InboundEvent}
import org.slf4j.LoggerFactory

import scala.concurrent.blocking
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

case object CheckConnection

case object CloseConnection

case object OpenConnection


object SSEConnectionActor extends ActorDescription {
  def props(args: Any*): Props = Props(classOf[SSEConnectionActor], args: _*)
}

class SSEConnectionActor(streamUrl: String) extends AbstractLoggingActor with CommonSupportForActors with PulseActorLoggingNotificationProvider with TimeoutConfigurationProvider {

  private val logger = Logger(LoggerFactory.getLogger(classOf[SSEConnectionActor]))

  override protected val notificationActor: ActorRef = context.actorOf(PulseNotificationActor.props())

  private val decoder = new EventDecoder()

  val target = {
    val conf: ClientConfig = new ClientConfig()
    conf.property(ClientProperties.CONNECT_TIMEOUT, config.getInt("http.connect"))
    conf.property(ClientProperties.READ_TIMEOUT, config.getInt("http.connect"))

    val httpClient = JerseyClientBuilder.createClient(conf)
    httpClient.target(streamUrl)
  }

  val listener = new EventListener {
    override def onEvent(inboundEvent: InboundEvent): Unit = {
      try {
        actorFor(ElasticsearchActor) ! ElasticsearchActor.Index(decoder.fromString(inboundEvent.readData(classOf[String])))
      } catch {
        case e: Exception => logger.error(e.getMessage, e)
      }
    }
  }

  private var eventSource: Option[EventSource] = Option.empty

  def buildEventSource = {
    val c = JerseyClientBuilder.createClient()
    val es = EventSource.target(c.target(streamUrl)).reconnectingEvery(500, TimeUnit.MILLISECONDS).build()
    es.register(listener)
    Option(es)
  }

  private var isOpen: Boolean = false

  override def receive: Receive = {

    case CloseConnection if isOpen =>
      log.debug("closing sse connection")
      isOpen = false
      if (eventSource.isDefined && eventSource.get.isOpen) {
        eventSource.get.close()
        eventSource = Option.empty
      }

    case OpenConnection if !isOpen =>
      log.debug("opening sse connection")
      isOpen = true
      self ! CheckConnection

    case CheckConnection =>
      val result = Try(
        blocking {
          target.request().head()
        }
      )

      result match {

        case Failure(_) =>
          if (eventSource.isDefined && eventSource.get.isOpen) {
            eventSource.get.close()
            eventSource = Option.empty
          }
          reportException(UnableToConnectError(streamUrl))

        case Success(resp) if resp.getHeaderString("X-Vamp-Stream") != null =>
          if (eventSource.isEmpty && isOpen) {
            eventSource = buildEventSource
            eventSource.get.open()
            log.info(message(ConnectionSuccessful(streamUrl)))
          }

        case _ => reportException(NotStreamError(streamUrl))
      }

      if (isOpen) context.system.scheduler.scheduleOnce(config.getInt("sse.connection.checkup") millis, self, CheckConnection)
  }
}
