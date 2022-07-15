FROM openjdk:17-slim
#FROM adoptopenjdk:8u252-b09-jre-openj9-0.20.0-bionic


EXPOSE 9090
EXPOSE 9443

ENV JAVA_OPTS="-XX:TieredStopAtLevel=1 -noverify -Xms1G"
RUN mkdir /config
RUN mkdir /log
 
VOLUME /log
VOLUME /config


COPY /target/factcast.jar /factcast.jar
ENTRYPOINT exec java $JAVA_OPTS -jar /factcast.jar $0 $@
