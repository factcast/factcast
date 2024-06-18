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
import org.factcast.factus.Handler;
import org.factcast.factus.projection.Projection;
import org.factcast.itests.factus.event.UserCreated;
import org.factcast.itests.factus.event.UserDeleted;

public interface UserNames extends Projection {

  Map<UUID, String> userNames();

  @Handler
  default void apply(UserCreated created) {
    userNames().put(created.aggregateId(), created.userName());
  }

  @Handler
  default void apply(UserDeleted deleted) {
    userNames().remove(deleted.aggregateId());
  }

  default int count() {
    return userNames().size();
  }

  default boolean contains(String name) {
    return userNames().containsValue(name);
  }

  default Set<String> names() {
    return new HashSet<>(userNames().values());
  }

  default void clear() {
    userNames().clear();
  }
}
