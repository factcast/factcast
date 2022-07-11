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
package org.factcast.core.subscription.transformation;

import java.util.*;

import lombok.NonNull;

public class RequestedVersions {
  private final Map<String, Map<String, Set<Integer>>> c = new HashMap<>();

  public void add(@NonNull String ns, @NonNull String type, int version) {
    get(ns, type).add(version);
  }

  public Set<Integer> get(@NonNull String ns, String type) {
    return c.computeIfAbsent(ns, key -> new HashMap<>())
        .computeIfAbsent(type, k -> new HashSet<>());
  }

  public boolean matches(@NonNull String ns, String type, int version) {
    Set<Integer> set = get(ns, type);
    return set.contains(0) || set.contains(version);
  }
}
