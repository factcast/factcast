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

import com.fasterxml.jackson.annotation.*;
import jakarta.annotation.Nullable;
import java.util.*;
import lombok.*;
import org.factcast.factus.event.MetaMap;

@Getter
@Setter(AccessLevel.PROTECTED)
@JsonIgnoreProperties(ignoreUnknown = true)
@EqualsAndHashCode(of = {"id"})
public class FactHeader {

  @JsonProperty UUID id;

  @JsonProperty String ns;

  @JsonProperty String type;

  @JsonProperty int version;

  @JsonProperty Set<UUID> aggIds = new HashSet<>();

  @NonNull @JsonProperty final MetaMap meta = new MetaMap();

  @Nullable
  // could be null if not yet published to the factcast server. This should only happen in unit
  // tests.
  public Long serial() {
    String s = meta.getFirst("_ser");
    if (s != null) {
      return Long.parseLong(s);
    } else {
      return null;
    }
  }

  @Nullable
  // could be null if not yet published to the factcast server. This should only happen in unit
  // tests.
  public Long timestamp() {
    String s = meta.getFirst("_ts");
    if (s != null) {
      return Long.parseLong(s);
    } else {
      return null;
    }
  }

  /**
   * @param key
   * @return
   * @deprecated use meta() instead
   */
  @Deprecated
  @Nullable
  public String meta(@NonNull String key) {
    return meta.getFirst(key);
  }
}
