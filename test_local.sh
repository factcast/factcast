#!/bin/bash

#mvn clean install -o -DskipTests && \
java \
-D"spring.datasource.url=jdbc:pgsql://192.168.3.11/test1?user=tester&password=test_password" \
-Dmanagement.security.enabled=false \
-Dmanagement.port=8081 \
-Dlogging.level.org.factcast=DEBUG \
-jar factcast-server/target/factcast.jar 


#-Dlogging.level.org.factcast=TRACE \
#spring.datasource.url=jdbc:pgsql://192.168.3.11/test1?user=tester&password=test_password
