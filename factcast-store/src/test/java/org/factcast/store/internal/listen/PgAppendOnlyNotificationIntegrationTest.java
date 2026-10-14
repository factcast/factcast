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
package org.factcast.store.internal.listen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.factcast.core.Fact;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.*;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.store.internal.PgTestConfiguration;
import org.factcast.store.internal.horizon.FactStreamHorizonProvider;
import org.factcast.store.internal.notification.FactInsertionNotification;
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
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@IntegrationTest
class PgAppendOnlyNotificationIntegrationTest {

  private static final String NS = "append-only-notification";
  private static final String TYPE = "same-type";

  @Autowired FactStore store;
  @Autowired JdbcTemplate jdbc;
  @Autowired EventBus eventBus;
  @Autowired NudgeNotificationHandler handler;
  @Autowired FactStreamHorizonProvider horizonProvider;

  @Test
  void preservesEverySerialAndDispatchesSelectivelyAfterBootstrap() {
    horizonProvider.advance();
    store.publish(facts(100));

    List<Long> originalSerials = notificationSerials();
    assertThat(originalSerials).isNotEmpty().hasSizeLessThan(100).doesNotHaveDuplicates();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(DISTINCT tw) FROM notification WHERE ns = ? AND type = ?",
                Long.class,
                NS,
                TYPE))
        .isEqualTo(1);
    await()
        .atMost(Duration.ofSeconds(10))
        .until(
            () -> handler.notificationSer.get() == originalSerials.get(originalSerials.size() - 1));

    NotificationCollector collector = new NotificationCollector();
    eventBus.register(collector);
    try {
      for (int batch = 0; batch < 20; batch++) {
        store.publish(facts(20));
      }

      List<Long> allSerials = notificationSerials();
      assertThat(allSerials).hasSizeGreaterThan(originalSerials.size()).doesNotHaveDuplicates();
      assertThat(allSerials).containsAll(originalSerials);
      assertThat(allSerials.subList(0, originalSerials.size())).isEqualTo(originalSerials);
      await()
          .atMost(Duration.ofSeconds(10))
          .until(
              () ->
                  handler.notificationSer.get() == allSerials.get(allSerials.size() - 1)
                      && collector.selective.get() > 0);
      await()
          .during(Duration.ofMillis(500))
          .atMost(Duration.ofSeconds(2))
          .untilAsserted(() -> assertThat(collector.global.get()).isZero());
    } finally {
      eventBus.unregister(collector);
    }
  }

  @Test
  void cleanupOfNotificationCursorFallsBackToGlobalWakeAndFollowDeliversNextFact()
      throws Exception {
    horizonProvider.advance();
    Fact first = Fact.builder().id(UUID.randomUUID()).ns(NS).type(TYPE).buildWithoutPayload();
    store.publish(List.of(first));
    long cursor = notificationSerials().get(0);
    await().atMost(Duration.ofSeconds(10)).until(() -> handler.notificationSer.get() == cursor);

    List<UUID> received = new CopyOnWriteArrayList<>();
    AtomicReference<Throwable> subscriptionError = new AtomicReference<>();
    FactObserver observer =
        new FactObserver() {
          @Override
          public void onNext(Fact fact) {
            received.add(fact.id());
          }

          @Override
          public void onError(Throwable exception) {
            subscriptionError.compareAndSet(null, exception);
          }
        };

    try (Subscription subscription =
        store.subscribe(
            SubscriptionRequestTO.from(SubscriptionRequest.follow(FactSpec.ns(NS)).fromScratch()),
            observer)) {
      subscription.awaitCatchup(10_000);
      assertThat(received).containsExactly(first.id());

      NotificationCollector collector = new NotificationCollector();
      eventBus.register(collector);
      try {
        assertThat(jdbc.update("UPDATE notification SET tw=0 WHERE ser=?", cursor)).isEqualTo(1);
        jdbc.execute("CALL notificationCleanup()");
        assertThat(
                jdbc.queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM notification WHERE ser=?)", Boolean.class, cursor))
            .isFalse();

        Fact second = Fact.builder().id(UUID.randomUUID()).ns(NS).type(TYPE).buildWithoutPayload();
        store.publish(List.of(second));
        await()
            .atMost(Duration.ofSeconds(10))
            .until(
                () ->
                    (received.size() == 2 && collector.global.get() > 0)
                        || subscriptionError.get() != null);
        assertThat(subscriptionError.get()).isNull();
        assertThat(received).containsExactly(first.id(), second.id());
        await()
            .during(Duration.ofMillis(300))
            .atMost(Duration.ofSeconds(2))
            .untilAsserted(() -> assertThat(collector.selective.get()).isZero());
      } finally {
        eventBus.unregister(collector);
      }
    }
  }

  private List<Long> notificationSerials() {
    return jdbc.queryForList(
        "SELECT ser FROM notification WHERE ns = ? AND type = ? ORDER BY ser",
        Long.class,
        NS,
        TYPE);
  }

  private static List<Fact> facts(int count) {
    List<Fact> facts = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      facts.add(Fact.builder().ns(NS).type(TYPE).buildWithoutPayload());
    }
    return facts;
  }

  public static class NotificationCollector {
    final AtomicInteger global = new AtomicInteger();
    final AtomicInteger selective = new AtomicInteger();

    @Subscribe
    public void on(FactInsertionNotification notification) {
      if (notification.ns() == null) global.incrementAndGet();
      else if (NS.equals(notification.ns()) && TYPE.equals(notification.type()))
        selective.incrementAndGet();
    }
  }
}
