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
package org.factcast.server.grpc.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;
import java.util.*;
import java.util.stream.Collectors;
import lombok.*;

@NoArgsConstructor
@Data
public class FactCastAccount {
  private static final long serialVersionUID = 42;
  public static final FactCastAccount GOD =
      new FactCastAccount("GODMODE") {
        @Override
        public boolean canRead(String ns) {
          return true;
        }

        @Override
        public boolean canWrite(String ns) {
          return true;
        }
      };

  @Getter private String id;

  @JsonProperty("roles")
  private final List<String> roleNames = new LinkedList<>();

  @VisibleForTesting
  @Getter(value = AccessLevel.PROTECTED)
  private List<FactCastRole> roles;

  public void initialize(FactCastAccessConfiguration config) {
    if (id == null) {
      throw new IllegalArgumentException("Account without 'id' found.");
    }

    roles = new LinkedList<>();
    roleNames.forEach(
        n -> {
          Optional<FactCastRole> r = config.findRoleById(n);
          roles.add(
              r.orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          "Unknown role '" + n + "'. Definition not found.")));
        });
  }

  public boolean canWrite(String ns) {
    if (roles == null) {
      throw new IllegalStateException("Not yet initialized");
    }

    List<Boolean> results =
        roles.stream()
            .map(r -> r.canWrite(ns))
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
    if (results.contains(false)) {
      return false;
    }
    return results.contains(true);
  }

  public boolean canRead(String ns) {
    if (roles == null) {
      throw new IllegalStateException("Not yet initialized");
    }

    List<Boolean> results =
        roles.stream()
            .map(r -> r.canRead(ns))
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
    if (results.contains(false)) {
      return false;
    }
    return results.contains(true);
  }

  @VisibleForTesting
  protected FactCastAccount role(@NonNull FactCastRole... other) {
    if (roles == null) {
      roles = new LinkedList<>();
    }

    roles.addAll(Arrays.asList(other));
    return this;
  }

  public FactCastAccount(@NonNull String id) {
    this.id = id;
  }
}
