/*
 * Copyright © 2017-2021 factcast.org
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
package org.factcast.example.client.spring.boot2.hello;

import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import org.factcast.factus.Handler;
import org.factcast.factus.projection.SnapshotProjection;

public class UserNames implements SnapshotProjection {
  private static final long serialVersionUID = -1L;
  @Getter private final Set<String> userNames = new HashSet<>();

  @Handler
  void apply(UserCreated created) {
    userNames().add(created.firstName());
  }

  //  @Handler
  //   void apply(UserDeleted deleted) {
  //    userNames().remove(deleted.aggregateId());
  //  }

  int count() {
    return userNames().size();
  }

  boolean contains(String name) {
    return userNames().contains(name);
  }

  void clear() {
    userNames().clear();
  }
}
