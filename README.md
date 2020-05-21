# OCHaCafe Season 2 #6 「Cloud Native × Streaming はじめの一歩」

2020年5月13日開催の [Oracle Cloud Hangout Cafe](https://ochacafe.connpass.com/event/169396/) でお見せしたデモのソースコードです。

```text
.
├── helidon [フロントの温度センサー&Slackとのインターフェース - Javaのソース]
└── spark [Spark Streaming処理部分 - Scalaのソース]
```

helidon(Java)はmvn、spark(Scala)はsbtでビルドして下さい。

## option-multi-store-types ブランチ

ラック・シミュレーターから送られてくる温度データの保存するストアの実装を
[Oracle Coherence](https://www.oracle.com/jp/middleware/coherence/)、 
[Oracle NoSQL Database Cloud Service](https://docs.oracle.com/cd/E83857_01/paas/nosql-cloud/index.html)、
[Redis](https://redis.io/)
の3種類で実装してみました。Spark Streaming のデモのシナリオとは直接関係ありませんが、複数プロセスから参照・更新可能なキャッシュの実装例として参考になれば幸いです。

## 変更履歴

|Date      | 内容 |
|----------|--------------------------------------|
|2020.05.13| 初版 |
|2020.05.19| option-multi-store-types ブランチを追加 |

---
_Copyright © 2020, Oracle and/or its affiliates. All rights reserved._