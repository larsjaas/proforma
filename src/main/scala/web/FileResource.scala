package skjemail.web

import akka.actor.{Actor, ActorRef}
import com.typesafe.scalalogging.LazyLogging
import java.io.{File, FileNotFoundException}
import java.nio.file._

import com.typesafe.config.Config
import skjemail.framework.ConfigMonitor.GetConfig
import skjemail.framework._

// TODO: add in-memory caching?

case class FileName(path: String, basename: String, extension: String) {
    def this(pathname: String) =
        this(FileName.getPathComponent(pathname), FileName.getBasenameComponent(pathname), FileName.getExtensionComponent(pathname))

    def filename: String = path + basename + extension
}

object FileName {
    def getPathComponent(pathname: String): String = {
        val elts = pathname.split("/")
        if (elts.length > 1)
            elts.dropRight(1).mkString("/") + "/"
        else
            ""
    }

    def getBasenameComponent(pathname: String): String = {
        val elts = pathname.split("/")
        val comps = elts(elts.length - 1).split("\\.")
        if (comps.length > 1)
            comps.dropRight(1).mkString(".")
        else
            elts(elts.length - 1)
    }

    def getExtensionComponent(pathname: String): String = {
        val elts = pathname.split("/")
        val comps = elts(elts.length - 1).split("\\.")
        if (comps.length > 1)
            "." + comps(comps.length - 1)
        else
            ""
    }

    def apply(pathname: String) = new FileName(pathname)
}

class FileResource(configMonitor: ActorRef) extends Actor with LazyLogging {

    var rootdir: Option[String] = None

    override def preStart: Unit = {
        logger.info(s"${FileResource.NAME} born")
        configMonitor ! ConfigMonitor.GetConfig
        configMonitor ! AddConfigListener(FileResource.rootdirConfig)
        super.preStart()
    }

    override def postStop: Unit = {
        logger.info(s"${FileResource.NAME} died")
        super.postStop()
    }

    def receive: Actor.Receive = {
        case req: FileRequest =>
            if (rootdir.isEmpty) {
                logger.warn("no rootdir configured yet")
                sender ! "no rootdir configured yet"
            }
            else {
                try {
                    val filename = (this.rootdir.get + req.filename.filename).split("\\?")(0)
                    logger.debug(s"get $filename")
                    if (req.compressed && Files.exists(Paths.get(s"${filename}.gz"), LinkOption.NOFOLLOW_LINKS)) {
                        val bytes = Files.readAllBytes(Paths.get(s"${filename}.gz"))
                        sender ! FileData(FileName(filename), bytes, true)
                    }
                    else if (Files.exists(Paths.get(filename), LinkOption.NOFOLLOW_LINKS)) {
                        val bytes = Files.readAllBytes(Paths.get(filename))
                        sender ! FileData(FileName(filename), bytes, false)
                    }
                    else {
                        logger.info(s"no such file: $filename")
                        sender ! s"no such file ${this.rootdir.get}$req"
                    }
                } catch {
                    case e: NoSuchFileException =>
                        logger.info(s"no such file: ${this.rootdir.get}$req")
                        sender ! e.getMessage
                    case e: FileNotFoundException =>
                        logger.info(s"no such file: ${this.rootdir.get}$req")
                        sender ! e.getMessage
                    case e: Throwable =>
                        logger.error(s"reading data: $e")
                        sender ! e.getMessage
                }
            }
        case ev: ConfigEvent => {
            reconfigure(ev.config)
        }
        case ev: ConfigUpdatedEvent => {
            reconfigure(ev.config)
        }
        case m =>
            logger.error(s"fallthrough: $m/${m.getClass}")
    }

    def reconfigure(config: Config) = {
        logger.info("updating config")
        rootdir = Some(config.getString(FileResource.rootdirConfig))
    }
}

object FileResource {
    val NAME = "FileResource"

    val rootdirConfig = "skjemail.web.rootdir"

}
