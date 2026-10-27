/*
 * Copyright © 2017-2023 factcast.org
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
package org.factcast.server.ui.plugins;

import java.util.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;

/** meta-data for one fact (json entry) */
@Getter(AccessLevel.PROTECTED)
public class JsonEntryMetaData {
  public static final String HEADER = "header.";

  public static final String PAYLOAD = "payload.";

  private final Map<String, Collection<String>> annotations = new HashMap<>();

  private final Map<String, Collection<String>> hoverContent = new HashMap<>();

  private final Map<String, FilterOptions> filterOptions = new HashMap<>();

  public void annotateHeader(@NonNull String path, @NonNull String value) {
    var p = HEADER + path;
    var l = annotations.getOrDefault(p, new ArrayList<>());
    l.add(value);
    annotations.put(p, l);
  }

  public void annotatePayload(@NonNull String path, @NonNull String value) {
    var p = PAYLOAD + path;
    var l = annotations.getOrDefault(p, new ArrayList<>());
    l.add(value);
    annotations.put(p, l);
  }

  public void addHeaderHoverContent(@NonNull String path, @NonNull String value) {
    var p = HEADER + path;
    var l = hoverContent.getOrDefault(p, new ArrayList<>());
    l.add(value);
    hoverContent.put(p, l);
  }

  public void addPayloadHoverContent(@NonNull String path, @NonNull String value) {
    var p = PAYLOAD + path;
    var l = hoverContent.getOrDefault(p, new ArrayList<>());
    l.add(value);
    hoverContent.put(p, l);
  }

  public void addHeaderMetaFilterOption(
      @NonNull String path, String key, Collection<String> value) {
    var p = HEADER + path;
    filterOptions.put(p, FilterOptions.forMeta(key, value));
  }

  public void addPayloadAggregateIdFilterOption(@NonNull String path, UUID aggregateId) {
    var p = PAYLOAD + path;
    filterOptions.put(p, FilterOptions.forAggregateId(aggregateId));
  }

  public record FilterOptions(UUID aggregateId, MultiMetaFilterOption meta) {
    public static FilterOptions forAggregateId(UUID aggregateId) {
      return new FilterOptions(aggregateId, null);
    }

    public static FilterOptions forMeta(String key, Collection<String> value) {
      return new FilterOptions(null, new MultiMetaFilterOption(key, value));
    }
  }

  public record MultiMetaFilterOption(String key, Collection<String> value) {}
}
