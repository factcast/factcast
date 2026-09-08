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
package org.factcast.itests.factus.client;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.factcast.core.FactStreamPosition;
import org.factcast.factus.Factus;
import org.factcast.factus.Handler;
import org.factcast.factus.projection.WriterToken;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.factcast.factus.spring.tx.SpringTransactional;
import org.factcast.factus.spring.tx.jdbc.AbstractSpringJdbcManagedProjection;
import org.factcast.factus.spring.tx.jdbc.AbstractSpringJdbcSubscribedProjection;
import org.factcast.factus.spring.tx.jdbc.JdbcWriterTokenManager;
import org.factcast.factus.spring.tx.jdbc.ProjectionNames;
import org.factcast.itests.TestFactusApplication;
import org.factcast.itests.factus.event.UserCreated;
import org.factcast.itests.factus.event.UserDeleted;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ContextConfiguration(classes = TestFactusApplication.class)
@EnableAutoConfiguration
@Slf4j
public class SpringJdbcProjectionLockITest extends AbstractFactCastIntegrationTest {

  private static final String LOCK_TABLE = JdbcWriterTokenManager.DEFAULT_LOCK_TABLE_NAME;

  /** short enough for a takeover test to observe an expiring lease within seconds */
  private static final Duration LEASE = Duration.ofSeconds(2);

  /**
   * Keeps a holder's keepalive quiet long enough for another instance to take over an artificially
   * expired lease before the holder gets a chance to notice.
   */
  private static final Duration SLOW_KEEPALIVE_LEASE = Duration.ofSeconds(9);

  private static final Duration NO_WAIT = Duration.ZERO;
  private static final Duration MAX_WAIT = Duration.ofMillis(1500);
  private static final Duration BEYOND_LEASE = LEASE.multipliedBy(3);

  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired PlatformTransactionManager platformTransactionManager;
  @Autowired Factus factus;

  private final List<WriterToken> handedOutTokens = new ArrayList<>();

  private String projectionKey;
  private String lockName;

  @BeforeEach
  void setUp() {
    createTables();
    projectionKey = "jdbc_lock_itest_" + randomUUID();
    lockName = ProjectionNames.lockName(projectionKey);
  }

  @AfterEach
  void stopKeepalives() {
    handedOutTokens.forEach(SpringJdbcProjectionLockITest::closeQuietly);
    handedOutTokens.clear();
  }

  @Nested
  class Contention {

    @Test
    void onlyOneOfTwoInstancesGetsAToken() {
      assertThat(acquire(NO_WAIT)).isNotNull();

      long startedAt = System.nanoTime();
      WriterToken second = acquire(MAX_WAIT);

      assertThat(second).isNull();
      assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isGreaterThanOrEqualTo(MAX_WAIT);
    }

    @Test
    void releasingTheTokenLetsTheNextInstanceIn() {
      WriterToken first = acquire(NO_WAIT);
      assertThat(first).isNotNull();

      closeQuietly(first);

      assertThat(acquire(BEYOND_LEASE)).isNotNull();
    }

    @Test
    void takesOverAfterTheHolderDiedWithoutUnlocking() {
      simulateHolderThatDiedWithoutUnlocking();

      assertThat(acquire(NO_WAIT)).isNull();

      assertThat(acquire(BEYOND_LEASE)).isNotNull();
    }
  }

  @Nested
  class Liveness {

    @Test
    void keepaliveRenewsTheLeaseBeyondItsDuration() {
      WriterToken token = acquire(NO_WAIT);
      assertThat(token).isNotNull();
      Timestamp initialLockUntil = lockUntil();

      await().atMost(LEASE).until(() -> lockUntil().after(initialLockUntil));

      assertThat(token.isValid()).isTrue();
      assertThat(acquire(NO_WAIT)).isNull();
    }

    @Test
    void isValidTurnsFalseOnceAnotherInstanceTookOver() {
      WriterToken outgoing = acquire(SLOW_KEEPALIVE_LEASE, NO_WAIT);
      assertThat(outgoing).isNotNull();
      assertThat(outgoing.isValid()).isTrue();

      expireLease();
      WriterToken incoming = acquire(SLOW_KEEPALIVE_LEASE, NO_WAIT);
      assertThat(incoming).isNotNull();

      // both instances share this host, so a lease owner identified by hostname alone would let the
      // outgoing instance keep renewing the lease the incoming one now owns
      await().atMost(SLOW_KEEPALIVE_LEASE).until(() -> !outgoing.isValid());
      assertThat(incoming.isValid()).isTrue();
    }

    @Test
    void leaseRenewalSurvivesARollbackOfTheProjectionTransaction() {
      TransactionTemplate transaction = new TransactionTemplate(platformTransactionManager);

      WriterToken token =
          transaction.execute(
              status -> {
                WriterToken acquired = acquire(NO_WAIT);
                status.setRollbackOnly();
                return acquired;
              });

      assertThat(token).isNotNull();
      Timestamp lockUntilAfterRollback = lockUntil();

      await().atMost(LEASE).until(() -> lockUntil().after(lockUntilAfterRollback));

      assertThat(token.isValid()).isTrue();
      assertThat(acquire(NO_WAIT)).isNull();
    }
  }

