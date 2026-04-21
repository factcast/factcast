/*
 * Copyright © 2017-2026 factcast.org
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

import ch.qos.logback.classic.LoggerContext;
import org.factcast.store.internal.filter.FromScratchCatchupLogSuppressingTurboFilter;
import org.factcast.test.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ContextConfiguration(classes = {PgTestConfiguration.class})
@Sql(scripts = "/wipe.sql", config = @SqlConfig(separator = "#"))
@ExtendWith(SpringExtension.class)
@IntegrationTest
@TestPropertySource(
    properties = {
      "factcast.store.schemaRegistryUrl=classpath:example-registry",
      "factcast.store.persistentRegistry=false",
      "factcast.store.fromScratchCatchupMinLogLevel=DEBUG"
    })
class PgFactStoreLogSuppressionPropertyIntegrationTest {

  @Test
  void turboFilterIsRegisteredFromProperty() {
    LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
    boolean found =
        ctx.getTurboFilterList().stream()
            .anyMatch(f -> f instanceof FromScratchCatchupLogSuppressingTurboFilter);
    assertThat(found).isTrue();
  }
}
