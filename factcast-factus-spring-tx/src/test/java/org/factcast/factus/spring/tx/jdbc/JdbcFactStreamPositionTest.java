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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.factcast.core.FactStreamPosition;
import org.factcast.factus.spring.tx.jdbc.JdbcFactStreamPosition.ProjectionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class JdbcFactStreamPositionTest {

  private static final String KEY = "org.example.MyProjection_1";
  private static final UUID FACT_ID = UUID.randomUUID();

  @Mock private JdbcTemplate jdbcTemplate;

  private JdbcFactStreamPosition uut;

  @BeforeEach
  void setUp() {
    uut = new JdbcFactStreamPosition(jdbcTemplate, ProjectionType.MANAGED, KEY);
  }

  private static RowMapper<FactStreamPosition> anyRowMapper() {
    return ArgumentMatchers.any();
  }

  @Nested
  class WhenReading {

    @Test
    void returnsNullWhenThereIsNoRowYet() {
      when(jdbcTemplate.query(anyString(), anyRowMapper(), eq(KEY))).thenReturn(List.of());

      assertThat(uut.factStreamPosition()).isNull();
    }

    @Test
    void returnsThePersistedPosition() {
      FactStreamPosition expected = FactStreamPosition.of(FACT_ID, 42L);
      when(jdbcTemplate.query(anyString(), anyRowMapper(), eq(KEY))).thenReturn(List.of(expected));

      assertThat(uut.factStreamPosition()).isEqualTo(expected);
    }

    @Test
    void selectsFromTheManagedTableByName() {
      uut.factStreamPosition();

      verify(jdbcTemplate)
          .query(
              eq("SELECT state, serial FROM managed_projection WHERE name = ?"),
              anyRowMapper(),
              eq(KEY));
    }

    @Test
    void mapsBothStateAndSerial() throws Exception {
      AtomicReference<RowMapper<FactStreamPosition>> rowMapper = new AtomicReference<>();
      when(jdbcTemplate.query(anyString(), anyRowMapper(), eq(KEY)))
          .thenAnswer(
              invocation -> {
                rowMapper.set(invocation.getArgument(1));
                return List.of();
              });
      uut.factStreamPosition();

      ResultSet rs = mock(ResultSet.class);
      when(rs.getObject("state", UUID.class)).thenReturn(FACT_ID);
      when(rs.getLong("serial")).thenReturn(42L);

      assertThat(rowMapper.get().mapRow(rs, 0)).isEqualTo(FactStreamPosition.of(FACT_ID, 42L));
    }
  }

  @Nested
  class WhenWriting {

    @Test
    void updatesTheExistingRowWithBothStateAndSerial() {
      when(jdbcTemplate.update(anyString(), eq(FACT_ID), eq(42L), eq(KEY))).thenReturn(1);

      uut.factStreamPosition(FactStreamPosition.of(FACT_ID, 42L));

      verify(jdbcTemplate)
          .update(
              "UPDATE managed_projection SET state = ?, serial = ? WHERE name = ?",
              FACT_ID,
              42L,
              KEY);
      verifyNoMoreInteractions(jdbcTemplate);
    }

    @Test
    void insertsWhenNoRowWasUpdated() {
      when(jdbcTemplate.update(anyString(), eq(FACT_ID), eq(42L), eq(KEY))).thenReturn(0);

      uut.factStreamPosition(FactStreamPosition.of(FACT_ID, 42L));

      verify(jdbcTemplate)
          .update(
              "INSERT INTO managed_projection (name, state, serial) VALUES (?, ?, ?)",
              KEY,
              FACT_ID,
              42L);
    }
  }

  @Nested
  class WhenConfiguringTheTable {

    @Test
    void usesTheSubscribedTableForSubscribedProjections() {
      new JdbcFactStreamPosition(jdbcTemplate, ProjectionType.SUBSCRIBED, KEY).factStreamPosition();

      verify(jdbcTemplate)
          .query(
              eq("SELECT state, serial FROM subscribed_projection WHERE name = ?"),
              anyRowMapper(),
              eq(KEY));
    }

    @Test
    void honoursACustomTableName() {
      new JdbcFactStreamPosition(jdbcTemplate, "my_positions", KEY).factStreamPosition();

      verify(jdbcTemplate)
          .query(
              eq("SELECT state, serial FROM my_positions WHERE name = ?"), anyRowMapper(), eq(KEY));
    }

    @Test
    void shortensAnOverlongProjectionName() {
      String longKey = "z".repeat(300);

      new JdbcFactStreamPosition(jdbcTemplate, ProjectionType.MANAGED, longKey)
          .factStreamPosition();

      verify(jdbcTemplate)
          .query(anyString(), anyRowMapper(), eq(ProjectionNames.positionName(longKey)));
    }
  }
}
