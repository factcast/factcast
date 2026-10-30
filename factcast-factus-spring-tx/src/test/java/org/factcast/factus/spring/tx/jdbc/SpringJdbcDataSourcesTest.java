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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.sql.DataSource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;

@ExtendWith(MockitoExtension.class)
class SpringJdbcDataSourcesTest {

  @Mock private JdbcTemplate jdbcTemplate;

  private final DataSource plain = mock(DataSource.class);

  @Nested
  class ForLock {

    @Test
    void usesThePlainDataSource() {
      when(jdbcTemplate.getDataSource()).thenReturn(plain);

      assertThat(SpringJdbcDataSources.forLock(jdbcTemplate)).isSameAs(plain);
    }

    @Test
    void unwrapsATransactionAwareDataSource() {
      when(jdbcTemplate.getDataSource()).thenReturn(new TransactionAwareDataSourceProxy(plain));

      assertThat(SpringJdbcDataSources.forLock(jdbcTemplate)).isSameAs(plain);
    }
  }

  @Nested
  class ForPosition {

    @Test
    void wrapsThePlainDataSource() {
      when(jdbcTemplate.getDataSource()).thenReturn(plain);

      DataSource dataSource = SpringJdbcDataSources.forPosition(jdbcTemplate);

      assertThat(dataSource).isInstanceOf(TransactionAwareDataSourceProxy.class);
      assertThat(((TransactionAwareDataSourceProxy) dataSource).getTargetDataSource())
          .isSameAs(plain);
    }

    @Test
    void keepsAnAlreadyTransactionAwareDataSource() {
      TransactionAwareDataSourceProxy proxy = new TransactionAwareDataSourceProxy(plain);
      when(jdbcTemplate.getDataSource()).thenReturn(proxy);

      assertThat(SpringJdbcDataSources.forPosition(jdbcTemplate)).isSameAs(proxy);
    }
  }

  @Test
  void rejectsAJdbcTemplateWithoutDataSource() {
    when(jdbcTemplate.getDataSource()).thenReturn(null);

    assertThatThrownBy(() -> SpringJdbcDataSources.forLock(jdbcTemplate))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("DataSource");
  }
}
