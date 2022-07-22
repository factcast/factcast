FROM openjdk:17-slim

EXPOSE 9090
EXPOSE 9443

ENV JAVA_OPTS="-XX:TieredStopAtLevel=1 -Xms1G"
RUN mkdir /config
RUN mkdir /log
 
VOLUME /log
VOLUME /config


COPY /target/factcast.jar /factcast.jar
ENTRYPOINT exec java $JAVA_OPTS -jar /factcast.jar $0 $@
