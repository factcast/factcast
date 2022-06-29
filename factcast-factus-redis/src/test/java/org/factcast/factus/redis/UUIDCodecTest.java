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
      ByteBuf enc = Unpooled.wrappedBuffer(new byte[] {1, 2, 3, 4});
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
