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
package org.factcast.store.registry.transformation.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.sql.ResultSet;
import lombok.SneakyThrows;
import org.factcast.core.Fact;
import org.factcast.core.TestFact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FactRowMapperTest {
  @InjectMocks private FactRowMapper underTest;

  @Nested
  class WhenMapingRow {
    private final int ROW_NUM = 7;
    @Mock private ResultSet rs;

    @BeforeEach
    void setup() {}

    @SneakyThrows
    @Test
    void usesHeaderAndPayload() {
      TestFact f = new TestFact().jsonPayload("{\"a\":1}");
      when(rs.getString("header")).thenReturn(f.jsonHeader());
      when(rs.getString("payload")).thenReturn(f.jsonPayload());

      Fact r = new FactRowMapper().mapRow(rs, 1);

      assertThat(r.jsonHeader()).isEqualTo(f.jsonHeader());
      assertThat(r.jsonPayload()).isEqualTo(f.jsonPayload());
    }
  }
}
