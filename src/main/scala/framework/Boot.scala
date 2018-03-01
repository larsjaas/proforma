package skjemail.framework

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import java.net.URL

import akka.actor.{ActorSystem, Props}
//import skjemail.framework.SubscribeEventLog
import skjemail.web._

import scala.io.StdIn
import scala.language.postfixOps

object Boot extends LazyLogging {
    val NAME = "Boot"
    val system = ActorSystem("skjemail")

    def main(args: Array[String]) : Unit = {
        val configdir = ConfigMonitor.configDirectory.get
        val url = new URL(s"file://$configdir/profile.conf")
        val profile = ConfigFactory.parseURL(url).getString("profile")
        val user = System.getProperties.getProperty("user.name")
        logger.info(s"using profile '$profile', user '$user'")

        val configmon = system.actorOf(Props.create(classOf[ConfigMonitor], profile, user), ConfigMonitor.NAME)
        val systeminfo = system.actorOf(Props.create(classOf[SystemInfo], configmon), SystemInfo.NAME)
        val fileservice = system.actorOf(Props.create(classOf[FileResource], configmon), FileResource.NAME)
        val resthandler = system.actorOf(Props.create(classOf[RestHandler], configmon, systeminfo), RestHandler.NAME)
        val webservice = system.actorOf(Props.create(classOf[WebService], configmon, fileservice, resthandler), WebService.NAME)

        systeminfo ! LogEvent("started service", NAME)
        configmon.tell(SubscribeEventLog, systeminfo)

        val terminator = new Thread(() => {
            Thread.sleep(1000L)
            logger.info("Press RETURN to stop...")
            StdIn.readLine()
            systeminfo ! LogEvent("stopping service", NAME)
            logger.info("Stopping...")
        //    system.stop(pollhandler)
            system.terminate()
        })
        terminator.setDaemon(true)
        terminator.start()
    }
}
