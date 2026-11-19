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
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.factcast.core.Fact;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.*;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.PgMetrics;
import org.factcast.store.internal.PgTestConfiguration;
import org.factcast.store.internal.notification.FactInsertionNotification;
import org.factcast.test.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

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
  @Autowired StoreConfigurationProperties props;
  @Autowired PgMetrics metrics;
  @Autowired PlatformTransactionManager transactionManager;

  @Test
  void publicationCommittedAfterSuppressedNudgePollWindowStillReachesFollower() throws Exception {
    Fact first = facts(1).get(0);
    Fact second = facts(1).get(0);
    List<UUID> received = new CopyOnWriteArrayList<>();
    CountDownLatch secondReceived = new CountDownLatch(1);

    try (Subscription subscription =
        store.subscribe(
            SubscriptionRequestTO.from(SubscriptionRequest.follow(FactSpec.ns(NS)).fromScratch()),
            fact -> {
              received.add(fact.id());
              if (second.id().equals(fact.id())) secondReceived.countDown();
            })) {
      subscription.awaitCatchup(10_000);
      long nudgeVersion = handler.timerVersion.get();
      var outer = new TransactionTemplate(transactionManager);
      var inner = new TransactionTemplate(transactionManager);
      inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

      // Both transactions must have the same real PostgreSQL now() bucket. Start the outer
      // transaction first, but publish/commit the inner one first. Retry boundary crossings
      // before either transaction publishes; do not change timestamps or notification triggers.
      boolean publishedInSameWindow = false;
      for (int attempt = 0; attempt < 20 && !publishedInSameWindow; attempt++) {
        publishedInSameWindow =
            Boolean.TRUE.equals(
                outer.execute(
                    status -> {
                      long window = notificationWindow();
                      boolean firstPublished =
                          Boolean.TRUE.equals(
                              inner.execute(
                                  innerStatus -> {
                                    if (notificationWindow() != window) return false;
                                    store.publish(List.of(first));
                                    return true;
                                  }));
                      if (!firstPublished) return false;

                      store.publish(List.of(second));
                      assertThat(
                              jdbc.queryForList(
                                  "SELECT tw FROM notification WHERE ns=? ORDER BY ser",
                                  Long.class,
                                  NS))
                          .containsExactly(window, window);
                      await()
                          .atMost(Duration.ofSeconds(5))
                          .until(() -> handler.timerVersion.get() > nudgeVersion);
                      await()
                          .atMost(Duration.ofSeconds(5))
                          .untilAsserted(() -> assertThat(received).containsExactly(first.id()));

                      // A marker on the handler's own timer runs after the four polls due at
                      // 25/50/75/100 ms. Keep the second publication uncommitted until then.
                      AtomicBoolean pollsFinished = new AtomicBoolean();
                      handler.timer.schedule(
                          new TimerTask() {
                            @Override
                            public void run() {
                              pollsFinished.set(true);
                            }
                          },
                          200);
                      await().atMost(Duration.ofSeconds(5)).untilTrue(pollsFinished);
                      return true;
                    }));
      }
      assertThat(publishedInSameWindow)
          .as("two transactions started in one 100 ms bucket")
          .isTrue();
      assertThat(store.fetchById(second.id())).as("the second publication committed").isPresent();

      secondReceived.await(3, TimeUnit.SECONDS);
      List<UUID> deliveredWithoutAnotherNudge = List.copyOf(received);
      long cursorBeforeRecovery = handler.notificationSer.get();
      long committedNotificationSerial = notificationSerials().get(1);
      List<Boolean> committedNudges =
          jdbc.queryForList(
              "SELECT nudge FROM notification WHERE ns=? ORDER BY ser", Boolean.class, NS);

      // Positive control: no further publication is needed. An explicit refresh alone must
      // deliver the committed second fact, ruling out a subscription/filter/insert failure.
      handler.fetchPairsAndDispatch();
      await()
          .atMost(Duration.ofSeconds(5))
          .untilAsserted(() -> assertThat(received).containsExactly(first.id(), second.id()));

      assertThat(deliveredWithoutAnotherNudge)
          .as(
              "delivery without an extra refresh (notification cursor=%s, committed maximum=%s,"
                  + " nudge flags=%s)",
              cursorBeforeRecovery, committedNotificationSerial, committedNudges)
          .containsExactly(first.id(), second.id());
    }
  }

  private long notificationWindow() {
    return jdbc.queryForObject("SELECT (extract(epoch FROM now()) * 10)::bigint", Long.class);
  }

  @Test
  void sequenceResetRestoresSelectiveDispatch() throws Exception {
    EventBus bus = new EventBus();
    NotificationCollector collector = new NotificationCollector();
    bus.register(collector);
    // This test drives every refresh explicitly; background polls would change the event counts.
    StoreConfigurationProperties isolatedProps = new StoreConfigurationProperties();
    isolatedProps.setFactNotificationSafetyPollLatencyInMillis(Long.MAX_VALUE);
    NudgeNotificationHandler isolatedHandler =
        new NudgeNotificationHandler(bus, jdbc, isolatedProps, metrics, false);
    try {
      for (int i = 0; i < 3; i++) {
        store.publish(facts(1));
      }
      long oldSerial = notificationSerials().get(2);
      assertThat(oldSerial).isGreaterThan(1);
      isolatedHandler.fetchPairsAndDispatch();
      assertThat(isolatedHandler.notificationSer).hasValue(oldSerial);
      assertThat(collector.global).hasValue(1);

      jdbc.execute("TRUNCATE notification RESTART IDENTITY");
      store.publish(facts(1));
      isolatedHandler.fetchPairsAndDispatch();
      assertThat(isolatedHandler.notificationSer).hasValue(1);
      assertThat(collector.global).hasValue(2);
      isolatedHandler.fetchPairsAndDispatch();
      assertThat(collector.global).hasValue(2);

      store.publish(facts(1));
      isolatedHandler.fetchPairsAndDispatch();
      assertThat(isolatedHandler.notificationSer).hasValue(2);
      assertThat(collector.global).hasValue(2);
      assertThat(collector.selective).hasValue(1);
    } finally {
      isolatedHandler.destroy();
    }
  }

  @Test
  void preservesEverySerialAndDispatchesSelectivelyAfterBootstrap() {
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
