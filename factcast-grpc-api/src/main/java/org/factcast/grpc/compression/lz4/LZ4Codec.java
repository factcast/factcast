/**
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
package org.factcast.grpc.compression.lz4;

import java.io.InputStream;
import java.io.OutputStream;

import io.grpc.Codec;
import lombok.Generated;
import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;

// waits for release of https://github.com/yidongnan/grpc-spring-boot-starter/issues/96
// exclude from coverage analysis
@Generated
public class LZ4Codec implements Codec {

    public static final String ENCODING = "lz4";

    @Override
    public String getMessageEncoding() {
        return ENCODING;
    }

    @Override
    public OutputStream compress(OutputStream os) {
        return new LZ4BlockOutputStream(os);
    }

    @Override
    public InputStream decompress(InputStream is) {
        return new LZ4BlockInputStream(is);
    }
}
