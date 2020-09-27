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

import java.time.Duration;
import java.util.UUID;
import lombok.NonNull;
import org.factcast.factus.projection.ManagedProjection;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

public abstract class AbstractRedisManagedProjection extends ManagedProjection {

  private final RBucket<UUID> stateBucket;

  private final RLock lock;

  public AbstractRedisManagedProjection(RedissonClient redisson) {
    stateBucket = redisson.getBucket("state_tracking_" + getClass().getSimpleName());
    lock = redisson.getLock("lock_" + getClass().getName());
  }

  @Override
  public UUID state() {
    return stateBucket.get();
  }

  @Override
  public void state(@NonNull UUID state) {
    stateBucket.set(state);
  }

  @Override
  public AutoCloseable acquireWriteToken(@NonNull Duration maxWait) {
    lock.lock();
    return lock::unlock;
  }
}
