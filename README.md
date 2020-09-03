# OCHaCafe Season 2 #6 「Cloud Native × Streaming はじめの一歩」

2020年5月13日開催の [Oracle Cloud Hangout Cafe](https://ochacafe.connpass.com/event/169396/) でお見せしたデモのソースコードです。

```text
.
├── helidon [フロントの温度センサー&Slackとのインターフェース - Javaのソース]
└── spark [Spark Streaming処理部分 - Scalaのソース]
```

helidon(Java)はmvn、spark(Scala)はsbtでビルドして下さい。

## 変更履歴

|Date      | 内容 |
|----------|-------------------------------------------------------|
|2020.05.13| 初版 |
|2020.05.19| option-multi-store-types ブランチを追加                 |
|2020.08.18| Helidon を2.0.1にバージョンアップ、Kafka Connectorを使用 |
|2020.09.03| option-multi-store-types ブランチをmainにマージ         |

---
_Copyright © 2020, Oracle and/or its affiliates. All rights reserved._
