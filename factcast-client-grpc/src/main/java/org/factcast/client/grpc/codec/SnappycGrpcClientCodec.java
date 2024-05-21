/*
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
package org.factcast.client.grpc.codec;

import io.grpc.Codec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import lombok.SneakyThrows;
import net.devh.boot.grpc.common.codec.CodecType;
import net.devh.boot.grpc.common.codec.GrpcCodec;
import org.apache.commons.compress.compressors.snappy.FramedSnappyCompressorInputStream;
import org.apache.commons.compress.compressors.snappy.FramedSnappyCompressorOutputStream;

@GrpcCodec(advertised = true, codecType = CodecType.ALL)
public class SnappycGrpcClientCodec implements Codec {

  @Override
  public String getMessageEncoding() {
    return "snappyc";
  }

  @SneakyThrows
  @Override
  public OutputStream compress(OutputStream os) {
    return new FramedSnappyCompressorOutputStream(os);
  }

  @Override
  public InputStream decompress(InputStream is) throws IOException {
    return new FramedSnappyCompressorInputStream(is);
  }
}
