package org.factcast.core.snap.mongo;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.BsonBinarySubType;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.Binary;
import org.factcast.factus.projection.SnapshotProjection;
import org.factcast.factus.serializer.SnapshotSerializerId;
import org.factcast.factus.snapshot.SnapshotData;
import org.factcast.factus.snapshot.SnapshotIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MongoDbSnapshotCacheTest {

    @Mock
    private MongoClient mongoClient;
    @Mock
    private MongoDatabase mongoDatabase;
    @Mock
    private MongoCollection<Document> collection;

    private MongoDbSnapshotCache underTest;

    private final SnapshotIdentifier id = SnapshotIdentifier.of(SnapshotProjection.class);
    private final SnapshotSerializerId serId = SnapshotSerializerId.of("buh");

    @BeforeEach
    void setUp() {
        MongoDbSnapshotProperties props = new MongoDbSnapshotProperties();
        when(mongoClient.getDatabase("db"))
                .thenReturn(mongoDatabase);
        when(mongoDatabase.getCollection("factus_snapshot"))
                .thenReturn(collection);
        underTest = new MongoDbSnapshotCache(mongoClient, "db", props);

        verify(collection, times(2)).createIndex(any(Bson.class), any());
        verify(collection, times(1)).createIndex(any(Bson.class));
    }

    @Nested
    class WhenGettingSnapshot {
        @Captor
        private ArgumentCaptor<Document> documentCaptor;

        @Test
        void happyCase() {
            FindIterable<Document> findIterable = mock(FindIterable.class);
            UUID lastFactId = UUID.randomUUID();
            Document result = new Document()
                    .append("projectionClass", id.projectionClass().getName())
                    .append("aggregateId", id.aggregateId() != null ? id.aggregateId().toString() : null)
                    .append("snapshotSerializerId", serId.name())
                    .append("serializedProjection", new org.bson.types.Binary(BsonBinarySubType.BINARY, "foo".getBytes()))
                    .append("lastFactId", lastFactId.toString());

            when(collection.find(documentCaptor.capture())).thenReturn(findIterable);
            when(findIterable.first()).thenReturn(result);

            Optional<SnapshotData> found = underTest.find(id);

            assertThat(found).isPresent();
            assertThat(found.get().serializedProjection()).isEqualTo("foo".getBytes());
            assertThat(found.get().snapshotSerializerId()).isEqualTo(serId);
            assertThat(found.get().lastFactId()).isEqualTo(lastFactId);

            verify(collection).updateOne(eq(documentCaptor.getValue()), any(Bson.class));
        }

        @Test
        void shouldReturnEmptyOptionalWhenNoSnapshotFound() {
            FindIterable<Document> findIterable = mock(FindIterable.class);

            when(collection.find(documentCaptor.capture())).thenReturn(findIterable);
            when(findIterable.first()).thenReturn(null);

            Optional<SnapshotData> found = underTest.find(id);

            assertThat(found).isEmpty();
        }
    }

    @Nested
    class WhenStoringSnapshot {

        @Captor
        private ArgumentCaptor<Document> keyCaptor;
        @Captor
        private ArgumentCaptor<Document> documentCaptor;

        @Test
        void happyCase() {
            final SnapshotData snap = new SnapshotData("foo".getBytes(), serId, UUID.randomUUID());
            Instant expectedExpireAt = Instant.now().plus(90, ChronoUnit.DAYS);

            underTest.store(id, snap);

            verify(collection).replaceOne(keyCaptor.capture(), documentCaptor.capture(), any(ReplaceOptions.class));
            Document keyDocument = keyCaptor.getValue();
            Document storedDocument = documentCaptor.getValue();

            // Validate key document
            assertThat(keyDocument.getString("projectionClass")).isEqualTo(id.projectionClass().getName());
            assertThat(keyDocument.getString("aggregateId")).isEqualTo(id.aggregateId() != null ? id.aggregateId().toString() : null);

            // Validate stored document
            assertThat(storedDocument.getString("projectionClass")).isEqualTo(id.projectionClass().getName());
            assertThat(storedDocument.getString("snapshotSerializerId")).isEqualTo(serId.name());
            assertThat(storedDocument.get("serializedProjection", Binary.class).getData()).isEqualTo("foo".getBytes());
            assertThat(storedDocument.getString("lastFactId")).isEqualTo(snap.lastFactId().toString());
            assertThat(storedDocument.get("expireAt", Instant.class)).isCloseTo(expectedExpireAt, within(1, ChronoUnit.SECONDS));
        }

        @Test
        void overwriteExistingSnapshot() {
            final SnapshotData snap1 = new SnapshotData("foo".getBytes(), serId, UUID.randomUUID());
            final SnapshotData snap2 = new SnapshotData("bar".getBytes(), serId, UUID.randomUUID());
            Instant expectedExpireAt = Instant.now().plus(90, ChronoUnit.DAYS);

            underTest.store(id, snap1);
            underTest.store(id, snap2);

            verify(collection, times(2)).replaceOne(keyCaptor.capture(), documentCaptor.capture(), any(ReplaceOptions.class));

            // Validate key document
            Document keyDocument = keyCaptor.getValue();
            assertThat(keyDocument.getString("projectionClass")).isEqualTo(id.projectionClass().getName());
            assertThat(keyDocument.getString("aggregateId")).isEqualTo(id.aggregateId() != null ? id.aggregateId().toString() : null);

            // Validate stored document
            Document storedDocument = documentCaptor.getValue();
            assertThat(storedDocument.getString("projectionClass")).isEqualTo(id.projectionClass().getName());
            assertThat(storedDocument.getString("snapshotSerializerId")).isEqualTo(serId.name());
            assertThat(storedDocument.get("serializedProjection", Binary.class).getData()).isEqualTo("bar".getBytes());
            assertThat(storedDocument.getString("lastFactId")).isEqualTo(snap2.lastFactId().toString());
            assertThat(storedDocument.get("expireAt", Instant.class)).isCloseTo(expectedExpireAt, within(1, ChronoUnit.SECONDS));
        }

        @Test
        void storeSnapshotWithoutAggregateId() {
            SnapshotIdentifier idWithoutAggregate = SnapshotIdentifier.of(SnapshotProjection.class);
            final SnapshotData snap = new SnapshotData("foo".getBytes(), serId, UUID.randomUUID());
            Instant expectedExpireAt = Instant.now().plus(90, ChronoUnit.DAYS);

            underTest.store(idWithoutAggregate, snap);

            verify(collection).replaceOne(keyCaptor.capture(), documentCaptor.capture(), any(ReplaceOptions.class));
            Document keyDocument = keyCaptor.getValue();
            Document storedDocument = documentCaptor.getValue();

            // Validate key document
            assertThat(keyDocument.getString("projectionClass")).isEqualTo(idWithoutAggregate.projectionClass().getName());
            assertThat(keyDocument.containsKey("aggregateId")).isFalse();

            // Validate stored document
            assertThat(storedDocument.getString("projectionClass")).isEqualTo(idWithoutAggregate.projectionClass().getName());
            assertThat(storedDocument.getString("snapshotSerializerId")).isEqualTo(serId.name());
            assertThat(storedDocument.get("serializedProjection", Binary.class).getData()).isEqualTo("foo".getBytes());
            assertThat(storedDocument.getString("lastFactId")).isEqualTo(snap.lastFactId().toString());
            assertThat(storedDocument.get("expireAt", Instant.class)).isCloseTo(expectedExpireAt, within(1, ChronoUnit.SECONDS));
        }

    }

    @Nested
    class WhenRemovingSnapshot {

        @Captor
        private ArgumentCaptor<Document> keyCaptor;

        @Test
        void happyCase() {
            underTest.remove(id);

            verify(collection).deleteOne(keyCaptor.capture());
            Document capturedKeyDocument = keyCaptor.getValue();

            // Validate captured key document
            assertThat(capturedKeyDocument.getString("projectionClass")).isEqualTo(id.projectionClass().getName());
            assertThat(capturedKeyDocument.getString("aggregateId")).isEqualTo(id.aggregateId() != null ? id.aggregateId().toString() : null);
        }

        @Test
        void removeSnapshotWithoutAggregateId() {
            SnapshotIdentifier idWithoutAggregate = SnapshotIdentifier.of(SnapshotProjection.class);

            underTest.remove(idWithoutAggregate);

            verify(collection).deleteOne(keyCaptor.capture());
            Document capturedKeyDocument = keyCaptor.getValue();

            // Validate captured key document
            assertThat(capturedKeyDocument.getString("projectionClass")).isEqualTo(idWithoutAggregate.projectionClass().getName());
            assertThat(capturedKeyDocument.containsKey("aggregateId")).isFalse();
        }
    }
}