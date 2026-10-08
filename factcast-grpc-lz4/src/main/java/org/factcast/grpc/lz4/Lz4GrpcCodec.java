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
package org.factcast.grpc.lz4;

import io.grpc.Codec;
import java.io.InputStream;
import java.io.OutputStream;
import net.jpountz.lz4.*;

public class Lz4GrpcCodec implements Codec {
  private static final LZ4FastDecompressor decomp = LZ4Factory.fastestInstance().fastDecompressor();

  private static final LZ4Compressor comp = LZ4Factory.fastestInstance().fastCompressor();

  @Override
  public String getMessageEncoding() {
    return "lz4";
  }

  @Override
  public InputStream decompress(InputStream inputStream) {
    return new LZ4BlockInputStream(inputStream, decomp);
  }

  @Override
  public OutputStream compress(OutputStream outputStream) {
    return new LZ4BlockOutputStream(outputStream, 65536, comp);
  }
}
