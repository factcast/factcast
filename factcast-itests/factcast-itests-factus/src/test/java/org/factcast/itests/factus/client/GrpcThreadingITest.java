/*
 * Copyright © 2017-2022 factcast.org
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

import static java.util.UUID.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.Factus;
import org.factcast.factus.Handler;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.projection.WriterToken;
import org.factcast.factus.spring.tx.AbstractSpringTxSubscribedProjection;
import org.factcast.itests.TestFactusApplication;
import org.factcast.itests.factus.event.UserCreated;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@ContextConfiguration(classes = TestFactusApplication.class)
@EnableAutoConfiguration
@Slf4j
public class GrpcThreadingITest extends AbstractFactCastIntegrationTest {
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired PlatformTransactionManager platformTransactionManager;
  @Autowired Factus factus;

  public static PostgreSQLContainer postgreSQLContainer =
      new PostgreSQLContainer("postgres:" + System.getProperty("postgres.version", "11.5"))
          .withPassword("sa")
          .withUsername("sa");

  static {
    postgreSQLContainer.start();

    System.setProperty("spring.datasource.url", postgreSQLContainer.getJdbcUrl());
    System.setProperty("spring.datasource.username", postgreSQLContainer.getUsername());
    System.setProperty("spring.datasource.password", postgreSQLContainer.getPassword());
  }

  final int NUMBER_OF_EVENTS = 1000;

  @BeforeEach
  void setUp() {
    createTables();

    var l = new ArrayList<EventObject>(NUMBER_OF_EVENTS);
    for (int i = 0; i < NUMBER_OF_EVENTS; i++) {
      l.add(new UserCreated(randomUUID(), getClass().getSimpleName() + ":" + i));
    }
    log.info("publishing {} Events ", NUMBER_OF_EVENTS);
    factus.publish(l);
  }

  @Nested
  class Subscribed {
    @Test
    public void test() {
      var s1 = new TestProjection(platformTransactionManager, jdbcTemplate, 1);
      var s2 = new TestProjection(platformTransactionManager, jdbcTemplate, 2);

      ForkJoinPool.commonPool()
          .invokeAll(
              List.of(
                  () -> {
                    factus.subscribeAndBlock(s1).awaitCatchup();
                    return null;
                  },
                  () -> {
                    factus.subscribeAndBlock(s2).awaitCatchup();
                    return null;
                  }));

      assertThat(s1.txSeen().size()).isEqualTo(s2.txSeen().size());
    }
  }

  private void createTables() {
    jdbcTemplate.execute("DROP TABLE IF EXISTS managed_projection;");
    jdbcTemplate.execute(
        "CREATE TABLE managed_projection (\n"
            + "\n"
            + "    name  varchar(255),\n"
            + "    fact_stream_position UUID,\n"
            + "\n"
            + "    PRIMARY KEY (name)\n"
            + ");");

    jdbcTemplate.execute("DROP TABLE IF EXISTS users;");
    jdbcTemplate.execute(
        "CREATE TABLE users (\n"
            + "\n"
            + "    name  varchar(255),\n"
            + "    id UUID,\n"
            + "\n"
            + "    PRIMARY KEY (id)\n"
            + ");");
  }

  @Slf4j
  static class TestProjection extends AbstractSpringTxSubscribedProjection {
    private final JdbcTemplate jdbcTemplate;
    @Getter private int factStreamPositionModifications = 0;

    @Getter private final Set<String> txSeen = new HashSet<>();

    private final int id;

    public TestProjection(
        @NonNull PlatformTransactionManager platformTransactionManager,
        JdbcTemplate jdbcTemplate,
        int i) {
      super(platformTransactionManager);
      this.jdbcTemplate = jdbcTemplate;
      id = i;
    }

    @Handler
    void apply(UserCreated e) {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();

      jdbcTemplate.update(
          "INSERT INTO users (name,id) VALUES (?,?);", e.userName(), UUID.randomUUID());
    }

    @Override
    public UUID factStreamPosition() {
      try {
        return jdbcTemplate.queryForObject(
            "SELECT fact_stream_position FROM managed_projection WHERE name = ?",
            UUID.class,
            getScopedName().with(String.valueOf(id)).asString());
      } catch (IncorrectResultSizeDataAccessException e) {
        // no position yet, just return null
        return null;
      }
    }

    @Override
    public void factStreamPosition(@NonNull UUID state) {
      log.debug("set state");
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      factStreamPositionModifications++;

      txSeen.add(jdbcTemplate.queryForObject("select txid_current()", String.class));

      jdbcTemplate.update(
          "INSERT INTO managed_projection (name, fact_stream_position) VALUES (?, ?) "
              + "ON CONFLICT (name) DO UPDATE SET fact_stream_position = ?",
          getScopedName().with(String.valueOf(id)).asString(),
          state,
          state);
    }

    @Override
    public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
      return () -> {};
    }
  }
}
