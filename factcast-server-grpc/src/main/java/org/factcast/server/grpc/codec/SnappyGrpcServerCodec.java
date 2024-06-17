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
package org.factcast.server.grpc.codec;

import io.grpc.Codec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import net.devh.boot.grpc.common.codec.CodecType;
import net.devh.boot.grpc.common.codec.GrpcCodec;
import org.xerial.snappy.SnappyInputStream;
import org.xerial.snappy.SnappyOutputStream;

@SuppressWarnings("DeprecatedIsStillUsed")
@GrpcCodec(advertised = true, codecType = CodecType.ALL)
@Deprecated
public class SnappyGrpcServerCodec implements Codec {

  @Override
  public String getMessageEncoding() {
    return "snappy";
  }

  @Override
  public InputStream decompress(InputStream inputStream) throws IOException {
    return new SnappyInputStream(inputStream);
  }

  @Override
  public OutputStream compress(OutputStream outputStream) {
    return new SnappyOutputStream(outputStream);
  }
}
