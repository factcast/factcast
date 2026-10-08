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

import com.fasterxml.jackson.databind.node.*;
import jakarta.annotation.Nullable;
import java.util.*;
import lombok.*;
import org.factcast.core.util.FactCastJson;
import org.factcast.factus.event.MetaMap;

@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(of = "id")
// TODO remove this class
public class TestFact implements Fact {

  UUID id = UUID.randomUUID();

  long serial = 42L;

  Set<UUID> aggIds = new LinkedHashSet<>();

  String type = "test";

  int version = 1;

  String ns = "default";

  String jsonPayload = "{}";

  MetaMap meta = new MetaMap();

  public TestFact() {
    meta().set("_ser", String.valueOf(this.serial));
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
    if (header == null) {
      header = FactCastJson.readValue(FactHeader.class, jsonHeader());
    }
    return header;
  }

  @Nullable
  @Override
  public String meta(@NonNull String key) {
    return meta.getFirst(key);
  }

  @SneakyThrows
  public static Fact copy(@NonNull Fact f) {
    String header = f.jsonHeader();
    String payload = f.jsonPayload();
    ObjectNode h = (ObjectNode) FactCastJson.readTree(header);
    h.set("id", new TextNode(UUID.randomUUID().toString()));
    return Fact.of(h.toString(), payload);
  }

  public TestFact meta(String k, String v) {
    meta().set(k, v);
    return this;
  }

  public TestFact meta(String k, Collection<String> vc) {
    vc.forEach(v -> meta().add(k, v));
    return this;
  }
}
