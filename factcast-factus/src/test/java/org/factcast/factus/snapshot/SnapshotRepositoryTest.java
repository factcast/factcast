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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import lombok.NonNull;
import org.assertj.core.api.Assertions;
import org.factcast.factus.metrics.FactusMetrics;
import org.factcast.factus.projection.SnapshotProjection;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.factcast.factus.serializer.SnapshotSerializer;
import org.factcast.factus.serializer.SnapshotSerializerId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SnapshotRepositoryTest {

  @Mock SnapshotCache snapshotCache;
  @Mock SnapshotSerializer ser;
  @Mock SnapshotSerializerSelector snapshotSerializerSelector;
  @Mock FactusMetrics factusMetrics;
  @InjectMocks SnapshotRepository underTest;

  @Test
  void store() {
    // INIT
    UUID state = UUID.randomUUID();
    UUID aggId = UUID.randomUUID();
    SomeSnapshotProjection projection = new SomeSnapshotProjection();

    when(snapshotSerializerSelector.selectSeralizerFor(any())).thenReturn(ser);
    when(ser.serialize(projection)).thenReturn(new byte[0]);
    SnapshotSerializerId foo = SnapshotSerializerId.of("foo");
    when(ser.id()).thenReturn(foo);
    // RUN
    underTest.store(projection, state);

    // ASSERT
    Mockito.verify(snapshotCache)
        .store(
            Mockito.argThat(
                i -> i.aggregateId() == null && i.projectionClass() == projection.getClass()),
            ArgumentMatchers.argThat(
                sd -> state.equals(sd.lastFactId()) && foo.equals(sd.snapshotSerializerId())));
  }

  @Nested
  class WhenFindingLatest {

    @Captor ArgumentCaptor<SnapshotIdentifier> idCaptor;

    private final UUID aggregateId = UUID.randomUUID();

    @BeforeEach
    void setup() {}

    @Test
    void findNone() {
      Assertions.assertThat(underTest.findLatest(SomeSnapshotProjection.class)).isEmpty();
    }

    @Test
    void findOne_givenSVUID() {
      // INIT
      SnapshotSerializerId testId = SnapshotSerializerId.of("test");
      UUID last = UUID.randomUUID();
      SnapshotData withSVUID = new SnapshotData(new byte[0], testId, last);

      @NonNull SnapshotIdentifier snapIdent = SnapshotIdentifier.of(SomeSnapshotProjection.class);
      when(snapshotSerializerSelector.selectSeralizerFor(any())).thenReturn(ser);
      when(snapshotCache.find(snapIdent)).thenReturn(Optional.of(withSVUID));
      when(ser.deserialize(eq(SomeSnapshotProjection.class), any(byte[].class)))
          .thenReturn(new SomeSnapshotProjection());

      // RUN
      @NonNull
      Optional<ProjectionAndState<SomeSnapshotProjection>> result =
          underTest.findLatest(SomeSnapshotProjection.class);

      // ASSERT
      assertThat(result).isPresent();
      Assertions.assertThat(result.map(ProjectionAndState::lastFactIdApplied)).hasValue(last);
      Assertions.assertThat(result.map(ProjectionAndState::projectionInstance))
          .get()
          .isInstanceOf(SomeSnapshotProjection.class);
    }
  }

  @Nested
  class WhenStoring {
    private final UUID STATE = UUID.randomUUID();

    private final SomeSnapshotProjection aggregate = new SomeSnapshotProjection();

    @Captor private ArgumentCaptor<SnapshotData> snapshotCaptor;

    @Test
    void store() {
      // INIT
      when(snapshotSerializerSelector.selectSeralizerFor(any())).thenReturn(ser);

      when(ser.serialize(aggregate)).thenReturn("foo".getBytes());
      SnapshotSerializerId serId = SnapshotSerializerId.of("narf");
      when(ser.id()).thenReturn(serId);

      UUID aggId = UUID.randomUUID();
      SnapshotIdentifier id = SnapshotIdentifier.of(SomeSnapshotProjection.class);

      // RUN
      underTest.store(aggregate, STATE);

      verify(snapshotCache).store(eq(id), snapshotCaptor.capture());

      assertThat(snapshotCaptor.getValue())
          .extracting(
              SnapshotData::snapshotSerializerId,
              SnapshotData::serializedProjection,
              SnapshotData::lastFactId)
          .containsExactly(serId, "foo".getBytes(), STATE);
    }
  }

  @Nested
  class WhenRemoving {
    @Mock SnapshotIdentifier id;

    @Test
    void happyPath() {
      underTest.remove(id);

      verify(snapshotCache).remove(id);
    }
  }

  @ProjectionMetaData(revision = 43)
  public static class SomeSnapshotProjection implements SnapshotProjection {}
}
