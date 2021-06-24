package org.factcast.factus.redis;

import lombok.NonNull;
import org.factcast.factus.projection.SubscribedProjection;
import org.redisson.api.RedissonClient;

public abstract class AbstractRedisSubscribedProjection extends AbstractRedisProjection
    implements SubscribedProjection {
  public AbstractRedisSubscribedProjection(@NonNull RedissonClient redisson) {
    super(redisson);
  }
}
