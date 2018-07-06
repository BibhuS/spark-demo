import sbt._

object Dependencies {
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.0.5"
  lazy val sparkSql = "org.apache.spark" %% "spark-sql" % "2.3.1"
  lazy val sparkTesting = "com.holdenkarau" %% "spark-testing-base" % "2.2.0_0.8.0"
}
