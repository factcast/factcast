package org.factcast.factus.redis.tx;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import lombok.NonNull;
import lombok.val;
import org.factcast.core.Fact;
import org.factcast.factus.projection.Projection;
import org.factcast.factus.projection.WriterToken;
import org.factcast.factus.redis.ARedisManagedProjection;
import org.factcast.factus.redis.RedisManagedProjection;
import org.factcast.factus.redis.tx.RedisTransactional.Defaults;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RTransaction;
import org.redisson.api.RedissonClient;
import org.redisson.api.TransactionOptions;

@ExtendWith(MockitoExtension.class)
class RedisTransactionalLensTest {
  @Mock private RedissonClient client;

  @Nested
  class WhenBeforingFactProcessing {
    @Mock private Fact f;

    @BeforeEach
    void setup() {}

    @Test
    void resetsTimeIfBatching() {
      RedisManagedProjection p = new ARedisManagedProjection(client);
      val underTest = new RedisTransactionalLens(p, client);

      underTest.batchSize(100);
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
      RedisManagedProjection p = new ARedisManagedProjection(client);
      val underTest = spy(new RedisTransactionalLens(p, client));
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
    void flushes() {

      RedisManagedProjection p = new ARedisManagedProjection(client);
      val underTest = spy(new RedisTransactionalLens(p, client));
      underTest.onCatchup(p);

      verify(underTest, times(1)).flush();
    }

    @Test
    void disablesBatching() {

      RedisManagedProjection p = new ARedisManagedProjection(client);
      val underTest = spy(new RedisTransactionalLens(p, client));
      assertThat(underTest.batchSize()).isNotEqualTo(1);
      underTest.onCatchup(p);

      assertThat(underTest.batchSize()).isEqualTo(1);
    }
  }

  @Nested
  class WhenSkipingStateUpdate {
    @BeforeEach
    void setup() {}

    @Test
    void calculatesStateSkipping() {

      RedisManagedProjection p = new ARedisManagedProjection(client);
      val underTest = spy(new RedisTransactionalLens(p, client));
      when(underTest.shouldFlush()).thenReturn(false, false, true, true);
      when(underTest.isBatching()).thenReturn(false, true, false, true);

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
      RedisManagedProjection p = new ARedisManagedProjection(client);
      val underTest = spy(new RedisTransactionalLens(p, client));
      underTest.start().set(System.currentTimeMillis());

      underTest.flush();

      assertThat(underTest.start().get()).isEqualTo(0);
    }

    @Test
    void delegates() {
      RedisManagedProjection p = new ARedisManagedProjection(client);
      val underTest = spy(new RedisTransactionalLens(p, client));
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
      RedisManagedProjection p = new ARedisManagedProjection(client);
      RedissonTxManager tx = mock(RedissonTxManager.class);
      when(tx.inTransaction()).thenReturn(true);
      val underTest = new RedisTransactionalLens(p, client, tx, Defaults.create());

      underTest.doClear();

      verify(tx).rollback();
    }
  }

  @Nested
  class WhenDoingFlush {
    @BeforeEach
    void setup() {}

    @Test
    void delegates() {
      RedisManagedProjection p = new ARedisManagedProjection(client);
      RedissonTxManager tx = mock(RedissonTxManager.class);
      when(tx.inTransaction()).thenReturn(true);
      val underTest = new RedisTransactionalLens(p, client, tx, Defaults.create());

      underTest.doFlush();

      verify(tx).commit();
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
      RedisManagedProjection p = new ARedisManagedProjection(client);
      val underTest = spy(new RedisTransactionalLens(p, client));

      underTest.afterFactProcessingFailed(f, new IOException("oh dear"));

      verify(underTest).doClear();
      assertThat(RedissonTxManager.get(client).inTransaction()).isFalse();
    }
  }

  @Nested
  class WhenCreatingOpts {
    // TODO
  }

  @Nested
  class WhenGettingSize {

    @Test
    void failsOnNoAnnotation() {
      assertThatThrownBy(
              () -> {
                RedisTransactionalLens.getSize(new NonAnnotatedRedisManagedProjection());
              })
          .isInstanceOf(IllegalStateException.class);
    }
  }

  @Nested
  class WhenCalculatingFlushTimeout {
    @Test
    void is8of10() {
      TransactionOptions opts = TransactionOptions.defaults().timeout(10, TimeUnit.MINUTES);

      long result = RedisTransactionalLens.calculateFlushTimeout(opts);

      assertThat(result).isEqualTo(Duration.ofMinutes(8).toMillis());
    }

    @Test
    void disableIfToSmall() {
      TransactionOptions opts = TransactionOptions.defaults().timeout(10, TimeUnit.MILLISECONDS);

      long result = RedisTransactionalLens.calculateFlushTimeout(opts);

      assertThat(result).isEqualTo(0L);
    }

    @Test
    void edgeCase() {
      TransactionOptions opts = TransactionOptions.defaults().timeout(100, TimeUnit.MILLISECONDS);

      long result = RedisTransactionalLens.calculateFlushTimeout(opts);

      assertThat(result).isEqualTo(80L);
    }
  }

  @Nested
  class WhenParameteringTransformerFor {

    @Mock private Fact f;
    @Mock RTransaction current;

    @Test
    void returnsCurrentTx() {
      RedisManagedProjection p = new ARedisManagedProjection(client);
      RedissonTxManager tx = mock(RedissonTxManager.class);
      when(tx.getCurrentTransaction()).thenReturn(current);
      val underTest = new RedisTransactionalLens(p, client, tx, Defaults.create());

      Function<Fact, ?> t = underTest.parameterTransformerFor(RTransaction.class);
      assertThat(t).isNotNull();
      assertThat(t.apply(f)).isInstanceOf(RTransaction.class).isNotNull().isSameAs(current);
    }

    @Test
    void returnsNullForOtherType() {
      RedisManagedProjection p = new ARedisManagedProjection(client);
      RedissonTxManager tx = mock(RedissonTxManager.class);
      val underTest = new RedisTransactionalLens(p, client, tx, Defaults.create());

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
