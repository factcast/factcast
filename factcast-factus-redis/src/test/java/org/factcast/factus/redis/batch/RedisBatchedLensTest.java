package org.factcast.factus.redis.batch;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Function;
import lombok.NonNull;
import lombok.val;
import org.factcast.core.Fact;
import org.factcast.factus.projection.Projection;
import org.factcast.factus.projection.WriterToken;
import org.factcast.factus.redis.ARedisBatchedManagedProjection;
import org.factcast.factus.redis.RedisManagedProjection;
import org.factcast.factus.redis.batch.RedisBatched.Defaults;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBatch;
import org.redisson.api.RedissonClient;

@ExtendWith(MockitoExtension.class)
class RedisBatchedLensTest {
  @Mock private RedissonClient client;

  @Nested
  class WhenBeforingFactProcessing {
    @Mock private Fact f;

    @BeforeEach
    void setup() {}

    @Test
    void resetsTimeIfBatching() {
      RedisManagedProjection p = new ARedisBatchedManagedProjection(client);
      val underTest = new RedisBatchedLens(p, client);

      underTest.bulkSize(100);
      underTest.start().set(0L);

      underTest.beforeFactProcessing(f);

      assertThat(underTest.start().get())
          .isNotEqualTo(0L)
          .isLessThanOrEqualTo(System.currentTimeMillis())
          .isGreaterThan(System.currentTimeMillis() - 1000);
    }
  }

  @Nested
  class WhenAfteringFactProcessing {
    @Mock private Fact f;

    @BeforeEach
    void setup() {}

    @Test
    void counts() {
      RedisManagedProjection p = new ARedisBatchedManagedProjection(client);
      val underTest = spy(new RedisBatchedLens(p, client));
      when(underTest.shouldFlush()).thenReturn(false);

      val before = underTest.count().get();
      underTest.afterFactProcessing(f);

      assertThat(underTest.count().get()).isEqualTo(before + 1);
    }
  }

  @Nested
  class WhenOningCatchup {
    @Mock private Projection p;

    @BeforeEach
    void setup() {}

    @Test
    void doesNotUnnecessarilyflush() {

      RedisManagedProjection p = new ARedisBatchedManagedProjection(client);
      val underTest = spy(new RedisBatchedLens(p, client));

      underTest.onCatchup(p);

      verify(underTest, never()).flush();
    }

    @Test
    void flushesOnCacthupIfNecessary() {

      RedisManagedProjection p = new ARedisBatchedManagedProjection(client);
      val underTest = spy(new RedisBatchedLens(p, client));

      // mark it dirty
      underTest.afterFactProcessing(Fact.builder().id(UUID.randomUUID()).buildWithoutPayload());

      underTest.onCatchup(p);

      verify(underTest, times(1)).flush();
    }

    @Test
    void disablesBatching() {

      RedisManagedProjection p = new ARedisBatchedManagedProjection(client);
      val underTest = spy(new RedisBatchedLens(p, client));
      assertThat(underTest.bulkSize()).isNotEqualTo(1);
      underTest.onCatchup(p);

      assertThat(underTest.bulkSize()).isEqualTo(1);
    }
  }

  @Nested
  class WhenSkipingStateUpdate {
    @BeforeEach
    void setup() {}

    @Test
    void calculatesStateSkipping() {

      RedisManagedProjection p = new ARedisBatchedManagedProjection(client);
      val underTest = spy(new RedisBatchedLens(p, client));
      when(underTest.shouldFlush(anyBoolean())).thenReturn(false, false, true, true);
      when(underTest.isBulkApplying()).thenReturn(false, true, false, true);

      assertThat(underTest.skipStateUpdate()).isFalse();
      assertThat(underTest.skipStateUpdate()).isTrue();
      assertThat(underTest.skipStateUpdate()).isFalse();
      assertThat(underTest.skipStateUpdate()).isTrue();
    }
  }

  @Nested
  class WhenFlushing {
    @BeforeEach
    void setup() {}

