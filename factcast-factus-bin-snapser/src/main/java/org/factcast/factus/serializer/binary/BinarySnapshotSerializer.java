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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.val;
import net.jpountz.lz4.*;
import org.factcast.factus.projection.SnapshotProjection;
import org.factcast.factus.serializer.SnapshotSerializer;
import org.msgpack.jackson.dataformat.MessagePackFactory;

public class BinarySnapshotSerializer implements SnapshotSerializer {

  private static final ObjectMapper om =
      new ObjectMapper(new MessagePackFactory())
          .setVisibility(PropertyAccessor.FIELD, Visibility.ANY)
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  @SneakyThrows
  @Override
  public byte[] serialize(@NonNull SnapshotProjection a) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    val os = new LZ4BlockOutputStream(baos, 8192);
    om.writeValue(os, a);
    os.close();
    return baos.toByteArray();
  }

  @SneakyThrows
  @Override
  public <A extends SnapshotProjection> A deserialize(Class<A> type, byte[] bytes) {
    try (LZ4BlockInputStream is = new LZ4BlockInputStream(new ByteArrayInputStream(bytes)); ) {
      return om.readerFor(type).readValue(is);
    }
  }

  @Override
  public boolean includesCompression() {
    return true;
  }
}
