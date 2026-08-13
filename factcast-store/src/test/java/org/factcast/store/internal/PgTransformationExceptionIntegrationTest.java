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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import lombok.NonNull;
import nl.altindag.log.LogCaptor;
import org.factcast.core.Fact;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.*;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.store.internal.catchup.CatchupDataSource;
import org.factcast.store.internal.pipeline.ServerPipeline;
import org.factcast.store.internal.pipeline.ServerPipelineFactory;
import org.factcast.test.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ContextConfiguration(
    classes = {PgTestConfiguration.class, PgTransformationExceptionIntegrationTest.Config.class})
@Sql(scripts = "/wipe.sql", config = @SqlConfig(separator = "#"))
@ExtendWith(SpringExtension.class)
@IntegrationTest
@TestPropertySource(
    properties = {
      "factcast.store.schemaRegistryUrl=classpath:example-registry",
      "factcast.store.persistentRegistry=false",
      "factcast.store.pageSize=1",
      "factcast.store.transformationCachePageSize=10",
    })
class PgTransformationExceptionIntegrationTest {

  @Configuration
  public static class Config {
    @Bean
    public List<ServerPipeline> capturedPipelines() {
      return Collections.synchronizedList(new ArrayList<>());
    }

    @Bean
    @Primary
    public ServerPipelineFactory serverPipelineFactory(
        ServerPipelineFactory delegate, List<ServerPipeline> capturedPipelines) {
      ServerPipelineFactory spy = Mockito.spy(delegate);
      doAnswer(
              invocation -> {
                ServerPipeline pipeline = (ServerPipeline) invocation.callRealMethod();
                ServerPipeline spyPipeline = Mockito.spy(pipeline);
                capturedPipelines.add(spyPipeline);
                return spyPipeline;
              })
          .when(spy)
          .create(any(), any(), anyInt());
      return spy;
    }
  }

  @Autowired FactStore store;

  @Autowired List<ServerPipeline> capturedPipelines;

  @Test
  void testTransformationExceptionCancelsServerPipeline() throws Exception {
    // Publish a fact of version 1
    for (int i = 0; i < 15; i++) {
      store.publish(
          List.of(
              Fact.builder()
                  .ns("ns")
                  .type("type")
                  .version(1)
                  .build("{\"firstName\":\"Peter\",\"lastName\":\"Peterson\"}")));
    }

    //    // Configure factTransformerService to throw TransformationException
    //    doThrow(new TransformationException("Simulated transformation error"))
    //        .when(factTransformerService)
    //        .transform(anyList());

    // Subscribe requesting version 2 (forcing transformation)
    Collection<FactSpec> spec =
        Collections.singletonList(FactSpec.ns("ns").type("type").version(42));
    SubscriptionRequest request = SubscriptionRequest.catchup(spec).fromScratch();

    AtomicReference<Throwable> receivedError = new AtomicReference<>();

    FactObserver observer =
        new FactObserver() {
          @Override
          public void onNext(@NonNull Fact element) {}

          @Override
          public void onError(@NonNull Throwable throwable) {
            receivedError.set(throwable);
          }

          @Override
          public void onComplete() {}
        };

    try (LogCaptor logCaptor = LogCaptor.forClass(CatchupDataSource.class)) {
      logCaptor.setLogLevelToDebug();

      var subscription = store.subscribe(SubscriptionRequestTO.from(request), observer);

      try {
        subscription.awaitComplete(5000);
      } catch (Exception expected) {
        // expected error or closed exception
      }

      Thread.sleep(500);

      // Verify that TransformationException was received by subscriber
      assertThat(receivedError.get()).isInstanceOf(TransformationException.class);
      assertThat(receivedError.get().getMessage())
          .startsWith("Cannot reach any version in [42] from version 1");

      // Verify that server pipeline close() was called (cancelled)
      assertThat(capturedPipelines).isNotEmpty();
      ServerPipeline pipeline = capturedPipelines.get(0);
      verify(pipeline, atLeastOnce()).close();
      // Verify that "Cancellation requested" is logged
      assertThat(logCaptor.getDebugLogs()).contains("Cancellation requested");
    }
  }
}
