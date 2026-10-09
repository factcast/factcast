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

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.factcast.core.Fact;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.*;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.store.internal.horizon.FactStreamHorizonProvider;
import org.factcast.test.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = PgTestConfiguration.class)
@Sql(scripts = "/wipe.sql", config = @SqlConfig(separator = "#"))
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@IntegrationTest
class PgConcurrentHorizonIntegrationTest {

  private static final String NS = "concurrent-horizon";
  private static final int PUBLISHERS = 8;
  private static final int FACTS_PER_PUBLISHER = 5000;
  private static final int PREEXISTING_FACTS = 200;
  private static final int EXPECTED_FACTS = PREEXISTING_FACTS + PUBLISHERS * FACTS_PER_PUBLISHER;

  @Autowired FactStore store;
  @Autowired JdbcTemplate jdbc;
  @Autowired FactStreamHorizonProvider horizonProvider;

  @Test
  void concurrentPublishesReachFollowingSubscriberExactlyOnceInSerialOrder() throws Exception {
    // @Sql resets the row in PostgreSQL; refresh the provider's in-memory primary horizon too.
    horizonProvider.advance();
    store.publish(facts(0, PREEXISTING_FACTS, 0));

    ExecutorService publishers = Executors.newFixedThreadPool(PUBLISHERS);
    CountDownLatch ready = new CountDownLatch(PUBLISHERS);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch firstWaveDone = new CountDownLatch(PUBLISHERS);
    CountDownLatch followWaveStart = new CountDownLatch(1);
    CountDownLatch firstObserved = new CountDownLatch(1);
    CountDownLatch firstPublished = new CountDownLatch(1);
    CountDownLatch remaining = new CountDownLatch(EXPECTED_FACTS);
    AtomicReference<Throwable> subscriptionError = new AtomicReference<>();
    List<ObservedFact> received = Collections.synchronizedList(new ArrayList<>(EXPECTED_FACTS));
    AtomicBoolean firstCallback = new AtomicBoolean(true);
    Subscription subscription = null;

    try {
      List<CompletableFuture<Void>> publicationTasks = new ArrayList<>(PUBLISHERS);
      for (int publisher = 0; publisher < PUBLISHERS; publisher++) {
        int publisherIndex = publisher;
        publicationTasks.add(
            CompletableFuture.runAsync(
                () ->
                    publishInSmallBatches(
                        publisherIndex,
                        ready,
                        start,
                        firstPublished,
                        firstWaveDone,
                        followWaveStart),
                publishers));
      }
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();

      FactObserver observer =
          new FactObserver() {
            @Override
            public void onNext(Fact fact) {
              received.add(
                  new ObservedFact(Objects.requireNonNull(fact.header().serial()), fact.id()));
              remaining.countDown();
              if (firstCallback.compareAndSet(true, false)) {
                firstObserved.countDown();
                // Keep catch-up in flight while at least one new transaction commits.
                awaitLatch(firstPublished, "first concurrent publish");
              }
            }

            @Override
            public void onError(Throwable exception) {
              subscriptionError.compareAndSet(null, exception);
            }
          };

      subscription =
          store.subscribe(
              SubscriptionRequestTO.from(SubscriptionRequest.follow(FactSpec.ns(NS)).fromScratch()),
              observer);
      assertThat(firstObserved.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();

      assertThat(firstWaveDone.await(30, TimeUnit.SECONDS)).isTrue();
      subscription.awaitCatchup(30_000);
      // Everything in the second wave must be picked up by the live notification path.
      followWaveStart.countDown();
      CompletableFuture.allOf(publicationTasks.toArray(CompletableFuture[]::new))
          .get(60, TimeUnit.SECONDS);

      assertCompleteDelivery(received, remaining, subscriptionError, EXPECTED_FACTS);
    } finally {
      start.countDown();
      followWaveStart.countDown();
      firstPublished.countDown();
      if (subscription != null) subscription.close();
      publishers.shutdownNow();
      try {
        publishers.awaitTermination(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  @Test
  void subscriberAlreadyFollowingReceivesConcurrentPublishesExactlyOnceInSerialOrder()
      throws Exception {
    horizonProvider.advance();

    int expectedFacts = PUBLISHERS * FACTS_PER_PUBLISHER + 1;
    ExecutorService publishers = Executors.newFixedThreadPool(PUBLISHERS);
    CountDownLatch ready = new CountDownLatch(PUBLISHERS);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch firstWaveDone = new CountDownLatch(PUBLISHERS);
    CountDownLatch followWaveStart = new CountDownLatch(1);
    CountDownLatch firstPublished = new CountDownLatch(1);
    CountDownLatch remaining = new CountDownLatch(expectedFacts);
    AtomicReference<Throwable> subscriptionError = new AtomicReference<>();
    List<ObservedFact> received = Collections.synchronizedList(new ArrayList<>(expectedFacts));
    Subscription subscription = null;

    try {
      List<CompletableFuture<Void>> publicationTasks = new ArrayList<>(PUBLISHERS);
      for (int publisher = 0; publisher < PUBLISHERS; publisher++) {
        int publisherIndex = publisher;
        publicationTasks.add(
            CompletableFuture.runAsync(
                () ->
                    publishInSmallBatches(
                        publisherIndex,
                        ready,
                        start,
                        firstPublished,
                        firstWaveDone,
                        followWaveStart),
                publishers));
      }
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();

      FactObserver observer =
          new FactObserver() {
            @Override
            public void onNext(Fact fact) {
              received.add(
                  new ObservedFact(Objects.requireNonNull(fact.header().serial()), fact.id()));
              remaining.countDown();
            }

            @Override
            public void onError(Throwable exception) {
              subscriptionError.compareAndSet(null, exception);
            }
          };

      subscription =
          store.subscribe(
              SubscriptionRequestTO.from(SubscriptionRequest.follow(FactSpec.ns(NS)).fromScratch()),
              observer);
      subscription.awaitCatchup(30_000);
      assertThat(received).isEmpty();

      // A live delivery proves the subscriber is following before the eight publishers start.
      Fact sentinel = facts(0, 1, 0).get(0);
      store.publish(List.of(sentinel));
      await()
          .atMost(Duration.ofSeconds(10))
          .until(() -> received.size() == 1 || subscriptionError.get() != null);
      assertThat(subscriptionError.get()).isNull();
      ObservedFact sentinelDelivery =
          new ObservedFact(store.serialOf(sentinel.id()).orElseThrow(), sentinel.id());
      assertThat(received).containsExactly(sentinelDelivery);

      start.countDown();
      assertThat(firstWaveDone.await(60, TimeUnit.SECONDS)).isTrue();
      // Require the first burst to arrive without a later publish rescuing a missed nudge.
      int firstWaveFacts = 1 + PUBLISHERS * (FACTS_PER_PUBLISHER / 2);
      await()
          .atMost(Duration.ofSeconds(60))
          .until(() -> received.size() >= firstWaveFacts || subscriptionError.get() != null);
      assertThat(subscriptionError.get()).isNull();
      assertThat(received).hasSize(firstWaveFacts);

      followWaveStart.countDown();
      CompletableFuture.allOf(publicationTasks.toArray(CompletableFuture[]::new))
          .get(120, TimeUnit.SECONDS);
      assertCompleteDelivery(received, remaining, subscriptionError, expectedFacts);
    } finally {
      start.countDown();
      followWaveStart.countDown();
      if (subscription != null) subscription.close();
      publishers.shutdownNow();
      try {
        publishers.awaitTermination(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private void assertCompleteDelivery(
      List<ObservedFact> received,
      CountDownLatch remaining,
      AtomicReference<Throwable> subscriptionError,
      int expectedFacts) {
    // No extra publish is needed to wake the subscriber after the final burst.
    await()
        .atMost(Duration.ofSeconds(60))
        .until(() -> remaining.getCount() == 0 || subscriptionError.get() != null);
    assertThat(subscriptionError.get()).isNull();
    assertThat(remaining.getCount()).isZero();
    await()
        .during(Duration.ofMillis(500))
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> assertThat(received).hasSize(expectedFacts));

    List<ObservedFact> databaseOrder =
        jdbc.query(
            "SELECT ser, (header ->> 'id')::uuid AS id FROM fact "
                + "WHERE header ->> 'ns' = ? ORDER BY ser",
            (rs, rowNum) -> new ObservedFact(rs.getLong("ser"), rs.getObject("id", UUID.class)),
            NS);
    assertThat(databaseOrder).hasSize(expectedFacts);

    List<ObservedFact> delivered;
    synchronized (received) {
      delivered = List.copyOf(received);
    }
    assertThat(delivered).containsExactlyElementsOf(databaseOrder);
    for (int i = 1; i < delivered.size(); i++) {
      assertThat(delivered.get(i).serial()).isGreaterThan(delivered.get(i - 1).serial());
    }
    assertThat(delivered.stream().map(ObservedFact::id).distinct().count())
        .isEqualTo(expectedFacts);
    assertThat(subscriptionError.get()).isNull();

    FactStreamHorizon finalHorizon = horizonProvider.currentPrimary();
    ObservedFact latest = databaseOrder.get(databaseOrder.size() - 1);
    assertThat(finalHorizon.factSerial()).isEqualTo(latest.serial());
    assertThat(finalHorizon.factId()).isEqualTo(latest.id());
    assertThat(finalHorizon.notificationSerial())
        .isEqualTo(jdbc.queryForObject("SELECT MAX(ser) FROM notification", Long.class));
  }

  private void publishInSmallBatches(
      int publisher,
      CountDownLatch ready,
      CountDownLatch start,
      CountDownLatch firstPublished,
      CountDownLatch firstWaveDone,
      CountDownLatch followWaveStart) {
    ready.countDown();
    awaitLatch(start, "publisher start");
    int published = 0;
    for (int wave = 0; wave < 2; wave++) {
      int waveEnd = (wave + 1) * (FACTS_PER_PUBLISHER / 2);
      while (published < waveEnd) {
        int batchSize = Math.min(1 + ((published + publisher) % 8), waveEnd - published);
        store.publish(facts(publisher + 1, batchSize, published));
        firstPublished.countDown();
        published += batchSize;
        if (published % 16 == 0) Thread.yield();
      }
      if (wave == 0) {
        firstWaveDone.countDown();
        awaitLatch(followWaveStart, "follow-phase publish");
      }
    }
  }

  private static List<Fact> facts(int publisher, int count, int offset) {
    List<Fact> facts = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      facts.add(
          Fact.builder()
              .id(UUID.randomUUID())
              .ns(NS)
              .type("type-" + ((publisher + offset + i) % 4))
              .buildWithoutPayload());
    }
    return facts;
  }

  private static void awaitLatch(CountDownLatch latch, String description) {
    try {
      if (!latch.await(15, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out waiting for " + description);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for " + description, e);
    }
  }

  private record ObservedFact(long serial, UUID id) {}
}
