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
package org.factcast.server.ui.config;

import org.factcast.server.ui.adapter.FileSystemReportStore;
import org.factcast.server.ui.adapter.FilesystemServiceInitListener;
import org.factcast.server.ui.port.ReportStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ReportStoreConfigurationProperties.class)
public class FileSystemReportStoreConfiguration {
  @Bean
  @ConditionalOnMissingBean
  ReportStore fileSystemReportStore(ReportStoreConfigurationProperties properties) {
    return new FileSystemReportStore(properties.getPath());
  }

  @Bean
  @ConditionalOnBean(value = ReportStore.class, name = "fileSystemReportStore")
  public FilesystemServiceInitListener filesystemServiceInitListener(
      ReportStoreConfigurationProperties properties) {
    return new FilesystemServiceInitListener(properties.getPath());
  }
}
