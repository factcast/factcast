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
package org.factcast.factus.serializer.fory;

import java.io.*;
import lombok.SneakyThrows;
import org.apache.commons.compress.compressors.snappy.*;
import org.assertj.core.api.Assertions;
import org.factcast.factus.serializer.SnapshotSerializerId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SnappyForySnapshotSerializerTest {

  @InjectMocks private SnappyForySnapshotSerializer underTest;

  @Nested
  class WhenWraping {
    private ByteArrayOutputStream os = new ByteArrayOutputStream();

    @SneakyThrows
    @Test
    void wraps() {
      OutputStream wrap = underTest.wrap(os);
      Assertions.assertThat(wrap).isInstanceOf(FramedSnappyCompressorOutputStream.class);
      wrap.close();

      InputStream is = underTest.wrap(new ByteArrayInputStream(os.toByteArray()));
      Assertions.assertThat(is).isInstanceOf(FramedSnappyCompressorInputStream.class);
      is.close();
    }
  }

  @Nested
  class WhenIding {

    @Test
    void wraps() {
      Assertions.assertThat(underTest.id()).isEqualTo(SnapshotSerializerId.of("snappyfory"));
    }
  }
}
