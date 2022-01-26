package org.factcast.factus.dynamodb;

import java.util.Collection;
import java.util.Collections;
import lombok.NonNull;
import org.factcast.factus.dynamodb.tx.DynamoTransactional;
import org.factcast.factus.dynamodb.tx.DynamoTransactionalLens;
import org.factcast.factus.projection.Projection;
import org.factcast.factus.projector.ProjectorLens;
import org.factcast.factus.projector.ProjectorPlugin;
import org.factcast.factus.redis.batch.RedisBatched;
import org.factcast.factus.redis.batch.RedisBatchedLens;
import org.redisson.api.RedissonClient;

public class DynamoProjectorPlugin implements ProjectorPlugin {

  @Override
  public Collection<ProjectorLens> lensFor(@NonNull Projection p) {
    if (p instanceof DynamoProjection) {

      DynamoTransactional transactional = p.getClass().getAnnotation(DynamoTransactional.class);
      RedisBatched batched = p.getClass().getAnnotation(RedisBatched.class);

      if (transactional != null && batched != null) {
        throw new IllegalStateException(
                "RedisProjections cannot use both @"
                        + DynamoTransactional.class.getSimpleName()
                        + " and @"
                        + RedisBatched.class.getSimpleName()
                        + ". Offending class:"
                        + p.getClass().getName());
      }

      DynamoProjection redisProjection = (DynamoProjection) p;
      RedissonClient redissonClient = redisProjection.redisson();

      if (transactional != null) {
        return Collections.singletonList(
                new DynamoTransactionalLens(redisProjection, redissonClient));
      }

      //noinspection SingleStatementInBlock
      if (batched != null) {
        return Collections.singletonList(new RedisBatchedLens(redisProjection, redissonClient));
      }
    }

    // any other case:
    return Collections.emptyList();
  }
}
