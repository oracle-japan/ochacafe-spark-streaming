#!/bin/bash

NOSQL_DRIVER_PATH="/home/opc/work/nosql/sdk/lib/nosqldriver.jar"
LOCAL_REPO_PATH="$(pwd)/local-repo"

mvn org.apache.maven.plugins:maven-install-plugin:3.0.0-M1:install-file \
    -DgroupId=oracle.nosql \
    -DartifactId=nosqldriver \
    -Dversion=0.0 \
    -Dpackaging=jar \
    -DcreateChecksum=true \
    -Dfile=$NOSQL_DRIVER_PATH \
    -DlocalRepositoryPath=$LOCAL_REPO_PATH

