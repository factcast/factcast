/*
 * Copyright © 2017-2020 factcast.org
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
package org.factcast.store.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.factcast.store.OffloadDataSource;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.logsuppression.DefaultLogSuppression;
import org.factcast.store.internal.logsuppression.LogSuppression;
import org.factcast.store.internal.logsuppression.NopLogSuppression;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class PgFactStoreInternalConfigurationTest {

  private static final String OFFLOAD_URL = "jdbc:postgresql://localhost:5432/factcast-offload";

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(OffloadTestConfiguration.class);

  @Test
  void testLogSuppressionBean() {
    PgFactStoreInternalConfiguration config = new PgFactStoreInternalConfiguration();
    StoreConfigurationProperties props = new StoreConfigurationProperties();

    // Disabled
    props.getLogSuppression().setEnabled(false);
    LogSuppression disabled = config.logSuppression(props);
    assertThat(disabled).isInstanceOf(NopLogSuppression.class);

    // Enabled
    props.getLogSuppression().setEnabled(true);
    LogSuppression enabled = config.logSuppression(props);
    assertThat(enabled).isInstanceOf(DefaultLogSuppression.class);
  }

  @Test
  void offloadUrlAloneDoesNotEnableOffloading() {
    contextRunner
        .withPropertyValues("factcast.store.offload.url=" + OFFLOAD_URL)
        .run(context -> assertThat(context).doesNotHaveBean(OffloadDataSource.class));
  }

  @Test
  void explicitlyDisabledOffloadingDoesNotCreateOffloadDataSource() {
    contextRunner
        .withPropertyValues(
            "factcast.store.offload.enabled=false", "factcast.store.offload.url=" + OFFLOAD_URL)
        .run(context -> assertThat(context).doesNotHaveBean(OffloadDataSource.class));
  }

  @Test
  void enabledOffloadingCreatesOffloadDataSource() {
    contextRunner
        .withPropertyValues(
            "factcast.store.offload.enabled=true", "factcast.store.offload.url=" + OFFLOAD_URL)
        .run(context -> assertThat(context).hasSingleBean(OffloadDataSource.class));
  }

  @Test
  void enabledOffloadingRequiresUrl() {
    contextRunner
        .withPropertyValues("factcast.store.offload.enabled=true")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseInstanceOf(IllegalArgumentException.class)
                  .hasMessageContaining(
                      "factcast.store.offload.url must be configured when "
                          + "factcast.store.offload.enabled=true");
            });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(StoreConfigurationProperties.class)
  @Import(PgFactStoreInternalConfiguration.OffloadConfiguration.class)
  static class OffloadTestConfiguration {}
}
