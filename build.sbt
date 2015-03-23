Revolver.settings

organization := "io.vamp"

name := """pulse"""

version := "0.7.0-RC1"

scalaVersion := "2.11.5"

publishMavenStyle := true

description := """Pulse provides means of event storage/aggregation, consumes streams via rest api, kafka and sse."""


pomExtra := (<url>http://vamp.io</url>
    <licenses>
      <license>
        <name>The Apache License, Version 2.0</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
      </license>
    </licenses>
    <developers>
      <developer>
        <name>Roman Useinov</name>
        <email>roman@mangetic.io</email>
        <organization>VAMP</organization>
        <organizationUrl>http://vamp.io</organizationUrl>
      </developer>
    </developers>
    <scm>
      <connection>scm:git:git@github.com:magneticio/vamp-pulse.git</connection>
      <developerConnection>scm:git:git@github.com:magneticio/vamp-pulse.git</developerConnection>
      <url>git@github.com:magneticio/vamp-pulse.git</url>
    </scm>
  )




resolvers ++= Seq(
  "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/",
  "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases",
  "spray repo" at "http://repo.spray.io",
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
  Resolver.mavenLocal
)


libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream-experimental" % "1.0-M3",
  "org.scala-lang.modules" %% "scala-async" % "0.9.2",
  "org.glassfish.jersey.core" % "jersey-client" % "2.15",
  "org.glassfish.jersey.media" % "jersey-media-sse" % "2.15",
  "com.typesafe" % "config" % "1.2.1",
  "com.sclasen" %% "akka-kafka" % "0.1.0",
  "org.elasticsearch" % "elasticsearch" % "1.4.3",
  "commons-io" % "commons-io" % "2.4",
  "org.scalatest" %% "scalatest" % "3.0.0-SNAP4" % "test",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.9" % "test",
  "io.vamp" %% "common" % "0.7.0-RC1",
  "io.spray" %% "spray-can" % "1.3.2",
  "io.spray" %% "spray-http" % "1.3.2",
  "io.spray" %% "spray-util" % "1.3.2",
  "io.spray" %% "spray-io" % "1.3.2",
  "io.spray" %% "spray-routing" % "1.3.2",
  "com.github.nscala-time" %% "nscala-time" % "1.8.0",
  "com.sksamuel.elastic4s" %% "elastic4s" % "1.4.12",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.5.0",
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % "2.5.0",
  "org.slf4j" % "slf4j-simple" % "1.7.10"
)
