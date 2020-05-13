# Spark Streaming デモ - 外部とのインターフェス部分

| モジュール | 機能 | Streamingから見ると |
|-----------|------|--------------------|
| TempReporter  | RESTで受信した温度データを管理し、OCI Streaming 経由で Spark Streaming に渡す        | Source |
| SlackAlerter | Spark Streaming から返されたデータをOCI Streaming 経由で受信＆処理してSlackにアラートを出す | Sink |

Webサーバーが起動するのはTempReporterを有効にした場合のみ  

TempReporter はAPIで受信したラック単位の温度をキャッシュし、一定間隔で、全ラックの温度情報をkafkaに送信する

## ビルド
```
mvn clean package
```

src/main/resources/example-application.yaml を application.yaml にリネームして必要な情報を設定して下さい。

## 起動

通常は、シェルを使って、TempReporter, SlackAlerter を別JVMで起動した方が標準出力が見やすい

```
./start-reporter.sh
```

Web/RESTサーバーは TempReporter と一緒に立ち上がります。ポート 8080 でリクエストを受け付けます。  
ブラウザからルートにアクセスするとシミュレーションのページが表示されます。

```
./start-alerter.sh
```

1 JVMで起動することももちろん可能
```
java -jar target/tempmon.jar
```

### API

#### 温度情報をポストする

```
curl http://localhost:8080/tempmon/ -X POST -H "Content-Type: application/json" -d '{"rackId":"rack-03","temperature":100}'
```

#### OCI Streaming (Kafaka) への送信を中断する

```
curl http://localhost:8080/tempmon/control?op=pause
```

#### OCI Streaming (Kafaka) への送信を再開する


```
curl http://localhost:8080/tempmon/control?op=resume
```

