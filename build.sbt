ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.10"

lazy val root = (project in file("."))
  .settings(
    name := "scala-managed-database-testing"
  )

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.2.14" % Test,
  "com.github.docker-java" % "docker-java" % "3.2.14",
  "com.kohlschutter.junixsocket" % "junixsocket-common" % "2.6.1",
  "com.kohlschutter.junixsocket" % "junixsocket-native" % "2.6.1",
  "com.kohlschutter.junixsocket" % "junixsocket-native-common" % "2.6.1",
  "mysql" % "mysql-connector-java" % "8.0.30" % Test,
  "org.postgresql" % "postgresql" % "42.5.1" % Test,
  "org.mockito" %% "mockito-scala-scalatest" % "1.17.12" % Test
)
