package com.oracle.demo

import java.sql.Timestamp

import org.apache.spark.sql.{DataFrame, Row, SparkSession}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.{DataStreamWriter, GroupState}

import scala.reflect.io.Directory
import java.io.File

import io.helidon.config.Config

/**
 * Arbitrary Stateful Processing を使った温度センサー異常検知シナリオ
 */
object TemperatureMonitorKafkaASP {

  case class RackInfo(rackId:String, temperature:Double, timestamp:Timestamp)
  case class RackState(var rackId:String, var status:String, var prevStatus:String, var eventTS:Timestamp, var ts:Timestamp, var temperature:Double)

  val config: Config = Config.create()
  val temperatureThreshold: Double = config.get("config.threshold-temp").asDouble().orElse(100.0)
  val warningThreshold: Long = config.get("config.threshold-warning").asLong().orElse(30 * 1000L)
  val normalThreshold: Long = config.get("config.threshold-normal").asLong().orElse(30 * 1000L)

  /**
   * 受信データが一定時間（warningThreshold/normalThreshould）閾値(temperatureThreshould)を
   * 越えているか/下回っているかをチェックする
   */
  def updateRackState(rackState : RackState, rackInfo : RackInfo) : RackState = {
    println(rackInfo)
    val oldState = rackState.copy()
    var firstTS = rackState.eventTS

    val isGreaterThanEqualToThreshold = rackInfo.temperature >= temperatureThreshold // boolean
    val prev = Option(rackState.status).getOrElse(if(isGreaterThanEqualToThreshold) "Warning" else "Normal")
    val isInNormalStatus = (prev == "Normal")
    val duration = rackInfo.timestamp.getTime - Option(firstTS).getOrElse(rackInfo.timestamp).getTime

    println(s""" = ${if(isGreaterThanEqualToThreshold) "Above" else "Below"} $temperatureThreshold for $duration msec since changed.""")

    (isGreaterThanEqualToThreshold, isInNormalStatus) match {
      case (true, true) => { // >=temperatureThreshold and Normal status
        if(Option(firstTS).isEmpty){
          rackState.eventTS = rackInfo.timestamp
          rackState.status = prev
        }else{
          if(duration >= warningThreshold){
            rackState.eventTS = null
            rackState.status = "Warning"
          }
        }
      }
      case (false, false) => { // < temperatureThreshold and Warning status
        if(Option(firstTS).isEmpty){
          rackState.eventTS = rackInfo.timestamp
          rackState.status = prev
        }else{
          if(duration >= normalThreshold){
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
    printStates(oldState, rackState)

    if(rackState.status != rackState.prevStatus){
      println("!!!!! Status has changed !!!!!")
    }
    println()
    rackState
  }

  def updateAcrossAllRackStatus(rackId : String, inputs : Iterator[RackInfo], oldState : GroupState[RackState]) : RackState = {
    println(s"[$rackId] >> updateAcrossAllRackStatus")

    var rackState = if (oldState.exists) oldState.get else RackState(rackId, null, null, null, null, 0.0)

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

    import org.apache.spark.sql.streaming.GroupStateTimeout
    import org.apache.spark.sql.types._
    import spark.implicits._

    val monitorDataSchema = new StructType()
      .add(name = "rackId", dataType = StringType, nullable = false)
      .add(name = "temperature", dataType = DoubleType, nullable = false)
      .add(name = "timestamp", dataType = TimestampType, nullable = false)

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

  def printStates(oldState: RackState, newState: RackState): Unit = {
    println("+-----+--------+-----+--------+--------+---------------------+---------------------+")
    println("|     |Rack ID |Temp.|Current |Previous|TS of Initial Event  |TS of Last Event     |")
    println("+-----+--------+-----+--------+--------+---------------------+---------------------+")
    println(formatState("Last", oldState))
    println(formatState("New", newState))
    println("+-----+--------+-----+--------+--------+---------------------+---------------------+")
  }

  def formatState(state: String, rackState: RackState): String = {
    "|%-5s|%-8s|%5.1f|%-8s|%-8s|%21s|%21s|"
      .format(state, rackState.rackId, rackState.temperature, rackState.status, rackState.prevStatus, rackState.eventTS, rackState.ts)
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

