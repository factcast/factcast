/*
 * Copyright © 2017-2024 factcast.org
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
package org.factcast.core;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FactStreamPositionTest {

  private static final UUID FACT_ID = UUID.randomUUID();
  private static final long SERIAL = 40;
  private Fact f;

  @Nested
  class WhenFroming {

    @Test
    void rejectsNull() {
      assertThatThrownBy(
              () -> {
                FactStreamPosition.from(null);
              })
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void happyPath() {
      long ser = 43L;
      UUID id = UUID.randomUUID();
      f = Fact.builder().id(id).serial(ser).buildWithoutPayload();
      FactStreamPosition actual = FactStreamPosition.from(f);

      Assertions.assertThat(actual.factId()).isSameAs(id);
      Assertions.assertThat(actual.serial()).isSameAs(ser);
    }
  }

  @SuppressWarnings("deprecation")
  @Nested
  class WhenWithoutingSerial {

    @Test
    void fromFactNoSerial() {
      UUID id = UUID.randomUUID();
      f = Fact.builder().id(id).buildWithoutPayload();
      FactStreamPosition actual = FactStreamPosition.from(f);

      Assertions.assertThat(actual.factId()).isSameAs(id);
      Assertions.assertThat(actual.serial()).isSameAs(-1L);
    }

    @Test
    void wihoutSerial() {
      UUID id = UUID.randomUUID();
      FactStreamPosition actual = verify(FactStreamPosition.withoutSerial(id));

      Assertions.assertThat(actual.factId()).isSameAs(id);
      Assertions.assertThat(actual.serial()).isSameAs(-1L);
    }
  }
}
