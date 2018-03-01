package skjemail

import com.typesafe.config.Config

package object framework {
    case class AddConfigListener(paths: List[String]) {
        def this(path: String) = this(List(path))
        def this() = this(List())
    }

    case object AddConfigListener {
        def apply(path: String) = new AddConfigListener(List(path))
        def apply() = new AddConfigListener(List())
    }

    case class ConfigEvent(config: Config)

    case class ConfigUpdatedEvent(paths: List[String], config: Config) {
        def this(path: String, config: Config) = this(List(path), config)
        def this(config: Config) = this(List(), config)
    }

    case object ConfigUpdatedEvent {
        def apply(path: String, config: Config) = new ConfigUpdatedEvent(List(path), config)
        def apply(config: Config) = new ConfigUpdatedEvent(List(), config)
    }

    case object SubscribeEventLog

}
