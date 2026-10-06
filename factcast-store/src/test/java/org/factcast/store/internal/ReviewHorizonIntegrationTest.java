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
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.factcast.core.Fact;
import org.factcast.core.store.FactStore;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.horizon.*;
import org.factcast.store.internal.listen.*;
import org.factcast.store.internal.lock.AdvisoryLocks;
import org.factcast.store.internal.notification.*;
import org.factcast.test.IntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.jdbc.*;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = PgTestConfiguration.class)
@Sql(scripts = "/wipe.sql", config = @SqlConfig(separator = "#"))
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@IntegrationTest
class ReviewHorizonIntegrationTest {
  @Autowired FactStore writer;
  @Autowired DataSource ds;
  @Autowired JdbcTemplate jdbc;
  @Autowired FactStreamHorizonProvider writableHorizon;
  @Autowired PgListener listener;
  @Autowired PgConnectionSupplier connectionSupplier;
  @Autowired NudgeNotificationHandler writerHandler;
  @Autowired PgMetrics metrics;
  @Autowired StoreConfigurationProperties props;

  @BeforeEach
  void stopAutomaticAdvancement() throws Exception {
    listener.destroy();
    writerHandler.destroy();
    writableHorizon.advance();
  }

  static class Wakeups {
    AtomicInteger count = new AtomicInteger();

    @Subscribe
    public void onInsertion(FactInsertionNotification n) {
      count.incrementAndGet();
    }
  }

  static class Nudges {
    AtomicInteger count = new AtomicInteger();

    @Subscribe
    public void onNudge(NudgeNotification nudge) {
      if (nudge.txId() != 0) count.incrementAndGet();
    }
  }

  Fact fact() {
    return Fact.builder().id(UUID.randomUUID()).ns("review").type("test").buildWithoutPayload();
  }

  private static void acquireSharedPublishLock(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute(
          "SELECT pg_advisory_xact_lock_shared(" + AdvisoryLocks.PUBLISH.code() + ")");
    }
  }

  NudgeNotificationHandler readerHandler(EventBus bus, FactStreamHorizonProvider provider) {
    StoreConfigurationProperties props = new StoreConfigurationProperties();
    props.setReadOnlyModeEnabled(true);
    return new NudgeNotificationHandler(bus, jdbc, props, metrics, provider);
  }

  @Test
  void readOnlySubscriberWakesWhenDelayedCheckpointCommits() throws Exception {
    EventBus bus = new EventBus();
    Wakeups wakeups = new Wakeups();
    Nudges nudges = new Nudges();
    bus.register(wakeups);
    bus.register(nudges);
    NudgeNotificationHandler reader =
        readerHandler(bus, new ReadOnlyPgFactStreamHorizonProvider(ds));
    PgListener readerListener = new PgListener(connectionSupplier, bus, props, metrics);
    readerListener.afterPropertiesSet();
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try (Connection lower = ds.getConnection()) {
      lower.setAutoCommit(false);
      acquireSharedPublishLock(lower);
      writer.publish(List.of(fact()));
      Future<?> advancing = executor.submit(writableHorizon::advance);
      await().atMost(Duration.ofSeconds(5)).until(() -> nudges.count.get() > 0);
      await()
          .atMost(Duration.ofSeconds(5))
          .until(
              () ->
                  Boolean.TRUE.equals(
                      jdbc.queryForObject(
                          "SELECT EXISTS (SELECT 1 FROM pg_locks "
                              + "WHERE locktype='advisory' AND mode='ExclusiveLock' "
                              + "AND granted=false)",
                          Boolean.class)));
      Thread.sleep(300);
      assertThat(advancing).isNotDone();
      assertThat(wakeups.count.get()).isZero();
      lower.commit();
      advancing.get(5, TimeUnit.SECONDS);
      await()
          .atMost(Duration.ofSeconds(5))
          .untilAsserted(
              () ->
                  assertThat(wakeups.count.get())
                      .as("subscriber wakes after checkpoint becomes readable")
                      .isPositive());
    } finally {
      readerListener.destroy();
      reader.destroy();
      executor.shutdownNow();
    }
  }

  @Test
  void missingNotificationBaselineStillWakesSubscribersWithoutNewNotificationMaximum()
      throws Exception {
    EventBus bus = new EventBus();
    Wakeups wakeups = new Wakeups();
    bus.register(wakeups);
    NudgeNotificationHandler handler = readerHandler(bus, writableHorizon);
    try {
      writer.publish(List.of(fact()));
      handler.nudge(new NudgeNotification(1));
      assertThat(wakeups.count.get()).isEqualTo(1);
      writer.publish(List.of(fact()));
      jdbc.execute("TRUNCATE notification");
      int beforeRetry = wakeups.count.get();
      handler.nudge(new NudgeNotification(2));
      assertThat(wakeups.count.get())
          .as("reconnect with a lost notification baseline wakes all subscribers")
          .isGreaterThan(beforeRetry);
    } finally {
      handler.destroy();
    }
  }

  @Test
  void migratedCheckpointDoesNotSkipInFlightLowerSerial() throws Exception {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Fact lowerFact = fact();
    try (Connection lower = ds.getConnection()) {
      lower.setAutoCommit(false);
      acquireSharedPublishLock(lower);
      long reserved;
      try (Statement statement = lower.createStatement();
          ResultSet rs =
              statement.executeQuery("SELECT nextval(pg_get_serial_sequence('fact', 'ser'))")) {
        rs.next();
        reserved = rs.getLong(1);
      }
      writer.publish(List.of(fact()));
      String migration =
          new ClassPathResource("db/changelog/factcast/issue4976/refresh_factstream_horizon.sql")
              .getContentAsString(StandardCharsets.UTF_8);
      jdbc.update("DELETE FROM factstream_horizon");
      Future<?> migrating =
          executor.submit(
              () -> {
                try (Connection connection = ds.getConnection()) {
                  connection.setAutoCommit(false);
                  try (Statement statement = connection.createStatement()) {
                    statement.execute(migration);
                  }
                  connection.commit();
                } catch (SQLException e) {
                  throw new CompletionException(e);
                }
              });
      await()
          .atMost(Duration.ofSeconds(5))
          .until(
              () ->
                  Boolean.TRUE.equals(
                      jdbc.queryForObject(
                          "SELECT EXISTS (SELECT 1 FROM pg_locks "
                              + "WHERE locktype='advisory' AND mode='ExclusiveLock' "
                              + "AND granted=false)",
                          Boolean.class)));
      assertThat(migrating).isNotDone();

      try (PreparedStatement statement =
          lower.prepareStatement(
              "INSERT INTO fact(ser, header, payload) VALUES (?, ?::jsonb, ?::jsonb)")) {
        statement.setLong(1, reserved);
        statement.setString(2, lowerFact.jsonHeader());
        statement.setString(3, lowerFact.jsonPayload());
        statement.executeUpdate();
      }
      lower.commit();
      migrating.get(5, TimeUnit.SECONDS);
      assertThat(new ReadOnlyPgFactStreamHorizonProvider(ds).advance().factSerial())
          .as("migration includes the committed lower serial before claiming a safe boundary")
          .isGreaterThan(reserved);
      assertThat(writer.serialOf(lowerFact.id())).hasValue(reserved);
    } finally {
      executor.shutdownNow();
    }
  }
}
