package skjemail

import java.time.ZonedDateTime

import akka.http.scaladsl.model.HttpCharsets.`UTF-8`
import akka.http.scaladsl.model.{ContentType, MediaType}
import argonaut.Argonaut._
import argonaut.CodecJson

package object web {

    trait PollResponseEvent {
        def asJson(): String
    }

    trait RestResponse {
        def asJson(): String
    }

    trait RestEvent {
        def fromJson(json: String): Unit
    }

    case class FileRequest(filename: FileName, compressed: Boolean)

    case class FileData(filename: FileName, payload: Array[Byte], compressed: Boolean)

    case class BuildInfo(version: String, gitrevision: String, gitbranch: String, gittag: String, buildtime: Long, username: String, hostname: String)

    object BuildInfo {
        implicit def BuildInfoCodecJson: CodecJson[BuildInfo] =
            casecodec7(BuildInfo.apply, BuildInfo.unapply)("version", "gitrevision", "gitbranch", "gittag", "buildtime", "username", "hostname")
    }

    case class RestRequest(method: String, path: Array[String], args: Map[String, String], body: String) {
        override def toString: String = {
            "RestRequest(\"%s\", \"%s\")".format(method, path.mkString("/"))
        }
    }

    case class LogEvent(time: Long, message: String, source: String) {
        def this(message: String, source: String) = this(ZonedDateTime.now(java.time.Clock.systemDefaultZone()).toInstant.getEpochSecond*1000L, message, source)
    }

    object LogEvent {
        implicit def LogEventCodecJson: CodecJson[LogEvent] =
            casecodec3(LogEvent.apply, LogEvent.unapply)("time", "message", "source")

        def apply(message: String, source: String) = new LogEvent(message, source)
    }

    object CustomTypes {
        val `application/javascript(UTF-8)`: ContentType = MediaType.customWithFixedCharset("application", "javascript", `UTF-8`)
        val `application/json(UTF-8)`: ContentType = MediaType.customWithFixedCharset("application", "json", `UTF-8`)
        val `application/font-woff`: ContentType = MediaType.customWithFixedCharset("application", "font-woff", `UTF-8`)
        val `application/font-woff2`: ContentType = MediaType.customWithFixedCharset("application", "font-woff2", `UTF-8`)
        val `application/x-font-truetype`: ContentType = MediaType.customWithFixedCharset("application", "x-font-truetype", `UTF-8`)
        val `text/css(UTF-8)`: ContentType = MediaType.customWithFixedCharset("text", "css", `UTF-8`)
        val `image/svg+xml(UTF-8)`: ContentType = MediaType.customWithFixedCharset("image", "svg+xml", `UTF-8`)
    }
}
