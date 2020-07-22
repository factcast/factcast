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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.SneakyThrows;

/**
 * Statically shared ObjectMappers to be used within FactCast for Headers and
 * FactCast-specific objects.
 * <p>
 *
 * since 2.6, you can add modules to the mappers by calling addModule()
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FactCastJson {

    private static final List<Module> modules = new LinkedList<>();

    private static ObjectMapper configure(ObjectMapper objectMapper) {
        objectMapper
                .setVisibility(PropertyAccessor.FIELD, Visibility.ANY)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.registerModules(modules);
        return objectMapper;
    }

    private static final ObjectMapper defaultMapper = Encoder.STD_JSON.get();

    public static void addModule(@NonNull Module m) {
        modules.add(m);
        Arrays.stream(Encoder.values()).forEach(e -> {
            if (e.om != null) {
                // already has been used, we're late to the party
                e.om.registerModule(m);
            }
        });
    }

    public enum Encoder {

        STD_JSON(() -> configure(new ObjectMapper())),
        MSGPACK(() -> configure(new ObjectMapper(instantiateMessagePackFactory())));

        @SneakyThrows
        private static JsonFactory instantiateMessagePackFactory() {
            // this way, we can still have a client with an optional dependency
            // to MSGPack
            return (JsonFactory) Class.forName("org.msgpack.jackson.dataformat.MessagePackFactory")
                    .getDeclaredConstructor()
                    .newInstance();
        }

        private final Supplier<ObjectMapper> supplier;

        private ObjectMapper om = null;

        public ObjectMapper get() {
            if (om == null) {
                om = supplier.get();
            }
            return om;
        }

        Encoder(Supplier<ObjectMapper> s) {
            this.supplier = s;
        }

    }

    @SneakyThrows
    public static <T> T copy(@NonNull T toCopy) {
        Class<?> c = toCopy.getClass();
        return defaultMapper.reader()
                .forType(c)
                .readValue(defaultMapper.writer().forType(c).writeValueAsBytes(toCopy));
    }

    @SneakyThrows
    public static <T> String writeValueAsString(@NonNull T value) {
        return defaultMapper.writer().writeValueAsString(value);
    }

    @SneakyThrows
    public static <T> byte[] writeValueAsBytes(@NonNull T value, @NonNull Encoder encoder) {
        return encoder.get().writer().writeValueAsBytes(value);
    }

    @SneakyThrows
    public static <T> T readValue(@NonNull Class<T> class1, @NonNull String json) {
        return defaultMapper.reader().forType(class1).readValue(json);
    }

    @SneakyThrows
    public static <T> T readValue(@NonNull Class<T> class1, @NonNull InputStream json) {
        return readValue(class1, json, Encoder.STD_JSON);
    }

    @SneakyThrows
    public static <T> T readValue(
            @NonNull Class<T> class1, @NonNull InputStream json,
            @NonNull Encoder encoder) {
        return encoder.get().reader().forType(class1).readValue(json);
    }

    @SneakyThrows
    public static <T> T readValue(
            @NonNull Class<T> class1, @NonNull byte[] bytes,
            @NonNull Encoder encoder) {
        return encoder.get().reader().forType(class1).readValue(bytes);
    }

    public static ObjectNode toObjectNode(String json) {
        try {
            return (ObjectNode) defaultMapper.readTree(json);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static ObjectNode newObjectNode() {
        return defaultMapper.getNodeFactory().objectNode();
    }

    @SneakyThrows
    public static String writeValueAsPrettyString(Object o) {
        return defaultMapper.writerWithDefaultPrettyPrinter().writeValueAsString(o);
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
        return defaultMapper.readTree(file).toString();
    }

    public static <T> JsonNode valueToTree(T object) {
        return defaultMapper.valueToTree(object);
    }

    public static JsonNode readTree(String json) throws JsonProcessingException {
        return defaultMapper.readTree(json);
    }

    public static JsonNode readTree(byte[] bytes, Encoder encoder) throws IOException {
        return encoder.get().readTree(bytes);
    }

    public static <T> T convertValue(Object fromValue, Class<T> toValueType) {
        return defaultMapper.convertValue(fromValue, toValueType);
    }

    public static JsonNode toJsonNode(Map<String, Object> jsonAsMap) {
        return defaultMapper.convertValue(jsonAsMap, JsonNode.class);
    }

}
