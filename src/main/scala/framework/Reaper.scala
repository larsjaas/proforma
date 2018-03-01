package skjemail.framework

import akka.actor.Actor
import com.typesafe.scalalogging.LazyLogging

/**
  * Created by larsa on 26/03/2017.
  */
class Reaper extends Actor with LazyLogging {

    override def preStart(): Unit = {
        logger.info(Reaper.NAME + " born")
        super.preStart()
    }

    override def postStop(): Unit = {
        logger.info(Reaper.NAME + " died")
        super.postStop()
    }

    def receive: Actor.Receive = {
        case x => logger.info(s"received ${x}")
    }

}

object Reaper {
    val NAME = "Reaper"
}
