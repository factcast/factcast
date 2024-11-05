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
package org.factcast.factus.serializer.fury; /*
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

import java.io.*;
import java.util.zip.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.fury.Fury;
import org.apache.fury.config.Language;
import org.apache.fury.io.FuryInputStream;
import org.factcast.factus.projection.SnapshotProjection;
import org.factcast.factus.serializer.*;

@Slf4j
public class FurySnapshotSerializer implements SnapshotSerializer {

  private static final int BLOCKSIZE = 65536;
  public static final Fury fury;

  static {
    fury =
        Fury.builder()
            .withLanguage(Language.JAVA)
            .withStringCompressed(true)
            .requireClassRegistration(false)
            .build();
  }

  public FurySnapshotSerializer() {}

  // acceptable coverage miss:
  @SneakyThrows
  @Override
  public byte[] serialize(@NonNull SnapshotProjection a) {

    ByteArrayOutputStream baos = new ByteArrayOutputStream(BLOCKSIZE + 16);
    OutputStream os = wrap(baos);
    os.write(fury.serialize(a));
    os.close();
    return baos.toByteArray();
  }

  protected OutputStream wrap(OutputStream os) {
    return os;
  }

  protected InputStream wrap(InputStream is) {
    return is;
  }

  // acceptable coverage miss:
  @SneakyThrows
  @Override
  public <A extends SnapshotProjection> A deserialize(Class<A> type, byte[] bytes) {
    try (InputStream is = wrap(new ByteArrayInputStream(bytes))) {
      return (A) fury.deserialize(new FuryInputStream(is));
    }
  }

  @Override
  public SnapshotSerializerId id() {
    return SnapshotSerializerId.of("fury");
  }
}
