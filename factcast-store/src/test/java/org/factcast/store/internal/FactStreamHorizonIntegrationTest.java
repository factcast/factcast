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

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.factcast.core.Fact;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.*;
import org.factcast.store.internal.horizon.FactStreamHorizon;
import org.factcast.store.internal.horizon.FactStreamHorizonProvider;
import org.factcast.store.internal.lock.AdvisoryLocks;
import org.factcast.test.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = PgTestConfiguration.class)
@Sql(scripts = "/wipe.sql", config = @SqlConfig(separator = "#"))
@IntegrationTest
final class FactStreamHorizonIntegrationTest {

  private static final String NS = "safe-horizon";

  @Autowired FactStore store;
  @Autowired DataSource dataSource;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired FactStreamHorizonProvider horizonProvider;

  @BeforeEach
  void refreshHorizonAfterDatabaseWipe() {
    horizonProvider.advance();
  }

  @Test
  void migrationSeedsHorizonFromExistingFactsAndNotifications() throws Exception {
    Fact first = Fact.builder().id(UUID.randomUUID()).ns(NS).type("first").buildWithoutPayload();
    Fact latest = Fact.builder().id(UUID.randomUUID()).ns(NS).type("latest").buildWithoutPayload();
    store.publish(List.of(first, latest));

    long expectedFactSerial = store.serialOf(latest.id()).orElseThrow();
    Long expectedNotificationSerial =
        jdbcTemplate.queryForObject("SELECT MAX(ser) FROM notification", Long.class);
    ClassPathResource migrationScriptResource =
        new ClassPathResource(
            "db/changelog/factcast/safe_hwm/create_factstream_horizon.sql",
            getClass().getClassLoader());
    String migrationScript = migrationScriptResource.getContentAsString(StandardCharsets.UTF_8);

    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      connection.setAutoCommit(false);
      statement.executeUpdate("DELETE FROM factstream_horizon WHERE id=1");
      statement.execute(migrationScript);
      connection.commit();
    }

    FactStreamHorizon horizon = horizonProvider.read(dataSource);
    assertThat(horizon.highWaterMark().targetId()).isEqualTo(latest.id());
    assertThat(horizon.highWaterMark().targetSer()).isEqualTo(expectedFactSerial);
    assertThat(horizon.notificationSerial()).isEqualTo(expectedNotificationSerial);
  }

  @Test
  void horizonWaitsForLowerSerialBeforeFollowQueryCanAdvancePastIt() throws Exception {
    List<UUID> received = new CopyOnWriteArrayList<>();
    SubscriptionRequest request =
        SubscriptionRequest.follow(FactSpec.ns(NS).meta("projection", "match")).fromScratch();

    try (Subscription subscription =
        store.subscribe(SubscriptionRequestTO.from(request), fact -> received.add(fact.id()))) {
      subscription.awaitCatchup(5_000);

      Fact lowerMatching =
          Fact.builder()
              .id(UUID.randomUUID())
              .ns(NS)
              .type("matching-type")
              .meta("projection", "match")
              .buildWithoutPayload();
      Fact higherCoarseMatch =
          Fact.builder().id(UUID.randomUUID()).ns(NS).type("different-type").buildWithoutPayload();

      try (Connection lowerTransaction = dataSource.getConnection()) {
        lowerTransaction.setAutoCommit(false);
        acquireSharedPublishLock(lowerTransaction);
        long reservedLowerSerial = reserveFactSerial(lowerTransaction);

        // A shared publisher lock must not serialize another publisher.
        CompletableFuture<Void> higherPublish =
            CompletableFuture.runAsync(() -> store.publish(List.of(higherCoarseMatch)));
        higherPublish.get(5, TimeUnit.SECONDS);

        CompletableFuture<FactStreamHorizon> advancingHorizon =
            CompletableFuture.supplyAsync(horizonProvider::advance);

        await()
            .atMost(Duration.ofSeconds(5))
            .until(
                () ->
                    Boolean.TRUE.equals(
                        jdbcTemplate.queryForObject(
                            "SELECT EXISTS (SELECT 1 FROM pg_locks "
                                + "WHERE locktype='advisory' AND mode='ExclusiveLock' "
                                + "AND granted=false)",
                            Boolean.class)));
        assertThat(advancingHorizon).isNotDone();
        assertThat(received).isEmpty();

        insert(lowerTransaction, reservedLowerSerial, lowerMatching);
        lowerTransaction.commit();

        FactStreamHorizon horizon = advancingHorizon.get(5, TimeUnit.SECONDS);
        long lowerSerial = store.serialOf(lowerMatching.id()).orElseThrow();
        long higherSerial = store.serialOf(higherCoarseMatch.id()).orElseThrow();
        assertThat(lowerSerial).isEqualTo(reservedLowerSerial);
        assertThat(lowerSerial).isLessThan(higherSerial);
        assertThat(horizon.highWaterMark().targetSer()).isGreaterThanOrEqualTo(higherSerial);
      }

      await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(received).hasSize(1));

      Fact laterMatching =
          Fact.builder()
              .id(UUID.randomUUID())
              .ns(NS)
              .type("matching-type")
              .meta("projection", "match")
              .buildWithoutPayload();
      store.publish(List.of(laterMatching));

      await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(received).hasSize(2));
      assertThat(received).containsExactly(lowerMatching.id(), laterMatching.id());
    }
  }

  private static void acquireSharedPublishLock(Connection connection) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT pg_advisory_xact_lock_shared(" + AdvisoryLocks.PUBLISH.code() + ")")) {
      statement.execute();
    }
  }

  private static long reserveFactSerial(Connection connection) throws SQLException {
    try (PreparedStatement statement =
            connection.prepareStatement("SELECT nextval(pg_get_serial_sequence('fact', 'ser'))");
        ResultSet resultSet = statement.executeQuery()) {
      resultSet.next();
      return resultSet.getLong(1);
    }
  }

  private static void insert(Connection connection, long serial, Fact fact) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO fact(ser, header, payload) VALUES (?, ?::jsonb, ?::jsonb)")) {
      statement.setLong(1, serial);
      statement.setString(2, fact.jsonHeader());
      statement.setString(3, fact.jsonPayload());
      statement.executeUpdate();
    }
  }
}
