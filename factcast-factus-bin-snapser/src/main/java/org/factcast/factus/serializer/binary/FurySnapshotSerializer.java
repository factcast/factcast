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

import java.io.*;
import java.util.Set;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.jpountz.lz4.*;
import org.apache.fury.Fury;
import org.apache.fury.config.Language;
import org.apache.fury.io.FuryInputStream;
import org.factcast.factus.projection.SnapshotProjection;
import org.factcast.factus.serializer.SnapshotSerializer;
import org.factcast.factus.serializer.SnapshotSerializerId;
import org.reflections8.Reflections;

@Slf4j
public class FurySnapshotSerializer implements SnapshotSerializer {

  private static final int BLOCKSIZE = 65536;
  public static final Fury fury;

  static {
    fury =
        Fury.builder()
            .withLanguage(Language.JAVA)
            // Allow to deserialize objects unknown types,
            // more flexible but less secure.
            .withStringCompressed(true)
            .requireClassRegistration(false)
            .build();
    Reflections reflections = new Reflections();
    Set<Class<? extends SnapshotProjection>> classes =
        reflections.getSubTypesOf(SnapshotProjection.class);
    for (Class<? extends SnapshotProjection> c : classes) {
      // Registering types can reduce class name serialization overhead, but not
      // mandatory.
      // If secure mode enabled, all custom types must be registered.
      System.err.println("Registering " + c.getCanonicalName());
      fury.register(c);
    }
  }

  public FurySnapshotSerializer() {}

  // acceptable coverage miss:
  @SneakyThrows
  @Override
  public byte[] serialize(@NonNull SnapshotProjection a) {

    ByteArrayOutputStream baos = new ByteArrayOutputStream(BLOCKSIZE + 16);

    LZ4BlockOutputStream os = new LZ4BlockOutputStream(baos, BLOCKSIZE);
    os.write(fury.serialize(a));
    os.close();
    return baos.toByteArray();
  }

  // acceptable coverage miss:
  @SneakyThrows
  @Override
  public <A extends SnapshotProjection> A deserialize(Class<A> type, byte[] bytes) {

    try (LZ4BlockInputStream is = new LZ4BlockInputStream(new ByteArrayInputStream(bytes))) {
      return (A) fury.deserialize(new FuryInputStream(is));
    }
  }

  @Override
  public SnapshotSerializerId id() {
    return SnapshotSerializerId.of("FurySnapshotSerializer");
  }
}
