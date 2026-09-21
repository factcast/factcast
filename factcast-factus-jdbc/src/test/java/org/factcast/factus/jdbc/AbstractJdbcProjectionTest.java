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
package org.factcast.factus.jdbc;

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
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.factcast.factus.projection.WriterToken;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AbstractJdbcProjectionTest {

  @Mock private DataSource dataSource;
  @Mock private Connection connection;
  @Mock private PreparedStatement statement;
  @Mock private ResultSet resultSet;

  @Nested
  class PositionWiring {

    @BeforeEach
    void setUp() throws SQLException {
      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.prepareStatement(anyString())).thenReturn(statement);
      when(statement.executeQuery()).thenReturn(resultSet);
    }

    @Test
    void managedProjectionsUseTheManagedTable() throws SQLException {
      new MyManagedProjection(dataSource).factStreamPosition();

      verify(connection)
          .prepareStatement("SELECT state, serial FROM managed_projection WHERE name = ?");
      verify(statement).setString(1, "managed_1");
    }

    @Test
    void subscribedProjectionsUseTheSubscribedTable() throws SQLException {
      new MySubscribedProjection(dataSource).factStreamPosition();

      verify(connection)
          .prepareStatement("SELECT state, serial FROM subscribed_projection WHERE name = ?");
      verify(statement).setString(1, "subscribed_1");
    }

    @Test
    void honoursCustomTableNames() throws SQLException {
      new MyManagedProjection(dataSource, "my_locks", "my_positions").factStreamPosition();

      verify(connection).prepareStatement("SELECT state, serial FROM my_positions WHERE name = ?");
    }
  }

  @Nested
  class TokenWiring {

    @Mock private LockProvider lockProvider;

    private MySubscribedProjection uut;

    @BeforeEach
    void setUp() {
      uut =
          new MySubscribedProjection(
              dataSource,
              new JdbcWriterTokenManager(lockProvider, "subscribed_1"),
              new JdbcFactStreamPosition(
                  dataSource, JdbcFactStreamPosition.ProjectionType.SUBSCRIBED, "subscribed_1"));
    }

    @Test
    void exposesTheDataSource() {
      assertThat(uut.dataSource()).isSameAs(dataSource);
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

    @Test
    void returnsNoTokenWhenTheLockIsTaken() {
      when(lockProvider.lock(any(LockConfiguration.class))).thenReturn(Optional.empty());

      assertThat(uut.acquireWriteToken(Duration.ZERO)).isNull();
    }
  }

  @ProjectionMetaData(name = "managed", revision = 1)
  static class MyManagedProjection extends AbstractJdbcManagedProjection {
    MyManagedProjection(DataSource dataSource) {
      super(dataSource);
    }

    MyManagedProjection(DataSource dataSource, String lockTableName, String positionTableName) {
      super(dataSource, lockTableName, positionTableName);
    }
  }

  @ProjectionMetaData(name = "subscribed", revision = 1)
  static class MySubscribedProjection extends AbstractJdbcSubscribedProjection {
    MySubscribedProjection(DataSource dataSource) {
      super(dataSource);
    }

    MySubscribedProjection(
        DataSource dataSource,
        JdbcWriterTokenManager writerTokenManager,
        JdbcFactStreamPosition factStreamPosition) {
      super(dataSource, writerTokenManager, factStreamPosition);
    }
  }
}
