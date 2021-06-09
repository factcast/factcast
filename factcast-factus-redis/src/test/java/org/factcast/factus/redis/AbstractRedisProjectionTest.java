package org.factcast.factus.redis;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.util.UUID;
import lombok.NonNull;
import org.factcast.factus.projection.WriterToken;
import org.factcast.factus.redis.tx.RedissonTxManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

@ExtendWith(MockitoExtension.class)
class AbstractRedisProjectionTest {

  private static final String STATE_BUCKET_NAME = "STATE_BUCKET_NAME";
  private static final String REDIS_KEY = "REDIS_KEY";
  @Mock private RedissonClient redisson;
  @Mock private RLock lock;
  @Mock private RedissonTxManager redissonTxManager;
  @InjectMocks private AbstractRedisProjection underTest;

  @Nested
  class WhenSettingState {
    private final UUID STATE = UUID.randomUUID();

    @BeforeEach
    void setup() {}
  }

  @Nested
  class WhenAcquiringWriteToken {
    @BeforeEach
    void setup() {}
  }

  @Nested
  class WhenCreatingRedisKey {
    @Test
    void happyPath() {
      assertThat(new Foo().createRedisKey()).isEqualTo("Foo");
    }

    @Test
    void filtersCgLib() {
      assertThat(new Foo$$EnhancerByCGLIB().createRedisKey()).isEqualTo("Foo");
    }

    @Test
    void filtersSpring() {
      assertThat(new Foo$$EnhancerBySpring().createRedisKey()).isEqualTo("Bar");
    }
  }
}

class Foo implements RedisProjection {
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

class Bar extends Foo {}

class Foo$$EnhancerByCGLIB extends Foo {}

class Foo$$EnhancerBySpring extends Bar {}
