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

import java.io.*;

import io.grpc.*;
import net.devh.boot.grpc.common.codec.*;
import net.jpountz.lz4.*;

@GrpcCodec(advertised = true, codecType = CodecType.ALL)
public class Lz4GrpcClientCodec implements Codec {

    @Override
    public String getMessageEncoding() {
        return "lz4";
    }

    @Override
    public InputStream decompress(InputStream inputStream) {
        return new LZ4BlockInputStream(inputStream);
    }

    @Override
    public OutputStream compress(OutputStream outputStream) {
        return new LZ4BlockOutputStream(outputStream);
    }
}