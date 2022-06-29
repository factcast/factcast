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
package org.factcast.factus.redis;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.factcast.factus.redis.batch.RedissonBatchManager;
import org.factcast.factus.redis.tx.RedissonTxManager;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.*;
import org.redisson.config.Config;

@ExtendWith(MockitoExtension.class)
class AbstractRedisManagedProjectionTest {

  @Mock private RedissonClient redisson;
  @Mock private RLock lock;
  @Mock private RTransaction tx;
  @Mock private Config config;

  @InjectMocks private TestProjection underTest;

  @Nested
  class BucketFromThinAir {

    @Test
    void happyPath() {
      RBucket<Object> bucket = mock(RBucket.class);
      when(redisson.getBucket(any(), any())).thenReturn(bucket);

      assertThat(underTest.stateBucket()).isNotNull().isInstanceOf(RBucket.class).isSameAs(bucket);
      verify(redisson).getBucket(underTest.redisKey() + "_state_tracking", UUIDCodec.INSTANCE);
    }
  }

  @Nested
  class BucketFromTx {

    @Test
    void happyPath() {
      RBucket<Object> bucket = mock(RBucket.class);
      RTransaction tx = mock(RTransaction.class);
      when(tx.getBucket(any(), any())).thenReturn(bucket);

      assertThat(underTest.stateBucket(tx))
          .isNotNull()
          .isInstanceOf(RBucket.class)
          .isSameAs(bucket);
      verify(tx).getBucket(underTest.redisKey() + "_state_tracking", UUIDCodec.INSTANCE);
    }
  }

  @Nested
  class BucketFromBatch {
    @Test
    void happyPath() {
      RBucket<Object> bucket = mock(RBucket.class);
      RBatch batch = mock(RBatch.class);
      when(batch.getBucket(any(), any())).thenReturn(bucket);

      assertThat(underTest.stateBucket(batch))
          .isNotNull()
          .isInstanceOf(RBucket.class)
          .isSameAs(bucket);
      verify(batch).getBucket(underTest.redisKey() + "_state_tracking", UUIDCodec.INSTANCE);
    }
  }

  @Nested
  class WhenGettingState {
    @Test
    void nonRunningTransaction() {
      RedissonTxManager man = RedissonTxManager.get(redisson);
      man.rollback();

      UUID id = new UUID(23, 43);
      RBucket<Object> bucket = mock(RBucket.class);
      RTransaction tx = mock(RTransaction.class);
      when(redisson.getBucket(any(), any())).thenReturn(bucket);
      when(bucket.get()).thenReturn(id);

      UUID result = underTest.factStreamPosition();

      assertThat(result).isEqualTo(id);
    }

    @Test
    void runningTransaction() {
      RTransaction tx = mock(RTransaction.class);
      when(redisson.createTransaction(any())).thenReturn(tx);

      RedissonTxManager man = RedissonTxManager.get(redisson);
      man.startOrJoin();

      UUID id = new UUID(23, 43);
      RBucket<Object> bucket = mock(RBucket.class);
      when(tx.getBucket(any(), any())).thenReturn(bucket);
      when(bucket.get()).thenReturn(id);

      UUID result = underTest.factStreamPosition();

      assertThat(result).isEqualTo(id);
    }
  }

  @Nested
  class WhenSettingFactStreamPosition {
    private final UUID FACT_STREAM_POSITION = UUID.randomUUID();

    @Test
    void nonRunningTransaction() {
      RedissonBatchManager.get(redisson).discard();
      RedissonTxManager.get(redisson).rollback();

      RBucket<Object> bucket = mock(RBucket.class);
      when(redisson.getBucket(any(), any())).thenReturn(bucket);

      underTest.factStreamPosition(FACT_STREAM_POSITION);

      verify(bucket).set(FACT_STREAM_POSITION);
    }

    @Test
    void runningTransaction() {
      RedissonBatchManager.get(redisson).discard();
      RedissonTxManager.get(redisson).rollback();

      RTransaction tx = mock(RTransaction.class);
      when(redisson.createTransaction(any())).thenReturn(tx);

      RedissonTxManager man = RedissonTxManager.get(redisson);
      man.startOrJoin();

      RBucket<Object> bucket = mock(RBucket.class);
      when(tx.getBucket(any(), any())).thenReturn(bucket);

      underTest.factStreamPosition(FACT_STREAM_POSITION);

      verify(bucket).set(FACT_STREAM_POSITION);
    }

