name := "temperature-monitor"

version := "0.1"

//scalaVersion := "2.12.8"
scalaVersion := "2.11.11"

val sparkVersion = "2.4.4"

libraryDependencies += "org.apache.spark" %% "spark-core" % sparkVersion % "provided"

libraryDependencies += "org.apache.spark" %% "spark-sql" % sparkVersion % "provided"

libraryDependencies += "org.apache.spark" %% "spark-streaming" % sparkVersion % "provided"

//libraryDependencies += "org.apache.spark" %% "spark-mllib" % sparkVersion % "provided"

//libraryDependencies += "io.helidon.config" % "helidon-config" % "1.4.4"

libraryDependencies += "io.helidon.config" % "helidon-config-yaml" % "1.4.4" % "provided"
