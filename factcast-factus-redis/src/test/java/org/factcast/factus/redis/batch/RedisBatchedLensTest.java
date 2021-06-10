package org.factcast.factus.redis.batch;

import lombok.NonNull;
import org.factcast.factus.redis.AbstractRedisManagedProjection;
import org.redisson.api.RedissonClient;

@RedisBatched
class ARedisManagedProjection extends AbstractRedisManagedProjection {

  public ARedisManagedProjection(@NonNull RedissonClient redisson) {
    super(redisson);
  }
}
