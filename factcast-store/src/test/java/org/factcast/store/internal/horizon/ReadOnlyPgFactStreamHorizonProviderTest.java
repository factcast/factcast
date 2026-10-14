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

import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.factcast.core.subscription.FactStreamHorizon;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
final class ReadOnlyPgFactStreamHorizonProviderTest {

  @Mock DataSource primaryDataSource;
  @Mock DataSource otherDataSource;
  @Mock JdbcTemplate primaryJdbcTemplate;
  @Mock JdbcTemplate otherJdbcTemplate;

  @Test
  @SuppressWarnings("unchecked")
  void advanceReadsPrimaryAndUpdatesCurrentWhileReadCanUseAnotherDataSource() {
    FactStreamHorizon primary = new FactStreamHorizon(UUID.randomUUID(), 42, 7);
    FactStreamHorizon other = new FactStreamHorizon(UUID.randomUUID(), 21, 4);
    when(primaryJdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(List.of(primary));
    when(otherJdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(List.of(other));
    ReadOnlyPgFactStreamHorizonProvider underTest =
        new ReadOnlyPgFactStreamHorizonProvider(primaryDataSource) {
          @Override
          protected JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return dataSource == primaryDataSource ? primaryJdbcTemplate : otherJdbcTemplate;
          }
        };

    assertThat(underTest.advance()).isEqualTo(primary);
    assertThat(underTest.currentPrimary()).isEqualTo(primary);
    assertThat(underTest.read(otherDataSource)).isEqualTo(other);
    assertThat(underTest.currentPrimary()).isEqualTo(primary);
  }

  @Test
  @SuppressWarnings("unchecked")
  void missingSingletonRowFailsInsteadOfReturningAnUnsafeEmptyHorizon() {
    when(primaryJdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(List.of());
    ReadOnlyPgFactStreamHorizonProvider underTest =
        new ReadOnlyPgFactStreamHorizonProvider(primaryDataSource) {
          @Override
          protected JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return primaryJdbcTemplate;
          }
        };

    assertThatThrownBy(underTest::advance)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(ReadOnlyPgFactStreamHorizonProvider.MISSING_HORIZON);
    assertThat(underTest.currentPrimary()).isEqualTo(FactStreamHorizon.empty());
  }
}
