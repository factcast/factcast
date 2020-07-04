FROM openjdk:8u252-slim

EXPOSE 9090
EXPOSE 9443

ENV JAVA_OPTS="-XX:+TieredCompilation -XX:TieredStopAtLevel=1 -noverify -Xms1G -Dspring.jmx.enabled=false"
RUN mkdir /config
RUN mkdir /log
 
VOLUME /log
VOLUME /config


COPY /target/factcast.jar /factcast.jar
ENTRYPOINT exec java $JAVA_OPTS -jar /factcast.jar $0 $@
