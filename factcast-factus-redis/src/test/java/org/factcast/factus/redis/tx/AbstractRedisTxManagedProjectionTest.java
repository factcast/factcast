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
package org.factcast.factus.redis.tx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.factcast.core.FactStreamPosition;
import org.factcast.core.TestFactStreamPosition;
import org.factcast.factus.redis.FactStreamPositionCodec;
import org.factcast.factus.redis.RedisWriterToken;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.*;
import org.redisson.config.Config;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class AbstractRedisTxManagedProjectionTest {

  @Mock private RedissonClient redisson;
  @Mock private RLock lock;
  @Mock private RTransaction tx;
  @Mock private Config config;

  @InjectMocks private TestProjection underTest;

  @Nested
  class BucketFromThinAir {

    @Test
    void happyPathTx() {
      RBucket<Object> bucket = mock(RBucket.class);
      when(tx.getBucket(any(), any())).thenReturn(bucket);

      assertThat(underTest.stateBucket(tx)).isInstanceOf(RBucket.class).isSameAs(bucket);
      verify(tx)
          .getBucket(underTest.redisKey() + "_state_tracking", FactStreamPositionCodec.INSTANCE);
    }
  }

  @Nested
  class WhenInspectingClass {

    @RedisTransactional(bulkSize = 12, responseTimeout = 112)
    @ProjectionMetaData(revision = 1)
    class ProjectionWithBulkSet extends AbstractRedisTxProjection {
      protected ProjectionWithBulkSet(RedissonClient redisson) {
        super(redisson);
      }
    }

    @Test
    void getMaxSize() {
      AbstractRedisTxProjection p = new ProjectionWithBulkSet(mock(RedissonClient.class));
      assertThat(p.maxBatchSizePerTransaction()).isEqualTo(12);
    }
  }

  @Nested
  class BucketFromTx {

    @Test
    void happyPath() {
      RBucket<Object> bucket = mock(RBucket.class);
      when(tx.getBucket(any(), any())).thenReturn(bucket);

      assertThat(underTest.stateBucket(tx)).isInstanceOf(RBucket.class).isSameAs(bucket);
      verify(tx)
          .getBucket(underTest.redisKey() + "_state_tracking", FactStreamPositionCodec.INSTANCE);
    }
  }

  @Nested
  class WhenGettingState {
    @Test
    void nonRunningTransaction() {

      UUID id = new UUID(23, 43);
      RBucket<Object> bucket = mock(RBucket.class);
      when(redisson.getBucket(any(), any())).thenReturn(bucket);
      when(bucket.get()).thenReturn(FactStreamPosition.of(id, -1L));

      FactStreamPosition factStreamPosition = underTest.factStreamPosition();
      UUID result = Objects.requireNonNull(factStreamPosition).factId();

      assertThat(result).isEqualTo(id);
    }

    @Test
    void runningTransaction() {
      when(redisson.createTransaction(any())).thenReturn(tx);

      underTest.begin();

      FactStreamPosition pos = TestFactStreamPosition.random();
      RBucket<Object> bucket = mock(RBucket.class);
      when(redisson.getBucket(any(), any())).thenReturn(bucket);
      when(bucket.get()).thenReturn(pos);

      FactStreamPosition result = underTest.factStreamPosition();
      verifyNoInteractions(tx);

      assertThat(result).isEqualTo(pos);
    }
  }

  @Nested
  class WhenSettingFactStreamPosition {
    private final FactStreamPosition FACT_STREAM_POSITION = TestFactStreamPosition.random();

    @Test
    void nonRunningTransaction() {

      RBucket<Object> bucket = mock(RBucket.class);
      when(redisson.getBucket(any(), any())).thenReturn(bucket);

      underTest.factStreamPosition(FACT_STREAM_POSITION);

      verify(bucket).set(FACT_STREAM_POSITION);
    }

    @Test
    void runningTransaction() {

      when(redisson.createTransaction(any())).thenReturn(tx);

      underTest.begin();

      RBucket<Object> bucket = mock(RBucket.class);
      when(tx.getBucket(any(), any())).thenReturn(bucket);
      when(redisson.getBucket(any(), any())).thenReturn(bucket);

      underTest.factStreamPosition(FACT_STREAM_POSITION);
      verifyNoInteractions(tx);

      underTest.transactionalFactStreamPosition(FACT_STREAM_POSITION);

      verify(tx).getBucket(any(), any());
      verify(bucket, times(2)).set(FACT_STREAM_POSITION);
    }
  }

  @Nested
  class WhenAcquiringWriteToken {

    @SneakyThrows
    @Test
    void happyPath() {

      when(redisson.getLock(anyString())).thenReturn(lock);
      when(redisson.getConfig()).thenReturn(config);
      when(config.getLockWatchdogTimeout()).thenReturn(1000L);
      when(lock.tryLock(anyLong(), any())).thenReturn(true);
      AbstractRedisTxManagedProjection testProjection = new TestProjection(redisson);

      AutoCloseable wt = testProjection.acquireWriteToken();

      assertThat(wt).isInstanceOf(RedisWriterToken.class);
      verify(lock).tryLock(anyLong(), any(TimeUnit.class));
    }

    @SneakyThrows
    @Test
    void passesWaitTime() {

      when(redisson.getLock(anyString())).thenReturn(lock);
      when(redisson.getConfig()).thenReturn(config);
      when(config.getLockWatchdogTimeout()).thenReturn(1000L);
      when(lock.tryLock(anyLong(), any())).thenReturn(true);
      AbstractRedisTxManagedProjection testProjection = new TestProjection(redisson);

      @NonNull Duration dur = Duration.ofMillis(127);
      AutoCloseable wt = testProjection.acquireWriteToken(dur);

      verify(lock).tryLock(dur.toMillis(), TimeUnit.MILLISECONDS);
      assertThat(wt).isInstanceOf(RedisWriterToken.class);
    }

    @SneakyThrows
    @Test
    void withWaitTimeExpiring() {

      when(redisson.getLock(anyString())).thenReturn(lock);
      when(lock.tryLock(anyLong(), any())).thenReturn(false);
      AbstractRedisTxManagedProjection testProjection = new TestProjection(redisson);

      @NonNull Duration dur = Duration.ofMillis(127);
      AutoCloseable wt = testProjection.acquireWriteToken(dur);

      verify(lock).tryLock(dur.toMillis(), TimeUnit.MILLISECONDS);
      assertThat(wt).isNull();
    }

    @SneakyThrows
    @Test
    void withWaitInterruption() {

      when(redisson.getLock(anyString())).thenReturn(lock);
      when(lock.tryLock(anyLong(), any())).thenThrow(InterruptedException.class);
      AbstractRedisTxManagedProjection testProjection = new TestProjection(redisson);

      @NonNull Duration dur = Duration.ofMillis(127);
      AutoCloseable wt = testProjection.acquireWriteToken(dur);

      verify(lock).tryLock(dur.toMillis(), TimeUnit.MILLISECONDS);
      assertThat(wt).isNull();
    }
  }

  @Nested
  class MissingProjectionMetaDataAnnotation {

    @Test
    void happyPath() {
      assertThatThrownBy(() -> new MissingAnnotationTestProjection(redisson))
          .isInstanceOf(IllegalStateException.class);
    }
  }

  @ProjectionMetaData(revision = 1)
  static class TestProjection extends AbstractRedisTxManagedProjection {

    TestProjection(@NonNull RedissonClient redisson) {
      super(redisson);
    }
  }

  static class MissingAnnotationTestProjection extends AbstractRedisTxManagedProjection {

    MissingAnnotationTestProjection(@NonNull RedissonClient redisson) {
      super(redisson);
    }
  }
}
