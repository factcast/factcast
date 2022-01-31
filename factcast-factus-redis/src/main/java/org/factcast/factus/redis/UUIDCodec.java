/*
 * Copyright © 2017-2022 factcast.org
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
package org.factcast.factus.redis;

import io.netty.buffer.Unpooled;
import java.nio.ByteBuffer;
import java.util.UUID;
import org.redisson.client.codec.BaseCodec;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;

public class UUIDCodec extends BaseCodec {

  public static final UUIDCodec INSTANCE = new UUIDCodec();
  private final Encoder encoder =
      in -> {
        UUID id = (UUID) in;
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES * 2);
        buffer.putLong(id.getMostSignificantBits());
        buffer.putLong(id.getLeastSignificantBits());
        return Unpooled.wrappedBuffer(buffer.array());
      };
  private final Decoder<Object> decoder =
      (buf, state) -> {
        ByteBuffer buffer = buf.nioBuffer(0, 16);
        return new UUID(buffer.getLong(), buffer.getLong());
      };

  UUIDCodec() {}

  public UUIDCodec(ClassLoader cl, UUIDCodec c) {}

  @Override
  public Decoder<Object> getValueDecoder() {
    return decoder;
  }

  @Override
  public Encoder getValueEncoder() {
    return encoder;
  }
}
