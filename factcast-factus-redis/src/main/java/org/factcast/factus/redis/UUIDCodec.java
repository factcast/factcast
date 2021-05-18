//
// Copyright PRISMA European Capacity Platform GmbH
//
package org.factcast.factus.redis;

import io.netty.buffer.Unpooled;
import java.nio.ByteBuffer;
import java.util.UUID;
import lombok.val;
import org.redisson.client.codec.BaseCodec;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;

public class UUIDCodec extends BaseCodec {

  public static final UUIDCodec INSTANCE = new UUIDCodec();
  private final Encoder encoder =
      in -> {
        val id = (UUID) in;
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES * 2);
        buffer.putLong(id.getMostSignificantBits());
        buffer.putLong(id.getLeastSignificantBits());
        return Unpooled.wrappedBuffer(buffer.array());
      };
  private final Decoder<Object> decoder =
      (buf, state) -> {
        val buffer = buf.nioBuffer(0, 16);
        return new UUID(buffer.getLong(), buffer.getLong());
      };

  private UUIDCodec() {}

  @Override
  public Decoder<Object> getValueDecoder() {
    return decoder;
  }

  @Override
  public Encoder getValueEncoder() {
    return encoder;
  }
}
