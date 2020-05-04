package com.oracle.demo

import java.sql.Timestamp

import org.apache.spark.sql.{DataFrame, Row, SparkSession}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.{DataStreamReader, DataStreamWriter, GroupState, StreamingQuery, Trigger}
import java.util.concurrent.atomic.AtomicInteger

import scala.reflect.io.Directory
import java.io.File

import io.helidon.config.Config;

/**
 * Arbitrary Stateful Processing を使った温度センサー異常検知シナリオ
 */
object TemperatureMonitorKafkaASP {

  case class RackInfo(rackId:String, temperature:Double, timestamp:Timestamp)
  case class RackState(var rackId:String, var status:String, var prevStatus:String, var eventTS:Timestamp, var ts:Timestamp, var temperature:Double)

  val config: Config = Config.create()
  val temperatureThreshould = config.get("config.threshould-temp").asDouble().orElse(100.0)
  val warningThreshold = config.get("config.threshould-warning").asLong().orElse(30 * 1000L)
  val normalThreshould = config.get("config.threshould-normal").asLong().orElse(30 * 1000L)

  /**
   * 受信データが一定時間（warningThreshold/normalThreshould）閾値(temperatureThreshould)を
   * 越えているか/下回っているかをチェックする
   */
  def updateRackState(rackState : RackState, rackInfo : RackInfo) : RackState = {
    println("== updateRackState ==")
    println("Last state: " + rackState)
    println("Current rack info: " + rackInfo)

    var firstTS = rackState.eventTS

    val isGreaterThanEqualToThreshould = rackInfo.temperature >= temperatureThreshould // boolean
    println(s"isGreaterThanEqualToThreshould: $isGreaterThanEqualToThreshould")

    val prev = Option(rackState.status).getOrElse(if(isGreaterThanEqualToThreshould) "Warining" else "Normal")

    val isInNormalStatus = (prev == "Normal")
    println(s"isInNormalStatus: $isInNormalStatus")

    val duration = rackInfo.timestamp.getTime - Option(firstTS).getOrElse(rackInfo.timestamp).getTime
    println(s"duration: $duration")

    (isGreaterThanEqualToThreshould, isInNormalStatus) match {
      case (true, true) => { // >=temperatureThreshould and Normal status
        if(firstTS == null){
          rackState.eventTS = rackInfo.timestamp
          rackState.status = prev
        }else{
          if(duration >= warningThreshold){
            rackState.eventTS = null
            rackState.status = "Warning"
          }
        }
      }
      case (false, false) => { // < temperatureThreshould and Warning status
        if(firstTS == null){
          rackState.eventTS = rackInfo.timestamp
          rackState.status = prev
        }else{
          if(duration >= normalThreshould){
            rackState.eventTS = null
            rackState.status = "Normal"
          }
        }
      }
      case _ => {
        rackState.eventTS = null
        rackState.status = prev
      } // keep it as is
    }

    rackState.prevStatus = prev
    rackState.ts = rackInfo.timestamp
    rackState.temperature = rackInfo.temperature
    println("Updated state: " + rackState)
    if(rackState.status != rackState.prevStatus){
      println("!!!!! Status has changed !!!!!")
    }
    println()
    rackState
  }

  def updateAcrossAllRackStatus(rackId : String, inputs : Iterator[RackInfo], oldState : GroupState[RackState]) : RackState = {
    println(s"[$rackId] >> updateAcrossAllRackStatus")

    var rackState = if (oldState.exists) oldState.get else RackState(rackId, null, null, null, null, 0)

    inputs.toList.sortBy(_.timestamp.getTime).foreach( input => {
      rackState = updateRackState(rackState, input)
      oldState.update(rackState)
    })
    rackState
  }

  def main(args: Array[String]) {

    val spark = SparkSession
      .builder()
      .appName("TemperatureMonitor_ArbitraryStatefulProcessing")
      .getOrCreate()

    sys.ShutdownHookThread {
      val sc = spark.sparkContext
      if(!sc.isStopped) sc.stop()
      Thread.sleep(3000L)
    }

    import org.apache.spark.sql.streaming.GroupStateTimeout
    import org.apache.spark.sql.types._
    import spark.implicits._

    val monitorDataSchema = new StructType()
      .add("rackId", StringType, false)
      .add("temperature", DoubleType, false)
      .add("timestamp", TimestampType, false)

    val df = subscribe(spark)
      .selectExpr("CAST(value AS STRING)").as("value")
      .select(from_json($"value", monitorDataSchema).as("rackInfo"))
      .select($"rackInfo.rackId".as("rackId"), $"rackInfo.temperature".as("temperature"), $"rackInfo.timestamp".as("timestamp"))
      .as[RackInfo]
      .groupByKey(_.rackId).mapGroupsWithState[RackState, RackState](GroupStateTimeout.NoTimeout)(updateAcrossAllRackStatus)
      .where($"status" =!= $"prevStatus")
      .select(lit("ASP").as("key"), to_json(struct($"rackId", $"status", $"ts", $"temperature")).as("value"))

    val sq = publish(df).start()

    sq.awaitTermination()
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
      .outputMode("update") //  must be "update" in case of ASP
  }
}

