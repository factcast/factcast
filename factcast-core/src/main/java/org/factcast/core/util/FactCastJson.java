package org.factcast.core.util;

import java.io.IOException;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.SneakyThrows;

/**
 * Statically shared ObjectMapper reader & writer to be used within FactCast for
 * Headers and FactCast-specific objects.
 * 
 * You must not change the configuration of this mapper, and it should not be
 * used outside of FactCast.
 * 
 * @author uwe.schaefer@mercateo.com
 *
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FactCastJson {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final ObjectReader reader;

    private static final ObjectWriter writer;

    static {

        objectMapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);

        writer = objectMapper.writer();
        reader = objectMapper.reader();
    }

    @SneakyThrows
    public static <T> T copy(@NonNull T toCopy) {
        Class<? extends Object> c = toCopy.getClass();
        return reader.forType(c).readValue(writer.forType(c).writeValueAsString(toCopy));
    }

    @SneakyThrows
    public static <T> String writeValueAsString(@NonNull T value) {
        return writer.writeValueAsString(value);
    }

    @SneakyThrows
    public static <T> T readValue(@NonNull Class<T> class1, String json) {
        return reader.forType(class1).readValue(json);
    }

    public static ObjectNode toObjectNode(String jsonHeader) {
        try {
            return (ObjectNode) objectMapper.readTree(jsonHeader);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static ObjectNode newObjectNode() {
        return objectMapper.getNodeFactory().objectNode();
    }
}
