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
package org.factcast.factus.spring.tx.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.factcast.core.FactStreamPosition;
import org.factcast.factus.jdbc.JdbcFactStreamPosition;
import org.factcast.factus.jdbc.JdbcWriterTokenManager;
import org.factcast.factus.projection.WriterToken;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class AbstractSpringJdbcProjectionTest {

  @Mock private PlatformTransactionManager platformTransactionManager;
  @Mock private JdbcTemplate jdbcTemplate;
  @Mock private DataSource dataSource;
  @Mock private Connection connection;
  @Mock private PreparedStatement statement;
  @Mock private ResultSet resultSet;

  @Nested
  class PositionWiring {

    @BeforeEach
    void setUp() throws SQLException {
      when(jdbcTemplate.getDataSource()).thenReturn(dataSource);
      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.prepareStatement(anyString())).thenReturn(statement);
    }

    @Test
    void readsItsPositionFromTheManagedTable() throws SQLException {
      when(statement.executeQuery()).thenReturn(resultSet);
      when(resultSet.next()).thenReturn(true);
      UUID factId = UUID.randomUUID();
      when(resultSet.getObject("state", UUID.class)).thenReturn(factId);
      when(resultSet.getLong("serial")).thenReturn(7L);

      MyManagedProjection uut = new MyManagedProjection(platformTransactionManager, jdbcTemplate);

      assertThat(uut.factStreamPosition()).isEqualTo(FactStreamPosition.of(factId, 7L));
      verify(connection)
          .prepareStatement("SELECT state, serial FROM managed_projection WHERE name = ?");
      verify(statement).setString(1, "managed_1");
    }

    @Test
    void writesItsPositionToTheManagedTable() throws SQLException {
      when(statement.executeUpdate()).thenReturn(1);
      MyManagedProjection uut = new MyManagedProjection(platformTransactionManager, jdbcTemplate);
      UUID factId = UUID.randomUUID();

      uut.factStreamPosition(FactStreamPosition.of(factId, 7L));

      verify(connection)
          .prepareStatement("UPDATE managed_projection SET state = ?, serial = ? WHERE name = ?");
      verify(statement).setObject(1, factId);
      verify(statement).setLong(2, 7L);
      verify(statement).setString(3, "managed_1");
    }

    @Test
    void readsItsPositionFromTheSubscribedTable() throws SQLException {
      when(statement.executeQuery()).thenReturn(resultSet);

      new MySubscribedProjection(platformTransactionManager, jdbcTemplate).factStreamPosition();

      verify(connection)
          .prepareStatement("SELECT state, serial FROM subscribed_projection WHERE name = ?");
    }

    @Test
    void honoursACustomPositionTable() throws SQLException {
      when(statement.executeQuery()).thenReturn(resultSet);

      new MyManagedProjection(platformTransactionManager, jdbcTemplate, "my_locks", "my_positions")
          .factStreamPosition();

      verify(connection).prepareStatement("SELECT state, serial FROM my_positions WHERE name = ?");
    }
  }

  @Nested
  class SubscribedProjectionLock {

    @Mock private LockProvider lockProvider;

    private MySubscribedProjection uut;

    @BeforeEach
    void setUp() {
      uut =
          new MySubscribedProjection(
              platformTransactionManager,
              new JdbcWriterTokenManager(
                  lockProvider,
                  "subscribed_1",
                  JdbcWriterTokenManager.DEFAULT_LOCK_AT_MOST_FOR,
                  JdbcWriterTokenManager.DEFAULT_LOCK_AT_LEAST_FOR),
              new JdbcFactStreamPosition(
                  dataSource, JdbcFactStreamPosition.ProjectionType.SUBSCRIBED, "subscribed_1"));
    }

    @Test
    void hasNoLockBeforeAcquiringOne() {
      assertThat(uut.hasLock()).isFalse();
    }

    @Test
    void hasLockWhileTheTokenIsValid() throws Exception {
      when(lockProvider.lock(any(LockConfiguration.class)))
          .thenReturn(Optional.of(mock(SimpleLock.class)));

      WriterToken token = uut.acquireWriteToken(Duration.ZERO);

      assertThat(token).isNotNull();
      assertThat(uut.hasLock()).isTrue();

      token.close();
      assertThat(uut.hasLock()).isFalse();
    }
  }

  @ProjectionMetaData(name = "managed", revision = 1)
  static class MyManagedProjection extends AbstractSpringJdbcManagedProjection {
    MyManagedProjection(
        PlatformTransactionManager platformTransactionManager, JdbcTemplate jdbcTemplate) {
      super(platformTransactionManager, jdbcTemplate);
    }

    MyManagedProjection(
        PlatformTransactionManager platformTransactionManager,
        JdbcTemplate jdbcTemplate,
        String lockTableName,
        String positionTableName) {
      super(platformTransactionManager, jdbcTemplate, lockTableName, positionTableName);
    }
  }

  @ProjectionMetaData(name = "subscribed", revision = 1)
  static class MySubscribedProjection extends AbstractSpringJdbcSubscribedProjection {
    MySubscribedProjection(
        PlatformTransactionManager platformTransactionManager, JdbcTemplate jdbcTemplate) {
      super(platformTransactionManager, jdbcTemplate);
    }

    MySubscribedProjection(
        PlatformTransactionManager platformTransactionManager,
        JdbcWriterTokenManager writerTokenManager,
        JdbcFactStreamPosition factStreamPosition) {
      super(platformTransactionManager, writerTokenManager, factStreamPosition);
    }
  }
}
