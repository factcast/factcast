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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
public class FastForwardTargetRefresherTest {

  @Mock private JdbcTemplate jdbc;
  @Mock private FastForwardTargetRefresher.HighWaterMark target;
  @InjectMocks private FastForwardTargetRefresher underTest;

  @Nested
  class FfwdTarget {
    @Test
    void refresh() {
      var uut = spy(underTest);

      assertThat(uut.targetId()).isNull();
      assertThat(uut.targetSer()).isZero();

      UUID id = UUID.randomUUID();
      long ser = 42L;

      when(jdbc.queryForObject(anyString(), any(RowMapper.class)))
          .thenReturn(FastForwardTargetRefresher.HighWaterMark.of(id, ser));

      uut.refresh();

      assertThat(uut.targetId()).isEqualTo(id);
      assertThat(uut.targetSer()).isEqualTo(ser);
    }

    @Test
    void resetAfterEmptyResult() {
      var uut = spy(underTest);

      assertThat(uut.targetId()).isNull();
      assertThat(uut.targetSer()).isZero();

      UUID id = UUID.randomUUID();
      long ser = 42L;

      when(jdbc.queryForObject(anyString(), any(RowMapper.class)))
          .thenReturn(FastForwardTargetRefresher.HighWaterMark.of(id, ser))
          .thenThrow(new EmptyResultDataAccessException(1));

      uut.refresh();

      assertThat(uut.targetId()).isEqualTo(id);
      assertThat(uut.targetSer()).isEqualTo(ser);

      uut.refresh();

      assertThat(uut.targetId()).isNull();
      assertThat(uut.targetSer()).isZero();
    }
  }
}
