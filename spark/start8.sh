#!/bin/bash


JAVA_HOME=/home/opc/opt/jdk1.8.0_241
export JAVA_HOME

MONITOR_TYPE=$1

echo "MONITOR_TYPE=${MONITOR_TYPE}"

shift

$SPARK_HOME/bin/spark-submit \
  --master local[*] --driver-memory 4g \
  --class com.oracle.demo.TemperatureMonitorKafka${MONITOR_TYPE} \
  --packages org.apache.spark:spark-sql-kafka-0-10_2.11:2.4.5,io.helidon.config:helidon-config-yaml:1.4.4 \
  $(pwd)/target/scala-2.11/temperature-monitor_2.11-0.1.jar $*
