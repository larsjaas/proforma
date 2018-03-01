package skjemail.web

import java.io.{BufferedReader, InputStreamReader}
import java.time.ZonedDateTime
import java.util.regex.Pattern

import argonaut.Argonaut.casecodec2
import argonaut.CodecJson
import com.typesafe.scalalogging.LazyLogging


case class Uptimes(systemBootTime: Long, serviceBootTime: Long)

object Uptimes extends LazyLogging {
    def getSystemBootTime: Long = {
        val uptime = System.getProperty("os.name").toLowerCase() match {
            case "mac os x" =>
                val uptimeProc: Process = Runtime.getRuntime.exec("uptime") // FIXME: find method for accurate time
            val in: BufferedReader = new BufferedReader(new InputStreamReader(uptimeProc.getInputStream))
                val line = in.readLine()
                in.close()
                if (line != null) parseUptime(line) else 0L
            case x => {
                logger.info(s"os.name = $x not implemented")
                0L
            }
        }
        logger.debug(s"uptime $uptime ms")
        ZonedDateTime.now().toEpochSecond * 1000L - uptime
    }

    val matchers = List[(Pattern, (Pattern, String) => Long)](
        (Pattern.compile("up ((\\d+) days?,)?  ?(\\d+):(\\d+)"), { (pattern, line) =>
            val matcher = pattern.matcher(line)
            if (!matcher.find()) -1L else {
                val days = if (matcher.group(2) == null) 0L else matcher.group(2).toInt
                val hours = matcher.group(3).toInt
                val minutes = matcher.group(4).toInt
                (minutes * 60000L) + (hours * 60000L * 60L) + (days * 60000L * 60L * 24L)
            }
        }),
        (Pattern.compile("up  ?(\\d+):(\\d+)"), { (pattern, line) =>
            val matcher = pattern.matcher(line)
            if (!matcher.find()) -1L else {
                val hours = matcher.group(1).toInt
                val minutes = matcher.group(2).toInt
                (minutes * 60000L) + (hours * 60000L * 60L)
            }
        }),
        (Pattern.compile("up (\\d+) mins?,"), { (pattern, line) =>
            val matcher = pattern.matcher(line)
            if (!matcher.find()) -1L else {
                val minutes = matcher.group(1).toInt
                (minutes * 60000L)
            }
        }),
        (Pattern.compile("up ((\\d+) days?, )?(\\d+) hrs?,"), { (pattern, line) =>
            val matcher = pattern.matcher(line)
            if (!matcher.find()) -1L else {
                val days = if (matcher.group(2) == null) 0L else matcher.group(2).toInt
                val hours = matcher.group(3).toInt
                (hours * 60000L * 60L) + (days * 60000L * 60L * 24L)
            }
        })
    )

    def parseUptime(uptime: String): Long = {
        for ((pattern, extractor) <- matchers) {
            val millis = extractor(pattern, uptime)
            if (millis != -1L) return millis
        }
        if (logger.underlying.isDebugEnabled())
            logger.debug(s"uptime: '$uptime'")
        return 0L
    }

    implicit def UptimesCodecJson: CodecJson[Uptimes] =
        casecodec2(Uptimes.apply, Uptimes.unapply)("systemBootTime", "serviceBootTime")
}
