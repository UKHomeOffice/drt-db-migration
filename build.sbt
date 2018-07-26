import sbt._
import Keys._
import com.github.retronym.SbtOneJar

val moduleName = "drt-db-migration"
val typesafeConfig = "1.3.0"
val akka = "2.5.14"
val akkaStreamContrib = "0.9"
val levelDb = "0.7"
val levelDbJni = "1.8"
val specs2 = "3.7"

val root = Project(id = moduleName, base = file("."))
  .configs(IntegrationTest)
  .settings(Revolver.settings)
  .settings(Defaults.itSettings: _*)
  .settings(SbtOneJar.oneJarSettings)
  .settings(
    name := moduleName,
    organization := "uk.gov.homeoffice",
    scalaVersion := "2.11.8",
    scalacOptions ++= Seq(
      "-feature",
      "-language:implicitConversions",
      "-language:higherKinds",
      "-language:existentials",
      "-language:reflectiveCalls",
      "-language:postfixOps",
      "-Yrangepos"),
    exportJars := true,
    ivyScala := ivyScala.value map {
      _.copy(overrideScalaVersion = true)
    },
    resolvers ++= Seq(
      "Artifactory Snapshot Realm" at "http://artifactory.registered-traveller.homeoffice.gov.uk/artifactory/libs-snapshot-local/",
      "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
      "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
      "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases",
      "Kamon Repository" at "http://repo.kamon.io"),
    PB.targets in Compile := Seq(
      scalapb.gen() -> (sourceManaged in Compile).value / "protobuf"
    ),
    libraryDependencies ++= Seq(
      "org.clapper" %% "grizzled-slf4j" % "1.3.2",
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "org.postgresql" % "postgresql" % "42.2.2",
      "commons-dbcp" % "commons-dbcp" % "1.4",
      "com.typesafe" % "config" % typesafeConfig,
      "com.typesafe.akka" %% "akka-persistence-query" % akka,
      "com.typesafe.akka" %% "akka-persistence" % akka,
      "com.typesafe.akka" %% "akka-stream-contrib" % akkaStreamContrib,
      "com.typesafe.akka" %% "akka-slf4j" % akka,
      "org.fusesource.leveldbjni" % "leveldbjni-all" % levelDbJni,
      "org.iq80.leveldb" % "leveldb" % levelDb,
      "com.github.scopt" %% "scopt" % "3.7.0"
    ),
    libraryDependencies ++= Seq(
      "joda-time" % "joda-time" % "2.9.4" % Test,
      "org.specs2" %% "specs2-core" % specs2 % Test,
      "org.specs2" %% "specs2-junit" % specs2 % Test,
      "org.specs2" %% "specs2-mock" % specs2 % Test,
      "com.typesafe.akka" %% "akka-testkit" % akka % Test,
      "com.typesafe.akka" %% "akka-stream-testkit" % akka % Test
    ))


publishTo := Some("Artifactory Realm" at "http://artifactory.registered-traveller.homeoffice.gov.uk/artifactory/libs-snapshot-local")

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

// Enable publishing the jar produced by `test:package`
publishArtifact in(Test, packageBin) := true

// Enable publishing the test API jar
publishArtifact in(Test, packageDoc) := true

// Enable publishing the test sources jar
publishArtifact in(Test, packageSrc) := true

javaOptions in run += "-Djdk.logging.allowStackWalkSearch=true"

assemblyMergeStrategy in assembly := {
  case "overview.html" => MergeStrategy.discard
  case PathList("META-INF", "spring.tooling") => MergeStrategy.first
  case PathList("META-INF", "spring.factories") => MergeStrategy.first
  case PathList(p @ _*) if p.last endsWith ".properties" => MergeStrategy.concat
  case PathList("ArgumentsProcessor.class", "MatchersBinder.class") => MergeStrategy.first
  case PathList("org", "scalactic", _*) => MergeStrategy.first
  case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
  case PathList(ps @ _*) if ps.last endsWith ".java" => MergeStrategy.discard
  case PathList("org", "mockito", _*) => MergeStrategy.discard
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

fork in run := true

mainClass in(Compile, run) := Some("uk.gov.homeoffice.drt.Boot")
