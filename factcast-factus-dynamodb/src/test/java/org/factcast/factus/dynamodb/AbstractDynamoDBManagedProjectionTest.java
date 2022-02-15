package org.factcast.factus.dynamodb;

import org.junit.jupiter.api.extension.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AbstractDynamoDBManagedProjectionTest {
  //
  //  @Mock private AmazonDynamoDB redisson;
  //
  //  // @Mock private RLock lock;
  //  @InjectMocks private TestProjection underTest;
  //
  //  @Nested
  //  class BucketFromThinAir {
  //    @Mock private RTransaction tx;
  //
  //    @Test
  //    void happyPath() {
  //      RBucket<Object> bucket = mock(RBucket.class);
  //      when(redisson.getBucket(any(), any())).thenReturn(bucket);
  //
  //
  // assertThat(underTest.stateBucket()).isNotNull().isInstanceOf(RBucket.class).isSameAs(bucket);
  //      verify(redisson).getBucket(underTest.redisKey() + "_state_tracking", UUIDCodec.INSTANCE);
  //    }
  //  }
  //
  //  @Nested
  //  class BucketFromTx {
  //
  //    @Test
  //    void happyPath() {
  //      RBucket<Object> bucket = mock(RBucket.class);
  //      RTransaction tx = mock(RTransaction.class);
  //      when(tx.getBucket(any(), any())).thenReturn(bucket);
  //
  //      assertThat(underTest.stateBucket(tx))
  //          .isNotNull()
  //          .isInstanceOf(RBucket.class)
  //          .isSameAs(bucket);
  //      verify(tx).getBucket(underTest.redisKey() + "_state_tracking", UUIDCodec.INSTANCE);
  //    }
  //  }
  //
  //  @Nested
  //  class BucketFromBatch {
  //    @Test
  //    void happyPath() {
  //      RBucket<Object> bucket = mock(RBucket.class);
  //      RBatch batch = mock(RBatch.class);
  //      when(batch.getBucket(any(), any())).thenReturn(bucket);
  //
  //      assertThat(underTest.stateBucket(batch))
  //          .isNotNull()
  //          .isInstanceOf(RBucket.class)
  //          .isSameAs(bucket);
  //      verify(batch).getBucket(underTest.redisKey() + "_state_tracking", UUIDCodec.INSTANCE);
  //    }
  //  }
  //
  //  @Nested
  //  class WhenGettingState {
  //    @Test
  //    void nonRunningTransaction() {
  //      RedissonTxManager man = RedissonTxManager.get(redisson);
  //      man.rollback();
  //
  //      UUID id = new UUID(23, 43);
  //      RBucket<Object> bucket = mock(RBucket.class);
  //      RTransaction tx = mock(RTransaction.class);
  //      when(redisson.getBucket(any(), any())).thenReturn(bucket);
  //      when(bucket.get()).thenReturn(id);
  //
  //      UUID result = underTest.factStreamPosition();
  //
  //      assertThat(result).isEqualTo(id);
  //    }
  //
  //    @Test
  //    void runningTransaction() {
  //      RTransaction tx = mock(RTransaction.class);
  //      when(redisson.createTransaction(any())).thenReturn(tx);
  //
  //      RedissonTxManager man = RedissonTxManager.get(redisson);
  //      man.startOrJoin();
  //
  //      UUID id = new UUID(23, 43);
  //      RBucket<Object> bucket = mock(RBucket.class);
  //      when(tx.getBucket(any(), any())).thenReturn(bucket);
  //      when(bucket.get()).thenReturn(id);
  //
  //      UUID result = underTest.factStreamPosition();
  //
  //      assertThat(result).isEqualTo(id);
  //    }
  //  }
  //
  //  @Nested
  //  class WhenSettingFactStreamPosition {
  //    private final UUID FACT_STREAM_POSITION = UUID.randomUUID();
  //
  //    @Test
  //    void nonRunningTransaction() {
  //      RedissonBatchManager.get(redisson).discard();
  //      RedissonTxManager.get(redisson).rollback();
  //
  //      RBucket<Object> bucket = mock(RBucket.class);
  //      when(redisson.getBucket(any(), any())).thenReturn(bucket);
  //
  //      underTest.factStreamPosition(FACT_STREAM_POSITION);
  //
  //      verify(bucket).set(FACT_STREAM_POSITION);
  //    }
  //
  //    @Test
  //    void runningTransaction() {
  //      RedissonBatchManager.get(redisson).discard();
  //      RedissonTxManager.get(redisson).rollback();
  //
  //      RTransaction tx = mock(RTransaction.class);
  //      when(redisson.createTransaction(any())).thenReturn(tx);
  //
  //      RedissonTxManager man = RedissonTxManager.get(redisson);
  //      man.startOrJoin();
  //
  //      RBucket<Object> bucket = mock(RBucket.class);
  //      when(tx.getBucket(any(), any())).thenReturn(bucket);
  //
  //      underTest.factStreamPosition(FACT_STREAM_POSITION);
  //
  //      verify(bucket).set(FACT_STREAM_POSITION);
  //    }
  //
  //    @Test
  //    void runningBatch() {
  //
  //      RedissonBatchManager.get(redisson).discard();
  //      RedissonTxManager.get(redisson).rollback();
  //
  //      RBatch batch = mock(RBatch.class);
  //      when(redisson.createBatch(any())).thenReturn(batch);
  //
  //      RedissonBatchManager man = RedissonBatchManager.get(redisson);
  //      man.startOrJoin();
  //
  //      RBucket<Object> bucket = mock(RBucket.class);
  //      when(batch.getBucket(any(), any())).thenReturn(bucket);
  //
  //      underTest.factStreamPosition(FACT_STREAM_POSITION);
  //
  //      verify(bucket).setAsync(FACT_STREAM_POSITION);
  //    }
  //
  //    @Test
  //    void noTxnoBatch() {
  //      RedissonBatchManager.get(redisson).discard();
  //      RedissonTxManager.get(redisson).rollback();
  //
  //      RBucket<Object> bucket = mock(RBucket.class);
  //      when(redisson.getBucket(any(), any())).thenReturn(bucket);
  //
  //      underTest.factStreamPosition(FACT_STREAM_POSITION);
  //
  //      verify(bucket).set(FACT_STREAM_POSITION);
  //    }
  //  }
  //
  //  @Nested
  //  class WhenAcquiringWriteToken {
  //
  //    @Mock private Config config;
  //
  //    @Test
  //    void happyPath() {
  //
  //      when(redisson.getLock(any())).thenReturn(lock);
  //      when(redisson.getConfig()).thenReturn(config);
  //      when(config.getLockWatchdogTimeout()).thenReturn(1000L);
  //      AbstractRedisManagedProjection underTest = new TestProjection(redisson);
  //
  //      AutoCloseable wt = underTest.acquireWriteToken();
  //
  //      verify(lock).lock();
  //      assertThat(wt).isNotNull().isInstanceOf(RedisWriterToken.class);
  //    }
  //  }
  //
  //  @Nested
  //  class MissingProjectionMetaDataAnnotation {
  //
  //    @Test
  //    void happyPath() {
  //      assertThatThrownBy(() -> new MissingAnntoationTestProjection(redisson))
  //          .isInstanceOf(IllegalStateException.class);
  //    }
  //  }
  //
  //  @ProjectionMetaData(serial = 1)
  //  static class TestProjection extends AbstractRedisManagedProjection {
  //
  //    public TestProjection(@NonNull RedissonClient redisson) {
  //      super(redisson);
  //    }
  //  }
  //
  //  static class MissingAnntoationTestProjection extends AbstractRedisManagedProjection {
  //
  //    public MissingAnntoationTestProjection(@NonNull RedissonClient redisson) {
  //      super(redisson);
  //    }
  //  }
}
