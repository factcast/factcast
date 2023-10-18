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

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.*;
import lombok.Getter;
import lombok.NonNull;
import org.factcast.core.util.FactCastJson;

@Getter
public class JsonViewEntries {
  // should not be sent to client
  private final String json;
  private final String meta;

  public JsonViewEntries(@NonNull List<JsonViewEntry> entries) {
    json =
        FactCastJson.writeValueAsPrettyString(entries.stream().map(JsonViewEntry::fact).toList());
    meta = FactCastJson.writeValueAsString(new MetaData(entries));
  }

  static class MetaData {
    @JsonProperty private final Map<String, Collection<String>> annotations = new HashMap<>();

    @JsonProperty private final Map<String, Collection<String>> hoverContent = new HashMap<>();

    MetaData(List<JsonViewEntry> entries) {
      for (int index = 0; index < entries.size(); index++) {
        var entry = entries.get(index);
        var prefix = "$.[" + index + "].";
        JsonEntryMetaData meta = entry.metaData();
        meta.annotations().forEach((k, v) -> annotations.put(prefix + k, v));
        meta.hoverContent().forEach((k, v) -> hoverContent.put(prefix + k, v));
      }
    }
  }
}
