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

import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.Handler;
import org.factcast.factus.redis.AbstractRedisManagedProjection;
import org.factcast.factus.redis.UUIDCodec;
import org.factcast.factus.redis.tx.RedisTransactional;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.factcast.itests.factus.event.UserCreated;
import org.factcast.itests.factus.event.UserDeleted;
import org.redisson.api.RMap;
import org.redisson.api.RTransaction;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.redisson.codec.CompositeCodec;
import org.redisson.codec.LZ4Codec;
import org.redisson.codec.MarshallingCodec;

@Slf4j
@ProjectionMetaData(serial = 1)
@RedisTransactional(bulkSize = 3, timeout = 10000000)
public class TxRedissonManagedUserNames extends AbstractRedisManagedProjection {

  protected final Codec codec =
      new CompositeCodec(UUIDCodec.INSTANCE, new LZ4Codec(new MarshallingCodec()));

  public TxRedissonManagedUserNames(RedissonClient redisson) {
    super(redisson);
  }

  public RMap<UUID, String> userNames() {
    return redisson.getMap(redisKey(), codec);
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

  // ---- processing:

  @SneakyThrows
  @Handler
  protected void apply(UserCreated created, RTransaction tx) {
    RMap<UUID, String> userNames = tx.getMap(redisKey(), codec);
    userNames.fastPut(created.aggregateId(), created.userName());
  }

  @SneakyThrows
  @Handler
  protected void apply(UserDeleted deleted, RTransaction tx) {
    tx.getMap(redisKey(), codec).fastRemove(deleted.aggregateId());
  }
}
