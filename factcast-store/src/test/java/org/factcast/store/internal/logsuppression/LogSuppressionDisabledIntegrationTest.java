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
package org.factcast.store.internal.logsuppression;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.LoggerContext;
import java.util.*;
import lombok.NonNull;
import nl.altindag.log.LogCaptor;
import org.factcast.core.Fact;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.*;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.*;
import org.factcast.store.internal.catchup.cursor.PgCursorCatchup;
import org.factcast.store.internal.pipeline.BufferedTransformingServerPipeline;
import org.factcast.store.registry.transformation.FactTransformerServiceImpl;
import org.factcast.test.IntegrationTest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    })
class LogSuppressionDisabledIntegrationTest {

  @Test
  void turboFilterIsRegisteredFromProperty() {
    LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
    boolean found =
        ctx.getTurboFilterList().stream().anyMatch(f -> f instanceof DefaultLogSuppression.Filter);
    assertThat(found).isFalse();
  }

  @Autowired FactStore store;
  @Autowired StoreConfigurationProperties storeProps;
  @Autowired PgFactStoreInternalConfiguration conf;

  // the example-registry has ns="ns", type="type", version 1 with a 1->2 transformation
  @NonNull
  final Collection<FactSpec> spec =
      Collections.singletonList(FactSpec.ns("ns").type("type").version(2));

  @NonNull final FactObserver obs = f -> {};

  @BeforeEach
  void setup() {
    store.publish(
        List.of(
            Fact.builder()
                .ns("ns")
                .type("type")
                .version(1)
                .build("{\"firstName\":\"Peter\",\"lastName\":\"Peterson\"}"),
            Fact.builder()
                .ns("ns")
                .type("type")
                .version(1)
                .build("{\"firstName\":\"John\",\"lastName\":\"Johnson\"}"),
            Fact.builder()
                .ns("ns")
                .type("type")
                .version(1)
                .build("{\"firstName\":\"Jane\",\"lastName\":\"Doe\"}")));
  }

  @Test
  void noSuppressionWhenDisabled() {
    try (LogCaptor pgFactStreamLogs = LogCaptor.forClass(PgFactStream.class);
        LogCaptor catchupLogs = LogCaptor.forClass(PgCursorCatchup.class);
        LogCaptor transformerLogs = LogCaptor.forClass(FactTransformerServiceImpl.class);
        LogCaptor pipelineLogs = LogCaptor.forClass(BufferedTransformingServerPipeline.class)) {

      // 1) baseline: subscribe from scratch WITHOUT filter — TRACE logs should be present
      {
        SubscriptionRequest scratch = SubscriptionRequest.catchup(spec).fromScratch();
        store.subscribe(SubscriptionRequestTO.from(scratch), obs).awaitComplete();

        List<String> baselineStreamTrace = List.copyOf(pgFactStreamLogs.getTraceLogs());
        List<String> baselineCatchupTrace = List.copyOf(catchupLogs.getTraceLogs());
        List<String> baselineTransformerTrace = List.copyOf(transformerLogs.getTraceLogs());
        List<String> baselinePipelineTrace = List.copyOf(pipelineLogs.getTraceLogs());

        assertThat(baselineStreamTrace).isNotEmpty();
        assertThat(baselineCatchupTrace).isNotEmpty();
        assertThat(baselineTransformerTrace).isNotEmpty();
        assertThat(baselinePipelineTrace).isNotEmpty();
      }
    }
  }
}
