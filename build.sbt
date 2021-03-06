name := "intellij-scala-bundle"

organization := "org.jetbrains"

description := "IntelliJ Scala Bundle"

version := "1.0"

licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html"))

scalaVersion := "2.12.3"

libraryDependencies += "org.apache.commons" % "commons-compress" % "1.14"
libraryDependencies += "com.hierynomus" % "sshj" % "0.26.0"
libraryDependencies += "org.slf4j" % "slf4j-simple" % "1.8.0-beta2"

mainClass in Compile := Some("org.intellij.scala.bundle.Main")
