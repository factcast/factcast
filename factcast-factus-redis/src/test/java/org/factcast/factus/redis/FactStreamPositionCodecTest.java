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

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import lombok.SneakyThrows;
import org.assertj.core.api.Assertions;
import org.factcast.core.FactStreamPosition;
import org.junit.jupiter.api.Test;

class FactStreamPositionCodecTest {

  FactStreamPositionCodec uut = FactStreamPositionCodec.INSTANCE;

  @SneakyThrows
  @Test
  void symetric() {
    UUID id = UUID.randomUUID();
    long ser = 28763;
    FactStreamPosition pos = FactStreamPosition.of(id, ser);
    ByteBuf encoded = uut.getValueEncoder().encode(pos);
    Assertions.assertThat(uut.getValueDecoder().decode(encoded, null)).isEqualTo(pos);
  }

  @SneakyThrows
  @Test
  void canReadFromToUUIDCodec() {
    UUID id = UUID.randomUUID();
    FactStreamPosition expected = FactStreamPosition.of(id, -1);
    ByteBuf encoded = UUIDCodec.INSTANCE.getValueEncoder().encode(id);
    Assertions.assertThat(uut.getValueDecoder().decode(encoded, null)).isEqualTo(expected);
  }

  @SneakyThrows
  @Test
  void UUIDCodecCanReadFromFactStreamPosition() {
    UUID id = UUID.randomUUID();
    FactStreamPosition pos = FactStreamPosition.of(id, -1);
    ByteBuf encoded = uut.getValueEncoder().encode(pos);
    Assertions.assertThat(UUIDCodec.INSTANCE.getValueDecoder().decode(encoded, null))
        .isEqualTo(pos.factId());
  }
}
