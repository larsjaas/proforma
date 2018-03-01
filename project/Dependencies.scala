import sbt._
import Keys._

object Dependencies {
    lazy val akkaVersion = "2.4.20"
    lazy val scalaTestVersion = "3.0.1"
    lazy val logbackVersion = "1.2.2"

    val slf4jApi = "org.slf4j" % "slf4j-api" % "1.7.25"
    val logbackCore = "ch.qos.logback" % "logback-core" % logbackVersion
    val logbackClassic = "ch.qos.logback" % "logback-classic" % logbackVersion
    val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0"
    val grizzledSlf4j = "org.clapper" %% "grizzled-slf4j" % "1.3.0"

    val jodaConvert = "org.joda" % "joda-convert" % "1.8.1"
    val jodaTime = "joda-time" % "joda-time" % "2.9.7"

    val schwatcher = "com.beachape.filemanagement" %% "schwatcher" % "0.3.3"

    val argonaut = "io.argonaut" %% "argonaut" % "6.2-RC2"
    val config = "com.typesafe" % "config" % "1.3.1"
    val akkaActor = "com.typesafe.akka" %% "akka-actor" % akkaVersion
    val akkaStream = "com.typesafe.akka" %% "akka-stream" % akkaVersion
    val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % akkaVersion
    val akkaHttp = "com.typesafe.akka" %% "akka-http" % "10.0.11"

    val commonsMail = "org.apache.commons" % "commons-email" % "1.5"

    val scalactic = "org.scalactic" %% "scalactic" % scalaTestVersion % "test"
    val scalaTest = "org.scalatest" %% "scalatest" % scalaTestVersion % "test"

    val skjemailDeps = Seq(slf4jApi, logbackCore, logbackClassic, scalaLogging,
            grizzledSlf4j,
            jodaConvert, jodaTime, argonaut, config, schwatcher,
            akkaActor, akkaStream, akkaSlf4j, akkaHttp,
            commonsMail,
            scalactic, scalaTest)
}
