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
package org.factcast.example.server.ui;

import java.net.URI;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@Slf4j
public class S3Configuration {
  public static final String LOCAL_S_3_ENDPOINT = "local.s3.endpoint";
  public static final String LOCAL_S_3_SIGNING_REGION = "local.s3.signing-region";

  @Bean
  @Primary
  public S3AsyncClient amazonS3(
      @Value("${" + LOCAL_S_3_ENDPOINT + "}") String endpoint,
      @Value("${" + LOCAL_S_3_SIGNING_REGION + "}") String signingRegion) {

    return S3AsyncClient.builder()
        .endpointOverride(URI.create(endpoint))
        .region(Region.of(signingRegion))
        .build();
  }

  @Bean
  @Primary
  public S3Presigner s3Presigner(
      @Value("${" + LOCAL_S_3_ENDPOINT + "}") String endpoint,
      @Value("${" + LOCAL_S_3_SIGNING_REGION + "}") String signingRegion) {

    return S3Presigner.builder()
        .endpointOverride(URI.create(endpoint))
        .region(Region.of(signingRegion))
        .build();
  }
}