    @Test
    void resetsClock() {
      RedisManagedProjection p = new ARedisBatchedManagedProjection(client);
      val underTest = spy(new RedisBatchedLens(p, client));
      underTest.start().set(System.currentTimeMillis());

      underTest.flush();

      assertThat(underTest.start().get()).isEqualTo(0);
    }

    @Test
    void delegates() {
      RedisManagedProjection p = new ARedisBatchedManagedProjection(client);
      val underTest = spy(new RedisBatchedLens(p, client));
      underTest.flush();
      verify(underTest).doFlush();
    }
  }

  @Nested
  class WhenDoingClear {
    @BeforeEach
    void setup() {}

    @Test
    void delegates() {
      RedisManagedProjection p = new ARedisBatchedManagedProjection(client);
      RedissonBatchManager tx = mock(RedissonBatchManager.class);
      when(tx.inBatch()).thenReturn(true);
      val underTest = new RedisBatchedLens(p, tx, Defaults.create());

      underTest.doClear();

      verify(tx).discard();
    }
  }

  @Nested
  class WhenDoingFlush {
    @BeforeEach
    void setup() {}

    @Test
    void delegates() {
      RedisManagedProjection p = new ARedisBatchedManagedProjection(client);
      RedissonBatchManager tx = mock(RedissonBatchManager.class);
      when(tx.inBatch()).thenReturn(true);
      val underTest = new RedisBatchedLens(p, tx, Defaults.create());

      underTest.doFlush();

      verify(tx).execute();
    }
  }

  @Nested
  class WhenAfteringFactProcessingFailed {
    @Mock private Fact f;
    @Mock private Throwable justForInformation;

    @BeforeEach
    void setup() {}

    @Test
    void rollsback() {
      RedisManagedProjection p = new ARedisBatchedManagedProjection(client);
      val underTest = spy(new RedisBatchedLens(p, client));

      underTest.afterFactProcessingFailed(f, new IOException("oh dear"));

      verify(underTest).doClear();
      assertThat(RedissonBatchManager.get(client).inBatch()).isFalse();
    }
  }

  @Nested
  class WhenCreatingOpts {
    @Test
    void failsOnNoAnnotation() {
      assertThatThrownBy(
              () -> {
                RedisBatchedLens.createOpts(new NonAnnotatedRedisManagedProjection());
              })
          .isInstanceOf(IllegalStateException.class);
    }
  }

  @Nested
  class WhenGettingSize {

    @Test
    void failsOnNoAnnotation() {
      assertThatThrownBy(
              () -> {
                RedisBatchedLens.getSize(new NonAnnotatedRedisManagedProjection());
              })
          .isInstanceOf(IllegalStateException.class);
    }
  }

  @Nested
  class WhenParameteringTransformerFor {

    @Mock private Fact f;
    @Mock RBatch current;

    @Test
    void returnsCurrentBatch() {
      RedisManagedProjection p = new ARedisBatchedManagedProjection(client);
      RedissonBatchManager man = mock(RedissonBatchManager.class);
      when(man.getCurrentBatch()).thenReturn(current);
      val underTest = new RedisBatchedLens(p, man, Defaults.create());

      Function<Fact, ?> t = underTest.parameterTransformerFor(RBatch.class);
      assertThat(t).isNotNull();
      assertThat(t.apply(f)).isSameAs(current);
    }

    @Test
    void returnsNullForOtherType() {
      RedisManagedProjection p = new ARedisBatchedManagedProjection(client);
      RedissonBatchManager tx = mock(RedissonBatchManager.class);
      val underTest = new RedisBatchedLens(p, tx, Defaults.create());

      Function<Fact, ?> t = underTest.parameterTransformerFor(Fact.class);
      assertThat(t).isNull();
    }
  }
}

class NonAnnotatedRedisManagedProjection implements RedisManagedProjection {
  @Override
  public @NonNull RedissonClient redisson() {
    return null;
  }

  @Override
  public UUID state() {
    return null;
  }

  @Override
  public void state(@NonNull UUID state) {}

  @Override
  public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
    return null;
  }
}
