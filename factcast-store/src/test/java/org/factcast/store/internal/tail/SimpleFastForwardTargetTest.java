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
import org.factcast.core.subscription.observer.HighWaterMark;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.*;

@ExtendWith(MockitoExtension.class)
public class SimpleFastForwardTargetTest {

  @Mock private JdbcTemplate jdbc;
  @Mock private HighWaterMark target;
  @InjectMocks private SimpleFastForwardTarget underTest;

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

      assertThat(uut.highWaterMark()).isNotSameAs(highWaterMark);
      highWaterMark = uut.highWaterMark();
      assertThat(highWaterMark.targetId()).isEqualTo(id);
      assertThat(highWaterMark.targetSer()).isEqualTo(ser + 1);
    }
  }
}
