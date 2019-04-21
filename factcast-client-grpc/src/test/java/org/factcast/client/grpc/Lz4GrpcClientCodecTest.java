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
package org.factcast.client.grpc;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.*;

import org.junit.jupiter.api.*;

import net.jpountz.lz4.*;

class Lz4GrpcClientCodecTest {

    Lz4GrpcClientCodec uut = new Lz4GrpcClientCodec();

    @Test
    void getMessageEncoding() {
        assertEquals("lz4", uut.getMessageEncoding());
    }

    @Test
    void decompress() {
        assertThat(uut.decompress(mock(InputStream.class))).isInstanceOf(LZ4BlockInputStream.class);
    }

    @Test
    void compress() {
        assertThat(uut.compress(mock(OutputStream.class))).isInstanceOf(LZ4BlockOutputStream.class);
    }
}