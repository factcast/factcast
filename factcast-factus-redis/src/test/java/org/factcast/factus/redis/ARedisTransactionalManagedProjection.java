package org.factcast.factus.redis;

import lombok.NonNull;
import org.factcast.factus.redis.tx.RedisTransactional;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.redisson.api.RedissonClient;

@ProjectionMetaData(serial = 1)
@RedisTransactional
public class ARedisTransactionalManagedProjection extends AbstractRedisManagedProjection {
  public ARedisTransactionalManagedProjection(@NonNull RedissonClient redisson) {
    super(redisson);
  }
}
