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

import java.util.*;
import java.util.function.*;
import javax.annotation.Nullable;
import lombok.Data;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.index.qual.Positive;
import org.redisson.api.RBucket;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.codec.*;
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

  @SuppressWarnings("java:S115")
  enum RedissonCodec {

    /** When setting the codec to RedissonDefault, factcast will not specify a codec. */
    RedissonDefault(null),
    @SuppressWarnings("deprecation")
    MarshallingCodec(MarshallingCodec::new),
    Kryo5Codec(Kryo5Codec::new),
    JsonJacksonCodec(JsonJacksonCodec::new),
    SmileJacksonCodec(SmileJacksonCodec::new),
    CborJacksonCodec(CborJacksonCodec::new),
    MsgPackJacksonCodec(MsgPackJacksonCodec::new),
    IonJacksonCodec(IonJacksonCodec::new),
    SerializationCodec(SerializationCodec::new),
    LZ4Codec(LZ4Codec::new),
    SnappyCodecV2(SnappyCodecV2::new),
    StringCodec(StringCodec::new),
    LongCodec(LongCodec::new),
    ByteArrayCodec(ByteArrayCodec::new);
    // Support might be added: https://github.com/factcast/factcast/issues/2231
    // TypedJsonJacksonCodec,
    // CompositeCodec

    private final Supplier<Codec> codec;

    RedissonCodec(@Nullable Supplier<Codec> c) {
      this.codec = c;
    }

    @NonNull
    public Optional<Codec> codec() {
      if (codec == null) return Optional.empty();
      return Optional.ofNullable(codec.get());
    }

    @NonNull
    public <T> RBucket<T> getBucket(@NonNull RedissonClient redisson, @NonNull String key) {
      if (codec == null) return redisson.getBucket(key);
      else return redisson.getBucket(key, codec.get());
    }

    @NonNull
    public <K, V> RMap<K, V> getMap(@NonNull RedissonClient redisson, @NonNull String key) {
      if (codec == null) return redisson.getMap(key);
      else return redisson.getMap(key, codec.get());
    }
  }
}
