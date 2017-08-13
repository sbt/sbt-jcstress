sbtPlugin := true

organization := "pl.project13.scala"
name := "sbt-jcstress"

scalaVersion := "2.12.3"

libraryDependencies += Dependencies.jcstress

publishTo := Some(Classpaths.sbtPluginReleases)

// publishing settings

publishMavenStyle := false
licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html"))
bintrayRepository := "sbt-plugins"
bintrayOrganization := None

scriptedLaunchOpts += s"-Dproject.version=${version.value}"
