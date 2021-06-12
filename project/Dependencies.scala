import sbt._
import Keys._

object Version {
  val jcstress = "0.12"
}

object Dependencies {
  val jcstress = "org.openjdk.jcstress" % "jcstress-core" % Version.jcstress % "test"
}