    @Test
    void runningBatch() {

      RedissonBatchManager.get(redisson).discard();
      RedissonTxManager.get(redisson).rollback();

      RBatch batch = mock(RBatch.class);
      when(redisson.createBatch(any())).thenReturn(batch);

      RedissonBatchManager man = RedissonBatchManager.get(redisson);
      man.startOrJoin();

      RBucket<Object> bucket = mock(RBucket.class);
      when(batch.getBucket(any(), any())).thenReturn(bucket);

      underTest.factStreamPosition(FACT_STREAM_POSITION);

      verify(bucket).setAsync(FACT_STREAM_POSITION);
    }

    @Test
    void noTxnoBatch() {
      RedissonBatchManager.get(redisson).discard();
      RedissonTxManager.get(redisson).rollback();

      RBucket<Object> bucket = mock(RBucket.class);
      when(redisson.getBucket(any(), any())).thenReturn(bucket);

      underTest.factStreamPosition(FACT_STREAM_POSITION);

      verify(bucket).set(FACT_STREAM_POSITION);
    }
  }

  @Nested
  class WhenAcquiringWriteToken {

    @SneakyThrows
    @Test
    void happyPath() {

      when(redisson.getLock(any())).thenReturn(lock);
      when(redisson.getConfig()).thenReturn(config);
      when(config.getLockWatchdogTimeout()).thenReturn(1000L);
      when(lock.tryLock(anyLong(), any())).thenReturn(true);
      AbstractRedisManagedProjection underTest = new TestProjection(redisson);

      AutoCloseable wt = underTest.acquireWriteToken();

      assertThat(wt).isNotNull().isInstanceOf(RedisWriterToken.class);
      verify(lock).tryLock(anyLong(), any(TimeUnit.class));
    }

    @SneakyThrows
    @Test
    void passesWaitTime() {

      when(redisson.getLock(any())).thenReturn(lock);
      when(redisson.getConfig()).thenReturn(config);
      when(config.getLockWatchdogTimeout()).thenReturn(1000L);
      when(lock.tryLock(anyLong(), any())).thenReturn(true);
      AbstractRedisManagedProjection underTest = new TestProjection(redisson);

      @NonNull Duration dur = Duration.ofMillis(127);
      AutoCloseable wt = underTest.acquireWriteToken(dur);

      verify(lock).tryLock(dur.toMillis(), TimeUnit.MILLISECONDS);
      assertThat(wt).isNotNull().isInstanceOf(RedisWriterToken.class);
    }

    @SneakyThrows
    @Test
    void withWaitTimeExpiring() {

      when(redisson.getLock(any())).thenReturn(lock);
      when(lock.tryLock(anyLong(), any())).thenReturn(false);
      AbstractRedisManagedProjection underTest = new TestProjection(redisson);

      @NonNull Duration dur = Duration.ofMillis(127);
      AutoCloseable wt = underTest.acquireWriteToken(dur);

      verify(lock).tryLock(dur.toMillis(), TimeUnit.MILLISECONDS);
      assertThat(wt).isNull();
    }

    @SneakyThrows
    @Test
    void withWaitInterruption() {

      when(redisson.getLock(any())).thenReturn(lock);
      when(lock.tryLock(anyLong(), any())).thenThrow(InterruptedException.class);
      AbstractRedisManagedProjection underTest = new TestProjection(redisson);

      @NonNull Duration dur = Duration.ofMillis(127);
      AutoCloseable wt = underTest.acquireWriteToken(dur);

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

  @ProjectionMetaData(serial = 1)
  static class TestProjection extends AbstractRedisManagedProjection {

    public TestProjection(@NonNull RedissonClient redisson) {
      super(redisson);
    }
  }

  static class MissingAnnotationTestProjection extends AbstractRedisManagedProjection {

    public MissingAnnotationTestProjection(@NonNull RedissonClient redisson) {
      super(redisson);
    }
  }
}
