/*
 * Copyright © 2017-2024 factcast.org
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

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.*;
import com.google.common.collect.*;
import java.io.IOException;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.*;

/**
 * Compromise, that breaks a bit of the Map contract. We basically want a Map<String,String> that is
 * capable of hosting multiple values per key. However, we would not want to break the API for that,
 * or reduce the readability of the header for non-engineers, so that we extend with getAll() that
 * returns a collection of values to make it possible to opt-in this feature.
 *
 * <p>get(String) will only return the first if multiple values were put to the map.
 *
 * <p>All faccast filtering will be done on the potential collection if it exists. <br>
 * Do not use this somewhere else.
 */
@JsonSerialize(using = FactMeta.Serializer.class)
@JsonDeserialize(using = FactMeta.Deserializer.class)
public class FactMeta {
  private final Multimap<String, String> backing = ArrayListMultimap.create();

  public static FactMeta of(String k1, String v1) {
    FactMeta ret = new FactMeta();
    ret.add(k1, v1);
    return ret;
  }

  public static FactMeta of(String k1, String v1, String k2, String v2) {
    FactMeta ret = of(k1, v1);
    ret.add(k2, v2);
    return ret;
  }

  // not symmetric with setSingle, but we settled for this naming to be crystal clear
  public @Nullable String getFirst(@NonNull String key) {
    return firstElement(backing.get(key));
  }

  /**
   * @param key
   * @return empty collection if key not found
   */
  @NonNull
  public Collection<String> getAll(String key) {
    return backing.get(key);
  }

  public void setSingle(String k, String v) {
    backing.removeAll(k);
    add(k, v);
  }

  public String remove(@NonNull String key) {
    return firstElement(backing.removeAll(key));
  }

  private static String firstElement(@NonNull Collection<String> col) {
    return col.stream().findFirst().orElse(null);
  }

  @NonNull
  public Set<String> keySet() {
    return backing.entries().stream().map(Map.Entry::getKey).collect(Collectors.toSet());
  }

  public void add(@NonNull String k, @Nullable String v) {
    backing.put(k, v);
  }

  public void forEachEntry(BiConsumer<String, String> consumer) {
    backing.entries().forEach(e -> consumer.accept(e.getKey(), e.getValue()));
  }

  public void forEachDistinctKey(BiConsumer<String, Collection<String>> consumer) {
    backing.keySet().forEach(k -> consumer.accept(k, backing.get(k)));
  }

  public boolean containsKey(String k) {
    return backing.keySet().contains(k);
  }

  static class Serializer extends JsonSerializer<FactMeta> {

    @Override
    @SneakyThrows
    public void serialize(
        FactMeta metaMap, JsonGenerator jgen, SerializerProvider serializerProvider)
        throws IOException {
      if (metaMap != null) {
        jgen.writeStartObject();
        for (String k : metaMap.keySet()) {
          Collection<String> values = metaMap.getAll(k);
          if (values.size() == 1) {
            jgen.writeStringField(k, values.iterator().next());
          } else {
            // also valid if size==0
            String[] v = values.toArray(new String[0]);
            jgen.writeFieldName(k);
            jgen.writeArray(v, 0, v.length);
          }
        }
        jgen.writeEndObject();
      }
    }
  }

  static class Deserializer extends JsonDeserializer<FactMeta> {
    @Override
    public FactMeta deserialize(JsonParser jp, DeserializationContext deserializationContext)
        throws IOException {
      FactMeta ret = new FactMeta();

      JsonNode node = jp.getCodec().readTree(jp);
      Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
      fields.forEachRemaining(
          e -> {
            String k = e.getKey();
            JsonNode n = e.getValue();
            if (n.isNull()) ret.setSingle(k, null);
            else if (n.isTextual()) ret.add(k, n.textValue());
            else if (n.isNumber()) ret.add(k, n.numberValue().toString());
            else if (n.isBoolean()) ret.add(k, Boolean.toString(n.booleanValue()));
            else {
              if (!n.isArray()) throw new IllegalStateException("expected array but got " + n);

              n.iterator().forEachRemaining(v -> ret.add(k, v.textValue()));
            }
          });
      return ret;
    }
  }
}
