package skjemail.web

import akka.actor.{Actor, ActorRef}
import argonaut._
import Argonaut._
import com.typesafe.scalalogging.LazyLogging
import java.time.ZonedDateTime
import scala.language.postfixOps

// TODO: spit log into permanent log and decaying log (or just a decay-level?), and merge on log-request
// TODO: move to framework/, set up as userlog handler and configurable hooks
// TODO: ...or keep in web as ApiHandler, with util/ hooks?

class SystemInfo(configMonitor: ActorRef) extends Actor with LazyLogging {
    val serviceBootTime: Long = ZonedDateTime.now.toEpochSecond * 1000
    val systemBootTime: Long = Uptimes.getSystemBootTime

    var events: List[LogEvent] = List()

    override def preStart: Unit = {
        logger.info(s"${SystemInfo.NAME} born")
        super.preStart()
    }

    override def postStop: Unit = {
        logger.info(s"${SystemInfo.NAME} died")
        super.postStop()
    }

    def receive = {
        case RestRequest("GET", Array("uptimes"), _, _) =>
            sender ! Uptimes(systemBootTime, serviceBootTime).asJson.nospaces
        case RestRequest("GET", Array("log"), _, _) =>
            sender ! "[" + events.map({ _.asJson.nospaces }).mkString(",") + "]"
        case ev: LogEvent => {
            events = ev :: events
            if (events.size > 40)
                events = events.dropRight(events.size - 40)
        }
        case x => logger.warn(s"received unhandled event $x")
    }

}

object SystemInfo {
    val NAME = "SystemInfo"
}
