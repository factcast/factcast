package org.factcast.server.rest.resources.converter;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;
import javax.ws.rs.ext.Provider;

import org.factcast.core.subscription.FactSpec;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;

@Provider
public class FactSpecParamConverterProvider implements ParamConverterProvider {
    private final ObjectMapper objectMapper;

    @Inject
    public FactSpecParamConverterProvider(ObjectMapper objectMapper) {
        super();
        this.objectMapper = objectMapper;
    }

    @AllArgsConstructor
    public static class FactSpecParamConverter implements ParamConverter<FactSpec> {

        private final ObjectMapper objectMapper;

        @Override
        public FactSpec fromString(String value) {
            try {
                return objectMapper.readValue(value, FactSpec.class);
            } catch (IOException e) {
                throw new BadRequestException();
            }
        }

        @Override
        @SneakyThrows
        public String toString(FactSpec value) {
            return objectMapper.writeValueAsString(value);
        }

    }

    @Override
    public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType,
            Annotation[] annotations) {
        if (genericType.equals(FactSpec.class)) {
            return (ParamConverter<T>) new FactSpecParamConverter(objectMapper);
        }
        return null;
    }
}
