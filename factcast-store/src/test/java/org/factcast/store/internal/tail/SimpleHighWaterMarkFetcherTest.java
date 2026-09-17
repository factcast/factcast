/*
 * Copyright © 2017-2026 factcast.org
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
import static org.mockito.Mockito.when;

import java.util.UUID;
import javax.sql.DataSource;
import org.factcast.core.subscription.observer.HighWaterMark;
import org.factcast.store.internal.checkpoint.FactStreamCheckpoint;
import org.factcast.store.internal.checkpoint.FactStreamCheckpointProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SimpleHighWaterMarkFetcherTest {

  @Mock private DataSource dataSource;
  @Mock private FactStreamCheckpointProvider checkpointProvider;
  @InjectMocks private SimpleHighWaterMarkFetcher underTest;

  @Test
  void readsPersistedCheckpointFromRequestedDataSource() {
    HighWaterMark expected = HighWaterMark.of(UUID.randomUUID(), 42);
    when(checkpointProvider.read(dataSource)).thenReturn(new FactStreamCheckpoint(expected, 7));

    assertThat(underTest.highWaterMark(dataSource)).isSameAs(expected);
  }
}
