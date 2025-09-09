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
package org.factcast.itests.factus.event.film;

import java.util.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.*;
import org.factcast.factus.projection.LocalSubscribedProjection;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.springframework.stereotype.Component;

@Slf4j
@ProjectionMetaData(revision = 1)
@Component
public class SubscribedLucasNames extends LocalSubscribedProjection {

  @HandlerFor(ns = "*", type = "*")
  @FilterByMeta(key = "director", value = "lucas")
  void apply(CharacterCreated created) {
    userNames().put(created.aggregateId(), created.name());
    log.info("applied {}", created);
  }

  @Getter private final Map<UUID, String> userNames = new HashMap<>();

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
