name := "akka-exercise"

version := "0.1"

scalaVersion := "2.13.1"

val akkaVersion = "2.6.3"
val akkaHttpVersion = "10.1.11"

libraryDependencies ++= Seq("com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
                           "com.typesafe.akka" %% "akka-stream" % akkaVersion,
                           "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
                           "com.typesafe.akka" %% "akka-http-jackson" % akkaHttpVersion,
                           "ch.qos.logback" % "logback-classic" % "1.2.3")

mainClass in assembly := Some("dev.chikanov.exercise.HttpServer")
assemblyJarName in assembly := "exercise.jar"
assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}