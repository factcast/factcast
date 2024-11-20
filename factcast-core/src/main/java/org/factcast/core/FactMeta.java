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

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import java.io.IOException;
import java.util.*;
import lombok.*;
import lombok.experimental.Delegate;

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
public class FactMeta implements Map<String, String> {
  @Delegate(excludes = ExcludeDelegationFromMultiMap.class)
  private final Multimap<String, String> backing = ArrayListMultimap.create();

  public static FactMeta of(String k1, String v1) {
    FactMeta ret = new FactMeta();
    ret.put(k1, v1);
    return ret;
  }

  public static FactMeta of(String k1, String v1, String k2, String v2) {
    FactMeta ret = of(k1, v1);
    ret.put(k2, v2);
    return ret;
  }

  @Override
  public String get(Object key) {
    return firstElement(backing.get((String) key));
  }

  @Override
  public String put(String key, String value) {
    if (key.startsWith("_")) backing.removeAll(key);
    backing.put(key, value);
    return null;
  }

  @Override
  public String remove(Object key) {
    return firstElement(backing.removeAll(key));
  }

  private static String firstElement(Collection<String> col) {
    return col.stream().findFirst().orElse(null);
  }

  @Override
  public void putAll(Map<? extends String, ? extends String> m) {
    if (m != null) m.forEach(backing::put);
  }

  public Collection<String> getAll(String k) {
    return backing.get(k);
  }

  @Override
  @NonNull
  public Set<Entry<String, String>> entrySet() {
    return new HashSet<>(backing.entries());
  }

  public void set(String k, @NonNull String v) {
    backing.removeAll(k);
    backing.put(k, v);
  }

  private interface ExcludeDelegationFromMultiMap {
    String get(String key);

    String put(String k, String v);

    boolean putAll(Multimap<String, String> multimap);
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
        throws IOException, JacksonException {
      FactMeta ret = new FactMeta();

      JsonNode node = jp.getCodec().readTree(jp);
      Iterator<Entry<String, JsonNode>> fields = node.fields();
      fields.forEachRemaining(
          e -> {
            String k = e.getKey();
            JsonNode n = e.getValue();
            if (n.isNull()) ret.put(k, null);
            else if (n.isTextual()) ret.put(k, n.textValue());
            else if (n.isNumber()) ret.put(k, n.numberValue().toString());
            else if (n.isBoolean()) ret.put(k, Boolean.valueOf(n.booleanValue()).toString());
            else {
              if (!n.isArray()) throw new IllegalStateException("expected array but got " + n);

              n.iterator().forEachRemaining(v -> ret.put(k, v.textValue()));
            }
          });
      return ret;
    }
  }
}
