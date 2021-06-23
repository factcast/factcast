package org.factcast.factus.redis;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import lombok.NonNull;
import org.assertj.core.api.Assertions;
import org.factcast.factus.projection.Projection;
import org.factcast.factus.projector.ProjectorPlugin;
import org.factcast.factus.redis.batch.RedisBatched;
import org.factcast.factus.redis.batch.RedisBatchedLens;
import org.factcast.factus.redis.tx.RedisTransactional;
import org.factcast.factus.redis.tx.RedisTransactionalLens;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RedissonClient;

@ExtendWith(MockitoExtension.class)
class RedisProjectorPluginTest {

  @Mock private ProjectorPlugin projectorPlugin;
  @InjectMocks private RedisProjectorPlugin underTest;

  @SuppressWarnings("ConstantConditions")
  @Nested
  class WhenLensingFor {
    @Mock private Projection p;

    @Test
    void failOnMixedModes() {
      Assertions.assertThatThrownBy(
              () -> {
                underTest.lensFor(new DoubleFeature(mock(RedissonClient.class)));
              })
          .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void findsTx() {
      assertThat(underTest.lensFor(new TX(mock(RedissonClient.class))).iterator().next())
          .isInstanceOf(RedisTransactionalLens.class);
    }

    @Test
    void findsBatch() {
      assertThat(underTest.lensFor(new BA(mock(RedissonClient.class))).iterator().next())
          .isInstanceOf(RedisBatchedLens.class);
    }

    @Test
    void emptyOnNoAnnotation() {
      assertThat(underTest.lensFor(new None(mock(RedissonClient.class)))).isEmpty();
    }
  }
}

@RedisTransactional
@RedisBatched
class DoubleFeature extends AbstractRedisManagedProjection {
  public DoubleFeature(@NonNull RedissonClient redisson) {
    super(redisson);
  }
}

class TX extends ARedisTransactionalManagedProjection {
  public TX(@NonNull RedissonClient redisson) {
    super(redisson);
  }
}

class BA extends ARedisBatchedManagedProjection {
  public BA(@NonNull RedissonClient redisson) {
    super(redisson);
  }
}

class None extends AbstractRedisManagedProjection {
  public None(@NonNull RedissonClient redisson) {
    super(redisson);
  }
}
