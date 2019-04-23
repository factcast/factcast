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
package org.factcast.server.grpc.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.junit.jupiter.api.Test;

class Lz4GrpcServerCodecTest {

    Lz4GrpcServerCodec uut = new Lz4GrpcServerCodec();

    @Test
    void getMessageEncoding() {
        assertEquals("lz4", uut.getMessageEncoding());
    }

    @Test
    void compressionIsSymetric() throws IOException {
        byte[] original = "Some uncompressed String".getBytes();
        byte[] compressed = CodecTestHelper.toByteArray(uut, original);
        byte[] uncompressed = CodecTestHelper.fromByteArray(uut, compressed);

        assertThat(uncompressed).isEqualTo(original);
    }
}