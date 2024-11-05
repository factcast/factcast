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
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import net.jpountz.lz4.*;
import org.factcast.factus.serializer.*;

@Slf4j
public class LZ4FurySnapshotSerializer extends FurySnapshotSerializer {

  protected OutputStream wrap(OutputStream os) {
    return new LZ4BlockOutputStream(os);
  }

  protected InputStream wrap(InputStream is) {
    return new LZ4BlockInputStream(is);
  }

  @Override
  public SnapshotSerializerId id() {
    return SnapshotSerializerId.of("lz4fury");
  }
}
