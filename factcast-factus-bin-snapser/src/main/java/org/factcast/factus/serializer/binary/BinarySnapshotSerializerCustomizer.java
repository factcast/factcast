package org.factcast.factus.serializer.binary;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.function.Consumer;

public interface BinarySnapshotSerializerCustomizer extends Consumer<ObjectMapper> {

  static BinarySnapshotSerializerCustomizer defaultCustomizer() {
    return objectMapper ->
        objectMapper
            .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }
}
