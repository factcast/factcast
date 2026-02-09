/*
 * Copyright © 2017-2023 factcast.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.factcast.example.server.ui;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.Driver;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Slf4j
@SpringBootApplication
public class ExampleUiServerWithS3Reports {
  private static final String S3_LOCALSTACK_VERSION = "3.8.1";
  private static LocalStackContainer localStackContainer;

  // must match the name used in application.properties.
  private static final String bucketName = "factcast-ui-bucket";

  @SneakyThrows
  public static void main(String[] args) {
    startPostgresContainer();
    startS3Localstack();
    log.info("Trying to create bucket: '{}' in localstack", bucketName);
    localStackContainer.execInContainer("awslocal", "s3", "mb", "s3://" + bucketName);

    log.info("Starting server");
    SpringApplication.run(ExampleUiServerWithS3Reports.class, args);
  }

  private static void startPostgresContainer() {
    log.info("Trying to start postgres testcontainer");
    PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15.2");
    postgres.start();
    String url = postgres.getJdbcUrl();
    System.setProperty("spring.datasource.driver-class-name", Driver.class.getName());
    System.setProperty("spring.datasource.url", url);
    System.setProperty("spring.datasource.username", postgres.getUsername());
    System.setProperty("spring.datasource.password", postgres.getPassword());
  }

  private static void startS3Localstack() {
    log.info("Trying to start s3 localstack container");
    localStackContainer =
        new LocalStackContainer(
                DockerImageName.parse("localstack/localstack:" + S3_LOCALSTACK_VERSION))
            .withServices("s3");

    localStackContainer.start();

        final var endpointConfiguration = localStackContainer.getEndpoint(); // one endpoint for all services in LocalStack v2+
        System.setProperty(S3Configuration.LOCAL_S_3_ENDPOINT, endpointConfiguration.toString());
        System.setProperty(S3Configuration.LOCAL_S_3_SIGNING_REGION,
     localStackContainer.getRegion());
        System.setProperty("aws.accessKeyId", localStackContainer.getAccessKey());
        System.setProperty("aws.secretKey", localStackContainer.getSecretKey());
        System.setProperty("aws.region", localStackContainer.getRegion());
  }
}
