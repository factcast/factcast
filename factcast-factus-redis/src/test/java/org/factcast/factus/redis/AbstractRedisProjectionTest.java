package org.factcast.factus.redis;

import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

@ExtendWith(MockitoExtension.class)
// TODO
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
}
