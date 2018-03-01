package skjemail.framework

import java.net.URL

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.language.postfixOps
import scala.reflect.io.File

class AppConfig(files: List[String]) extends LazyLogging {
    var configs: List[Config] = List()
    var timestamps: List[Long] = List()
    var config: Config = _

    def init() = {
        for (file <- files) {
            val url = new URL(s"file://${file}")
            val config = ConfigFactory.parseURL(url)
            configs = config :: configs
            timestamps = File(file).lastModified :: timestamps
        }
        config = configs.foldRight[Config](ConfigFactory.empty())({ (acc, conf) => acc.withFallback(conf) })
    }

    init()

    def invalidateCache() = {
        ConfigFactory.invalidateCaches()
        configs = null
        timestamps = null
        for (file <- files) {
            val config = ConfigFactory.load(file)
            configs = config :: configs
            timestamps = File(file).lastModified :: timestamps
        }
        config = configs.foldRight[Config](null)({ (over, conf) => conf.withFallback(over) })
    }

    def getBoolean(path: String): Boolean = config.getBoolean(path)
    def getInt(path: String): Int = config.getInt(path)
    def getString(path: String): String = config.getString(path)

    def getStrings(path: String): List[String] = {
        config.getValue(path).unwrapped() match {
            case x: java.util.List[_] => x.asScala.map { x => x.asInstanceOf[String] } toList
            case x: String => List(x)
            case _ =>
                logger.error(s"invalid config for '$path'")
                throw new RuntimeException(s"could not get config setting '$path'")
        }
    }
}
