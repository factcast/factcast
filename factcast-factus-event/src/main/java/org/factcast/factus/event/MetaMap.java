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
package org.factcast.factus.event;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;
import tools.jackson.core.*;
import tools.jackson.databind.*;
import tools.jackson.databind.annotation.*;

/**
 * Multimaps as defined in guava or commons are probably a better idea to use in general. However,
 * as this module is used in very different contexts, we really want to avoid transitive
 * dependencies. This is very simple, and not too efficient.
 *
 * <p>A simple list of entries, where multiple keys are allowed.
 *
 * <p>Unlike maps, this allows for null values, so that if getFirst(key) does not indicate the
 * existance of the key. Use containsKey instead.
 */
@JsonDeserialize(using = MetaMap.Deserializer.class)
@JsonSerialize(using = MetaMap.Serializer.class)
public class MetaMap {

  private static class EmptyMetaMap extends MetaMap {
    @Override
    public MetaMap set(@NonNull String k, @Nullable String v) {
      throw new UnsupportedOperationException();
    }

    @Override
    public MetaMap add(@NonNull String k, @Nullable String v) {
      throw new UnsupportedOperationException();
    }
  }

  private static final MetaMap EMPTY = new EmptyMetaMap();

  public static MetaMap empty() {
    return EMPTY;
  }

  @Value(staticConstructor = "of")
  static class Entry<K, V> {
    K key;
    V value;
  }

  private final Collection<Entry<String, String>> entries = new ArrayList<>();

  public static MetaMap of(@NonNull String k1, @Nullable String v1) {
    MetaMap ret = new MetaMap();
    ret.add(k1, v1);
    return ret;
  }

  public static MetaMap of(
      @NonNull String k1, @Nullable String v1, @NonNull String k2, @Nullable String v2) {
    MetaMap ret = of(k1, v1);
    ret.add(k2, v2);
    return ret;
  }

  public static MetaMap from(@NonNull Map<String, String> regularMap) {
    MetaMap ret = new MetaMap();
    regularMap.forEach(ret::set);
    return ret;
  }

  // not symmetric with setSingle, but we settled for this naming to be crystal clear
  public @Nullable String getFirst(@NonNull String key) {
    return entries.stream()
        .filter(e -> e.key().equals(key))
        .findFirst()
        .map(Entry::value)
        .orElse(null);
  }

  /**
   * @param key
   * @return empty collection if key not found
   */
  @NonNull
  public List<String> getAll(@NonNull String key) {
    return entries.stream()
        .filter(e -> e.key().equals(key))
        .map(Entry::value)
        .collect(Collectors.toList());
  }

  /**
   * will remove any< preexisting values for the key and set it to the given one only
   *
   * @param k
   * @param v
   */
  public MetaMap set(@NonNull String k, @Nullable String v) {
    entries.removeIf(e -> e.key().equals(k));
    add(k, v);
    return this;
  }

  public void remove(@NonNull String key) {
    entries.removeIf(e -> e.key().equals(key));
  }

  @NonNull
  public Set<String> keySet() {
    return entries.stream().map(Entry::key).collect(Collectors.toSet());
  }

  public MetaMap add(@NonNull String k, @Nullable String v) {
    entries.add(Entry.of(k, v));
    return this;
  }

  public void forEachEntry(BiConsumer<String, String> consumer) {
    entries.forEach(e -> consumer.accept(e.key(), e.value));
  }

  public void forEachDistinctKey(@NonNull BiConsumer<String, Collection<String>> consumer) {
    entries.stream().map(Entry::key).distinct().forEach(e -> consumer.accept(e, getAll(e)));
  }

  public boolean containsKey(@NonNull String k) {
    return keySet().contains(k);
  }

  static class Serializer extends ValueSerializer<MetaMap> {

    @Override
    @SneakyThrows
    public void serialize(
        MetaMap metaMap, JsonGenerator jgen, SerializationContext serializerProvider)
        throws JacksonException {
      if (metaMap != null) {
        jgen.writeStartObject();
        for (String k : metaMap.keySet()) {
          Collection<String> values = metaMap.getAll(k);
          if (values.size() == 1) {
            jgen.writeStringProperty(k, values.iterator().next());
          } else {
            // also valid if size==0
            String[] v = values.toArray(new String[0]);
            jgen.writeName(k);
            jgen.writeArray(v, 0, v.length);
          }
        }
        jgen.writeEndObject();
      }
    }
  }

  static class Deserializer extends ValueDeserializer<MetaMap> {
    @Override
    public MetaMap deserialize(JsonParser jp, DeserializationContext deserializationContext)
        throws JacksonException {
      {
        MetaMap ret = new MetaMap();

        JsonNode node = jp.objectReadContext().readTree(jp);
        Iterator<Map.Entry<String, JsonNode>> fields = node.properties().iterator();
        fields.forEachRemaining(
            e -> {
              String k = e.getKey();
              JsonNode n = e.getValue();
              if (n.isNull()) {
                ret.set(k, null);
              } else if (n.isString()) {
                ret.add(k, n.asString());
              } else if (n.isNumber()) {
                ret.add(k, n.numberValue().toString());
              } else if (n.isBoolean()) {
                ret.add(k, Boolean.toString(n.booleanValue()));
              } else {
                if (!n.isArray()) {
                  throw new IllegalStateException("expected array but got " + n);
                }

                n.iterator().forEachRemaining(v -> ret.add(k, v.asString()));
              }
            });
        return ret;
      }
    }
  }
}
