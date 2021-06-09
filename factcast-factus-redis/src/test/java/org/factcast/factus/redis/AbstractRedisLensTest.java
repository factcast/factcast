package org.factcast.factus.redis;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.IOException;
import lombok.NonNull;
import lombok.val;
import org.factcast.core.Fact;
import org.factcast.factus.projection.Projection;
import org.factcast.factus.redis.tx.RedisTransactional;
import org.factcast.factus.redis.tx.RedisTransactionalLens;
import org.factcast.factus.redis.tx.RedissonTxManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RedissonClient;

@ExtendWith(MockitoExtension.class)
class AbstractRedisLensTest {
  @Mock private RedissonClient client;

  @Nested
  class WhenBeforingFactProcessing {
    @Mock private Fact f;

    @BeforeEach
    void setup() {}

    @Test
    void resetsTimeIfBatching() {
      RedisProjection p = new ARedisProjection(client);
      val underTest = new RedisTransactionalLens(p, client);

      underTest.batchSize = 100;
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
      RedisProjection p = new ARedisProjection(client);
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

      RedisProjection p = new ARedisProjection(client);
      val underTest = spy(new RedisTransactionalLens(p, client));
      underTest.onCatchup(p);

      verify(underTest, times(1)).flush();
    }

    @Test
    void disablesBatching() {

      RedisProjection p = new ARedisProjection(client);
      val underTest = spy(new RedisTransactionalLens(p, client));
      assertThat(underTest.batchSize).isNotEqualTo(1);
      underTest.onCatchup(p);

      assertThat(underTest.batchSize).isEqualTo(1);
    }
  }

  @Nested
  class WhenSkipingStateUpdate {
    @BeforeEach
    void setup() {}

    @Test
    void calculatesStateSkipping() {

      RedisProjection p = new ARedisProjection(client);
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
      RedisProjection p = new ARedisProjection(client);
      val underTest = spy(new RedisTransactionalLens(p, client));
      underTest.start().set(System.currentTimeMillis());

      underTest.flush();

      assertThat(underTest.start().get()).isEqualTo(0);
    }

    @Test
    void delegates() {
      RedisProjection p = new ARedisProjection(client);
      val underTest = spy(new RedisTransactionalLens(p, client));
      underTest.flush();
      verify(underTest).doFlush();
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
      RedisProjection p = new ARedisProjection(client);
      val underTest = spy(new RedisTransactionalLens(p, client));

      underTest.afterFactProcessingFailed(f, new IOException("oh dear"));

      verify(underTest).doClear();
      assertThat(RedissonTxManager.get(client).inTransaction()).isFalse();
    }
  }
}

@RedisTransactional
class ARedisProjection extends AbstractRedisProjection {
  public ARedisProjection(@NonNull RedissonClient redisson) {
    super(redisson);
  }
}
