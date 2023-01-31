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
package org.factcast.core.snap.redisson;

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotCache;
import org.factcast.core.snap.SnapshotId;
import org.redisson.api.RBucket;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.codec.*;

@Slf4j
public class RedissonSnapshotCache implements SnapshotCache {

  final RedissonClient redisson;

  private final RedissonSnapshotProperties properties;

  // still needed to remove stale snapshots, can be removed at some point
  private static final String TS_INDEX = "SNAPCACHE_IDX";
  private final RMap<String, Long> index;

  public RedissonSnapshotCache(
      @NonNull RedissonClient redisson, @NonNull RedissonSnapshotProperties props) {
    this.redisson = redisson;
    this.properties = props;
    index = redisson.getMap(TS_INDEX);
  }

  @Override
  public @NonNull Optional<Snapshot> getSnapshot(@NonNull SnapshotId id) {
    String key = createKeyFor(id);

    RBucket<Snapshot> bucket =
        getCodecAccordingToProperties(properties)
            .map(codec -> createFromCodec(key, codec))
            .orElse(redisson.getBucket(key));

    Optional<Snapshot> snapshot = Optional.ofNullable(bucket.get());
    if (snapshot.isPresent()) {
      // renew TTL
      bucket.expireAsync(Duration.ofDays(properties.getRetentionTime()));
      // can be removed at some point
      index.removeAsync(key, System.currentTimeMillis());
    }
    return snapshot;
  }

  private RBucket<Snapshot> createFromCodec(String key, Codec codec) {
    return redisson.getBucket(key, codec);
  }

  @Override
  public void setSnapshot(@NonNull Snapshot snapshot) {
    String key = createKeyFor(snapshot.id());
    redisson.getBucket(key).set(snapshot, properties.getRetentionTime(), TimeUnit.DAYS);
  }

  @Override
  public void clearSnapshot(@NonNull SnapshotId id) {
    redisson.getBucket(createKeyFor(id)).delete();
  }

  @Override
  @Deprecated // using EXPIRE instead
  public void compact(int retentionTimeInDays) {
    Duration daysAgo = Duration.ofDays(retentionTimeInDays);
    long threshold = Instant.now().minus(daysAgo).toEpochMilli();
    removeEntriesUntouchedSince(threshold);
  }

  @VisibleForTesting
  @Deprecated
  public void removeEntriesUntouchedSince(long threshold) {
    index
        .readAllEntrySet()
        .forEach(
            e -> {
              if (e.getValue() < threshold) {
                String key = e.getKey();
                redisson.getBucket(key).deleteAsync();
                index.removeAsync(key);
              }
            });
  }

  protected Optional<Codec> getCodecAccordingToProperties(RedissonSnapshotProperties properties) {
    switch (properties.getSnapshotCacheRedissonCodec()) {
      case RedissonDefault:
        return Optional.empty();
      case MarshallingCodec:
        return Optional.of(new MarshallingCodec());
      case Kryo5Codec:
        return Optional.of(new Kryo5Codec());
      case JsonJacksonCodec:
        return Optional.of(new JsonJacksonCodec());
      case SmileJacksonCodec:
        return Optional.of(new SmileJacksonCodec());
      case CborJacksonCodec:
        return Optional.of(new CborJacksonCodec());
      case MsgPackJacksonCodec:
        return Optional.of(new MsgPackJacksonCodec());
      case IonJacksonCodec:
        return Optional.of(new IonJacksonCodec());
      case SerializationCodec:
        return Optional.of(new SerializationCodec());
      case LZ4Codec:
        return Optional.of(new LZ4Codec());
      case SnappyCodecV2:
        return Optional.of(new SnappyCodecV2());
      case StringCodec:
        return Optional.of(new StringCodec());
      case LongCodec:
        return Optional.of(new LongCodec());
      case ByteArrayCodec:
        return Optional.of(new ByteArrayCodec());
      default:
        throw new IllegalStateException(
            "Unexpected enum value: " + properties.getSnapshotCacheRedissonCodec());
    }
  }

  @NonNull
  @VisibleForTesting
  String createKeyFor(@NonNull SnapshotId id) {
    return id.key() + id.uuid();
  }
}
