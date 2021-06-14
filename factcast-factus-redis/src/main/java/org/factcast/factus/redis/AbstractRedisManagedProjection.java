package org.factcast.factus.redis;

import lombok.NonNull;
import org.redisson.api.RedissonClient;

public abstract class AbstractRedisManagedProjection extends AbstractRedisProjection
    implements RedisManagedProjection {
  public AbstractRedisManagedProjection(@NonNull RedissonClient redisson) {
    super(redisson);
  }
}
