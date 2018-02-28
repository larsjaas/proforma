import scala.sys.process._

import Dependencies._
import Resolvers._

// sbt clean compile run - compile and run
// sbt dependencyUpdates - list deps with mvnrepository updates

// edit "profile' for another profile than 'utv'


name := "skjemail"

organization := "no.eyasys"
organizationName := "Eyasys AS"
organizationHomepage := Some(url("http://eyasys.no/"))

startYear := Some(2018)
description := "A tool for presenting forms and emailing form fills."

licenses += "GPLv2" -> url("https://www.gnu.org/licenses/gpl-2.0.html")

scalaVersion := "2.12.6"

val gitbranch = ("git rev-parse --abbrev-ref HEAD" !!).trim
val gitcommit = ("git rev-parse HEAD" !!).trim
val gittag = ("git tag --points-at HEAD" !!).trim
val username = ("id -un" !!).trim
val hostname = ("hostname -s" !!).trim

val profile = "utv"
// val profile = "prod"

scalacOptions ++= Seq("-feature", "-unchecked", "-deprecation")

lazy val skjemail = Project("skjemail", file("."))

resourceGenerators in Compile += Def.task {
  val log = streams.value.log
  val file = (resourceManaged in Compile).value / "buildinfo.conf"
  log.info("Generating buildinfo.conf")

  val contents = Seq(
      "project=\"%s\"".format(name.value),
      "version=\"%s\"".format(version.value),
      "username=\"%s\"".format(username),
      "hostname=\"%s\"".format(hostname),
      "gitrevision=\"%s\"".format(gitcommit),
      "gitbranch=\"%s\"".format(gitbranch),
      "gittag=\"%s\"".format(gittag),
      "").mkString("\n")

  log.debug(contents)
  IO.write(file, contents)
  Seq(file)
}.taskValue

resourceGenerators in Compile += Def.task {
    val log = streams.value.log
    val file = (resourceManaged in Compile).value / "profile.conf"
    log.info("Generating profile.conf")

    val contents = Seq(
        "profile=\"%s\"".format(profile),
        "").mkString("\n")

    log.debug(contents)
    IO.write(file, contents)
    Seq(file)
}.taskValue

unmanagedJars in Compile := (baseDirectory.value ** "*.jar").classpath

resolvers := skjemailResolvers

libraryDependencies ++= skjemailDeps

// JstKeys.amd in Assets := true
// JstKeys.outputPath in Assets := "html/templates.js"
// JstKeys.prettify in Assets := false
// JstKeys.aggregate in Assets := true
// JstKeys.gzipOptions in Assets := "-9kf"

fork := true
connectInput in run := true
//javaOptions in run += "-Dportfolio.config.dir=/Users/larsa/Code/portfolio/target/scala-2.12/classes"

// includeFilter in (Assets, JstKeys.jst) := "*.html"
// excludeFilter in (Assets, JstKeys.jst) := "index.html"

testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-eNDXEHLO")

