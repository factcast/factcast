# Perform the extraction in a separate builder container
FROM bellsoft/liberica-openjre-debian:25.0.2-cds AS builder
WORKDIR /builder
# This points to the built jar file in the target folder
# Adjust this to 'build/libs/*.jar' if you're using Gradle
ARG JAR_FILE=target/*.jar
# Copy the jar file to the working directory and rename it to factcast-server.jar
COPY ${JAR_FILE} factcast-server.jar
# Extract the jar file using an efficient layout
RUN java -Djarmode=tools -jar factcast-server.jar extract --layers --destination extracted

# This is the runtime container
FROM amazoncorretto:25.0.2-alpine
EXPOSE 9090
EXPOSE 9443

VOLUME /log
VOLUME /config

WORKDIR /
RUN mkdir /factcast

# Copy the extracted jar contents from the builder container into the working directory in the runtime container
# Every copy step creates a new docker layer
# This allows docker to only pull the changes it really needs
COPY --from=builder /builder/extracted/dependencies/ factcast/
COPY --from=builder /builder/extracted/spring-boot-loader/ factcast/
COPY --from=builder /builder/extracted/snapshot-dependencies/ factcast/
COPY --from=builder /builder/extracted/application/ factcast/
# Execute the CDS training run
RUN java -XX:ArchiveClassesAtExit=factcast-server.jsa -Dspring.context.exit=onRefresh -jar factcast/factcast-server.jar | true 

# Start the factcast-server jar with CDS enabled - this is not the uber jar used by the builder
# This jar only contains factcast-server code and references to the extracted jar files
# This layout is efficient to start up and CDS friendly
ENTRYPOINT ["java","-XX:TieredStopAtLevel=1","-Xms1G", "-XX:SharedArchiveFile=factcast-server.jsa","-cp",".", "-jar", "factcast/factcast-server.jar"]
