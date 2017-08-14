package org.factcast.server.rest.resources.converter;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;

import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;
import javax.ws.rs.ext.Provider;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.SneakyThrows;

/**
 * provides a converter for query parameters which are a JSON entity.This
 * depends on the presence of the JsonParam annotation
 * 
 * @author joerg_adler
 *
 */
@Provider
public class JsonParamConverterProvider implements ParamConverterProvider {
    // would it be reasonable to use FCJson here?
    private final ObjectMapper objectMapper;

    @Inject
    public JsonParamConverterProvider(@NonNull ObjectMapper objectMapper) {
        super();
        this.objectMapper = objectMapper;
    }

    /**
     * converts a query parameter from json to object and back
     * 
     * @author joerg_adler
     *
     * @param <T>
     */
    @AllArgsConstructor
    public static class JsonParamConverter<T> implements ParamConverter<T> {
        @NonNull
        private final ObjectMapper objectMapper;

        @NonNull
        private final Class<T> clazz;

        @Override
        public T fromString(String value) {
            try {
                return objectMapper.readValue(value, clazz);
            } catch (Exception e) {
                throw new BadRequestException();
            }
        }

        @Override
        @SneakyThrows
        public String toString(T value) {
            return objectMapper.writeValueAsString(value);
        }

    }

    @Override
    public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType,
            Annotation[] annotations) {
        if (annotations != null && Arrays.stream(annotations)
                .filter(a -> a instanceof JsonParam)
                .findAny()
                .isPresent() && !Collection.class.isAssignableFrom(rawType)) {
            return new JsonParamConverter<T>(objectMapper, rawType);
        }
        return null;
    }
}
