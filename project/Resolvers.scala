import sbt._
import Keys._

object Resolvers {
  val typesafeReleases = "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
  val typesafeSnapshots = "Typesafe Snapshots" at "http://repo.typesafe.com/typesafe/snapshots/"
  val skjemailResolvers = Seq(typesafeReleases)
}
