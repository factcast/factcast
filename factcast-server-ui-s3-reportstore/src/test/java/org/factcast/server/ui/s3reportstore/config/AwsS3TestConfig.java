/*
 * Copyright © 2017-2024 factcast.org
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
package org.factcast.server.ui.s3reportstore.config;

import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.factcast.server.ui.s3reportstore.S3ReportStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.localstack.LocalStackContainer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CORSRule;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

@Slf4j
@Configuration
public class AwsS3TestConfig {

  @Primary
  @Bean
  S3ReportStore s3ReportStore(
      S3TransferManager s3TransferManager, S3AsyncClient s3Client, S3Presigner s3Presigner) {
    return new S3ReportStore(s3Client, s3TransferManager, s3Presigner);
  }

  @Bean
  public S3TransferManager localS3(
      @Value("${factcast.ui.report.store.s3}") String bucketName, LocalStackContainer container) {

    S3AsyncClient s3 = getClient(container.getEndpointOverride(LocalStackContainer.Service.S3));
    S3TransferManager s3TransferManager = S3TransferManager.builder().s3Client(s3).build();
    createBucket(s3, bucketName);
    return s3TransferManager;
  }

  private S3AsyncClient getClient(URI uri) {
    return S3AsyncClient.builder().endpointOverride(uri).forcePathStyle(true).build();
  }

  private void createBucket(S3AsyncClient client, String bucketName) {
    if (doesBucketExist(client, bucketName)) {
      log.debug("Bucket found, skip creation.");
    } else {
      log.debug("Bucket not found, creating");
      client.createBucket(x -> x.bucket(bucketName)).join();

      CORSRule corsRule =
          CORSRule.builder()
              .allowedHeaders("*")
              .allowedOrigins("*")
              .allowedMethods("GET", "PUT")
              .build();
      client
          .putBucketCors(x -> x.bucket(bucketName).corsConfiguration(c -> c.corsRules(corsRule)))
          .join();
    }
  }

  private boolean doesBucketExist(S3AsyncClient s3Client, String bucketName) {
    try {
      s3Client.headBucket(x -> x.bucket(bucketName)).join();
      return true;
    } catch (NoSuchBucketException e) {
      return false;
    }
  }

  @Bean
  public LocalStackContainer localStackContainer() {
    LocalStackContainer container = new LocalStackContainer("2.2.0");
    container.withServices(LocalStackContainer.Service.S3);
    container.start();
    return container;
  }
}