  @Nested
  class SameHost {

    @Test
    void eachInstanceGetsItsOwnLeaseOwner() {
      WriterToken first = acquire(NO_WAIT);
      assertThat(first).isNotNull();
      String firstOwner = lockedBy();
      closeQuietly(first);

      assertThat(acquire(BEYOND_LEASE)).isNotNull();
      String secondOwner = lockedBy();

      assertThat(firstOwner).isNotEqualTo(secondOwner);
      assertThat(hostPartOf(firstOwner)).isEqualTo(hostPartOf(secondOwner));
    }

    @Test
    void closingATakenOverTokenDoesNotReleaseTheNewHolder() {
      WriterToken outgoing = acquire(SLOW_KEEPALIVE_LEASE, NO_WAIT);
      assertThat(outgoing).isNotNull();

      expireLease();
      WriterToken incoming = acquire(SLOW_KEEPALIVE_LEASE, NO_WAIT);
      assertThat(incoming).isNotNull();

      // still before the outgoing keepalive noticed, so this really does issue shedlock's unlock
      closeQuietly(outgoing);

      assertThat(acquire(NO_WAIT)).isNull();
      assertThat(incoming.isValid()).isTrue();
    }

    private String hostPartOf(String lockedBy) {
      return lockedBy.substring(0, lockedBy.indexOf('/'));
    }
  }

  @Nested
  class EndToEnd {

    @Test
    void managedProjectionAppliesFactsAndResumesFromItsPersistedPosition() {
      UUID klaus = randomUUID();
      factus.publish(
          List.of(
              new UserCreated(randomUUID(), "Peter"),
              new UserCreated(randomUUID(), "Paul"),
              new UserCreated(klaus, "Klaus"),
              new UserDeleted(klaus)));

      JdbcLockedUserNames uut = new JdbcLockedUserNames(platformTransactionManager, jdbcTemplate);
      factus.update(uut);

      assertThat(uut.getUserNames()).containsExactlyInAnyOrder("Peter", "Paul");

      FactStreamPosition afterFirstUpdate = uut.factStreamPosition();
      assertThat(afterFirstUpdate).isNotNull();
      assertThat(afterFirstUpdate.factId()).isNotNull();
      assertThat(afterFirstUpdate.serial()).isPositive();
      assertThat(persistedPosition("managed_projection", uut.getScopedName().asString()))
          .isEqualTo(afterFirstUpdate);

      factus.publish(new UserCreated(randomUUID(), "Zora"));

      JdbcLockedUserNames resumed =
          new JdbcLockedUserNames(platformTransactionManager, jdbcTemplate);
      assertThat(resumed.factStreamPosition()).isEqualTo(afterFirstUpdate);

      factus.update(resumed);

      assertThat(resumed.getUserNames()).containsExactlyInAnyOrder("Peter", "Paul", "Zora");
      assertThat(resumed.factStreamPosition().serial()).isGreaterThan(afterFirstUpdate.serial());
    }

    @Test
    void subscribedProjectionHoldsTheWriteTokenWhileSubscribed() throws Exception {
      factus.publish(
          List.of(new UserCreated(randomUUID(), "Peter"), new UserCreated(randomUUID(), "Paul")));

      JdbcLockedSubscribedUserNames uut =
          new JdbcLockedSubscribedUserNames(platformTransactionManager, jdbcTemplate);
      JdbcLockedSubscribedUserNames competitor =
          new JdbcLockedSubscribedUserNames(platformTransactionManager, jdbcTemplate);

      FactStreamPosition whileSubscribed;
      try (var ignored = factus.subscribeAndBlock(uut).awaitCatchup()) {
        assertThat(uut.hasLock()).isTrue();
        assertThat(competitor.acquireWriteToken(MAX_WAIT)).isNull();
        assertThat(uut.getUserNames()).containsExactlyInAnyOrder("Peter", "Paul");

        await().atMost(BEYOND_LEASE).until(() -> uut.factStreamPosition() != null);
        whileSubscribed = uut.factStreamPosition();
      }

      assertThat(whileSubscribed.serial()).isPositive();
      assertThat(persistedPosition("subscribed_projection", uut.getScopedName().asString()))
          .isEqualTo(whileSubscribed);

      await().atMost(BEYOND_LEASE).until(() -> !uut.hasLock());

      WriterToken afterRelease = competitor.acquireWriteToken(BEYOND_LEASE);
      assertThat(afterRelease).isNotNull();
      closeQuietly(afterRelease);
    }
  }

  private WriterToken acquire(Duration maxWait) {
    return acquire(LEASE, maxWait);
  }

  private WriterToken acquire(Duration lease, Duration maxWait) {
    WriterToken token =
        JdbcWriterTokenManager.create(jdbcTemplate, projectionKey, LOCK_TABLE, lease, Duration.ZERO)
            .acquireWriteToken(maxWait);
    if (token != null) {
      handedOutTokens.add(token);
    }
    return token;
  }

