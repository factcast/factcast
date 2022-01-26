package org.factcast.factus.dynamodb;

import lombok.NonNull;
import org.factcast.factus.projection.Projection;
import org.redisson.api.RedissonClient;

public interface DynamoProjection extends Projection {
  @NonNull
  RedissonClient redisson();
}
