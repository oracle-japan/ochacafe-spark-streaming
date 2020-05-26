name := "temperature-monitor"

version := "0.1"

scalaVersion := "2.11.11"

val sparkVersion = "2.4.5"

libraryDependencies += "org.apache.spark" %% "spark-core" % sparkVersion % "provided"

libraryDependencies += "org.apache.spark" %% "spark-sql" % sparkVersion % "provided"

libraryDependencies += "org.apache.spark" %% "spark-streaming" % sparkVersion % "provided"

libraryDependencies += "io.helidon.config" % "helidon-config-yaml" % "1.4.4" % "provided"
