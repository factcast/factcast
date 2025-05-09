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
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;
import lombok.NonNull;
import org.assertj.core.api.Assertions;
import org.factcast.factus.metrics.FactusMetrics;
import org.factcast.factus.projection.*;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.factcast.factus.serializer.SnapshotSerializer;
import org.factcast.factus.serializer.SnapshotSerializerId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AggregateRepositoryTest {

  @Mock SnapshotCache snapshotCache;
  @Mock SnapshotSerializer ser;
  @Mock SnapshotSerializerSelector snapshotSerializerSelector;
  @Mock FactusMetrics factusMetrics;
  @InjectMocks AggregateRepository underTest;

  @Nested
  class WhenFinding {
    SnapshotIdentifier id = SnapshotIdentifier.of(Aggregate.class, UUID.randomUUID());

    @Test
    void returnsEmptyWhenDeserializationFails() {
      Mockito.when(snapshotCache.find(Mockito.any()))
          .thenReturn(
              Optional.of(
                  new SnapshotData(
                      new byte[0], SnapshotSerializerId.of("guess"), UUID.randomUUID())));
      Mockito.when(snapshotSerializerSelector.selectSeralizerFor(Mockito.any()))
          .thenReturn(new FailingSerializer());
      Assertions.assertThat(underTest.findAndDeserialize(Aggregate.class, id)).isEmpty();
    }
  }

  @Test
  void store() {
    // INIT
    UUID state = UUID.randomUUID();
    UUID aggId = UUID.randomUUID();
    Aggregate a = new Aggregate(aggId) {};

    when(snapshotSerializerSelector.selectSeralizerFor(any())).thenReturn(ser);
    when(ser.serialize(a)).thenReturn(new byte[0]);
    SnapshotSerializerId foo = SnapshotSerializerId.of("foo");
    when(ser.id()).thenReturn(foo);
    // RUN
    underTest.store(a, state);

    // ASSERT
    verify(snapshotCache)
        .store(
            Mockito.argThat(
                i -> aggId.equals(i.aggregateId()) && i.projectionClass() == a.getClass()),
            ArgumentMatchers.argThat(
                sd -> state.equals(sd.lastFactId()) && foo.equals(sd.snapshotSerializerId())));
  }

  @Nested
  class WhenFindingLatest {
    private final WithAnnotation aggregate = spy(new WithAnnotation());

    private final UUID aggregateId = UUID.randomUUID();

    @Test
    void findNone() {
      Assertions.assertThat(underTest.findLatest(WithAnnotation.class, aggregateId)).isEmpty();
    }

    @Test
    void findOne_givenSVUID() {
      // INIT
      SnapshotSerializerId testId = SnapshotSerializerId.of("test");
      UUID last = UUID.randomUUID();
      SnapshotData withSVUID = new SnapshotData(new byte[0], testId, last);

      @NonNull
      SnapshotIdentifier snapIdent = SnapshotIdentifier.of(WithAnnotation.class, aggregateId);
      when(snapshotSerializerSelector.selectSeralizerFor(any())).thenReturn(ser);
      when(snapshotCache.find(snapIdent)).thenReturn(Optional.of(withSVUID));
      when(ser.deserialize(eq(WithAnnotation.class), any(byte[].class))).thenReturn(aggregate);

      // RUN
      @NonNull
      Optional<ProjectionAndState<WithAnnotation>> result =
          underTest.findLatest(WithAnnotation.class, aggregateId);

      // ASSERT
      verify(aggregate).onAfterRestore();
      assertThat(result).isPresent();
      assertThat(result.map(ProjectionAndState::lastFactIdApplied)).hasValue(last);
      assertThat(result.map(ProjectionAndState::projectionInstance))
          .get()
          .isInstanceOf(WithAnnotation.class);
    }
  }

  @Nested
  class WhenStoring {
    private final UUID state = UUID.randomUUID();

    private final WithAnnotation aggregate = spy(new WithAnnotation());

    @Captor private ArgumentCaptor<SnapshotData> snapshotCaptor;

    @Test
    void store() {
      // INIT
      when(snapshotSerializerSelector.selectSeralizerFor(any())).thenReturn(ser);

      when(ser.serialize(aggregate)).thenReturn("foo".getBytes());
      SnapshotSerializerId serId = SnapshotSerializerId.of("narf");
      when(ser.id()).thenReturn(serId);

      UUID aggId = UUID.randomUUID();
      AggregateUtil.aggregateId(aggregate, aggId);
      SnapshotIdentifier id = SnapshotIdentifier.of(WithAnnotation.class, aggId);

      // RUN
      underTest.store(aggregate, state);

      verify(snapshotCache).store(eq(id), snapshotCaptor.capture());
      verify(aggregate).onBeforeSnapshot();
      assertThat(snapshotCaptor.getValue())
          .extracting(
              SnapshotData::snapshotSerializerId,
              SnapshotData::serializedProjection,
              SnapshotData::lastFactId)
          .containsExactly(serId, "foo".getBytes(), state);
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
  public static class WithAnnotation extends Aggregate {}
}
