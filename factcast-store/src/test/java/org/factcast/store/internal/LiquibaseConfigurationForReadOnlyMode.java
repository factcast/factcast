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
package org.factcast.store.internal;

import javax.sql.DataSource;
import liquibase.Liquibase;
import liquibase.integration.spring.*;
import lombok.SneakyThrows;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.liquibase.autoconfigure.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(LiquibaseProperties.class)
public class LiquibaseConfigurationForReadOnlyMode {
  @Primary
  @Bean
  @SneakyThrows
  public SpringLiquibase liquibase(
      LiquibaseProperties props,
      ObjectProvider<DataSource> dataSource,
      ObjectProvider<Customizer<Liquibase>> customizers) {
    // for some reason the factory method for the "liquibase" bean is package private while others
    // are still public accessible
    final var config = new LiquibaseAutoConfiguration.LiquibaseConfiguration();
    final var creator =
        config
            .getClass()
            .getDeclaredMethod(
                "liquibase",
                ObjectProvider.class,
                ObjectProvider.class,
                LiquibaseProperties.class,
                ObjectProvider.class,
                LiquibaseConnectionDetails.class);
    creator.setAccessible(true);

    return (SpringLiquibase)
        creator.invoke(config, dataSource, dataSource, props, customizers, null);
  }
}
