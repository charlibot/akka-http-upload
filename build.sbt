organization := "com.massiveanalytic"

name := "akka-test"

scalaVersion := "2.11.7"

version := "0.0.1"

//libraryDependencies += "org.apache.spark" % "spark-core_2.10" % "1.6.0" % "provided" withSources() withJavadoc()
//
//libraryDependencies += "org.apache.spark" % "spark-mllib_2.10" % "1.6.0" % "provided" withSources() withJavadoc()
//
//libraryDependencies += "org.apache.spark" % "spark-streaming_2.10" % "1.6.0" % "provided" withSources() withJavadoc()
//
//libraryDependencies += "org.apache.spark" % "spark-sql_2.10" % "1.6.0" % "provided" withSources() withJavadoc()
//
//libraryDependencies += "org.apache.spark" % "spark-hive_2.10" % "1.6.0" % "provided" withSources() withJavadoc()

libraryDependencies += "com.typesafe.akka" %% "akka-http-core" % "2.4.4"

libraryDependencies += "com.typesafe.akka" %% "akka-stream" % "2.4.4"

libraryDependencies += "com.typesafe.akka" %% "akka-http-experimental" % "2.4.4"
//
//libraryDependencies += "com.typesafe.akka" %% "akka-stream-experimental" % "2.0.4"
//
//libraryDependencies += "com.typesafe.akka" %% "akka-http-core-experimental" % "2.0.4"
//
//libraryDependencies += "com.typesafe.akka" %% "akka-http-experimental" % "2.0.4"
//
//libraryDependencies += "com.typesafe.akka" %% "akka-http-testkit-experimental" % "2.0.4"

//libraryDependencies += "org.pojava" % "datetime" % "3.0.1"
//
//libraryDependencies += "com.github.tototoshi" %% "scala-csv" % "1.1.2"
//
//libraryDependencies += "org.scalatest" % "scalatest_2.10" % "2.2.4" % "test"

//libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.3.15"

libraryDependencies += "de.heikoseeberger" %% "akka-http-json4s" % "1.6.0"

libraryDependencies += "org.json4s" %% "json4s-native" % "3.3.0"

resolvers += "Akka Repository" at "http://repo.akka.io/releases/"