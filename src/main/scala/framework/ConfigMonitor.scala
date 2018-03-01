package skjemail.framework

import java.net.URL
import java.nio.file.{Path, Paths, WatchKey, WatchService}
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef}
import com.beachape.filemanagement.Messages.RegisterCallback
import com.beachape.filemanagement.MonitorActor
import com.beachape.filemanagement.RegistryTypes.Callback
import com.sun.nio.file.SensitivityWatchEventModifier
import com.typesafe.config.{ConfigException, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import skjemail.framework.ConfigMonitor.GetConfig
import skjemail.web.LogEvent

import scala.collection.mutable
import scala.language.postfixOps
import scala.concurrent.duration.{FiniteDuration, _}
import scala.reflect.io.File
import scala.concurrent.ExecutionContext.Implicits.global

class ConfigMonitor(profile: String, user: String) extends Actor with LazyLogging {
    var applicationTimestamp = 0L
    var profileTimestamp = 0L
    var userTimestamp = 0L

    val fileMonitorActor: ActorRef = context.system.actorOf(MonitorActor(concurrency = 2, dedupeTime = 0.5 seconds))
    val watchers: mutable.Map[String, List[ActorRef]] = new mutable.HashMap[String, List[ActorRef]]
    val configValues: mutable.Map[String, String] = new mutable.HashMap[String, String]
    var configListeners: List[ActorRef] = List()

    val configdir = ConfigMonitor.configDirectory.get
    val appConfig = s"${configdir}/application.conf"
    val profileConfig = s"${configdir}/${profile}.conf"
    val userConfig = s"${configdir}/${user}.conf"

    var config: AppConfig = _
    var systeminfo: ActorRef = _

    override def preStart: Unit = {
        logger.info(ConfigMonitor.NAME + " born")

        logger.debug(s"monitoring: ${configdir}")

        val me = self

        fileMonitorActor ! RegisterCallback(
            event = ENTRY_MODIFY,
            path = Paths.get(configdir),
            callback = { path => me ! path },
            modifier = Some(SensitivityWatchEventModifier.HIGH)
        )

        applicationTimestamp = File(appConfig).lastModified
        profileTimestamp = File(profileConfig).lastModified
        userTimestamp = File(userConfig).lastModified

        config = new AppConfig(List(appConfig, profileConfig, userConfig))

        super.preStart()
    }

    override def postStop: Unit = {
        logger.info(ConfigMonitor.NAME + " died")
        super.postStop()
    }

    override def receive: Actor.Receive = {
        case path: Path => {
            onUpdatedPath(path.toString)
        }
        case AddConfigListener(paths) => {
            paths.map({ p =>
                watchers.get(p) match {
                    case Some(list: List[ActorRef]) =>
                        watchers.put(p, sender :: list)
                        configValues.put(p, valueForKey(p))
                    case None =>
                        watchers.put(p, List(sender))
                        configValues.put(p, valueForKey(p))
                }
            })
            if (paths.isEmpty)
                configListeners = sender :: configListeners
        }
        case SubscribeEventLog => systeminfo = sender
        case GetConfig => sender ! ConfigEvent(config.config)
        case x => logger.debug(s"received unhandled ${x}")
    }

    def valueForKey(key: String): String = {
        config.config.getAnyRef(key).toString
    }

    def onUpdatedPath(path: String): Unit = {
        //logger.debug(s"path: ${path}")
        var updated = true
        path match {
            case `appConfig` => applicationTimestamp = File(appConfig).lastModified
            case `profileConfig` => profileTimestamp = File(profileConfig).lastModified
            case `userConfig` => userTimestamp = File(userConfig).lastModified
            case x => {
                logger.debug(s"Something was modified in a path: ${path}")
                updated = false
            }
        }

        if (updated) {
            logger.debug("config updated")
            config = new AppConfig(List(appConfig, profileConfig, userConfig))

            val now = ZonedDateTime.now(java.time.Clock.systemDefaultZone()).toInstant.getEpochSecond * 1000L
            if (systeminfo != null) {
                systeminfo ! LogEvent(now, "config updated", "ConfigMonitor")
            }
            for (listener <- configListeners)
                listener ! ConfigUpdatedEvent(config.config)

            var messages = new mutable.HashMap[ActorRef, List[String]]
            for (key <- watchers.keys) {
                val oldvalue = configValues.getOrElse(key, "")
                var value = valueForKey(key)
                if (!value.equals(oldvalue)) {
                    //logger.info(s"caching for ${key}: ${value}")
                    configValues.put(key, value)
                    for (watcher <- watchers(key)) {
                        var list = messages.getOrElse(watcher, List())
                        messages.put(watcher, key :: list)
                    }
                }
            }
            for (watcher <- messages.keys) {
                watcher ! ConfigUpdatedEvent(messages(watcher), config.config)
            }
        }
    }
}

object ConfigMonitor {
    val NAME = "ConfigMonitor"
    val configDirProperty = "skjemail.config.dir"

    case object GetConfig

    def configDirectory: Option[String] = {
        val dir = System.getProperty(configDirProperty)
        if (dir != null) {
            // FIXME! canonicalize dir so monitor matches will work on string-matching
            Some(dir)
        } else {
            getClass.getResource("/application.conf") match {
                case x: URL => Some(File(x.getPath()).parent.toString)
                case _ => None
            }
        }
    }
}
