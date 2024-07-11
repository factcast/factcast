/*
 * Copyright © 2017-2024 factcast.org
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
package org.factcast.example.client.dynamo.hello.events;

import com.google.common.collect.Sets;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.event.Specification;

@Specification(ns = "users", type = "UserChanged", version = 1)
@ToString
@Getter
public class UserChangedV1 implements EventObject {
  private UUID aggregateId;

  String lastName;

  String firstName;

  @Override
  public Set<UUID> aggregateIds() {
    return Sets.newHashSet(aggregateId);
  }
}
