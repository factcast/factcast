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

import com.google.common.cache.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import javax.annotation.Nonnull;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.util.ExceptionHelper;
import org.factcast.factus.snapshot.*;
import org.redisson.api.*;
import org.redisson.api.listener.TrackingListener;

@SuppressWarnings("deprecation")
@Slf4j
public class RedissonLocalSnapshotCache extends RedissonSnapshotCache {

  @Nonnull private final LoadingCache<String, Optional<SnapshotData>> cache;
  private final Map<String, Integer> registeredListeners =
      Collections.synchronizedMap(new HashMap<>());

  public RedissonLocalSnapshotCache(
      @Nonnull RedissonClient redisson, @Nonnull RedissonSnapshotProperties properties) {
    super(redisson, properties);
    cache =
        CacheBuilder.newBuilder()
            .expireAfterWrite(properties.getRetentionTime(), TimeUnit.DAYS)
            .maximumSize(properties.getLocalCacheSize())
            .softValues()
            .removalListener(
                (RemovalListener<String, Optional<SnapshotData>>)
                    notification -> {
                      // make sure, we'll disconnect again
                      RemovalCause cause = notification.getCause();
                      if (cause != RemovalCause.REPLACED) {
                        String key = notification.getKey();
                        Integer listenerId = registeredListeners.get(key);
                        if (listenerId != null)
                          redisson.getBucket(key, CODEC).removeListenerAsync(listenerId);
                      }
                    })
            .build(
                new CacheLoader<>() {
                  @Override
                  public Optional<SnapshotData> load(@NonNull String redisKey) throws Exception {
                    return fetchFromRedis(redisKey);
                  }
                });
  }

  @Override
  public @NonNull Optional<SnapshotData> find(@NonNull SnapshotIdentifier id) {
    String key = createKeyFor(id).intern();
    try {
      return cache.get(key);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause != null) throw ExceptionHelper.toRuntime(cause);
      else throw new RuntimeException(e);
    }
  }

  private @NonNull Optional<SnapshotData> fetchFromRedis(@NonNull String redisKey) {
    RBucket<byte[]> bucket = redisson.getBucket(redisKey, CODEC);
    byte[] bytes = bucket.get();
    if (bytes != null && bytes.length > 0) {
      bucket.expireAsync(Duration.ofDays(properties.getRetentionTime()));
      SnapshotData data = SnapshotData.from(bytes).orElse(null);
      if (data != null) {
        synchronized (registeredListeners) {
          if (!registeredListeners.containsKey(redisKey)) {
            // we do not yet have a listener
            int listenerId =
                bucket.addListener((TrackingListener) name -> cache.invalidate(redisKey));
            registeredListeners.put(redisKey, listenerId);
          }
        }
      }
      return Optional.ofNullable(data);
    } else return Optional.empty();
  }

  @Override
  public void remove(@NonNull SnapshotIdentifier id) {
    String key = createKeyFor(id).intern();
    redisson.getBucket(key).delete();
    cache.invalidate(key);
  }
}
