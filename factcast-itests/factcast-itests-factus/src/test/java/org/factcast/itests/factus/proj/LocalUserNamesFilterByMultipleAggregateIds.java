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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.FilterByAggId;
import org.factcast.factus.Handler;
import org.factcast.factus.projection.LocalManagedProjection;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.factcast.itests.factus.event.UserCreated;
import org.factcast.itests.factus.event.UserFired;

@Slf4j
@ProjectionMetaData(revision = 1)
public class LocalUserNamesFilterByMultipleAggregateIds extends LocalManagedProjection {
  ConcurrentHashMap<UUID, String> map = new ConcurrentHashMap<>();

  public Map<UUID, String> userNames() {
    return map;
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
  protected void apply(UserCreated created) {
    userNames().put(created.aggregateId(), created.userName());
  }

  @SneakyThrows
  @Handler
  @FilterByAggId({"00000000-0000-0000-0000-00000000000a", "00000000-0000-0000-0000-00000000000b"})
  protected void apply(UserFired fired) {
    userNames().remove(fired.sackedId());
  }
}
