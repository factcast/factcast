package org.factcast.factus.dynamodb;

import lombok.NonNull;
import org.factcast.factus.projection.SubscribedProjection;
import org.redisson.api.RedissonClient;

public abstract class AbstractDynamoSubscribedProjection extends AbstractDynamoProjection
        implements SubscribedProjection {
  public AbstractDynamoSubscribedProjection(@NonNull RedissonClient redisson) {
    super(redisson);
  }
}
