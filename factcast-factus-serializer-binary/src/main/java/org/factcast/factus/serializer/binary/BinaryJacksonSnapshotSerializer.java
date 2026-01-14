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
package org.factcast.factus.serializer.binary;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.factcast.factus.projection.SnapshotProjection;
import org.factcast.factus.serializer.SnapshotSerializer;
import org.msgpack.jackson.dataformat.MessagePackFactory;

@RequiredArgsConstructor
public abstract class BinaryJacksonSnapshotSerializer implements SnapshotSerializer {
  private static final int BLOCKSIZE = 65536;

  private final ObjectMapper omMessagePack;

  protected BinaryJacksonSnapshotSerializer(
      @NonNull BinaryJacksonSnapshotSerializerCustomizer customizer) {
    ObjectMapper om = new ObjectMapper(new MessagePackFactory());
    customizer.accept(om);
    omMessagePack = om;
  }

  // acceptable coverage miss:
  @SneakyThrows
  @Override
  public byte[] serialize(@NonNull SnapshotProjection a) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream(BLOCKSIZE + 16);

    OutputStream os = wrap(baos);
    omMessagePack.writeValue(os, a);
    os.close();
    return baos.toByteArray();
  }

  protected abstract OutputStream wrap(OutputStream baos);

  protected abstract InputStream wrap(InputStream is);

  // acceptable coverage miss:
  @SneakyThrows
  @Override
  public <A extends SnapshotProjection> A deserialize(Class<A> type, byte[] bytes) {
    try (InputStream is = wrap(new ByteArrayInputStream(bytes))) {
      return omMessagePack.readerFor(type).readValue(is);
    }
  }
}
