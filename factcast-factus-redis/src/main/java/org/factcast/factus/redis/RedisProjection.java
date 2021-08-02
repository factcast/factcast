package org.factcast.factus.redis;

import lombok.NonNull;
import org.factcast.factus.projection.Projection;
import org.redisson.api.RedissonClient;

public interface RedisProjection extends Projection {
  @NonNull
  RedissonClient redisson();
}
