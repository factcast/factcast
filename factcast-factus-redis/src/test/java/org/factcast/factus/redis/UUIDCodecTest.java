//
// Copyright PRISMA European Capacity Platform GmbH
//
package org.factcast.factus.redis;

import static org.assertj.core.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import lombok.SneakyThrows;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UUIDCodecTest {

  @Nested
  class WhenEncoding {
    @BeforeEach
    void setup() {}

    @SneakyThrows
    @Test
    void shouldUse16Byte() {
      ByteBuf enc = UUIDCodec.INSTANCE.getValueEncoder().encode(UUID.randomUUID());
      assertThat(enc.array()).hasSize(16);
    }

    @SneakyThrows
    @Test
    void isSymetric() {
      UUID id = UUID.randomUUID();
      ByteBuf enc = UUIDCodec.INSTANCE.getValueEncoder().encode(id);
      UUID dec = (UUID) UUIDCodec.INSTANCE.getValueDecoder().decode(enc, null);
      assertThat(dec).isEqualTo(id);
    }

    @SneakyThrows
    @Test
    void failsOnMissingBits() {
      UUID id = UUID.randomUUID();
      ByteBuf enc = Unpooled.wrappedBuffer(new byte[]{1, 2, 3, 4});
      assertThatThrownBy(
              () -> {
                UUIDCodec.INSTANCE.getValueDecoder().decode(enc, null);
              })
          .isInstanceOf(IndexOutOfBoundsException.class);
    }
  }

  @Nested
  class WhenConstructing {
    @Test
    void justToCoverTheFrameworkConstructor() {
      new UUIDCodec(UUID.class.getClassLoader(), new UUIDCodec());
    }
  }
}
