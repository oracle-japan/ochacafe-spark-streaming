#!/bin/bash

# change the path for the nosql & coherence driver
NOSQL_DRIVER="/home/opc/work/nosql/sdk/lib/nosqldriver.jar"
COHERENCE_JAR="/home/opc/opt/wls1411/coherence/lib/coherence.jar"

java -cp target/tempmon.jar:$NOSQL_DRIVER:$COHERENCE_JAR \
    -Dtemp-reporter.enabled=false -Dslack-alerter.enabled=false -Dmonitor-store=$1 \
    oracle.demo.tempmon.Main

