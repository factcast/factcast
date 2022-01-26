package org.factcast.factus.dynamodb;

import lombok.NonNull;
import org.redisson.api.RedissonClient;

public abstract class AbstractDynamoManagedProjection extends AbstractDynamoProjection
        implements DynamoManagedProjection {
  public AbstractDynamoManagedProjection(@NonNull RedissonClient redisson) {
    super(redisson);
  }
}
