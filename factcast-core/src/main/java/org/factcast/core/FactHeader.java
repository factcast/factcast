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
package org.factcast.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.*;
import javax.annotation.Nullable;
import lombok.*;

@Getter
@Setter(value = AccessLevel.PROTECTED)
@JsonIgnoreProperties(ignoreUnknown = true)
@EqualsAndHashCode(of = {"id"})
public class FactHeader {

  @JsonProperty @NonNull UUID id;

  @JsonProperty @NonNull String ns;

  @JsonProperty String type;

  @JsonProperty int version;

  @JsonProperty Set<UUID> aggIds = new HashSet<>();

  @JsonProperty final Map<String, String> meta = new HashMap<>();

  @Nullable
  // could be null if not yet published to the factcast server. This should only happen in unit
  // tests.
  Long serial() {
    String s = meta("_ser");
    if (s != null) {
      return Long.parseLong(s);
    } else return null;
  }

  @Nullable
  // could be null if not yet published to the factcast server. This should only happen in unit
  // tests.
  Long timestamp() {
    String s = meta("_ts");
    if (s != null) {
      return Long.parseLong(s);
    } else return null;
  }

  @Nullable
  String meta(@NonNull String key) {
    return meta.get(key);
  }
}
