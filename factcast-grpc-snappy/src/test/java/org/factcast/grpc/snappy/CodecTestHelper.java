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
package org.factcast.grpc.snappy; /*
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

import io.grpc.Codec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import lombok.experimental.UtilityClass;
import org.apache.commons.compress.utils.IOUtils;

@UtilityClass
public class CodecTestHelper {
  byte[] fromByteArray(Codec codec, byte[] compressedBytes) throws IOException {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    IOUtils.copy(codec.decompress(new ByteArrayInputStream(compressedBytes)), os);
    os.close();
    return os.toByteArray();
  }

  byte[] toByteArray(Codec codec, byte[] uncompressed) throws IOException {
    ByteArrayOutputStream target = new ByteArrayOutputStream();
    OutputStream compressedStream = uut.compress(target);
    IOUtils.copy(new ByteArrayInputStream(uncompressed), compressedStream);
    compressedStream.close();
    return target.toByteArray();
  }
}
