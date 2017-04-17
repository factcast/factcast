#!/bin/bash

cd ..
mvn clean install -DskipTests
cd -
mvn docker:build

docker rm factcast_local


docker run  \
-e factcast.store.pgsql.user=tester \
-e factcast.store.pgsql.password=test_password \
-e factcast.store.pgsql.dbname=test1 \
-e factcast.store.pgsql.host=172.17.0.1 \
-e logging.level.org.factcast=TRACE \
--name factcast_local \
factcast/factcast-server