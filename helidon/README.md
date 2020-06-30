# Spark Streaming デモ - 外部とのインターフェス部分

| モジュール | 機能 | Streamingから見ると |
|-----------|------|:------------------:|
| TempMonitor | Web UI のラックからデータを受信してストアに格納する、Web Serverが起動する         | - |
| TempReporter  | ストアに格納されたデータを OCI Streaming 経由で Spark Streaming に渡す        | Source |
| SlackAlerter | Spark Streaming から返されたデータをOCI Streaming 経由で受信＆処理してSlackにアラートを出す | Sink |

ストアは ローカル(java,util.Map), Oracle Coherence, Oracle NoSQL Database Cloud, Redis, DynamoDB, MongoDB のいずれかを選択可能。

## ビルド

- Oracle NoSQL Database Cloud用のjar(nosqldriver.jar)は、Oracleのサイトから所定の手続きを経て入手し、pom.xml, .sh内のパスを正しく設定して下さい。
- src/main/resources/example-application.yaml を application.yaml にリネームして必要な情報を設定して下さい。

```
mvn clean package
```


## 起動

以下のシェルの組み合わせで起動できます。3つの機能が同じストアで起動するようにして下さい。

| シェル                                  | TempMonitor | TempReporter | SlackAlerter | 利用可能な store-type                   |
|----------------------------------------|:------------:|:------------:|:------------:|----------------------------------------|
|start-monitor.sh \<store-type\>         |      o       |      x       |      x       | coherence, nosql, redis, dynamodb, mongodb      |
|start-monitor-reporter.sh \<store-type\>|      o       |      o       |      x       | map, coherence, nosql, redis, dynamodb, mongodb |
|start-reporter.sh \<store-type\>        |      x       |      o       |      x       | coherence, nosql, redis, dynamodb, mongodb      |
|start-alerter.sh                        |      x       |      x       |      o       | n/a                                    |
|start-all.sh \<store-type\>             |      o       |      o       |      o       | map, coherence, nosql, redis, dynamodb, mongodb |


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

