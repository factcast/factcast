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
package org.factcast.itests.factus.load;

import java.util.*;

import org.factcast.factus.event.EventObject;
import org.factcast.factus.event.Specification;

import com.google.common.collect.Sets;

import lombok.ToString;

@Specification(ns = "users", type = "UserCreated", version = 1)
@ToString
public class UserCreatedV1 implements EventObject {

  private UUID id = UUID.randomUUID();

  private String lastName;

  private String firstName;

  public UserCreatedV1(String lastName, String firstName) {
    this.lastName = lastName;
    this.firstName = firstName;
  }

  protected UserCreatedV1() {}

  @Override
  public Set<UUID> aggregateIds() {
    return Sets.newHashSet(id);
  }
}
