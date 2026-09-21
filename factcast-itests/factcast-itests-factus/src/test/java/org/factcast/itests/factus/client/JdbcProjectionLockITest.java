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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.FactStreamPosition;
import org.factcast.factus.Factus;
import org.factcast.factus.Handler;
import org.factcast.factus.jdbc.AbstractJdbcManagedProjection;
import org.factcast.factus.jdbc.AbstractJdbcSubscribedProjection;
import org.factcast.factus.projection.WriterToken;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.factcast.itests.TestFactusApplication;
import org.factcast.itests.factus.event.UserCreated;
import org.factcast.itests.factus.event.UserDeleted;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

/**
 * The same lock and position handling as {@link SpringJdbcProjectionLockITest}, but on projections
 * that know nothing about Spring. JdbcTemplate is used to set up and inspect the schema only.
 */
@SpringBootTest
@ContextConfiguration(classes = TestFactusApplication.class)
@EnableAutoConfiguration
@Slf4j
public class JdbcProjectionLockITest extends AbstractFactCastIntegrationTest {

  private static final Duration MAX_WAIT = Duration.ofMillis(1500);
  private static final Duration BEYOND_LEASE = Duration.ofSeconds(6);

  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired Factus factus;

  private DataSource dataSource;
  private final List<WriterToken> handedOutTokens = new ArrayList<>();

  @BeforeEach
  void setUp() {
    dataSource = jdbcTemplate.getDataSource();
    createTables();
  }

  @AfterEach
  void releaseTokens() {
    handedOutTokens.forEach(JdbcProjectionLockITest::closeQuietly);
    handedOutTokens.clear();
  }

  @Test
  void onlyOneOfTwoInstancesGetsAToken() {
    JdbcUserNames first = new JdbcUserNames(dataSource);
    JdbcUserNames second = new JdbcUserNames(dataSource);

    assertThat(acquire(first, MAX_WAIT)).isNotNull();
    assertThat(acquire(second, MAX_WAIT)).isNull();
  }

  @Test
  void releasingTheTokenLetsTheNextInstanceIn() {
    JdbcUserNames first = new JdbcUserNames(dataSource);
    WriterToken token = acquire(first, MAX_WAIT);
    assertThat(token).isNotNull();

    closeQuietly(token);

    assertThat(acquire(new JdbcUserNames(dataSource), BEYOND_LEASE)).isNotNull();
  }

  @Test
  void managedProjectionAppliesFactsAndResumesFromItsPersistedPosition() {
    UUID klaus = randomUUID();
    factus.publish(
        List.of(
            new UserCreated(randomUUID(), "Peter"),
            new UserCreated(randomUUID(), "Paul"),
            new UserCreated(klaus, "Klaus"),
            new UserDeleted(klaus)));

    JdbcUserNames uut = new JdbcUserNames(dataSource);
    factus.update(uut);

    assertThat(uut.getUserNames()).containsExactlyInAnyOrder("Peter", "Paul");

    FactStreamPosition afterFirstUpdate = uut.factStreamPosition();
    assertThat(afterFirstUpdate).isNotNull();
    assertThat(afterFirstUpdate.serial()).isPositive();
    assertThat(persistedPosition("managed_projection", uut.getScopedName().asString()))
        .isEqualTo(afterFirstUpdate);

    factus.publish(new UserCreated(randomUUID(), "Zora"));

    JdbcUserNames resumed = new JdbcUserNames(dataSource);
    assertThat(resumed.factStreamPosition()).isEqualTo(afterFirstUpdate);

    factus.update(resumed);

    assertThat(resumed.getUserNames()).containsExactlyInAnyOrder("Peter", "Paul", "Zora");
    assertThat(resumed.factStreamPosition().serial()).isGreaterThan(afterFirstUpdate.serial());
  }

  @Test
  void subscribedProjectionHoldsTheWriteTokenWhileSubscribed() throws Exception {
    factus.publish(
        List.of(new UserCreated(randomUUID(), "Peter"), new UserCreated(randomUUID(), "Paul")));

    JdbcSubscribedUserNames uut = new JdbcSubscribedUserNames(dataSource);
    JdbcSubscribedUserNames competitor = new JdbcSubscribedUserNames(dataSource);

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
  }

  private WriterToken acquire(JdbcUserNames projection, Duration maxWait) {
    WriterToken token = projection.acquireWriteToken(maxWait);
    if (token != null) {
      handedOutTokens.add(token);
    }
    return token;
  }

  private static void closeQuietly(WriterToken token) {
    try {
      token.close();
    } catch (Exception e) {
      log.debug("Failed to close writer token", e);
    }
  }

  private FactStreamPosition persistedPosition(String table, String name) {
    return jdbcTemplate.queryForObject(
        "SELECT state, serial FROM " + table + " WHERE name = ?",
        (rs, rowNum) ->
            FactStreamPosition.of(rs.getObject("state", UUID.class), rs.getLong("serial")),
        name);
  }

  private void createTables() {
    jdbcTemplate.execute("DROP TABLE IF EXISTS factcast_projection_locks;");
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
  static class JdbcUserNames extends AbstractJdbcManagedProjection {

    JdbcUserNames(@NonNull DataSource dataSource) {
      super(dataSource);
    }

    List<String> getUserNames() {
      return queryUserNames(dataSource());
    }

    @Handler
    void apply(UserCreated e) {
      insertUser(dataSource(), e);
    }

    @Handler
    void apply(UserDeleted e) {
      deleteUser(dataSource(), e.aggregateId());
    }
  }

  @ProjectionMetaData(revision = 1)
  static class JdbcSubscribedUserNames extends AbstractJdbcSubscribedProjection {

    JdbcSubscribedUserNames(@NonNull DataSource dataSource) {
      super(dataSource);
    }

    List<String> getUserNames() {
      return queryUserNames(dataSource());
    }

    @Override
    public boolean hasLock() {
      return super.hasLock();
    }

    @Handler
    void apply(UserCreated e) {
      insertUser(dataSource(), e);
    }
  }

  @SneakyThrows
  private static List<String> queryUserNames(DataSource dataSource) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement("SELECT name FROM users");
        ResultSet resultSet = statement.executeQuery()) {
      List<String> names = new ArrayList<>();
      while (resultSet.next()) {
        names.add(resultSet.getString(1));
      }
      return names;
    }
  }

  @SneakyThrows
  private static void insertUser(DataSource dataSource, UserCreated e) {
    execute(
        dataSource,
        "INSERT INTO users (name, id) VALUES (?,?)",
        statement -> {
          statement.setString(1, e.userName());
          statement.setObject(2, e.aggregateId());
        });
  }

  @SneakyThrows
  private static void deleteUser(DataSource dataSource, UUID aggregateId) {
    execute(
        dataSource,
        "DELETE FROM users WHERE id = ?",
        statement -> statement.setObject(1, aggregateId));
  }

  private static void execute(DataSource dataSource, String sql, StatementBinder binder)
      throws SQLException {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      binder.bind(statement);
      statement.executeUpdate();
    }
  }

  @FunctionalInterface
  private interface StatementBinder {
    void bind(PreparedStatement statement) throws SQLException;
  }
}
