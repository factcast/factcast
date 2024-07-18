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
package org.factcast.store.registry.transformation;

import static org.junit.jupiter.api.Assertions.*;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import org.factcast.store.registry.transformation.cache.TransformationCache;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransformationCacheCompactorTest {
  public static final int RETENTION_DAYS = 21;
  @Mock TransformationCache cache;

  TransformationCacheCompactor uut;

  @BeforeEach
  void setup() {
    uut = new TransformationCacheCompactor(cache, RETENTION_DAYS);
  }

  @Test
  void compact() {
    ArgumentCaptor<ZonedDateTime> cap = ArgumentCaptor.forClass(ZonedDateTime.class);
    Mockito.doNothing().when(cache).compact(cap.capture());

    uut.compact();

    org.assertj.core.api.Assertions.assertThat(cap.getValue().truncatedTo(ChronoUnit.DAYS))
        .isNotNull()
        .isEqualTo(ZonedDateTime.now().minusDays(RETENTION_DAYS).truncatedTo(ChronoUnit.DAYS));
  }
}
