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
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.redis.AbstractRedisManagedProjection;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;

@Slf4j
@ProjectionMetaData(projectionVersion = 1)
public class RedissonManagedUserNames extends AbstractRedisManagedProjection implements UserNames {

  @Getter private final RMap<UUID, String> userNames;

  public RedissonManagedUserNames(RedissonClient redisson) {
    super(redisson);
    userNames = redisson.getMap(getClass().getSimpleName());
  }
}
