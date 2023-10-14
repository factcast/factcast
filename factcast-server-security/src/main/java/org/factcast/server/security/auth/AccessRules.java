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
import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import lombok.*;

@Data
@NoArgsConstructor
// exclusion has precedents
public class AccessRules implements Serializable {
  @Serial private static final long serialVersionUID = 42;

  @VisibleForTesting
  @Getter(value = AccessLevel.PROTECTED)
  private List<String> include = new LinkedList<>();

  @VisibleForTesting
  @Getter(value = AccessLevel.PROTECTED)
  private List<String> exclude = new LinkedList<>();

  Boolean includes(@NonNull String ns) {
    boolean excluded = exclude.stream().anyMatch(s -> matches(s, ns));
    if (excluded) return false;

    if (include.stream().anyMatch(s -> matches(s, ns))) return true;

    return null;
  }

  private boolean matches(@NonNull String pattern, @NonNull String ns) {
    if (pattern.equals(ns) || "*".equals(pattern)) return true;

    return pattern.endsWith("*") && ns.startsWith(pattern.substring(0, pattern.length() - 1));
  }
}
