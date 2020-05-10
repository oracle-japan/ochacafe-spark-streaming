package com.oracle.demo

import org.apache.spark.sql.{DataFrame, Row, SparkSession}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.DataStreamWriter

import scala.reflect.io.Directory
import java.io.File

import io.helidon.config.Config

/**
 * Sliding Window を使った温度センサー異常検知シナリオ
 */
object TemperatureMonitorKafkaSW {

  case class RackInfo(rackId:String, temperature:Double, timestamp:java.sql.Timestamp)
  case class RackState(var rackId:String, var highTempCount:Int, var status:String, var prevStatus:String, var lastTS:java.sql.Timestamp)

  val config: Config = Config.create()
  var temperatureThreshold: Double = config.get("config.threshold-temp").asDouble().orElse(100.0)
  var watermarkDelayThreshold: String = config.get("config.threshold-watermark").asString().orElse("0 seconds")
  var windowDuration: String = config.get("config.duration-window").asString().orElse("30 seconds")
  var windowSlideDuration: String = config.get("config.duration-slide").asString().orElse("5 seconds")
  var outputMode: String = config.get("sw.output-mode").asString().orElse("update")

  // データが一定間隔で到着する仮定の下で、Windowあたりのデータの最大個数
  // updateモードの場合、未確定のWindowが出力されるので、これでフィルタをかける
  // 30秒のWindowで5秒おきにデータが到着する場合 6 となる
  var eventsPerWindow: Int = config.get("config.events-per-window").asInt().orElse(6)

  def main(args: Array[String]) {

    parseArgs(args)
    println(s"Output mode: $outputMode")
    println(s"Watermark delay: $watermarkDelayThreshold")
    println(s"Window duration: $windowDuration")
    println(s"Window slide duration: $windowSlideDuration")

    val spark = SparkSession
      .builder()
      .appName("TemperatureMonitor_SlidingWindow")
      .getOrCreate()
    
    import org.apache.spark.sql.types._
    import spark.implicits._

    val monitorDataSchema = new StructType()
      .add(name = "rackId", dataType = StringType, nullable = false)
      .add(name = "temperature", dataType = DoubleType, nullable = false)
      .add(name = "timestamp", dataType = TimestampType, nullable = false)

    // ステートの判定を行うユーザー定義関数
    val checkState = udf((max: Double, min: Double, count: Int) => {
      var state = "Transient"
      if(outputMode == "update" && count < eventsPerWindow){ 
        // update modeの場合、イベントが1つでも存在すると出力されるので
        // eventsPerWindow 個になる（確定する）までVoid扱いする
        // ただし、watermarkが0より大きい場合それでも確定しているわけでは
        // ない（=更に更新される可能性がある）が、今回はイベントは一定間隔で
        // 到着する前提なのでこの条件は無視する
        state = "Void"
      }else{
        if(min >= temperatureThreshold) state = "Warning"
        else if(max < temperatureThreshold) state = "Normal"
      }
      state
    })

    val df = subscribe(spark)
      .selectExpr("CAST(value AS STRING)")
      .select(from_json($"value", monitorDataSchema).as("rackInfo"))
      .select($"rackInfo.rackId".as("rackId"), $"rackInfo.temperature".as("temperature"), $"rackInfo.timestamp".as("timestamp"))
      .withWatermark("timestamp", watermarkDelayThreshold) // necessary when append mode
      .groupBy($"rackId", window($"timestamp", windowDuration, windowSlideDuration))
      .agg(
        max("temperature").as("maxTemp"),
        min("temperature").as("minTemp"),
        count("temperature").as("count")
      )
      .withColumn("status", checkState($"maxTemp", $"minTemp", $"count"))

    // publish to kafka
    val kafkaDf = (if(outputMode == "update") df.where($"count" >= eventsPerWindow) else df)
      .select(lit("SW").as("key"), to_json(struct($"rackId", $"window", $"status", $"maxTemp", $"minTemp")).as("value"))
    val sq1 = publish(kafkaDf) 
      .outputMode(outputMode)
      .start()

    // write to console - デモのために意図的にコンソール出力を追加している
    val sq2 = df 
      .select($"rackId", $"window.start".as("start"), $"window.end".as("end"), $"status", $"maxTemp".as("max"), $"minTemp".as("min"), $"count")
      .writeStream
      .format("console")
      .outputMode(outputMode)
      .option("truncate", "false")
      .option("numRows", 100)
      .start()

    sq1.awaitTermination()
    sq2.awaitTermination()
    spark.close()
  }

  val loginModule = "org.apache.kafka.common.security.plain.PlainLoginModule"

  def subscribe(spark: SparkSession): DataFrame = {
    val subConfig = config.get("sub.kafka")
    val subTenantName = subConfig.get("tenant-name").asString().get()
    val subPoolId = subConfig.get("pool-id").asString().get()
    val subStreamingServer = subConfig.get("streaming-server").asString().get()
    val subUserName = subConfig.get("user-name").asString().get()
    val subAuthToken = subConfig.get("auth-token").asString().get()
    val subTopic = subConfig.get("topic").asString().get()

    spark
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", subStreamingServer)
      .option("kafka.security.protocol", "SASL_SSL")
      .option("kafka.sasl.mechanism", "PLAIN")
      .option("kafka.sasl.jaas.config", s"""${loginModule} required username="${subTenantName}/${subUserName}/${subPoolId}" password="${subAuthToken}";""")
      .option("kafka.max.partition.fetch.bytes", 1024 * 1024)
      .option("startingoffsets", "latest")
      .option("failOnDataLoss", false)
      .option("subscribe", subTopic)
      .load
  }

  def publish(df: DataFrame): DataStreamWriter[Row] = {
    val pubConfig = config.get("pub.kafka")
    val pubTenantName = pubConfig.get("tenant-name").asString().get()
    val pubPoolId = pubConfig.get("pool-id").asString().get()
    val pubStreamingServer = pubConfig.get("streaming-server").asString().get()
    val pubUserName = pubConfig.get("user-name").asString().get()
    val pubAuthToken = pubConfig.get("auth-token").asString().get()
    val pubTopic = pubConfig.get("topic").asString().get()
    val pubCheckpointLocation = pubConfig.get("checkpoint-location").asString().get()

    // clean up
    val directory = new Directory(new File(pubCheckpointLocation))
    directory.deleteRecursively()
    
    df
      .writeStream
      .format("kafka")
      .option("kafka.bootstrap.servers", pubStreamingServer)
      .option("kafka.security.protocol", "SASL_SSL")
      .option("kafka.sasl.mechanism", "PLAIN")
      .option("kafka.sasl.jaas.config", s"""${loginModule} required username="${pubTenantName}/${pubUserName}/${pubPoolId}" password="${pubAuthToken}";""")
      .option("startingoffsets", "latest")
      .option("max.request.size", 1024 * 1024)
      .option("retries", 5)
      .option("topic", pubTopic)
      .option("checkpointLocation", pubCheckpointLocation)
  }

  def parseArgs(args: Array[String]): Unit = {
      var i = 0
      while(i < args.length) {
          if(args(i) == "--output-mode"){
              i = i + 1
              outputMode = args(i)
          }
          if(args(i) == "--watermark"){
              i = i + 1
              watermarkDelayThreshold = args(i) + " seconds"
          }
          i = i + 1
      }
  }

}

