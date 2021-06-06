package org.factcast.factus.redis;

import lombok.NonNull;
import lombok.val;
import org.factcast.factus.projection.ManagedProjection;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.redisson.api.RedissonClient;

public interface RedisProjection extends ManagedProjection {

  /**
   * opportunity to cache the created key
   *
   * @return
   */
  default @NonNull String redisKey() {
    return createRedisKey();
  }

  default @NonNull String createRedisKey() {

    StringBuilder sb = new StringBuilder(32);

    Class<?> c = getClass();
    while (c.getName().contains("$$EnhancerBySpring") || c.getName().contains("CGLIB")) {
      c = c.getSuperclass();
    }
    sb.append(c.getSimpleName());

    val pmd = c.getAnnotation(ProjectionMetaData.class);
    if (pmd != null) {
      sb.append(":");
      sb.append(pmd.serial());
    }

    return sb.toString();
  }

  @NonNull
  RedissonClient redisson();
}
