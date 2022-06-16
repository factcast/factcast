/*
 * Copyright © 2017-2022 factcast.org
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
package org.factcast.store.internal.check;

import java.util.Collections;
import nl.altindag.log.LogCaptor;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import slf4jtest.LogLevel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IndexCheckTest {
  @Mock private JdbcTemplate jdbc;
  @InjectMocks private IndexCheck underTest;

  @Nested
  class WhenCheckingIndexes {
    @BeforeEach
    void setup() {}

    @Test
    void logsGivenIdexes() {

      when(jdbc.queryForList(any(), same(String.class)))
          .thenReturn(Lists.newArrayList("idx1", "idx2"));

      LogCaptor logCaptor = LogCaptor.forClass(underTest.getClass());
      underTest.checkIndexes();

      assertThat(logCaptor.getLogs()).contains("Detected invalid index: idx1");
      assertThat(logCaptor.getLogs()).contains("Detected invalid index: idx2");
      assertThat(logCaptor.getLogs().size()).isEqualTo(4);
      assertThat(
              logCaptor.getLogEvents().stream()
                  .filter(l -> l.getLevel() != LogLevel.DebugLevel.toString())
                  .allMatch(l -> l.getLevel() == LogLevel.WarnLevel.toString()))
          .isTrue();
    }

    @Test
    void doesNotLogSilentIfNoInvalidIndexesFound() {

      when(jdbc.queryForList(any(), same(String.class))).thenReturn(Collections.emptyList());

      LogCaptor logCaptor = LogCaptor.forClass(underTest.getClass());
      underTest.checkIndexes();

      assertThat(logCaptor.getLogs().size()).isEqualTo(2);
      assertThat(
              logCaptor.getLogEvents().stream()
                  .filter(l -> l.getLevel() != LogLevel.DebugLevel.toString()))
          .isEmpty();
    }
  }
}
