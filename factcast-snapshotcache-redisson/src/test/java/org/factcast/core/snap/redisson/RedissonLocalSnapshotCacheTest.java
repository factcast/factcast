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

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.factcast.factus.serializer.DefaultSnapshotSerializer;
import org.factcast.factus.serializer.SnapshotSerializer;
import org.factcast.factus.snapshot.SnapshotData;
import org.factcast.factus.snapshot.SnapshotIdentifier;
import org.factcast.test.IntegrationTest;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.listener.TrackingListener;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.spring.starter.RedissonAutoConfigurationV4;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@SpringJUnitConfig(classes = {RedissonAutoConfigurationV4.class, Conf.class})
@ExtendWith(MockitoExtension.class)
@Testcontainers
@IntegrationTest
class RedissonLocalSnapshotCacheTest extends RedissonSnapshotCacheTest {

  @Nested
  class WhenReadingSnapshots {
    private final SnapshotSerializer serializer = new DefaultSnapshotSerializer();
    private RedissonLocalSnapshotCache underTest;

    @BeforeEach
    void setup() {
      underTest = (RedissonLocalSnapshotCache) createUnderTest();
      redisson.getKeys().flushdb();
    }

    @Test
    void usesLocalCacheOnRepeatedFindForSameIdentifier() {
      SnapshotIdentifier id = SnapshotIdentifier.of(TestAggregate.class, randomUUID());
      SnapshotData data = new SnapshotData(new byte[] {1, 2, 3}, serializer.id(), randomUUID());
      String key = underTest.createKeyFor(id);
      @SuppressWarnings("rawtypes")
      var bucket = mock(org.redisson.api.RBucket.class);

      when(redisson.getBucket(key, ByteArrayCodec.INSTANCE)).thenReturn(bucket);
      when(bucket.get()).thenReturn(data.toBytes());
      when(bucket.addListener(any())).thenReturn(1);

      assertThat(underTest.find(id)).contains(data);
      assertThat(underTest.find(id)).contains(data);

      verify(redisson, times(1)).getBucket(key, ByteArrayCodec.INSTANCE);
      verify(bucket, times(1)).get();
      verify(bucket, times(1)).addListener(any());
      verify(bucket, times(1)).expireAsync(any(Duration.class));
      verify(bucket, never()).set(any(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void invalidatesLocalCacheWhenRedisSendsNotification() {
      SnapshotIdentifier id = SnapshotIdentifier.of(TestAggregate.class, randomUUID());
      String key = underTest.createKeyFor(id);
      SnapshotData first = new SnapshotData(new byte[] {1, 2, 3}, serializer.id(), randomUUID());
      SnapshotData second = new SnapshotData(new byte[] {4, 5, 6}, serializer.id(), randomUUID());
      @SuppressWarnings("rawtypes")
      var bucket = mock(org.redisson.api.RBucket.class);
      ArgumentCaptor<TrackingListener> trackingListenerCaptor =
          ArgumentCaptor.forClass(TrackingListener.class);

      when(redisson.getBucket(key, ByteArrayCodec.INSTANCE)).thenReturn(bucket);
      when(bucket.get()).thenReturn(first.toBytes(), second.toBytes());
      when(bucket.addListener(any())).thenReturn(1);

      assertThat(underTest.find(id)).contains(first);
      assertThat(underTest.find(id)).contains(first);

      verify(bucket).addListener(trackingListenerCaptor.capture());
      trackingListenerCaptor.getValue().onChange(key);

      assertThat(underTest.find(id)).contains(second);

      verify(bucket, times(2)).get();
      verify(bucket, times(1)).addListener(any());
    }

    @Test
    void removesTrackingListenerWhenEntryIsInvalidatedLocally() {
      SnapshotIdentifier id = SnapshotIdentifier.of(TestAggregate.class, randomUUID());
      String key = underTest.createKeyFor(id);
      SnapshotData first = new SnapshotData(new byte[] {1, 2, 3}, serializer.id(), randomUUID());
      @SuppressWarnings("rawtypes")
      var bucket = mock(org.redisson.api.RBucket.class);

      when(redisson.getBucket(key, ByteArrayCodec.INSTANCE)).thenReturn(bucket);
      when(redisson.getBucket(key)).thenReturn(bucket);
      when(bucket.get()).thenReturn(first.toBytes());
      when(bucket.addListener(any())).thenReturn(1);

      assertThat(underTest.find(id)).contains(first);

      underTest.remove(id);

      verify(bucket, times(1)).addListener(any());
      verify(bucket, times(1)).removeListenerAsync(1);
    }

    @Test
    void removesTrackingListenerWhenEntryIsEvictedDueToLocalCacheSize() {
      RedissonSnapshotProperties sizeLimitedProperties =
          new RedissonSnapshotProperties()
              .setDeleteSnapshotStaleForDays(props.getDeleteSnapshotStaleForDays())
              .setLocalCacheSize(1);
      RedissonLocalSnapshotCache sizeLimitedCache =
          new RedissonLocalSnapshotCache(redisson, sizeLimitedProperties);

      SnapshotIdentifier firstId = SnapshotIdentifier.of(TestAggregate.class, randomUUID());
      SnapshotIdentifier secondId = SnapshotIdentifier.of(TestAggregate.class, randomUUID());
      String firstKey = sizeLimitedCache.createKeyFor(firstId);
      String secondKey = sizeLimitedCache.createKeyFor(secondId);
      SnapshotData first = new SnapshotData(new byte[] {1, 2, 3}, serializer.id(), randomUUID());
      SnapshotData second = new SnapshotData(new byte[] {4, 5, 6}, serializer.id(), randomUUID());
      @SuppressWarnings("rawtypes")
      var firstBucket = mock(org.redisson.api.RBucket.class);
      @SuppressWarnings("rawtypes")
      var secondBucket = mock(org.redisson.api.RBucket.class);

      when(redisson.getBucket(firstKey, ByteArrayCodec.INSTANCE)).thenReturn(firstBucket);
      when(redisson.getBucket(secondKey, ByteArrayCodec.INSTANCE)).thenReturn(secondBucket);
      when(firstBucket.get()).thenReturn(first.toBytes());
      when(secondBucket.get()).thenReturn(second.toBytes());
      when(firstBucket.addListener(any())).thenReturn(11);
      when(secondBucket.addListener(any())).thenReturn(22);

      assertThat(sizeLimitedCache.find(firstId)).contains(first);
      assertThat(sizeLimitedCache.find(secondId)).contains(second);

      verify(firstBucket, times(1)).addListener(any());
      verify(firstBucket, times(1)).removeListenerAsync(11);
      verify(secondBucket, times(1)).addListener(any());
    }
  }

  @Override
  protected @NonNull RedissonSnapshotCache createUnderTest() {
    return new RedissonLocalSnapshotCache(redisson, props);
  }
}
