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
package org.factcast.store.internal.horizon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.factcast.core.subscription.FactStreamHorizon;
import org.factcast.store.internal.PgConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
final class ReadOnlyPgFactStreamHorizonProviderTest {

  @Mock DataSource primaryDataSource;
  @Mock DataSource otherDataSource;
  @Mock JdbcTemplate primaryJdbcTemplate;
  @Mock JdbcTemplate otherJdbcTemplate;

  ReadOnlyPgFactStreamHorizonProvider underTest;

  @BeforeEach
  void setUp() {
    underTest =
        new ReadOnlyPgFactStreamHorizonProvider(primaryDataSource) {
          @Override
          protected JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return dataSource == primaryDataSource ? primaryJdbcTemplate : otherJdbcTemplate;
          }
        };
  }

  @Test
  void currentHorizonStartsEmptyWithoutReadingTheDatabase() {
    assertThat(underTest.currentPrimary()).isEqualTo(FactStreamHorizon.empty());

    verifyNoInteractions(
        primaryDataSource, otherDataSource, primaryJdbcTemplate, otherJdbcTemplate);
  }

  @Test
  @SuppressWarnings("unchecked")
  void advanceReadsPrimaryAndUpdatesCurrentWhileReadCanUseAnotherDataSource() {
    FactStreamHorizon primary = new FactStreamHorizon(UUID.randomUUID(), 42, 7);
    FactStreamHorizon other = new FactStreamHorizon(UUID.randomUUID(), 21, 4);
    when(primaryJdbcTemplate.queryForObject(
            eq(ReadOnlyPgFactStreamHorizonProvider.READ_HORIZON), any(RowMapper.class)))
        .thenReturn(primary);
    when(otherJdbcTemplate.queryForObject(
            eq(ReadOnlyPgFactStreamHorizonProvider.READ_HORIZON), any(RowMapper.class)))
        .thenReturn(other);

    assertThat(underTest.advance()).isEqualTo(primary);
    assertThat(underTest.currentPrimary()).isEqualTo(primary);
    assertThat(underTest.readFrom(otherDataSource)).isEqualTo(other);
    assertThat(underTest.currentPrimary()).isEqualTo(primary);
  }

  @Test
  @SuppressWarnings("unchecked")
  void missingSingletonRowFailsInsteadOfReturningAnUnsafeEmptyHorizon() {
    when(primaryJdbcTemplate.queryForObject(
            eq(ReadOnlyPgFactStreamHorizonProvider.READ_HORIZON), any(RowMapper.class)))
        .thenThrow(new EmptyResultDataAccessException(1));

    assertThatThrownBy(underTest::advance).isInstanceOf(EmptyResultDataAccessException.class);
    assertThat(underTest.currentPrimary()).isEqualTo(FactStreamHorizon.empty());
  }

  @Test
  @SuppressWarnings("unchecked")
  void eachAdvanceRefreshesTheCacheIncludingAfterADatabaseReset() {
    FactStreamHorizon first = new FactStreamHorizon(UUID.randomUUID(), 21, 4);
    FactStreamHorizon next = new FactStreamHorizon(UUID.randomUUID(), 42, 7);
    when(primaryJdbcTemplate.queryForObject(
            eq(ReadOnlyPgFactStreamHorizonProvider.READ_HORIZON), any(RowMapper.class)))
        .thenReturn(first, next, FactStreamHorizon.empty());

    assertThat(underTest.advance()).isEqualTo(first);
    assertThat(underTest.currentPrimary()).isEqualTo(first);
    assertThat(underTest.advance()).isEqualTo(next);
    assertThat(underTest.currentPrimary()).isEqualTo(next);
    assertThat(underTest.advance()).isEqualTo(FactStreamHorizon.empty());
    assertThat(underTest.currentPrimary()).isEqualTo(FactStreamHorizon.empty());
    verify(primaryJdbcTemplate, times(3))
        .queryForObject(eq(ReadOnlyPgFactStreamHorizonProvider.READ_HORIZON), any(RowMapper.class));
    verifyNoInteractions(otherJdbcTemplate);
  }

  @Test
  @SuppressWarnings("unchecked")
  void failedRefreshPreservesTheLastSuccessfullyReadHorizon() {
    FactStreamHorizon previous = new FactStreamHorizon(UUID.randomUUID(), 42, 7);
    DataAccessResourceFailureException failure =
        new DataAccessResourceFailureException("database unavailable");
    when(primaryJdbcTemplate.queryForObject(
            eq(ReadOnlyPgFactStreamHorizonProvider.READ_HORIZON), any(RowMapper.class)))
        .thenReturn(previous)
        .thenThrow(failure);
    underTest.advance();

    assertThatThrownBy(underTest::advance).isSameAs(failure);

    assertThat(underTest.currentPrimary()).isEqualTo(previous);
  }

  @Test
  @SuppressWarnings("unchecked")
  void directReadDoesNotRefreshTheCachedHorizonEvenOnTheConfiguredDataSource() {
    FactStreamHorizon read = new FactStreamHorizon(UUID.randomUUID(), 42, 7);
    when(primaryJdbcTemplate.queryForObject(
            eq(ReadOnlyPgFactStreamHorizonProvider.READ_HORIZON), any(RowMapper.class)))
        .thenReturn(read);

    assertThat(underTest.readFrom(primaryDataSource)).isEqualTo(read);
    assertThat(underTest.currentPrimary()).isEqualTo(FactStreamHorizon.empty());
  }

  @Test
  @SuppressWarnings("unchecked")
  void failedAlternateDataSourceReadPreservesTheCachedHorizon() {
    FactStreamHorizon primary = new FactStreamHorizon(UUID.randomUUID(), 42, 7);
    when(primaryJdbcTemplate.queryForObject(
            eq(ReadOnlyPgFactStreamHorizonProvider.READ_HORIZON), any(RowMapper.class)))
        .thenReturn(primary);
    when(otherJdbcTemplate.queryForObject(
            eq(ReadOnlyPgFactStreamHorizonProvider.READ_HORIZON), any(RowMapper.class)))
        .thenThrow(new EmptyResultDataAccessException(1));
    underTest.advance();

    assertThatThrownBy(() -> underTest.readFrom(otherDataSource))
        .isInstanceOf(EmptyResultDataAccessException.class);

    assertThat(underTest.currentPrimary()).isEqualTo(primary);
  }

  @ParameterizedTest
  @MethodSource("persistedHorizons")
  void mapsThePersistedRowUsingTheConfiguredDataSource(FactStreamHorizon expected)
      throws Exception {
    Connection connection = mock(Connection.class);
    Statement statement = mock(Statement.class);
    ResultSet row = mock(ResultSet.class);
    when(primaryDataSource.getConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);
    when(statement.executeQuery(ReadOnlyPgFactStreamHorizonProvider.READ_HORIZON)).thenReturn(row);
    when(row.next()).thenReturn(true, false);
    when(row.getObject(PgConstants.HORIZON_COLUMN_FACT_ID, UUID.class))
        .thenReturn(expected.factId());
    when(row.getLong(PgConstants.HORIZON_COLUMN_FACT_SER)).thenReturn(expected.factSerial());
    when(row.getLong(PgConstants.HORIZON_COLUMN_NOTIFICATION_SER))
        .thenReturn(expected.notificationSerial());
    ReadOnlyPgFactStreamHorizonProvider provider =
        new ReadOnlyPgFactStreamHorizonProvider(primaryDataSource);

    assertThat(provider.advance()).isEqualTo(expected);
    assertThat(provider.currentPrimary()).isEqualTo(expected);
    verify(statement).executeQuery(ReadOnlyPgFactStreamHorizonProvider.READ_HORIZON);
    verifyNoInteractions(otherDataSource);
  }

  static Stream<Arguments> persistedHorizons() {
    return Stream.of(
        Arguments.of(new FactStreamHorizon(UUID.randomUUID(), 42, 7)),
        Arguments.of(FactStreamHorizon.empty()));
  }
}
