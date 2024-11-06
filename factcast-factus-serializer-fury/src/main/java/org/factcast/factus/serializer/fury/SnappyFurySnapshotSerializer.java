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
package org.factcast.factus.serializer.fury;
import java.io.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.compressors.snappy.*;
import org.factcast.factus.serializer.SnapshotSerializerId;

@Slf4j
public class SnappyFurySnapshotSerializer extends FurySnapshotSerializer {

  @SneakyThrows
  protected OutputStream wrap(OutputStream os) {
    return new FramedSnappyCompressorOutputStream(os);
  }

  @SneakyThrows
  protected InputStream wrap(InputStream is) {
    return new FramedSnappyCompressorInputStream(is);
  }

  @Override
  public SnapshotSerializerId id() {
    return SnapshotSerializerId.of("snappyfury");
  }
}
