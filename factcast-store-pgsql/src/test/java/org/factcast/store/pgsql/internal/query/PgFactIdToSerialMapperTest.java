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
package org.factcast.store.pgsql.internal.query;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import java.util.function.Supplier;
import lombok.val;
import org.factcast.store.pgsql.internal.PgMetrics;
import org.factcast.store.pgsql.internal.StoreMetrics.OP;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;

@ExtendWith(MockitoExtension.class)
public class PgFactIdToSerialMapperTest {

  @Mock JdbcTemplate jdbc;
  @Mock PgMetrics metrics;

  @Mock SqlRowSet rs;
  @InjectMocks PgFactIdToSerialMapper uut;

  @BeforeEach
  void setup() {}

  @Test
  public void acceptsNullParam() {

    assertThat(uut.retrieve(null)).isEqualTo(0);
  }

  @Test
  void happyPath() {
    doAnswer(i -> ((Supplier) i.getArgument(1)).get())
        .when(metrics)
        .time(any(OP.class), any(Supplier.class));

    when(jdbc.queryForObject(anyString(), any(), eq(Long.class))).thenReturn(42L);
    val ret = uut.retrieve(UUID.randomUUID());
    assertThat(ret).isEqualTo(42L);
  }

  @Test
  void empty() {
    doAnswer(i -> ((Supplier) i.getArgument(1)).get())
        .when(metrics)
        .time(any(OP.class), any(Supplier.class));

    when(jdbc.queryForObject(anyString(), any(), eq(Long.class)))
        .thenThrow(EmptyResultDataAccessException.class);
    val ret = uut.retrieve(UUID.randomUUID());
    assertThat(ret).isEqualTo(0);
  }
}
