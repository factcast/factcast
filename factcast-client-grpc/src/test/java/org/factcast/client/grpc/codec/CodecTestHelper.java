/*
 * Copyright © 2018 Mercateo AG (http://www.mercateo.com)
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
package org.factcast.client.grpc.codec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import io.grpc.Codec;
import io.grpc.internal.IoUtils;
import lombok.experimental.UtilityClass;

@UtilityClass
class CodecTestHelper {
    byte[] fromByteArray(Codec uut, byte[] compressedBytes) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        IoUtils.copy(uut.decompress(new ByteArrayInputStream(
                compressedBytes)), os);
        os.close();
        return os.toByteArray();
    }

    byte[] toByteArray(Codec uut, byte[] uncompressed) throws IOException {
        ByteArrayOutputStream target = new ByteArrayOutputStream();
        OutputStream compressedStream = uut.compress(target);
        IoUtils.copy(new ByteArrayInputStream(uncompressed), compressedStream);
        compressedStream.close();
        return target.toByteArray();
    }
}
