package org.factcast.factus.redis;

import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.factus.Handler;
import org.factcast.factus.redis.batch.RedisBatched;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.redisson.api.RedissonClient;

@ProjectionMetaData(serial = 1)
@RedisBatched
public class ARedisBatchedManagedProjection extends AbstractRedisManagedProjection {
  public ARedisBatchedManagedProjection(@NonNull RedissonClient redisson) {
    super(redisson);
  }

  @Handler
  void apply(Fact f) {}
}
