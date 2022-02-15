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

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.*;
import lombok.*;
import org.factcast.core.util.FactCastJson;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(of = "id")
// TODO remove this class
public class TestFact implements Fact {

  UUID id = UUID.randomUUID();

  Set<UUID> aggIds = new LinkedHashSet<>();

  String type = "test";

  int version = 1;

  String ns = "default";

  String jsonPayload = "{}";

  Map<String, String> meta = new HashMap<>();

  @Override
  public String meta(String key) {
    return meta.get(key);
  }

  public TestFact meta(String key, String value) {
    meta.put(key, value);
    return this;
  }

  @Override
  @SneakyThrows
  public String jsonHeader() {
    return FactCastJson.writeValueAsString(this);
  }

  public TestFact aggId(@NonNull UUID aggId, UUID... otherAggIds) {
    aggIds.add(aggId);
    if (otherAggIds != null) {
      aggIds.addAll(Arrays.asList(otherAggIds));
    }
    return this;
  }

  private transient FactHeader header;

  @Override
  public @NonNull FactHeader header() {
    if (header == null) header = FactCastJson.readValue(FactHeader.class, jsonHeader());
    return header;
  }

  @SneakyThrows
  public static Fact copy(@NonNull Fact f) {
    String header = f.jsonHeader();
    String payload = f.jsonPayload();
    ObjectNode h = (ObjectNode) FactCastJson.readTree(header);
    h.set("id", new TextNode(UUID.randomUUID().toString()));
    return Fact.of(h.toString(), payload);
  }
}
