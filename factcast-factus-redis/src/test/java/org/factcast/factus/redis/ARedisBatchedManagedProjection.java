package org.factcast.factus.redis;

import lombok.NonNull;
import org.factcast.factus.redis.batch.RedisBatched;
import org.redisson.api.RedissonClient;

@RedisBatched
public class ARedisBatchedManagedProjection extends AbstractRedisManagedProjection {
  public ARedisBatchedManagedProjection(@NonNull RedissonClient redisson) {
    super(redisson);
  }
}
