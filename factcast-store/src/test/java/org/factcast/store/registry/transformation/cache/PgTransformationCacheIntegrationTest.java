/*
 * Copyright © 2017-2020 factcast.org
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
package org.factcast.store.registry.transformation.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.util.*;
import javax.sql.DataSource;
import org.factcast.core.Fact;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.PgFact;
import org.factcast.store.internal.PgTestConfiguration;
import org.factcast.store.internal.notification.TransformationStoreChangeNotification;
import org.factcast.store.registry.NOPRegistryMetrics;
import org.factcast.test.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.postgresql.PGConnection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;

@SpringJUnitConfig(PgTestConfiguration.class)
@Sql(scripts = "/wipe.sql", config = @SqlConfig(separator = "#"))
@IntegrationTest
class PgTransformationCacheIntegrationTest {
  @Autowired JdbcTemplate jdbc;
  @Autowired DataSource dataSource;
  @Autowired PlatformTransactionManager transactionManager;

  private PgTransformationCache uut;

  @BeforeEach
  void createCache() {
    // Flush every put so lookups and invalidation exercise the database.
    uut =
        new PgTransformationCache(
            transactionManager,
            jdbc,
            new NOPRegistryMetrics(),
            new StoreConfigurationProperties(),
            1);
  }

  @AfterEach
  void closeCache() throws Exception {
    uut.close();
  }

  @Test
  void invalidatesOnlyTheChangedEdgeWithinNamespaceAndType() {
    var factId = UUID.randomUUID();
    var affected =
        List.of(
            TransformationCache.Key.of(factId, 3, List.of(2, 3)),
            TransformationCache.Key.of(factId, 3, List.of(1, 2, 3)),
            TransformationCache.Key.of(factId, 4, List.of(1, 2, 3, 4)),
            TransformationCache.Key.of(factId, 4, List.of(2, 3, 4)),
            TransformationCache.Key.of(factId, 4, List.of(3, 4)),
            TransformationCache.Key.of(factId, 6, List.of(1, 2, 3, 4, 5, 6)));

    var survivors =
        List.of(
            TransformationCache.Key.of(factId, 2, List.of(1, 2)),
            TransformationCache.Key.of(factId, 5, List.of(1, 5)),
            TransformationCache.Key.of(factId, 5, List.of(5, 6)));

    affected.forEach(
        key ->
            uut.put(
                key,
                PgFact.from(
                    Fact.builder().ns("ns").type("type").version(key.version()).build("{}"))));
    survivors.forEach(
        key ->
            uut.put(
                key,
                PgFact.from(
                    Fact.builder().ns("ns").type("type").version(key.version()).build("{}"))));

    PgFact otherNs = PgFact.from(Fact.builder().ns("other").type("type").version(3).build("{}"));
    PgFact otherType = PgFact.from(Fact.builder().ns("ns").type("other").version(3).build("{}"));
    var otherNsKey = TransformationCache.Key.of(otherNs.id(), 3, List.of(1, 2, 3));
    var otherTypeKey = TransformationCache.Key.of(otherType.id(), 3, List.of(1, 2, 3));
    uut.put(otherNsKey, otherNs);
    uut.put(otherTypeKey, otherType);

    // should invalidate all affected
    uut.invalidateTransformationFor("ns", "type", 3, 4);

    affected.forEach(key -> assertThat(uut.find(key)).isEmpty());
    survivors.forEach(key -> assertThat(uut.find(key)).isPresent());
    // others are untouched
    assertThat(uut.find(otherNsKey)).isPresent();
    assertThat(uut.find(otherTypeKey)).isPresent();

    // Legacy notifications still invalidate all paths for the namespace and type.
    uut.invalidateTransformationFor("ns", "type");

    survivors.forEach(key -> assertThat(uut.find(key)).isEmpty());
    assertThat(uut.find(otherNsKey)).isPresent();
    assertThat(uut.find(otherTypeKey)).isPresent();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "DELETE FROM transformationstore WHERE ns = 'ns' AND type = 'type'",
        "UPDATE transformationstore SET transformation = 'changed' WHERE ns = 'ns' AND type = 'type'"
      })
  void notifiesEachChangedEdgeInOneTransaction(String change) throws Exception {
    jdbc.update(
        "INSERT INTO transformationstore (id, ns, type, from_version, to_version) VALUES "
            + "('edge1', 'ns', 'type', 1, 2), ('edge2', 'ns', 'type', 2, 3)");
    try (Connection listener = dataSource.getConnection();
        var statement = listener.createStatement()) {
      statement.execute("LISTEN transformationstore_change");
      try {
        jdbc.update(change);
        var notifications = listener.unwrap(PGConnection.class).getNotifications(5000);
        assertThat(notifications).hasSize(2);
        var changes =
            Arrays.stream(notifications).map(TransformationStoreChangeNotification::from).toList();
        assertThat(changes)
            .extracting(TransformationStoreChangeNotification::fromVersion)
            .containsExactlyInAnyOrder(1, 2);
        assertThat(changes)
            .extracting(TransformationStoreChangeNotification::toVersion)
            .containsExactlyInAnyOrder(2, 3);
        assertThat(changes)
            .extracting(TransformationStoreChangeNotification::txId)
            .containsOnly(changes.get(0).txId());
        assertThat(changes)
            .extracting(TransformationStoreChangeNotification::uniqueId)
            .doesNotHaveDuplicates();
      } finally {
        statement.execute("UNLISTEN transformationstore_change");
      }
    }
  }

  @Test
  void noMatchingKeysDoesNotWaitForExclusiveLock() throws Exception {
    try (Connection writer = dataSource.getConnection();
        Connection invalidator = dataSource.getConnection();
        var writerStatement = writer.createStatement();
        var invalidatorStatement = invalidator.createStatement()) {
      boolean autoCommit = writer.getAutoCommit();
      writer.setAutoCommit(false);
      try {
        writerStatement.execute("LOCK TABLE transformation_cache IN EXCLUSIVE MODE");
        invalidatorStatement.execute("SET statement_timeout = '2s'");
        try {
          invalidatorStatement.execute(
              "CALL invalidate_transformation_cache('absent', 'absent', 1, 2)");
        } finally {
          invalidatorStatement.execute("RESET statement_timeout");
        }
      } finally {
        writer.rollback();
        writer.setAutoCommit(autoCommit);
      }
    }
  }
}
