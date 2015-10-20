#!/usr/bin/env bash

echo "Running Script"
cd /dynamodb
java -Djava.library.path=. -jar DynamoDBLocal.jar