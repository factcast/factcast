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
package org.factcast.grpc.lz4;

import io.grpc.Codec;
import java.io.InputStream;
import java.io.OutputStream;
import lombok.SneakyThrows;
import net.devh.boot.grpc.common.codec.CodecType;
import net.devh.boot.grpc.common.codec.GrpcCodec;
import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorInputStream;
import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorOutputStream;

@GrpcCodec(advertised = true, codecType = CodecType.ALL)
public class Lz4cGrpcClientCodec implements Codec {

  @Override
  public String getMessageEncoding() {
    return "lz4c";
  }

  @SneakyThrows
  @Override
  public InputStream decompress(InputStream inputStream) {
    return new FramedLZ4CompressorInputStream(inputStream);
  }

  @SneakyThrows
  @Override
  public OutputStream compress(OutputStream outputStream) {
    return new FramedLZ4CompressorOutputStream(outputStream);
  }
}
