package org.factcast.factus.redis.batch;

import lombok.NonNull;
import org.factcast.factus.redis.AbstractRedisProjection;
import org.redisson.api.RedissonClient;

@RedisBatched
class ARedisProjection extends AbstractRedisProjection {

  public ARedisProjection(@NonNull RedissonClient redisson) {
    super(redisson);
  }
}
