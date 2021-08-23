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
package org.factcast.factus.serializer.binary;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.hash.Hashing;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.function.Function;
import lombok.NonNull;
import lombok.Setter;
import lombok.SneakyThrows;
import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;
import org.factcast.factus.projection.SnapshotProjection;
import org.factcast.factus.serializer.SnapshotSerializer;
import org.msgpack.jackson.dataformat.MessagePackFactory;

public class BinarySnapshotSerializer implements SnapshotSerializer {

  private static final ObjectMapper omMessagePack =
      configure(new ObjectMapper(new MessagePackFactory()));

  // needed for schema generation, but with same settings like message pack
  // mapper
  private static final ObjectMapper omJson = configure(new ObjectMapper());

  private static final ObjectWriter writerJson = omJson.writer();

  private static final JsonSchemaGenerator schemaGen = new JsonSchemaGenerator(omJson);

  @Setter
  private static Function<String, String> schemaModifier = Function.identity();

  @SneakyThrows
  @Override
  public byte[] serialize(@NonNull SnapshotProjection a) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    LZ4BlockOutputStream os = new LZ4BlockOutputStream(baos, 8192);
    omMessagePack.writeValue(os, a);
    os.close();
    return baos.toByteArray();
  }

  @SneakyThrows
  @Override
  public <A extends SnapshotProjection> A deserialize(Class<A> type, byte[] bytes) {
    try (LZ4BlockInputStream is = new LZ4BlockInputStream(new ByteArrayInputStream(bytes))) {
      return omMessagePack.readerFor(type).readValue(is);
    }
  }

  @Override
  public boolean includesCompression() {
    return true;
  }

  @SuppressWarnings("UnstableApiUsage")
  @Override
  @SneakyThrows
  public Long calculateProjectionSerial(Class<? extends SnapshotProjection> projectionClass) {
    JsonSchema jsonSchema = schemaGen.generateSchema(projectionClass);

    String schema = writerJson.writeValueAsString(jsonSchema);

    return Hashing.sha512().hashUnencodedChars(schemaModifier.apply(schema)).asLong();
  }

  @Override
  public String getId() {
    return "BinarySnapshotSerializer";
  }

  private static ObjectMapper configure(ObjectMapper objectMapper) {
    return objectMapper
        .setVisibility(PropertyAccessor.FIELD, Visibility.ANY)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }
}
