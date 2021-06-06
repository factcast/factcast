package org.factcast.factus.redis;

import javax.annotation.Nullable;
import org.factcast.factus.projection.Projection;
import org.factcast.factus.projector.ProjectorLens;
import org.factcast.factus.projector.ProjectorPlugin;

public class RedisTransactionalPlugin implements ProjectorPlugin {

  @Nullable
  @Override
  public ProjectorLens lensFor(Projection p) {
    if (p instanceof RedisProjection) {
      return new RedisTransactionalLens((RedisProjection) p);
    } else {
      return null;
    }
  }
}
