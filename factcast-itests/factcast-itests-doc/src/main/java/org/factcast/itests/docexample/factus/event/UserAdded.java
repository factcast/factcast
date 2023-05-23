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
package org.factcast.itests.docexample.factus.event;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.event.Specification;

@Getter
@Specification(ns = "user", type = "UserAdded", version = 1)
public class UserAdded implements EventObject {

  private UUID userId;
  private String email;

  // used by Jackson deserializer
  protected UserAdded() {}

  public static UserAdded of(UUID userId, String email) {
    UserAdded fact = new UserAdded();
    fact.userId = userId;
    fact.email = email;
    return fact;
  }

  @Override
  public Set<UUID> aggregateIds() {
    return Collections.emptySet();
  }
}
