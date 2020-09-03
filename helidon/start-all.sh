#!/bin/bash

# change the path for the nosql & coherence driver
NOSQL_DRIVER="/home/opc/work/nosql/sdk/lib/nosqldriver.jar"

java -cp target/tempmon.jar:$NOSQL_DRIVER \
    -Dmonitor-store=$1 \
    oracle.demo.tempmon.Main

