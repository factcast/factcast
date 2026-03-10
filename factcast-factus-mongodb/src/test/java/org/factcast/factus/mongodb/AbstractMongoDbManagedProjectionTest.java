/*
 * Copyright © 2017-2025 factcast.org
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
package org.factcast.factus.mongodb;

import static org.assertj.core.api.Assertions.*;
import static org.factcast.factus.mongodb.AbstractMongoDbProjection.MAX_LEASE_DURATION_SECONDS;
import static org.factcast.factus.mongodb.AbstractMongoDbProjection.MIN_LEASE_DURATION_SECONDS;
import static org.mockito.Mockito.*;

import com.mongodb.client.*;
import com.mongodb.client.model.ReplaceOptions;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lombok.NonNull;
import lombok.SneakyThrows;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.bson.Document;
import org.factcast.core.FactStreamPosition;
import org.factcast.factus.projection.WriterToken;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AbstractMongoDbManagedProjectionTest {
  static final String SCOPED_NAME =
      "org.factcast.factus.mongodb.AbstractMongoDbManagedProjectionTest$TestProjection_1";

  @Mock MongoDatabase mongoDatabase;
  @Mock MongoCollection<Document> stateTable;
  @Mock MongoCollection<Document> lockTable;

  TestProjection uut;

  @BeforeEach
  void setUp() {
    when(mongoDatabase.getCollection(AbstractMongoDbManagedProjection.STATE_COLLECTION))
        .thenReturn(stateTable);
    when(mongoDatabase.getCollection(AbstractMongoDbManagedProjection.LOCK_COLLECTION))
        .thenReturn(lockTable);
    uut = new TestProjection(mongoDatabase);
  }

  @Nested
  class WhenInspectingClass {
    @Test
    void getProjectionKey() {
      assertThat(uut.projectionKey()).isEqualTo(SCOPED_NAME);
    }
  }

  @Nested
  class WhenOperatingOnState {
    @Captor private ArgumentCaptor<Document> filterCaptor;
    @Captor private ArgumentCaptor<Document> stateUpdateCaptor;
    @Captor private ArgumentCaptor<ReplaceOptions> optionsCaptor;
    @Mock private FindIterable<Document> findIterable;

    @Test
    void returnsFactStreamPositionIfSet() {
      final UUID factId = UUID.randomUUID();
      final Document exp =
          new Document("projectionKey", SCOPED_NAME)
              .append("lastFactId", factId)
              .append("lastFactSerial", 2L);
      when(stateTable.find(ArgumentMatchers.<Document>any())).thenReturn(findIterable);
      when(findIterable.first()).thenReturn(exp);

      FactStreamPosition res = uut.factStreamPosition();

      assertThat(res).isEqualTo(FactStreamPosition.of(factId, 2L));
    }

    @Test
    void returnsNullIfFactStreamPositionNotSet() {
      when(stateTable.find(ArgumentMatchers.<Document>any())).thenReturn(findIterable);
      when(findIterable.first()).thenReturn(null);

      FactStreamPosition res = uut.factStreamPosition();

      assertThat(res).isNull();
    }

    @Test
    void settingFactSteamPosition() {
      UUID factId = UUID.randomUUID();
      FactStreamPosition state = FactStreamPosition.of(factId, 2L);

      uut.factStreamPosition(state);

      verify(stateTable)
          .replaceOne(filterCaptor.capture(), stateUpdateCaptor.capture(), optionsCaptor.capture());

      assertThat(filterCaptor.getValue().get("projectionKey")).isEqualTo(SCOPED_NAME);

      Document update = stateUpdateCaptor.getValue();
      assertThat(update.get("projectionKey")).isEqualTo(SCOPED_NAME);
      assertThat(update.get("lastFactId")).isEqualTo(factId);
      assertThat(update.get("lastFactSerial")).isEqualTo(2L);

      assertThat(optionsCaptor.getValue().isUpsert()).isTrue();
    }
  }

  @Nested
  class WhenAquiringWriteToken {

    @Captor private ArgumentCaptor<LockConfiguration> captor;
    @Mock LockProvider lockProvider;

    @BeforeEach
    void setUp() {
      uut = new TestProjection(mongoDatabase, stateTable, lockProvider);
    }

    @Test
    @SneakyThrows
    void returnsTokenWhenLockingSuccessful() {
      Duration maxWaitDuration = Duration.ofSeconds(60L);
      SimpleLock lock = mock(SimpleLock.class);
      when(lockProvider.lock(any(LockConfiguration.class))).thenReturn(Optional.of(lock));

      final WriterToken res = uut.acquireWriteToken(maxWaitDuration);

      verify(lockProvider).lock(captor.capture());
      final LockConfiguration lockConfig = captor.getValue();
      assertThat(lockConfig.getName()).isEqualTo(SCOPED_NAME + "_lock");
      assertThat(lockConfig.getLockAtLeastFor()).isEqualTo(Duration.ofSeconds(1L));
      assertThat(lockConfig.getLockAtMostFor()).isEqualTo(maxWaitDuration);

      assertThat(res).isNotNull();
    }

    @Test
    @SneakyThrows
    void returnsLockWhenSuccessfulAfterMultipleAttempts() {
      Duration maxWaitDuration = Duration.ofSeconds(7);
      SimpleLock lock = mock(SimpleLock.class);
      when(lockProvider.lock(any(LockConfiguration.class)))
          .thenReturn(Optional.empty())
          .thenReturn(Optional.empty())
          .thenReturn(Optional.of(lock));

      final WriterToken res = uut.acquireWriteToken(maxWaitDuration);

      verify(lockProvider, times(3)).lock(captor.capture());
      final LockConfiguration lockConfig = captor.getValue();
      assertThat(lockConfig.getName()).isEqualTo(SCOPED_NAME + "_lock");
      assertThat(lockConfig.getLockAtLeastFor()).isEqualTo(MIN_LEASE_DURATION_SECONDS);
      assertThat(lockConfig.getLockAtMostFor()).isEqualTo(MAX_LEASE_DURATION_SECONDS);

      assertThat(res).isNotNull();
    }

    @Test
    @SneakyThrows
    void returnsNullIfLockCouldNotBeObtainedAfterMultipleAttempts() {
      Duration maxWaitDuration = Duration.ofSeconds(1);
      when(lockProvider.lock(any(LockConfiguration.class))).thenReturn(Optional.empty());

      final WriterToken res = uut.acquireWriteToken(maxWaitDuration);

      // After 3. attempt, we waited 1,5 seconds and abort.
      verify(lockProvider, times(2)).lock(captor.capture());
      assertThat(res).isNull();
    }
  }

  @Nested
  class MissingProjectionMetaDataAnnotation {
    @Test
    void happyPath() {
      assertThatThrownBy(() -> new MissingAnnotationTestProjection(mongoDatabase))
          .isInstanceOf(IllegalStateException.class);
    }
  }

  @ProjectionMetaData(revision = 1)
  static class TestProjection extends AbstractMongoDbManagedProjection {

    public TestProjection(@NonNull MongoDatabase mongoDatabase) {
      super(mongoDatabase);
    }

    public TestProjection(
        @NonNull MongoDatabase mongoDb,
        @NonNull MongoCollection<Document> stateTable,
        @NonNull LockProvider lockProvider) {
      super(mongoDb, stateTable, lockProvider);
    }
  }

  static class MissingAnnotationTestProjection extends AbstractMongoDbManagedProjection {

    public MissingAnnotationTestProjection(@NonNull MongoDatabase MongoDatabase) {
      super(MongoDatabase);
    }
  }
}
