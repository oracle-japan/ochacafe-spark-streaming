#!/bin/bash

# change the path for the nosql driver
NOSQL_DRIVER="/home/opc/work/nosql/sdk/lib/nosqldriver.jar"

java -cp $NOSQL_DRIVER:target/tempmon.jar \
    -Dmonitor-store=nosql \
    oracle.demo.tempmon.Main

