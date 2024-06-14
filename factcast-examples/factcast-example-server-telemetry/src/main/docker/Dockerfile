FROM frolvlad/alpine-oraclejdk8:slim
MAINTAINER uwe.schaefer <factcast-auth-7234@codesmell.de>
VOLUME /tmp
COPY factcast.jar app.jar
RUN sh -c 'touch /app.jar'
ENV JAVA_OPTS=""
ENTRYPOINT [ "sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar /app.jar" ]
