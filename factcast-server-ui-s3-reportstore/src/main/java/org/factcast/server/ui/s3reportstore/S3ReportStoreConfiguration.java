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
package org.factcast.server.ui.s3reportstore;

import org.factcast.server.ui.port.ReportStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

@Configuration
public class S3ReportStoreConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public S3AsyncClient s3Client() {
    return S3AsyncClient.create();
  }

  @Bean
  public S3Presigner s3Presigner() {
    return S3Presigner.create();
  }

  @Bean
  public S3TransferManager s3TransferManager() {
    return S3TransferManager.create();
  }

  @Bean
  @Primary
  ReportStore s3ReportStore(
      S3AsyncClient s3Client, S3TransferManager s3TransferManager, S3Presigner s3Presigner) {
    return new S3ReportStore(s3Client, s3TransferManager, s3Presigner);
  }
}
