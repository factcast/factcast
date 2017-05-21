#!/bin/bash

mvn clean install -o -DskipTests && \
java \
-Dmanagement.security.enabled=false \
-Dmanagement.port=8081 \
-Dlogging.level.org.factcast=DEBUG \
-jar factcast-server/target/factcast.jar 