  /**
   * Stands in for an instance that was killed before it could unlock, so its lease has to lapse.
   */
  private void simulateHolderThatDiedWithoutUnlocking() {
    JdbcTemplateLockProvider provider =
        new JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(jdbcTemplate)
                .withTableName(LOCK_TABLE)
                .withLockedByValue("dead-holder/" + randomUUID())
                .usingDbTime()
                .build());
    assertThat(provider.lock(new LockConfiguration(Instant.now(), lockName, LEASE, Duration.ZERO)))
        .isPresent();
  }

  /**
   * Derived from locked_at rather than from now(), so that the value lands in the past no matter
   * which time zone the session renders now() in.
   */
  private void expireLease() {
    int updated =
        jdbcTemplate.update(
            "UPDATE "
                + LOCK_TABLE
                + " SET lock_until = locked_at - interval '1 hour' WHERE name = ?",
            lockName);
    assertThat(updated).isOne();
  }

  private Timestamp lockUntil() {
    return jdbcTemplate.queryForObject(
        "SELECT lock_until FROM " + LOCK_TABLE + " WHERE name = ?", Timestamp.class, lockName);
  }

  private String lockedBy() {
    return jdbcTemplate.queryForObject(
        "SELECT locked_by FROM " + LOCK_TABLE + " WHERE name = ?", String.class, lockName);
  }

  private FactStreamPosition persistedPosition(String table, String name) {
    return jdbcTemplate.queryForObject(
        "SELECT state, serial FROM " + table + " WHERE name = ?",
        (rs, rowNum) ->
            FactStreamPosition.of(rs.getObject("state", UUID.class), rs.getLong("serial")),
        name);
  }

  private static void closeQuietly(WriterToken token) {
    try {
      token.close();
    } catch (Exception e) {
      log.debug("Failed to close writer token", e);
    }
  }

  private void createTables() {
    jdbcTemplate.execute("DROP TABLE IF EXISTS " + LOCK_TABLE + ";");
    jdbcTemplate.execute(
        """
        CREATE TABLE factcast_projection_locks (

            name       varchar(255) NOT NULL PRIMARY KEY,
            lock_until timestamp    NOT NULL,
            locked_at  timestamp    NOT NULL,
            locked_by  varchar(255) NOT NULL
        );\
        """);

    jdbcTemplate.execute("DROP TABLE IF EXISTS managed_projection;");
    jdbcTemplate.execute(
        """
        CREATE TABLE managed_projection (

            name   varchar(255),
            state  UUID,
            serial bigint DEFAULT -1,

            PRIMARY KEY (name)
        );\
        """);

    jdbcTemplate.execute("DROP TABLE IF EXISTS subscribed_projection;");
    jdbcTemplate.execute(
        """
        CREATE TABLE subscribed_projection (

            name   varchar(255),
            state  UUID,
            serial bigint DEFAULT -1,

            PRIMARY KEY (name)
        );\
        """);

    jdbcTemplate.execute("DROP TABLE IF EXISTS users;");
    jdbcTemplate.execute(
        """
        CREATE TABLE users (

            name  varchar(255),
            id UUID,

            PRIMARY KEY (id)
        );\
        """);
  }

  @ProjectionMetaData(revision = 1)
  @SpringTransactional
  static class JdbcLockedUserNames extends AbstractSpringJdbcManagedProjection {

    private final JdbcTemplate jdbcTemplate;

    JdbcLockedUserNames(
        @NonNull PlatformTransactionManager platformTransactionManager,
        @NonNull JdbcTemplate jdbcTemplate) {
      super(platformTransactionManager, jdbcTemplate);
      this.jdbcTemplate = jdbcTemplate;
    }

    List<String> getUserNames() {
      return jdbcTemplate.query("SELECT name FROM users", (rs, rowNum) -> rs.getString(1));
    }

    @Handler
    void apply(UserCreated e) {
      jdbcTemplate.update(
          "INSERT INTO users (name, id) VALUES (?,?);", e.userName(), e.aggregateId());
    }

    @Handler
    void apply(UserDeleted e) {
      jdbcTemplate.update("DELETE FROM users where id = ?", e.aggregateId());
    }
  }

  @ProjectionMetaData(revision = 1)
  @SpringTransactional
  static class JdbcLockedSubscribedUserNames extends AbstractSpringJdbcSubscribedProjection {

    private final JdbcTemplate jdbcTemplate;

    JdbcLockedSubscribedUserNames(
        @NonNull PlatformTransactionManager platformTransactionManager,
        @NonNull JdbcTemplate jdbcTemplate) {
      super(platformTransactionManager, jdbcTemplate);
      this.jdbcTemplate = jdbcTemplate;
    }

    List<String> getUserNames() {
      return jdbcTemplate.query("SELECT name FROM users", (rs, rowNum) -> rs.getString(1));
    }

    @Override
    public boolean hasLock() {
      return super.hasLock();
    }

    @Handler
    void apply(UserCreated e) {
      jdbcTemplate.update(
          "INSERT INTO users (name, id) VALUES (?,?);", e.userName(), e.aggregateId());
    }
  }
}
