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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotCache;
import org.factcast.core.snap.SnapshotId;
import org.factcast.factus.metrics.FactusMetrics;
import org.factcast.factus.projection.Aggregate;
import org.factcast.factus.projection.AggregateUtil;
import org.factcast.factus.serializer.OtherSnapSer;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.factcast.factus.serializer.SnapshotSerializer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AggregateSnapshotRepositoryImplTest {

  @Mock private SnapshotCache snap;

  @Mock private SnapshotSerializerSupplier snapshotSerializerSupplier;

  @Mock private SnapshotSerializer snapshotSerializer;
  @Mock private FactusMetrics factusMetrics;

  @InjectMocks private AggregateSnapshotRepositoryImpl underTest;

  @Nested
  class WhenFindingLatest {

    @Captor ArgumentCaptor<SnapshotId> idCaptor;

    private final UUID aggregateId = UUID.randomUUID();

    @BeforeEach
    void setup() {
      when(snapshotSerializerSupplier.retrieveSerializer(any())).thenReturn(snapshotSerializer);
    }

    @Test
    void findNone() {
      Optional<Snapshot> result = underTest.findLatest(WithSVUID.class, aggregateId);

      assertThat(result).isEmpty();
    }

    @Test
    void findOne_givenSVUID() {
      // INIT
      Snapshot withSVUID =
          new Snapshot(
              new SnapshotId("some key", aggregateId), UUID.randomUUID(), new byte[0], false);

      when(snap.getSnapshot(any())).thenReturn(Optional.of(withSVUID));
      when(snapshotSerializer.getId()).thenReturn("narf");

      // RUN
      Optional<Snapshot> result = underTest.findLatest(WithSVUID.class, aggregateId);

      // ASSERT
      assertThat(result).isPresent().get().extracting("id.uuid").isEqualTo(aggregateId);

      verify(snap).getSnapshot(idCaptor.capture());

      assertThat(idCaptor.getValue()).isNotNull();

      // never calculate hash, as it is given
      verify(snapshotSerializer, never()).calculateProjectionSerial(any());

      assertThat(idCaptor.getValue().key())
          .contains(":narf")
          // use value from serialVersionUID, not what serializer
          // would calculate
          .endsWith(":42");
    }

    @Test
    void findOne_calculatedSVUID() {
      // INIT
      Snapshot withoutSVUID =
          new Snapshot(
              new SnapshotId("some key", aggregateId), UUID.randomUUID(), new byte[0], false);

      when(snap.getSnapshot(any())).thenReturn(Optional.of(withoutSVUID));

      when(snapshotSerializer.calculateProjectionSerial(WithoutSVUID.class))
          // let's assume this is the serial id computed by the
          // serialiser; will not be used as we used a class with
          // serialVersionUID field
          .thenReturn(500L);
      when(snapshotSerializer.getId()).thenReturn("poit");

      // RUN
      Optional<Snapshot> result = underTest.findLatest(WithoutSVUID.class, aggregateId);

      // ASSERT
      assertThat(result).isPresent().get().extracting("id.uuid").isEqualTo(aggregateId);

      verify(snap).getSnapshot(idCaptor.capture());

      assertThat(idCaptor.getValue()).isNotNull();

      assertThat(idCaptor.getValue().key())
          .contains(":poit")
          // use value from serialVersionUID, not what serializer
          // would calculate
          .endsWith(":500");
    }

    @Test
    void findOne_calculatedSVUID_useCache() {
      // INIT
      Snapshot withoutSVUID =
          new Snapshot(
              new SnapshotId("some key", aggregateId), UUID.randomUUID(), new byte[0], false);

      when(snap.getSnapshot(any())).thenReturn(Optional.of(withoutSVUID));

      when(snapshotSerializer.calculateProjectionSerial(WithoutSVUID.class))
          // let's assume this is the serial id computed by the
          // serialiser; will not be used as we used a class with
          // serialVersionUID field
          .thenReturn(500L);

      when(snapshotSerializer.getId()).thenReturn("zort");

      // RUN
      underTest.findLatest(WithoutSVUID.class, aggregateId);
      underTest.findLatest(WithoutSVUID.class, aggregateId);

      // ASSERT
      verify(snap, times(2)).getSnapshot(idCaptor.capture());

      assertThat(idCaptor.getAllValues()).hasSize(2);

      idCaptor.getAllValues().stream()
          .map(SnapshotId::key)
          .forEach(
              key -> {
                assertThat(key).contains(":zort").endsWith(":500");
              });
    }
  }

  @Nested
  class WhenGettingSerialVersionUid {

    @Test
    void retrievesExistingSVUID() {
      assertEquals(Long.valueOf(42), underTest.getSerialVersionUid(WithSVUID.class, () -> null));
      assertEquals(
          Long.valueOf(43), underTest.getSerialVersionUid(WithAnnotation.class, () -> null));
      assertNull(underTest.getSerialVersionUid(WithoutSVUID.class, OtherSnapSer::new));
    }
  }

  @Nested
  class WhenCreatingKey {

    @Test
    void createsKeyIncludingSerialVersionUid() {

      when(snapshotSerializerSupplier.retrieveSerializer(any())).thenReturn(snapshotSerializer);

      String with =
          underTest.createKeyForType(
              WithSVUID.class,
              () -> snapshotSerializerSupplier.retrieveSerializer(WithSVUID.class));
      String without =
          underTest.createKeyForType(
              WithoutSVUID.class,
              () -> snapshotSerializerSupplier.retrieveSerializer(WithoutSVUID.class));

      assertThat(with).contains(WithSVUID.class.getCanonicalName()).contains(":42");
      assertThat(without).contains(WithoutSVUID.class.getCanonicalName()).contains(":0");
    }
  }

  @Nested
  class WhenPutting {
    private final UUID STATE = UUID.randomUUID();

    private final WithoutSVUID aggregate = new WithoutSVUID();

    @Captor private ArgumentCaptor<Snapshot> snapshotCaptor;

    @Test
    void put() {
      // INIT
      when(snapshotSerializerSupplier.retrieveSerializer(any())).thenReturn(snapshotSerializer);

      when(snapshotSerializer.serialize(aggregate)).thenReturn("foo".getBytes());
      when(snapshotSerializer.includesCompression()).thenReturn(true);

      AggregateUtil.aggregateId(aggregate, UUID.randomUUID());

      // RUN
      CompletableFuture<Void> result = underTest.put(aggregate, STATE);

      // ASSERT
      assertThat(result).succeedsWithin(Duration.ofSeconds(5));

      verify(snapshotSerializerSupplier).retrieveSerializer(any());

      verify(snap).setSnapshot(snapshotCaptor.capture());

      assertThat(snapshotCaptor.getValue())
          .isNotNull()
          .extracting(Snapshot::lastFact, Snapshot::bytes, Snapshot::compressed)
          .containsExactly(STATE, "foo".getBytes(), true);
    }
  }

  public static class WithSVUID extends Aggregate {
    private static final long serialVersionUID = 42L;
  }

  @ProjectionMetaData(serial = 43)
  public static class WithAnnotation extends Aggregate {
    private static final long serialVersionUID = 42L;
  }

  public static class WithoutSVUID extends Aggregate {}
}
