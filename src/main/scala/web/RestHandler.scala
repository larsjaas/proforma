package skjemail.web

import akka.actor.{Actor, ActorRef}
import argonaut._
import Argonaut._
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import java.io.File
import java.net.URL

import skjemail.framework.ConfigMonitor
import skjemail.web.mail.{Mail, send}

import scala.collection.mutable
import scala.util.{Failure, Success, Try}

class RestHandler(configMonitor: ActorRef, sysinfo : ActorRef) extends Actor with LazyLogging {
    val buildProps: Config = ConfigFactory.parseURL(new URL("file://" + ConfigMonitor.configDirectory.get + "/buildinfo.conf"))
    var buildInfo: BuildInfo = _

    override def preStart: Unit = {
        logger.info(s"${RestHandler.NAME} born")
        buildInfo = BuildInfo(buildProps.getString("version"),
            buildProps.getString("gitrevision"),
            buildProps.getString("gitbranch"),
            buildProps.getString("gittag"),
            new File(getClass.getResource("/buildinfo.conf").getFile).lastModified,
            buildProps.getString("username"),
            buildProps.getString("hostname")
        )
        super.preStart()
    }

    override def postStop: Unit = {
        logger.info(s"${RestHandler.NAME} died")
        super.postStop()
    }

    def receive: Actor.Receive = {
        //case req@RestRequest("GET", Array("stock"), _, _) => portfolio forward req
        //case req@RestRequest("GET", Array("trade"), _, _) => portfolio forward req
        //case req@RestRequest("GET", Array("performance"), _, _) => portfolio forward req
        //case req@RestRequest("GET", Array("account"), _, _) => portfolio forward req
        case req@RestRequest("GET", Array("uptimes"), _, _) => sysinfo forward req
        case req@RestRequest("GET", Array("log"), _, _) => sysinfo forward req
        case req@RestRequest("GET", Array("build"), _, _) => sender ! buildInfo.asJson.nospaces
        case req@RestRequest("POST", Array("post-form"), _, _) =>
            Try(onFormData(req)) match {
                case Success(str) => sender ! str
                case Failure(e) => sender ! "error processing request: " + e.toString
            }
        case req: RestRequest => {
            logger.warn(s"received unhandled REST request $req")
            sender ! None
        }
        case x => {
            logger.warn(s"received unhandled ${x.getClass}")
            sender ! None
        }
    }

    def onFormData(req: RestRequest): String = {
        val formparams = req.body.split("&").map({ elt =>
            val a = elt.split("=")
            a.size match {
                case 0 => ("", "")
                case 1 => (a(0), "")
                case _ => (a(0), a(1))
            }
        }).toMap[String, String]
        logger.info(s"form params: ${formparams}")

        send a new Mail(to = Seq("lars.j.aas@gmail.com"),
            subject = "Loppemarked-henting",
            from = ("lars.j.aas@gmail.com", "Lars J. Aas"),
            message = "Test sending mail v0.2.\n\n" + formparams.keys.map(s => s + " => " + formparams(s)).mkString("\n")
        )
        "post ok"
    }
}

object RestHandler {
    val NAME = "RestHandler"
}

