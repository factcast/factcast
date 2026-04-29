/*
 * Copyright © 2017-2025 factcast.org
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

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.Handler;
import org.factcast.factus.redis.tx.AbstractRedisTxManagedProjection;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.factcast.itests.factus.event.UserCreated;
import org.redisson.api.*;

@Slf4j
@ProjectionMetaData(revision = 1)
public class BlockingRedisTxManagedUserNames extends AbstractRedisTxManagedProjection {

  public BlockingRedisTxManagedUserNames(RedissonClient redisson) {
    super(redisson);
  }

  @SneakyThrows
  @Handler
  public void apply(UserCreated created) {
    log.info("BlockingRedisManagedUserNames:apply");
    Thread.sleep(500);
  }
}
