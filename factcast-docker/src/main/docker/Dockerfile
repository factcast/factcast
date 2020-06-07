FROM oracle/graalvm-ce:19.3.2-java8
EXPOSE 9090
EXPOSE 9443

ENV JAVA_OPTS=""
RUN mkdir /config
RUN mkdir /log
 
VOLUME /log
VOLUME /config

COPY factcast.jar /factcast.jar
ENTRYPOINT exec java $JAVA_OPTS -cp /config -jar /factcast.jar $0 $@