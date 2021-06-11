package org.factcast.factus.redis;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import lombok.NonNull;
import lombok.val;
import org.factcast.factus.redis.batch.RedissonBatchManager;
import org.factcast.factus.redis.tx.RedissonTxManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.*;
import org.redisson.config.Config;

@ExtendWith(MockitoExtension.class)
class AbstractRedisManagedProjectionTest {
  private static final String STATE_BUCKET_NAME = "STATE_BUCKET_NAME";
  private static final String REDIS_KEY = "REDIS_KEY";

  @Mock private RedissonClient redisson;

  @Mock private RLock lock;
  @InjectMocks private TestProjection underTest;

  @Nested
  class BucketFromThinAir {
    @Mock private RTransaction tx;

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

      val result = underTest.state();

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

      val result = underTest.state();

      assertThat(result).isEqualTo(id);
    }
  }

  @Nested
  class WhenSettingState {
    private final UUID STATE = UUID.randomUUID();

    @Test
    void nonRunningTransaction() {
      RedissonBatchManager.get(redisson).discard();
      RedissonTxManager.get(redisson).rollback();

      RBucket<Object> bucket = mock(RBucket.class);
      when(redisson.getBucket(any(), any())).thenReturn(bucket);

      underTest.state(STATE);

      verify(bucket).set(STATE);
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

      underTest.state(STATE);

      verify(bucket).set(STATE);
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

      underTest.state(STATE);

      verify(bucket).setAsync(STATE);
    }

    @Test
    void noTxnoBatch() {
      RedissonBatchManager.get(redisson).discard();
      RedissonTxManager.get(redisson).rollback();

      RBucket<Object> bucket = mock(RBucket.class);
      when(redisson.getBucket(any(), any())).thenReturn(bucket);

      underTest.state(STATE);

      verify(bucket).set(STATE);
    }
  }

  @Nested
  class WhenAcquiringWriteToken {

    @Mock private Config config;

    @Test
    void happyPath() {

      when(redisson.getLock(any())).thenReturn(lock);
      when(redisson.getConfig()).thenReturn(config);
      when(config.getLockWatchdogTimeout()).thenReturn(1000L);
      AbstractRedisManagedProjection underTest = new TestProjection(redisson);

      val wt = underTest.acquireWriteToken();

      verify(lock).lock();
      assertThat(wt).isNotNull().isInstanceOf(RedisWriterToken.class);
    }
  }

  static class TestProjection extends AbstractRedisManagedProjection {

    public TestProjection(@NonNull RedissonClient redisson) {
      super(redisson);
    }
  }
}
