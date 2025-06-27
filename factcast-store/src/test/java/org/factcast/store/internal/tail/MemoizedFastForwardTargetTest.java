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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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
public class MemoizedFastForwardTargetTest {

  @Mock private JdbcTemplate jdbc;
  @Mock private HighWaterMark target;
  @InjectMocks private MemoizedFastForwardTarget underTest;

  @Nested
  class FfwdTarget {
    @Test
    void refresh() {
      var uut = spy(underTest);

      UUID id = UUID.randomUUID();
      long ser = 42L;

      when(jdbc.queryForObject(anyString(), any(RowMapper.class)))
          .thenReturn(HighWaterMark.of(id, ser), HighWaterMark.of(id, ser + 1));

      HighWaterMark highWaterMark = uut.highWaterMark();
      assertThat(highWaterMark.targetId()).isEqualTo(id);
      assertThat(highWaterMark.targetSer()).isEqualTo(ser);

      assertThat(uut.highWaterMark()).isSameAs(highWaterMark);
      assertThat(uut.highWaterMark()).isSameAs(highWaterMark);

      uut.expire();

      assertThat(uut.highWaterMark()).isNotSameAs(highWaterMark);
      highWaterMark = uut.highWaterMark();
      assertThat(highWaterMark.targetId()).isEqualTo(id);
      assertThat(highWaterMark.targetSer()).isEqualTo(ser + 1);
    }

    @Test
    void resetAfterEmptyResult() {
      var uut = spy(underTest);
      UUID id = UUID.randomUUID();
      long ser = 42L;
      when(jdbc.queryForObject(anyString(), any(RowMapper.class)))
          .thenReturn(HighWaterMark.of(id, ser))
          .thenThrow(new EmptyResultDataAccessException(1));

      uut.expire();

      assertThat(uut.highWaterMark().targetId()).isEqualTo(id);
      assertThat(uut.highWaterMark().targetSer()).isEqualTo(ser);

      uut.expire();

      assertThat(uut.highWaterMark().targetId()).isNull();
      assertThat(uut.highWaterMark().targetSer()).isZero();
    }
  }

  @Nested
  class WhenNeedingRefresh {
    @Test
    void falseIfInRange() {
      UUID id = UUID.randomUUID();
      long ser = 42L;
      when(jdbc.queryForObject(anyString(), any(RowMapper.class)))
          .thenReturn(HighWaterMark.of(id, ser))
          .thenThrow(new EmptyResultDataAccessException(1));
      underTest.highWaterMark();

      assertThat(underTest.needsRefresh(System.currentTimeMillis())).isFalse();
    }

    @Test
    void trueIfNotInRange() {
      UUID id = UUID.randomUUID();
      long ser = 42L;
      when(jdbc.queryForObject(anyString(), any(RowMapper.class)))
          .thenReturn(HighWaterMark.of(id, ser))
          .thenThrow(new EmptyResultDataAccessException(1));
      underTest.highWaterMark();

      assertThat(underTest.needsRefresh(System.currentTimeMillis() * 2)).isTrue();
    }

    @Test
    void trueIfEmpty() {
      UUID id = UUID.randomUUID();
      long ser = 42L;
      when(jdbc.queryForObject(anyString(), any(RowMapper.class)))
          .thenReturn(HighWaterMark.of(id, ser))
          .thenThrow(new EmptyResultDataAccessException(1));
      underTest.highWaterMark();

      underTest.expire();
      assertThat(underTest.needsRefresh(System.currentTimeMillis())).isTrue();
    }
  }
}
