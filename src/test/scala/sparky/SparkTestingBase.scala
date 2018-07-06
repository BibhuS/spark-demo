package sparky

import com.holdenkarau.spark.testing.SharedSparkContext
import org.apache.spark.sql.SparkSession
import org.scalatest.Suite

trait SparkTestingBase extends SharedSparkContext {
  self: Suite =>
  lazy val spark = SparkSession
    .builder()
    .appName("Testing")
    .master("local")
    .getOrCreate()
}
