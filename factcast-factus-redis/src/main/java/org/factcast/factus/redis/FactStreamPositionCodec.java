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
package org.factcast.factus.redis;

import io.netty.buffer.Unpooled;
import java.nio.ByteBuffer;
import java.util.UUID;
import lombok.NoArgsConstructor;
import org.factcast.core.FactStreamPosition;
import org.redisson.client.codec.BaseCodec;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;

@NoArgsConstructor
public class FactStreamPositionCodec extends BaseCodec {

  public static final FactStreamPositionCodec INSTANCE = new FactStreamPositionCodec();
  private final Encoder encoder =
      in -> {
        FactStreamPosition id = (FactStreamPosition) in;
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES * 3);
        buffer.putLong(id.factId().getMostSignificantBits());
        buffer.putLong(id.factId().getLeastSignificantBits());
        buffer.putLong(id.serial());
        return Unpooled.wrappedBuffer(buffer.array());
      };
  private final Decoder<Object> decoder =
      (buf, state) -> {
        // in order to stay compatible with 0.7.4 and before, we try to be careful to treat the 3rd
        // long as optional.
        if (buf.capacity() == Long.BYTES * 2) {
          // only contains UUID, return -1 as serial
          ByteBuffer buffer = buf.nioBuffer(0, Long.BYTES * 2);
          return FactStreamPosition.of(new UUID(buffer.getLong(), buffer.getLong()), -1L);
        } else {
          ByteBuffer buffer = buf.nioBuffer(0, Long.BYTES * 3);
          return FactStreamPosition.of(
              new UUID(buffer.getLong(), buffer.getLong()), buffer.getLong());
        }
      };

  @Override
  public Decoder<Object> getValueDecoder() {
    return decoder;
  }

  @Override
  public Encoder getValueEncoder() {
    return encoder;
  }
}
