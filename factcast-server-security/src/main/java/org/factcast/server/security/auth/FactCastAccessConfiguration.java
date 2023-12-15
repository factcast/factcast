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
package org.factcast.server.security.auth;

import com.google.common.annotations.VisibleForTesting;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.factcast.core.util.FactCastJson;

@NoArgsConstructor
public class FactCastAccessConfiguration {
  @VisibleForTesting
  @Getter(value = AccessLevel.PROTECTED)
  private final List<FactCastRole> roles = new LinkedList<>();

  @Getter private final List<FactCastAccount> accounts = new LinkedList<>();

  private final Map<String, FactCastRole> roleIndex = new HashMap<>();

  private final Map<String, FactCastAccount> accountIndex = new HashMap<>();

  void initialize() {
    roles.forEach(r -> roleIndex.put(r.id(), r));

    accounts.forEach(
        r -> {
          accountIndex.put(r.id(), r);
          r.initialize(this);
        });
  }

  public Optional<FactCastRole> findRoleById(String name) {
    return Optional.ofNullable(roleIndex.get(name));
  }

  public Optional<FactCastAccount> findAccountById(String name) {
    return Optional.ofNullable(accountIndex.get(name));
  }

  public static FactCastAccessConfiguration read(InputStream is) {
    FactCastAccessConfiguration readValue =
        FactCastJson.readValue(FactCastAccessConfiguration.class, is);
    readValue.initialize();
    return readValue;
  }

  public static FactCastAccessConfiguration read(String json) {
    FactCastAccessConfiguration readValue =
        FactCastJson.readValue(FactCastAccessConfiguration.class, json);
    readValue.initialize();
    return readValue;
  }
}
