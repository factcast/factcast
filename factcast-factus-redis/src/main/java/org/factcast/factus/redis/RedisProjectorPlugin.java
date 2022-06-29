/*
 * Copyright © 2017-2022 factcast.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.factcast.factus.redis;

import java.util.Collection;
import java.util.Collections;
import lombok.NonNull;
import org.factcast.factus.projection.Projection;
import org.factcast.factus.projector.ProjectorLens;
import org.factcast.factus.projector.ProjectorPlugin;
import org.factcast.factus.redis.batch.RedisBatched;
import org.factcast.factus.redis.batch.RedisBatchedLens;
import org.factcast.factus.redis.tx.RedisTransactional;
import org.factcast.factus.redis.tx.RedisTransactionalLens;
import org.redisson.api.RedissonClient;

public class RedisProjectorPlugin implements ProjectorPlugin {

  @Override
  public Collection<ProjectorLens> lensFor(@NonNull Projection p) {
    if (p instanceof RedisProjection) {

      RedisTransactional transactional = p.getClass().getAnnotation(RedisTransactional.class);
      RedisBatched batched = p.getClass().getAnnotation(RedisBatched.class);

      if (transactional != null && batched != null) {
        throw new IllegalStateException(
            "RedisProjections cannot use both @"
                + RedisTransactional.class.getSimpleName()
                + " and @"
                + RedisBatched.class.getSimpleName()
                + ". Offending class:"
                + p.getClass().getName());
      }

      RedisProjection redisProjection = (RedisProjection) p;
      RedissonClient redissonClient = redisProjection.redisson();

      if (transactional != null) {
        return Collections.singletonList(
            new RedisTransactionalLens(redisProjection, redissonClient));
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
