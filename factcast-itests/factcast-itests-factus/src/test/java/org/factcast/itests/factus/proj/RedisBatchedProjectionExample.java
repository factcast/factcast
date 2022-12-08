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
package org.factcast.itests.factus.proj;

import java.util.*;
import lombok.Getter;
import org.factcast.factus.Handler;
import org.factcast.factus.redis.AbstractRedisManagedProjection;
import org.factcast.factus.redis.batch.RedisBatched;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.factcast.itests.factus.event.UserCreated;
import org.factcast.itests.factus.event.UserDeleted;
import org.redisson.api.RBatch;
import org.redisson.api.RMap;
import org.redisson.api.RMapAsync;
import org.redisson.api.RedissonClient;

public class RedisBatchedProjectionExample {
  private RedisBatchedProjectionExample() {}

  @ProjectionMetaData(serial = 1)
  @RedisBatched
  public static class UserNames extends AbstractRedisManagedProjection {
    @Getter int count = 0;

    public UserNames(RedissonClient redisson) {
      super(redisson);
    }

    public List<String> getUserNames() {
      RMap<UUID, String> userNames = redisson.getMap(redisKey());
      return new ArrayList<>(userNames.values());
    }

    @Handler
    void apply(UserCreated created, RBatch batch) {
      RMapAsync<UUID, String> userNames = batch.getMap(redisKey());
      userNames.putAsync(created.aggregateId(), created.userName());
      count++;
    }

    @Handler
    void apply(UserDeleted deleted, RBatch batch) {
      batch.getMap(redisKey()).removeAsync(deleted.aggregateId());

      count++;
    }
  }
}
