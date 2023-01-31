/*
 * Copyright © 2017-2023 factcast.org
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
package org.factcast.core.snap.redisson;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.index.qual.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = RedissonSnapshotProperties.PROPERTIES_PREFIX)
@Data
@Slf4j
@Accessors(fluent = false)
@Validated
public class RedissonSnapshotProperties {

  public static final String PROPERTIES_PREFIX = "factcast.redis";

  @Positive int deleteSnapshotStaleForDays = 90;

  RedissonCodec snapshotCacheRedissonCodec = RedissonCodec.MarshallingCodec;

  public int getRetentionTime() {
    return this.getDeleteSnapshotStaleForDays();
  }

  enum RedissonCodec {
    /** When setting the codec to RedissonDefault, factcast will not specify a codec. */
    RedissonDefault,
    MarshallingCodec,
    Kryo5Codec,
    JsonJacksonCodec,
    SmileJacksonCodec,
    CborJacksonCodec,
    MsgPackJacksonCodec,
    IonJacksonCodec,
    SerializationCodec,
    LZ4Codec,
    SnappyCodecV2,
    StringCodec,
    LongCodec,
    ByteArrayCodec,
    // Support might be added: https://github.com/factcast/factcast/issues/2231
    // TypedJsonJacksonCodec,
    // CompositeCodec
  }
}
