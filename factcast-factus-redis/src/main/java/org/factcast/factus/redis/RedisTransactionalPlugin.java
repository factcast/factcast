package org.factcast.factus.redis;

import javax.annotation.Nullable;
import lombok.val;
import org.factcast.factus.projection.Projection;
import org.factcast.factus.projector.ProjectorLens;
import org.factcast.factus.projector.ProjectorPlugin;

public class RedisTransactionalPlugin // extends AnnotationEnabledProjectorPlugin<BatchApply> {
implements ProjectorPlugin {

  @Nullable
  @Override
  public ProjectorLens lensFor(Projection p) {
    if (p instanceof AbstractRedisProjection) {
      val rp = (AbstractRedisProjection) p;
      return new RedisTransactionalLens(
          rp.redissonTXManager(), p.getClass().getAnnotation(BatchApply.class));
    } else {
      return null;
    }
  }
}
