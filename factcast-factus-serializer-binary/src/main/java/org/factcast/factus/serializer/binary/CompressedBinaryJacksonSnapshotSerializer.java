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
import lombok.NonNull;
import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;
import org.factcast.factus.serializer.SnapshotSerializerId;

public class CompressedBinaryJacksonSnapshotSerializer extends BinaryJacksonSnapshotSerializer {

  public CompressedBinaryJacksonSnapshotSerializer(
      @NonNull BinaryJacksonSnapshotSerializerCustomizer customizer) {
    super(customizer);
  }

  @Override
  protected OutputStream wrap(OutputStream baos) {
    return new LZ4BlockOutputStream(baos);
  }

  @Override
  protected InputStream wrap(InputStream is) {
    return new LZ4BlockInputStream(is);
  }

  @Override
  public SnapshotSerializerId id() {
    // even though this is a mouthful and does not indicate compression, we cannot change it without
    // breaking existing snaps.
    return SnapshotSerializerId.of("BinaryJacksonSnapshotSerializer");
  }
}
