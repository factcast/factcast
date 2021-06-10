package org.factcast.factus.redis;

import lombok.NonNull;
import org.factcast.factus.redis.tx.RedisTransactional;
import org.redisson.api.RedissonClient;

@RedisTransactional
public class ARedisManagedProjection extends AbstractRedisManagedProjection {
  public ARedisManagedProjection(@NonNull RedissonClient redisson) {
    super(redisson);
  }
}
