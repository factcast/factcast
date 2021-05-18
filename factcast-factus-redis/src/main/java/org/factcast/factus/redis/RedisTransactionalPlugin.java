package org.factcast.factus.redis;

import org.factcast.factus.projection.Projection;
import org.factcast.factus.projector.ProjectorLens;
import org.redisson.api.RedissonClient;

public class RedisTransactionalPlugin extends AnnotationEnabledProjectorPlugin<RedisTransactional> {

  private static RedissonClient redisson;

  public RedisTransactionalPlugin() {
    super(RedisTransactional.class);
  }

  // TODO find a better way,
  public static void initialize(RedissonClient redisson) {
    RedisTransactionalPlugin.redisson = redisson;
  }

  @Override
  protected ProjectorLens createLens(RedisTransactional redisTransactional, Projection p) {
    if (redisson == null) {
      throw new IllegalStateException(
          "RedisTransactionalPlugin is not yet initialized. Please call RedisTransactionalPlugin::initialize before using @RedisTransactional");
    }
    return new RedisTransactionalLens(redisson, redisTransactional);
  }
}
