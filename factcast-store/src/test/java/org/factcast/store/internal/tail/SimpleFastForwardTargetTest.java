/*
 * Copyright © 2017-2023 factcast.org
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
package org.factcast.store.internal.tail;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.*;
import java.util.UUID;
import org.factcast.core.subscription.observer.HighWaterMark;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.*;

@ExtendWith(MockitoExtension.class)
public class SimpleFastForwardTargetTest {

  @Mock private JdbcTemplate jdbc;
  @InjectMocks private SimpleFastForwardTarget uut;

  @Test
  void fetches() {

    UUID id = UUID.randomUUID();
    long ser = 42L;

    when(jdbc.queryForObject(anyString(), any(RowMapper.class)))
        .thenReturn(HighWaterMark.of(id, ser), HighWaterMark.of(id, ser + 1));

    HighWaterMark highWaterMark = uut.highWaterMark();
    assertThat(highWaterMark.targetId()).isEqualTo(id);
    assertThat(highWaterMark.targetSer()).isEqualTo(ser);

    assertThat(uut.highWaterMark()).isNotSameAs(highWaterMark);
    highWaterMark = uut.highWaterMark();
    assertThat(highWaterMark.targetId()).isEqualTo(id);
    assertThat(highWaterMark.targetSer()).isEqualTo(ser + 1);
  }

  @Test
  void returnsEmpty() {
    when(jdbc.queryForObject(anyString(), any(RowMapper.class)))
        .thenThrow(EmptyResultDataAccessException.class);

    HighWaterMark highWaterMark = uut.highWaterMark();
    assertThat(highWaterMark.isEmpty()).isTrue();
  }

  @Test
  void extracts() throws SQLException {
    UUID id = UUID.randomUUID();
    ResultSet rs = mock(ResultSet.class);
    when(rs.getLong("targetSer")).thenReturn(42L);
    when(rs.getObject("targetId", UUID.class)).thenReturn(id);

    HighWaterMark res = uut.extract(rs, 1);

    assertThat(res.isEmpty()).isFalse();
    assertThat(res.targetSer()).isEqualTo(42L);
    assertThat(res.targetId()).isEqualTo(id);
  }
}
