package org.factcast.factus.redis;

import lombok.NonNull;
import org.factcast.factus.redis.tx.RedisTransactional;
import org.redisson.api.RedissonClient;

@RedisTransactional
public class ARedisTransactionalManagedProjection extends AbstractRedisManagedProjection {
  public ARedisTransactionalManagedProjection(@NonNull RedissonClient redisson) {
    super(redisson);
  }
}
