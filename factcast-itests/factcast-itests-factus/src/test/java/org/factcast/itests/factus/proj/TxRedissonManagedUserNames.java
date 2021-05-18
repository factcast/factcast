/*
 * Copyright © 2017-2020 factcast.org
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

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.Handler;
import org.factcast.factus.redis.RedisTransactional;
import org.factcast.itests.factus.event.UserCreated;
import org.factcast.itests.factus.event.UserDeleted;
import org.redisson.api.RMap;
import org.redisson.api.RTransaction;
import org.redisson.api.RedissonClient;

@Slf4j
@RedisTransactional(size = 2)
public class TxRedissonManagedUserNames extends AbstractRedisManagedProjection {

  @Getter private final RMap<UUID, String> userNames;

  public TxRedissonManagedUserNames(RedissonClient redisson) {
    super(redisson);
    userNames = redisson.getMap(getClass().getSimpleName());
  }

  @Handler
  void apply(UserCreated created, RTransaction tx) {
    tx.getMap(getClass().getSimpleName()).put(created.aggregateId(), created.userName());
  }

  @Handler
  void apply(UserDeleted deleted, RTransaction tx) {
    tx.getMap(getClass().getSimpleName()).remove(deleted.aggregateId());
  }

  public int count() {
    return userNames().size();
  }

  public boolean contains(String name) {
    return userNames().containsValue(name);
  }

  public Set<String> names() {
    return new HashSet<>(userNames().values());
  }

  public void clear() {
    userNames().clear();
  }
}
