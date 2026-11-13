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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.factcast.core.FactStreamPosition;
import org.factcast.factus.jdbc.JdbcFactStreamPosition.ProjectionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
class JdbcFactStreamPositionTest {

  private static final String KEY = "org.example.MyProjection_1";
  private static final UUID FACT_ID = UUID.randomUUID();

  @Mock private DataSource dataSource;
  @Mock private Connection connection;
  @Mock private PreparedStatement statement;
  @Mock private ResultSet resultSet;

  private JdbcFactStreamPosition uut;

  @BeforeEach
  void setUp() throws SQLException {
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenReturn(statement);
    uut = new JdbcFactStreamPosition(dataSource, ProjectionType.MANAGED, KEY);
  }

  @Nested
  class WhenReading {

    @BeforeEach
    void setUp() throws SQLException {
      when(statement.executeQuery()).thenReturn(resultSet);
    }

    @Test
    void returnsNullWhenThereIsNoRowYet() throws SQLException {
      when(resultSet.next()).thenReturn(false);

      assertThat(uut.factStreamPosition()).isNull();
    }

    @Test
    void returnsThePersistedPosition() throws SQLException {
      when(resultSet.next()).thenReturn(true);
      when(resultSet.getObject("state", UUID.class)).thenReturn(FACT_ID);
      when(resultSet.getLong("serial")).thenReturn(42L);

      assertThat(uut.factStreamPosition()).isEqualTo(FactStreamPosition.of(FACT_ID, 42L));
    }

    @Test
    void selectsFromTheManagedTableByName() throws SQLException {
      uut.factStreamPosition();

      verify(connection)
          .prepareStatement("SELECT state, serial FROM managed_projection WHERE name = ?");
      verify(statement).setString(1, KEY);
    }

    @Test
    void releasesTheConnection() throws SQLException {
      uut.factStreamPosition();

      verify(connection).close();
    }
  }

  @Nested
  class WhenWriting {

    @Test
    void updatesTheExistingRowWithBothStateAndSerial() throws SQLException {
      when(statement.executeUpdate()).thenReturn(1);

      uut.factStreamPosition(FactStreamPosition.of(FACT_ID, 42L));

      verify(connection)
          .prepareStatement("UPDATE managed_projection SET state = ?, serial = ? WHERE name = ?");
      verify(statement).setObject(1, FACT_ID);
      verify(statement).setLong(2, 42L);
      verify(statement).setString(3, KEY);
    }

    @Test
    void doesNotInsertWhenARowWasUpdated() throws SQLException {
      when(statement.executeUpdate()).thenReturn(1);

      uut.factStreamPosition(FactStreamPosition.of(FACT_ID, 42L));

      verify(connection, never())
          .prepareStatement(
              "INSERT INTO managed_projection (name, state, serial) VALUES (?, ?, ?)");
    }

    @Test
    void insertsWhenNoRowWasUpdated() throws SQLException {
      when(statement.executeUpdate()).thenReturn(0);

      uut.factStreamPosition(FactStreamPosition.of(FACT_ID, 42L));

      verify(connection)
          .prepareStatement(
              "INSERT INTO managed_projection (name, state, serial) VALUES (?, ?, ?)");
      verify(statement).setString(1, KEY);
      verify(statement).setObject(2, FACT_ID);
      verify(statement).setLong(3, 42L);
    }

    @Test
    void reportsTheTableWhenTheWriteFails() throws SQLException {
      when(statement.executeUpdate()).thenThrow(new SQLException("nope"));

      assertThatThrownBy(() -> uut.factStreamPosition(FactStreamPosition.of(FACT_ID, 42L)))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("managed_projection")
          .hasCauseInstanceOf(SQLException.class);
    }
  }

  @Nested
  class WhenConfiguringTheTable {

    @BeforeEach
    void setUp() throws SQLException {
      when(statement.executeQuery()).thenReturn(resultSet);
    }

    @Test
    void usesTheSubscribedTableForSubscribedProjections() throws SQLException {
      new JdbcFactStreamPosition(dataSource, ProjectionType.SUBSCRIBED, KEY).factStreamPosition();

      verify(connection)
          .prepareStatement("SELECT state, serial FROM subscribed_projection WHERE name = ?");
    }

    @Test
    void honoursACustomTableName() throws SQLException {
      new JdbcFactStreamPosition(dataSource, "my_positions", KEY).factStreamPosition();

      verify(connection).prepareStatement("SELECT state, serial FROM my_positions WHERE name = ?");
    }

    @Test
    void shortensAnOverlongProjectionName() throws SQLException {
      String longKey = "z".repeat(300);

      new JdbcFactStreamPosition(dataSource, ProjectionType.MANAGED, longKey).factStreamPosition();

      verify(statement).setString(1, ProjectionNames.positionName(longKey));
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT) // validation throws before any JDBC call
    void rejectsATableNameThatIsNotAnIdentifier() {
      for (String tableName :
          List.of("positions; DROP TABLE users", "my positions", "1positions", "")) {
        assertThatThrownBy(() -> new JdbcFactStreamPosition(dataSource, tableName, KEY))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(tableName);
      }
    }
  }
}
