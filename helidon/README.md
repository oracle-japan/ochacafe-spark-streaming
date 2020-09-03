# Spark Streaming デモ - 外部とのインターフェス部分

| モジュール | 機能 | Streamingから見ると |
|-----------|------|:------------------:|
| TempMonitor | Web UI のラックからデータを受信してストアに格納する、Web Serverが起動する         | - |
| TempReporter  | ストアに格納されたデータを OCI Streaming 経由で Spark Streaming に渡す        | Source |
| SlackAlerter | Spark Streaming から返されたデータをOCI Streaming 経由で受信＆処理してSlackにアラートを出す | Sink |  

<br/>

### モニター情報の一時ストア  

ラック・シミュレーターから送られてくる温度データの保存するストアの実装を
[Oracle Coherence Community Edition](https://github.com/oracle/coherence)、 
[Oracle NoSQL Database Cloud Service](https://docs.oracle.com/cd/E83857_01/paas/nosql-cloud/index.html)、
[Redis](https://redis.io/)、
[DynamoDB](https://aws.amazon.com/jp/dynamodb/)、
[MongoDB](https://www.mongodb.com/)、
[Cassandra](https://cassandra.apache.org/)
の6種類で実装し、起動オプションで切り替えることができます。Spark Streaming のデモのシナリオとは直接関係ありませんが、複数プロセスから参照・更新可能なキャッシュの実装例として参考になれば幸いです。 


## ビルド

Helidon 2.0 を前提にしていますので、Java 11 が必要です。
- Oracle NoSQL Database Cloud用のjar(nosqldriver.jar)は、Oracleのサイトから所定の手続きを経て入手し、pom.xml, .sh内のパスを正しく設定して下さい。
- src/main/resources/example-application.yaml を application.yaml にリネームして必要な情報を設定して下さい。

```
mvn clean package
```

## Kafaka Connector の設定

application.yaml にKafakaの接続情報を設定します。OCI Streamingを利用する場合は、[Kafaka互換性に関するドキュメンテーション](https://docs.cloud.oracle.com/ja-jp/iaas/Content/Streaming/Tasks/kafkacompatibility.htm) を参照の上設定をして下さい。

## 起動

以下のシェルの組み合わせで起動できます。3つの機能が同じストアで起動するようにして下さい。

| シェル                                  | TempMonitor | TempReporter | SlackAlerter | 利用可能な store-type                   |
|----------------------------------------|:------------:|:------------:|:------------:|----------------------------------------|
|start-monitor.sh \<store-type\>         |      o       |      x       |      x       | coherence, nosql, redis, dynamodb, mongodb, cassandra      |
|start-monitor-reporter.sh \<store-type\>|      o       |      o       |      x       | map, coherence, nosql, redis, dynamodb, mongodb, cassandra |
|start-reporter.sh \<store-type\>        |      x       |      o       |      x       | coherence, nosql, redis, dynamodb, mongodb, cassandra      |
|start-alerter.sh                        |      x       |      x       |      o       | n/a                                    |
|start-all.sh \<store-type\>             |      o       |      o       |      o       | map, coherence, nosql, redis, dynamodb, mongodb, cassandra |


## API

### 温度情報をポストする

```
curl http://localhost:8080/tempmon/ -X POST -H "Content-Type: application/json" -d '{"rackId":"rack-03","temperature":100}'
```

### OCI Streaming (Kafaka) への送信を中断する

```
curl http://localhost:8080/tempmon/control?op=pause
```

### OCI Streaming (Kafaka) への送信を再開する


```
curl http://localhost:8080/tempmon/control?op=resume
```

