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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.factcast.factus.Handler;
import org.factcast.factus.redis.AbstractRedisManagedProjection;
import org.factcast.factus.redis.tx.RedisTransactional;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.factcast.itests.factus.event.UserCreated;
import org.factcast.itests.factus.event.UserDeleted;
import org.redisson.api.RTransaction;
import org.redisson.api.RedissonClient;

public class RedisTransactionalProjectionExample {

  @ProjectionMetaData(serial = 1)
  @RedisTransactional
  public static class UserNames extends AbstractRedisManagedProjection {

    public UserNames(RedissonClient redisson) {
      super(redisson);
    }

    public List<String> getUserNames() {
      Map<UUID, String> userNames = redisson.getMap(redisKey());
      return new ArrayList<>(userNames.values());
    }

    @Handler
    void apply(UserCreated e, RTransaction tx) {
      Map<UUID, String> userNames = tx.getMap(redisKey());
      userNames.put(e.aggregateId(), e.userName());
    }

    @Handler
    void apply(UserDeleted e, RTransaction tx) {
      tx.getMap(redisKey()).remove(e.aggregateId());
    }
  }
}
