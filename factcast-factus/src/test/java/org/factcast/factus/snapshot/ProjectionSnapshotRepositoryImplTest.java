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
package org.factcast.factus.snapshot;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotCache;
import org.factcast.core.snap.SnapshotId;
import org.factcast.factus.metrics.FactusMetrics;
import org.factcast.factus.projection.SnapshotProjection;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.factcast.factus.serializer.SnapshotSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectionSnapshotRepositoryImplTest {

  @Mock private SnapshotSerializer serializer;

  @Mock private SnapshotSerializerSupplier serializerSupplier;

  @Mock private SnapshotCache snapshotCache;

  @Mock private Map<Class<?>, Long> serials;

  @Mock private FactusMetrics factusMetrics;

  @InjectMocks private ProjectionSnapshotRepositoryImpl underTest;

  @Nested
  class WhenFindingLatest {

    @Captor ArgumentCaptor<SnapshotId> idCaptor;

    @BeforeEach
    void setup() {
      when(serializerSupplier.retrieveSerializer(any())).thenReturn(serializer);
    }

    @SuppressWarnings("unchecked")
    @Test
    void findLatest_exists() {
      // INIT
      SnapshotId id = mock(SnapshotId.class);
      UUID lastFact = UUID.randomUUID();
      byte[] bytes = "foo".getBytes();

      when(snapshotCache.getSnapshot(any()))
          .thenReturn(Optional.of(new Snapshot(id, lastFact, bytes, true)));

      when(serializer.getId()).thenReturn("hubba");

      // RUN
      Optional<Snapshot> latest = underTest.findLatest(SomeSnapshotProjection.class);

      // ASSERT
      assertThat(latest)
          .isPresent()
          .get()
          .extracting(Snapshot::lastFact, Snapshot::bytes, Snapshot::compressed)
          .containsExactly(lastFact, bytes, true);

      assertThat(latest.get().id()).isNotNull();

      verify(snapshotCache).getSnapshot(idCaptor.capture());

      assertThat(idCaptor.getValue()).isNotNull();

      assertThat(idCaptor.getValue().key()).contains("_1_").endsWith("_hubba");
    }

    @Test
    void findLatest_cachesSerialUId() {
      // INIT
      SnapshotId id = mock(SnapshotId.class);
      UUID lastFact = UUID.randomUUID();
      byte[] bytes = "foo".getBytes();

      when(snapshotCache.getSnapshot(any()))
          .thenReturn(Optional.of(new Snapshot(id, lastFact, bytes, true)));

      when(serializer.getId()).thenReturn("oink");

      // RUN
      underTest.findLatest(SomeSnapshotProjection.class);
      underTest.findLatest(SomeSnapshotProjection.class);

      // ASSERT
      verify(snapshotCache, times(2)).getSnapshot(idCaptor.capture());

      assertThat(idCaptor.getAllValues()).hasSize(2);

      idCaptor.getAllValues().stream()
          .map(SnapshotId::key)
          .forEach(
              key -> {
                assertThat(key).contains("_1_").endsWith("_oink");
              });
    }

    @Test
    void findLatest_doesNotExist() {
      // INIT
      when(serializer.getId()).thenReturn("oink");
      when(snapshotCache.getSnapshot(any())).thenReturn(Optional.empty());
      // RUN
      Optional<Snapshot> latest = underTest.findLatest(SomeSnapshotProjection.class);

      // ASSERT
      assertThat(latest).isEmpty();
    }
  }

  @Nested
  class WhenPutting {
    private final UUID STATE = UUID.randomUUID();

    @Spy private SnapshotProjection projection = new SomeSnapshotProjection();

    @Captor private ArgumentCaptor<Snapshot> snapshotCaptor;

    @Test
    void put() {
      // INIT
      when(serializer.getId()).thenReturn("oink");
      when(serializerSupplier.retrieveSerializer(any())).thenReturn(serializer);

      when(serializer.serialize(projection)).thenReturn("foo".getBytes());
      when(serializer.includesCompression()).thenReturn(true);

      // RUN
      CompletableFuture<Void> result = underTest.put(projection, STATE);

      // ASSERT
      assertThat(result).succeedsWithin(Duration.ofSeconds(5));

      verify(serializerSupplier).retrieveSerializer(any());

      verify(snapshotCache).setSnapshot(snapshotCaptor.capture());

      assertThat(snapshotCaptor.getValue())
          .isNotNull()
          .extracting(Snapshot::lastFact, Snapshot::bytes, Snapshot::compressed)
          .containsExactly(STATE, "foo".getBytes(), true);
    }
  }

  @ProjectionMetaData(revision = 1)
  static class SomeSnapshotProjection implements SnapshotProjection {}
}
