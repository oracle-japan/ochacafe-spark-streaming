# Spark Streaming デモ - 異常検知ロジック部分

ラックの個々の温度を監視し、一定時間閾値を越えた/下回った場合にSlackに対してアラートを発出します。  
3種類の方法で実装してみました。  

| ファイル                           | 説明                                                                | 
|-----------------------------------|---------------------------------------------------------------------|
| TemperatureMonitorKafkaSW.scala   | Sliding Window 方式による実装                                        |
| TemperatureMonitorKafkaASP.scala  | Arbitrary Stateful Processing 方式による実装(mapGroupsWithState)     |
| TemperatureMonitorKafkaASP2.scala | Arbitrary Stateful Processing 方式による実装(flatMapGroupsWithState) |

## Sliding Window 方式による実装

デフォルトで30秒のwindowを5秒ごとにスライドさせてwindow毎に集計処理を行います。  
過去一定時間閾値を越える = 全てのイベントのデータが閾値より大きい = 最小値が閾値より大きい  
と捉えることができます（逆の場合は最大値を見ればよい）。  
こうして、各Window毎のステータスを判断します。ただし、これは個々のWidnow内の集計処理なので、過去のステータスからの推移を追跡することはできません。したがって、Widnow集計処理の外でステータスの変化をチェックする（Slackにアラート投げるタイミングを計る）仕組みが必要です。

```
+-------+-------------------+-------------------+---------+-----+-----+-----+
|rackId |start              |end                |status   |max  |min  |count|
+-------+-------------------+-------------------+---------+-----+-----+-----+
|rack-02|2020-05-01 17:38:25|2020-05-01 17:38:55|Normal   |77.0 |77.0 |6    |
|rack-02|2020-05-01 17:38:30|2020-05-01 17:39:00|Normal   |77.0 |77.0 |6    |
|rack-02|2020-05-01 17:38:35|2020-05-01 17:39:05|Normal   |77.0 |77.0 |6    |
|rack-02|2020-05-01 17:38:40|2020-05-01 17:39:10|Normal   |77.0 |77.0 |6    |
|rack-02|2020-05-01 17:38:45|2020-05-01 17:39:15|Transient|144.0|77.0 |6    |
|rack-02|2020-05-01 17:38:50|2020-05-01 17:39:20|Transient|144.0|77.0 |6    |
|rack-02|2020-05-01 17:38:55|2020-05-01 17:39:25|Transient|144.0|77.0 |6    |
|rack-02|2020-05-01 17:39:00|2020-05-01 17:39:30|Transient|144.0|77.0 |6    |
|rack-02|2020-05-01 17:39:05|2020-05-01 17:39:35|Transient|144.0|77.0 |6    |
|rack-02|2020-05-01 17:39:10|2020-05-01 17:39:40|Warning  |144.0|144.0|6    |<< Warning のトリガー
|rack-02|2020-05-01 17:39:15|2020-05-01 17:39:45|Warning  |144.0|144.0|6    |
+-------+-------------------+-------------------+---------+-----+-----+-----+
```

このデモでは、ステータスのトラッキングはHelidon側で処理しています。

## Arbitrary Stateful Processing 方式による実装

新たな温度データが到達したタイミングで都度ステータスの計算を行います。任意の処理が可能で、さらに過去の情報を引き継ぐ仕組みも持っているので、ステータスの変化を自ら特定することができます。  
設定したタイムアウト値以内に新たなデータが到達しないとステータスは"Timeout"となり、判定処理がリセットされます。  
mapGroupsWithState での実装は必ず1行出力するので、出力した結果にwhereをかけてAlertするものだけにフィルタしています。

```
.groupByKey(_.rackId).mapGroupsWithState[RackState, RackState](GroupStateTimeout.ProcessingTimeTimeout)(updateAcrossAllRackStatus)
.where($"status" =!= $"prevStatus") // 直前のステータスが異なるものだけにフィルタ
.select(lit("ASP").as("key"), to_json(struct($"rackId", $"status", $"ts", $"temperature")).as("value"))
```

flatMapGroupsWithState での実装は0行以上の出力が可能なので、Alertするものだけを出力しています。

```
.groupByKey(_.rackId).flatMapGroupsWithState[RackState, RackState](om, GroupStateTimeout.ProcessingTimeTimeout)(updateAcrossAllRackStatus)
.select(lit("ASP").as("key"), to_json(struct($"rackId", $"status", $"ts", $"temperature")).as("value"))
```

## ビルド
```
sbt clean package
```
src/main/resources/example-application.yaml を application.yaml にリネームして必要な情報を設定して下さい。

## 起動

通常は、シェルを使って下さい（環境変数 SPARK_HOME が設定されている前提です）。

```
./start.sh [ SW | ASP | ASP2 ] 

option: --output-mode [ append | update ] 
        --watermark <seconds> 
        --timeout <seconds>
```
