import Dependencies._

lazy val root = (project in file(".")).settings(
  inThisBuild(
    List(
      organization := "sparky",
      scalaVersion := "2.11.12",
      version := "0.1.0-SNAPSHOT"
    )),
  name := "spark-demo",
  libraryDependencies ++= List(
    scalaTest % Test,
    sparkSql,
    sparkTesting % Test
  )
)
