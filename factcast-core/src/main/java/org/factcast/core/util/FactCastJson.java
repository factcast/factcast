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
package org.factcast.core.util;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import lombok.*;

/**
 * Statically shared ObjectMapper reader & writer to be used within FactCast for Headers and
 * FactCast-specific objects.
 *
 * <p>You must not change the configuration of this mapper, and it should not be used outside of
 * FactCast.
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FactCastJson {

  private static ObjectMapper objectMapper;

  private static ObjectReader reader;

  private static ObjectWriter writer;

  static {
    initializeObjectMapper();
  }

  @VisibleForTesting
  static AutoCloseable replaceObjectMapper(@NonNull ObjectMapper om) {
    objectMapper = om;
    writer = objectMapper.writer();
    reader = objectMapper.reader();
    return FactCastJson::initializeObjectMapper;
  }

  private static void initializeObjectMapper() {
    objectMapper = new ObjectMapper();
    objectMapper
        .setVisibility(PropertyAccessor.FIELD, Visibility.ANY)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    writer = objectMapper.writer();
    reader = objectMapper.reader();
  }

  @SneakyThrows
  public static <T> T copy(@NonNull T toCopy) {
    Class<?> c = toCopy.getClass();
    return reader.forType(c).readValue(writer.forType(c).writeValueAsString(toCopy));
  }

  @SneakyThrows
  public static <T> String writeValueAsString(@NonNull T value) {
    return objectMapper.writeValueAsString(value);
  }

  @SneakyThrows
  public static <T> T readValue(@NonNull Class<T> class1, @NonNull String json) {
    return reader.forType(class1).readValue(json);
  }

  @SneakyThrows
  public static <T> T readValue(@NonNull TypeReference<T> class1, @NonNull String json) {
    return reader.forType(class1).readValue(json);
  }

  @SneakyThrows
  public static <T> T readValue(@NonNull Class<T> class1, @NonNull InputStream json) {
    return reader.forType(class1).readValue(json);
  }

  public static ObjectNode toObjectNode(String json) {
    try {
      return (ObjectNode) objectMapper.readTree(json);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static ObjectNode newObjectNode() {
    return objectMapper.getNodeFactory().objectNode();
  }

  public static ArrayNode newArrayNode() {
    return objectMapper.getNodeFactory().arrayNode();
  }

  @SneakyThrows
  @Generated
  public static String writeValueAsPrettyString(Object o) {
    return objectMapper
        .writer(
            new DefaultPrettyPrinter().withObjectIndenter(new DefaultIndenter().withLinefeed("\n")))
        .writeValueAsString(o);
  }

  public static String addSerToHeader(long ser, String jsonHeader) {
    ObjectNode json = toObjectNode(jsonHeader);
    ObjectNode meta = (ObjectNode) json.get("meta");
    if (meta == null) {
      // create a new node
      meta = newObjectNode();
      json.set("meta", meta);
    }
    // set ser as attribute _ser
    meta.put("_ser", ser);
    return json.toString();
  }

  public static String toPrettyString(String jsonString) {
    return writeValueAsPrettyString(toObjectNode(jsonString));
  }

  public static String readJSON(File file) throws IOException {
    return objectMapper.readTree(file).toString();
  }

  public static <T> JsonNode valueToTree(T object) {
    return objectMapper.valueToTree(object);
  }

  public static JsonNode readTree(String json) throws JsonProcessingException {
    return objectMapper.readTree(json);
  }

  public static <T> T convertValue(Object fromValue, Class<T> toValueType) {
    return objectMapper.convertValue(fromValue, toValueType);
  }

  public static JsonNode toJsonNode(Map<String, Object> jsonAsMap) {
    return objectMapper.convertValue(jsonAsMap, JsonNode.class);
  }

  @SneakyThrows
  public static byte[] writeValueAsBytes(Object a) {
    return objectMapper.writeValueAsBytes(a);
  }

  @SneakyThrows
  public static <A> A readValueFromBytes(Class<A> type, byte[] bytes) {
    return objectMapper.readerFor(type).readValue(bytes);
  }

  public static ObjectMapper mapper() {
    return objectMapper;
  }

  public static ObjectMapper getObjectMapper() {
    return objectMapper;
  }
}
