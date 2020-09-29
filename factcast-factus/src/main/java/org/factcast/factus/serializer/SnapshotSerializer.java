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
package org.factcast.factus.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.hash.Hashing;
import java.util.function.Function;
import lombok.NonNull;
import lombok.Setter;
import lombok.SneakyThrows;
import org.factcast.core.util.FactCastJson;
import org.factcast.factus.projection.SnapshotProjection;

public interface SnapshotSerializer {
  byte[] serialize(SnapshotProjection a);

  <A extends SnapshotProjection> A deserialize(Class<A> type, byte[] bytes);

  boolean includesCompression();

  /**
   * In order to catch changes when a {@link SnapshotProjection} got changed, calculate a hash that
   * changes when the schema of the serialised class changes.
   *
   * <p>Note that in some cases, it is possible to add fields and use serializer-specific means to
   * ignore them for serialization (e.g. by using @JsonIgnore with FactCastJson).
   *
   * <p>Hence, every serializer is asked to calculate it's own hash, that should only change in case
   * changes to the projection where made that were relevant for deserialization.
   *
   * <p>In case a field of type long with name serialVersionUID (according to Serializable
   * interface) exists, it is used instead (then this method will not be called).
   *
   * <p>In case your serializer cannot calculate a hash, return 0.
   *
   * @param projectionClass the snapshot projection class to calculate the hash for
   * @return the calculated hash or 0, if no hash could be calculated
   */
  // TODO make it a string?
  default long calculateProjectionClassHash(Class<? extends SnapshotProjection> projectionClass) {
    return 0;
  }

  public static class DefaultSnapshotSerializer extends JacksonSnapshotSerializer {}

  public static class JacksonSnapshotSerializer implements SnapshotSerializer {

    private final ObjectMapper objectMapper;
    private final JsonSchemaGenerator schemaGen;

    @Setter(onMethod = @__(@VisibleForTesting))
    private static Function<String, String> schemaModifier = Function.identity();

    public JacksonSnapshotSerializer(@NonNull ObjectMapper configuredObjectMapper) {
      this.objectMapper = configuredObjectMapper;
      schemaGen = new JsonSchemaGenerator(objectMapper);
    }

    public JacksonSnapshotSerializer() {
      this(FactCastJson.getObjectMapper());
    }

    @SneakyThrows
    @Override
    public byte[] serialize(SnapshotProjection a) {
      return objectMapper.writeValueAsBytes(a);
    }

    @SneakyThrows
    @Override
    public <A extends SnapshotProjection> A deserialize(Class<A> type, byte[] bytes) {
      return objectMapper.readerFor(type).readValue(bytes);
    }

    @Override
    public boolean includesCompression() {
      return false;
    }

    @SuppressWarnings("UnstableApiUsage")
    @SneakyThrows
    @Override
    public long calculateProjectionClassHash(Class<? extends SnapshotProjection> projectionClass) {
      JsonSchema jsonSchema = schemaGen.generateSchema(projectionClass);
      String schema = objectMapper.writeValueAsString(jsonSchema);
      return Hashing.sha512().hashUnencodedChars(schemaModifier.apply(schema)).asLong();
    }
  }
}
