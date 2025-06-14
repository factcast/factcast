# Perform the extraction in a separate builder container
FROM bellsoft/liberica-openjre-debian:24-cds AS builder
WORKDIR /builder
# This points to the built jar file in the target folder
# Adjust this to 'build/libs/*.jar' if you're using Gradle
ARG JAR_FILE=target/*.jar
# Copy the jar file to the working directory and rename it to application.jar
COPY ${JAR_FILE} application.jar
# Extract the jar file using an efficient layout
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# This is the runtime container
FROM openjdk:21-slim
EXPOSE 9090
EXPOSE 9443
RUN mkdir /application
RUN mkdir config
RUN mkdir log

VOLUME log
VOLUME config

WORKDIR /

# Copy the extracted jar contents from the builder container into the working directory in the runtime container
# Every copy step creates a new docker layer
# This allows docker to only pull the changes it really needs
COPY --from=builder /builder/extracted/dependencies/ application/
COPY --from=builder /builder/extracted/spring-boot-loader/ application/
COPY --from=builder /builder/extracted/snapshot-dependencies/ application/
COPY --from=builder /builder/extracted/application/ application/
# Execute the CDS training run
RUN java -XX:ArchiveClassesAtExit=application.jsa -Dspring.context.exit=onRefresh -jar application/application.jar | true 
# Start the application jar with CDS enabled - this is not the uber jar used by the builder
# This jar only contains application code and references to the extracted jar files
# This layout is efficient to start up and CDS friendly
ENTRYPOINT ["java","-XX:TieredStopAtLevel=1","-Xms1G", "-XX:SharedArchiveFile=application.jsa", "-jar", "application/application.jar"]
