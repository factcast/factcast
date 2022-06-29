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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import org.assertj.core.util.Lists;
import org.factcast.test.Slf4jHelper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import slf4jtest.LogLevel;
import slf4jtest.TestLogger;

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

      TestLogger logger = Slf4jHelper.replaceLogger(underTest);
      underTest.checkIndexes();

      logger.contains("Detected invalid index: idx1");
      logger.contains("Detected invalid index: idx2");
      assertThat(logger.lines().size()).isEqualTo(4);
      assertThat(
              logger.lines().stream()
                  .filter(l -> l.level != LogLevel.DebugLevel)
                  .allMatch(l -> l.level == LogLevel.WarnLevel))
          .isTrue();
    }

    @Test
    void doesNotLogSilentIfNoInvalidIndexesFound() {

      when(jdbc.queryForList(any(), same(String.class))).thenReturn(Collections.emptyList());

      TestLogger logger = Slf4jHelper.replaceLogger(underTest);
      underTest.checkIndexes();

      assertThat(logger.lines().size()).isEqualTo(2);
      assertThat(logger.lines().stream().filter(l -> l.level != LogLevel.DebugLevel)).isEmpty();
    }
  }
}
