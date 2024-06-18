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

import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.factus.Handler;
import org.factcast.factus.redis.batch.RedisBatched;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.redisson.api.RedissonClient;

@ProjectionMetaData(revision = 1)
@RedisBatched
public class ARedisBatchedManagedProjection extends AbstractRedisManagedProjection {
  public ARedisBatchedManagedProjection(@NonNull RedissonClient redisson) {
    super(redisson);
  }

  @Handler
  void apply(Fact f) {}
}
