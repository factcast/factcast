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
import static org.awaitility.Awaitility.await;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.factcast.core.Fact;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.*;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.store.internal.horizon.*;
import org.factcast.store.internal.notification.NudgeNotification;
import org.factcast.test.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = PgTestConfiguration.class)
@Sql(scripts = "/wipe.sql", config = @SqlConfig(separator = "#"))
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@IntegrationTest
class PgReadOnlyHorizonIntegrationTest {

  private static final String NS = "read-only-horizon";

  @Autowired FactStore writer;
  @Autowired EventBus writerBus;
  @Autowired JdbcTemplate jdbc;
  @Autowired FactStreamHorizonProvider writableHorizon;

  @Test
  void readOnlyInstanceCatchesUpExistingFactsAndFollowsNewPublications() throws Exception {
    writableHorizon.advance();
    List<Fact> existing = facts(3);
    writer.publish(existing);
    long lastExistingSerial = writer.serialOf(existing.get(existing.size() - 1).id()).orElseThrow();
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(
                        jdbc.queryForObject(
                            "SELECT fact_ser FROM factstream_horizon WHERE id=1", Long.class))
                    .isEqualTo(lastExistingSerial));

    try (AnnotationConfigApplicationContext readOnlyContext =
        new AnnotationConfigApplicationContext()) {
      readOnlyContext
          .getEnvironment()
          .getPropertySources()
          .addFirst(
              new MapPropertySource(
                  "read-only-test", Map.of("factcast.store.read-only-mode-enabled", "true")));
      ConfigurationPropertySources.attach(readOnlyContext.getEnvironment());
      readOnlyContext.register(
          PgTestConfiguration.class, LiquibaseConfigurationForReadOnlyMode.class);
      readOnlyContext.refresh();

      assertThat(readOnlyContext.getBean(FactStreamHorizonProvider.class))
          .isInstanceOf(ReadOnlyPgFactStreamHorizonProvider.class);
      FactStore reader = readOnlyContext.getBean(FactStore.class);
      NudgeRelay relay = new NudgeRelay(readOnlyContext.getBean(EventBus.class));
      writerBus.register(relay);
      List<ObservedFact> received = Collections.synchronizedList(new ArrayList<>());
      AtomicReference<Throwable> error = new AtomicReference<>();
      FactObserver observer =
          new FactObserver() {
            @Override
            public void onNext(Fact fact) {
              received.add(new ObservedFact(fact.header().serial(), fact.id()));
            }

            @Override
            public void onError(Throwable exception) {
              error.compareAndSet(null, exception);
            }
          };

      try {
        try (Subscription subscription =
            reader.subscribe(
                SubscriptionRequestTO.from(
                    SubscriptionRequest.follow(FactSpec.ns(NS)).fromScratch()),
                observer)) {
          subscription.awaitCatchup(10_000);
          assertThat(error.get()).isNull();
          assertDelivered(received, existing);

          List<Fact> following = facts(2);
          writer.publish(following);
          await()
              .atMost(Duration.ofSeconds(10))
              .until(() -> received.size() == 5 || error.get() != null);
          assertThat(error.get()).isNull();
          List<Fact> all = new ArrayList<>(existing);
          all.addAll(following);
          assertDelivered(received, all);
        }
      } finally {
        writerBus.unregister(relay);
      }
    }
  }

  private void assertDelivered(List<ObservedFact> received, List<Fact> expectedFacts) {
    List<ObservedFact> expected =
        expectedFacts.stream()
            .map(fact -> new ObservedFact(writer.serialOf(fact.id()).orElseThrow(), fact.id()))
            .toList();
    synchronized (received) {
      assertThat(received).containsExactlyElementsOf(expected);
    }
  }

  private static List<Fact> facts(int count) {
    List<Fact> facts = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      facts.add(Fact.builder().id(UUID.randomUUID()).ns(NS).type("type").buildWithoutPayload());
    }
    return facts;
  }

  private record ObservedFact(long serial, UUID id) {}

  private record NudgeRelay(EventBus readOnlyBus) {
    @Subscribe
    public void forward(NudgeNotification notification) {
      readOnlyBus.post(notification);
    }
  }
}
