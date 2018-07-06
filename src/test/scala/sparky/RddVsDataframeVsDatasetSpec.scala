package sparky

import java.time.OffsetDateTime

import com.holdenkarau.spark.testing.SharedSparkContext
import org.apache.spark.rdd.RDD
import org.scalatest._
import org.apache.spark.sql.{
  AnalysisException,
  DataFrame,
  Dataset,
  Row,
  SparkSession
}
import org.apache.spark.sql.types._

class RddVsDataframeVsDatasetSpec
    extends FreeSpec
    with Matchers
    with SparkTestingBase {
  import spark.implicits._
  val csrPath = "src/test/resources/csr20180521.csv"
  val consumptionPath = "src/test/resources/consumption20180101.csv"
  def getCsrRdd = {
    val rawCsrRdd = sc.textFile(csrPath)
    val withoutHeaders = rawCsrRdd.mapPartitionsWithIndex {
      case (index, partition) =>
        if (index == 0) partition.drop(1) else partition
    }
    val csrRdd = withoutHeaders.map { row =>
      val lines = row.split(",")
      SimpleCsr(lines(0),
                lines(1),
                lines(2),
                lines(3),
                lines(4).toInt,
                lines(5).toInt)
    }
    csrRdd
  }

  def getConsumptionRdd = {
    val rawConsumptionRdd = sc.textFile(consumptionPath)
    val withoutHeaders = removeHeaders(rawConsumptionRdd)
    val consumptionRdd = withoutHeaders.map { line =>
      val cols = line.split(",")
      SimpleConsumption(cols(0), cols(1), cols(2).toInt, cols(3).toInt)
    }
    consumptionRdd
  }

  def getCsrDf =
    spark.read
      .option("header", "true")
      .option("inferSchema", true)
      .csv(csrPath)

  def getConsumptionDf =
    spark.read
      .option("header", "true")
      .option("inferSchema", true)
      .csv(consumptionPath)

  def getCsrDs = getCsrDf.as[SimpleCsr]
  def getConsumptionDs = getConsumptionDf.as[SimpleConsumption]

  "Reading in a CSV file" - {

    "With RDD" in {
      val rawCsrRdd = sc.textFile(csrPath)
      val withoutHeaders = removeHeaders(rawCsrRdd)
      val csrRdd = withoutHeaders.map { row =>
        val cols = row.split(",")
        SimpleCsr(cols(0),
                  cols(1),
                  cols(2),
                  cols(3),
                  cols(4).toInt,
                  cols(5).toInt)
      }

      // RDDs can be manipulated using the usual higher order functions we have in Scala...
      println(
        csrRdd
          .filter(_.householdId == "ABC-household")
          .map(_.householdId)
          .collect
          .toList)
    }

    "With Dataframe" in {
      val csrDf = spark.read
        .option("header", "true")
        .option("inferSchema", true)
        .csv(csrPath)

      // Can show the output
      csrDf.show()

      // Can show the schema
      csrDf.printSchema()

      // Can use Scala DSL to do SQL-like operations
      csrDf.select("HouseholdId").show()
      csrDf.filter($"HouseholdId" equalTo "not a match").show()

      // But this isn't typesafe...
      assertThrows[AnalysisException](csrDf.select($"NotAColumn").show())

      // Can also use plain SQL to do queries, though also not typesafe
      csrDf.createOrReplaceTempView("csrs")
      spark.sql("SELECT HouseholdId, PostalCode FROM csrs").show()
    }

    "With Dataset" in {
      val csrDf = spark.read
        .option("header", "true")
        .option("inferSchema", true)
        .csv(csrPath)

      // A Dataframe can be converted to a Dataset
      val csrDs = csrDf.as[SimpleCsr]
      csrDs.show()
    }
  }

  private def removeHeaders(rawCsrRdd: RDD[String]) = {
    rawCsrRdd.mapPartitionsWithIndex {
      case (index, partition) =>
        if (index == 0) partition.drop(1) else partition
    }
  }

  "Joining 2 data sources" - {
    "RDDs" in {
      val csrRdd = getCsrRdd
      val consumptionRdd = getConsumptionRdd
      // Downside - have to explicitly set keys
      val keyedCsrRdd = csrRdd.keyBy(_.electricSensorID)
      val keyedConsumptionRdd = consumptionRdd.keyBy(_.electricSensorID)
      val joined: RDD[(String, (SimpleCsr, Option[SimpleConsumption]))] = keyedCsrRdd.leftOuterJoin(keyedConsumptionRdd)
      println(joined.collect.toList)
    }

    "Dataset (looks similar with Dataframes)" in {
      val csrDs = getCsrDs
      val consumptionDs = getConsumptionDs
      // Downside - joins aren't typesafe, rely on correct string values for col names. Frameless library can help with this
      val joinedDf: DataFrame = csrDs.join(
        consumptionDs,
        csrDs.col("ElectricSensorId") === consumptionDs.col("ElectricSensorId"), "left")
      val joinedDs: Dataset[(SimpleCsr, SimpleConsumption)] = csrDs.joinWith(
        consumptionDs,
        csrDs.col("ElectricSensorId") === consumptionDs.col("ElectricSensorId"), "left")
      joinedDf.show()
      joinedDs.show(100)
    }
  }

  "Something more complex - join 2 data sources and lookup historical versions" - {
    "Dataset - serialization errors - these still happen!!!" in {

      def lookupHistoricalCsr(sensorId: String) = Some(SimpleCsr("123", "LIVE", sensorId, "123", 123, 123))
      def lookupHistoricConsumption(sensorId: String) = Some(SimpleConsumption(sensorId, "faux-timestamp", 999, 999))

      val csrDs = getCsrDs
      val consumptionDs = getConsumptionDs
      val joinedDs: Dataset[(Option[SimpleCsr], Option[SimpleConsumption])] = csrDs.joinWith(
        consumptionDs,
        csrDs.col("ElectricSensorId") === consumptionDs.col("ElectricSensorId"), "outer").map{
        case (null, consumption) => (lookupHistoricalCsr(consumption.electricSensorID), Some(consumption))
        case (csr, null) => (Some(csr), lookupHistoricConsumption(csr.electricSensorID))
        case (csr, consumption) => (Some(csr), Some(consumption))
      }
      joinedDs.show(100)
    }

    "Dataset - working" in {
      val lookupHistoricalCsr = (sensorId: String) => Some(SimpleCsr("123", "LIVE", sensorId, "123", 123, 123))
      val lookupHistoricConsumption = (sensorId: String) => Some(SimpleConsumption(sensorId, "faux-timestamp", 999, 999))

      val csrDs = getCsrDs
      val consumptionDs = getConsumptionDs
      val joinedDs: Dataset[(Option[SimpleCsr], Option[SimpleConsumption])] = csrDs.joinWith(
        consumptionDs,
        csrDs.col("ElectricSensorId") === consumptionDs.col("ElectricSensorId"), "outer").map{
        case (null, consumption) => (lookupHistoricalCsr(consumption.electricSensorID), Some(consumption))
        case (csr, null) => (Some(csr), lookupHistoricConsumption(csr.electricSensorID))
        case (csr, consumption) => (Some(csr), Some(consumption))
      }
      joinedDs.collect().toList.foreach(println)
    }
  }
}

case class SimpleCsr(householdId: String,
                     statusElectric: String,
                     electricSensorID: String,
                     postalCode: String,
                     latitude: Int,
                     longitude: Int)

case class SimpleConsumption(electricSensorID: String,
                             timestamp: String,
                             householdImport: Int,
                             householdExport: Int)
