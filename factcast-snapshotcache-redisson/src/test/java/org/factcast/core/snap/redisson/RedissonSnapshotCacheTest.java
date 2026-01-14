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
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import org.factcast.core.snap.Snapshot;
import org.factcast.factus.projection.Aggregate;
import org.factcast.factus.projection.SnapshotProjection;
import org.factcast.factus.serializer.DefaultSnapshotSerializer;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.factcast.factus.serializer.SnapshotSerializer;
import org.factcast.factus.snapshot.SnapshotData;
import org.factcast.factus.snapshot.SnapshotIdentifier;
import org.factcast.factus.snapshot.SnapshotSerializerSelector;
import org.factcast.test.IntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.Codec;
import org.redisson.spring.starter.RedissonAutoConfigurationV2;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
// TODO SB4 : @SpringJUnitConfig(classes = {RedissonAutoConfigurationV2.class,
// RedisAutoConfiguration.class})
@SpringJUnitConfig(classes = {RedissonAutoConfigurationV2.class})
@ExtendWith(MockitoExtension.class)
@Testcontainers
@IntegrationTest
class RedissonSnapshotCacheTest {

  @SuppressWarnings("rawtypes")
  @Container
  static final GenericContainer redis =
      new GenericContainer<>("redis:5.0.3-alpine").withExposedPorts(6379);

  private final RedissonSnapshotProperties props =
      new RedissonSnapshotProperties()
          .setSnapshotCacheRedissonCodec(RedissonSnapshotProperties.RedissonCodec.RedissonDefault)
          .setDeleteSnapshotStaleForDays(90);

  @BeforeAll
  public static void startContainers() throws InterruptedException {
    System.setProperty("spring.data.redis.host", redis.getHost());
    System.setProperty("spring.data.redis.port", String.valueOf(redis.getMappedPort(6379)));
  }

  @MockitoSpyBean private RedissonClient redisson;

  @Mock SnapshotSerializerSelector selector;

  final SnapshotSerializer serializer = new DefaultSnapshotSerializer();

  private RedissonSnapshotCache underTest;

  @Nested
  class WhenGettingSnapshot {
    @BeforeEach
    void setup() {
      when(selector.selectSeralizerFor(any())).thenReturn(serializer);
      underTest = new RedissonSnapshotCache(redisson, selector, props);
      redisson.getKeys().flushdb();
    }

    @Test
    void returnsEmptyForAggregate() {
      assertThat(underTest.find(SnapshotIdentifier.of(TestAggregate.class, randomUUID())))
          .isEmpty();
    }

    @Test
    void returnsEmptyForSnapshot() {
      assertThat(underTest.find(SnapshotIdentifier.of(TestSnapshotProjection.class))).isEmpty();
    }

    @Test
    void returnsDataForAggregate() {
      SnapshotIdentifier id = SnapshotIdentifier.of(TestAggregate.class, randomUUID());
      SnapshotData data = new SnapshotData(new byte[] {1, 2, 3}, serializer.id(), randomUUID());
      underTest.store(id, data);

      assertThat(underTest.find(id)).isNotEmpty().hasValue(data);
    }

    @Test
    void returnsDataForSnapshot() {
      SnapshotIdentifier id = SnapshotIdentifier.of(TestSnapshotProjection.class);
      SnapshotData data = new SnapshotData(new byte[] {1, 2, 3}, serializer.id(), randomUUID());
      underTest.store(id, data);

      assertThat(underTest.find(id)).isNotEmpty().hasValue(data);
    }
  }

  @Nested
  class WhenClearingSnapshot {
    @BeforeEach
    void setup() {
      when(selector.selectSeralizerFor(any())).thenReturn(serializer);
      underTest = new RedissonSnapshotCache(redisson, selector, props);
    }

    @Test
    void removesAggregate() {
      SnapshotIdentifier id = SnapshotIdentifier.of(TestAggregate.class, randomUUID());
      SnapshotData data = new SnapshotData(new byte[] {1, 2, 3}, serializer.id(), randomUUID());
      underTest.store(id, data);

      assertThat(underTest.find(id)).isNotEmpty().hasValue(data);

      underTest.remove(id);

      assertThat(underTest.find(id)).isEmpty();
    }

    @Test
    void removesSnapshot() {
      SnapshotIdentifier id = SnapshotIdentifier.of(TestSnapshotProjection.class);
      SnapshotData data = new SnapshotData(new byte[] {1, 2, 3}, serializer.id(), randomUUID());
      underTest.store(id, data);

      assertThat(underTest.find(id)).isNotEmpty().hasValue(data);

      underTest.remove(id);

      assertThat(underTest.find(id)).isEmpty();
    }
  }

  @Nested
  class WhenCompacting {
    private final int RETENTION_TIME_IN_DAYS = 42;

    @BeforeEach
    void setup() {
      when(selector.selectSeralizerFor(any())).thenReturn(serializer);
      RedissonSnapshotProperties props =
          new RedissonSnapshotProperties()
              .setSnapshotCacheRedissonCodec(
                  RedissonSnapshotProperties.RedissonCodec.RedissonDefault)
              .setDeleteSnapshotStaleForDays(RETENTION_TIME_IN_DAYS);
      underTest = new RedissonSnapshotCache(redisson, selector, props);
    }

    @Test
    void testTTL() {
      SnapshotIdentifier id = SnapshotIdentifier.of(TestAggregate.class, randomUUID());
      SnapshotData data = new SnapshotData(new byte[] {1, 2, 3}, serializer.id(), randomUUID());
      RBucket mockBucket = mock(RBucket.class);
      when(redisson.getBucket(underTest.createKeyFor(id), ByteArrayCodec.INSTANCE))
          .thenReturn(mockBucket);
      when(mockBucket.get()).thenReturn(new byte[] {4, 5, 6});

      underTest.store(id, data);

      verify(mockBucket).set(data.toBytes(), RETENTION_TIME_IN_DAYS, TimeUnit.DAYS);

      underTest.find(id); // touches it

      verify(mockBucket).expireAsync(Duration.ofDays(RETENTION_TIME_IN_DAYS));
    }

    @Test
    void testCreateKeyFor() {
      UUID uuid = UUID.fromString("a1d642dd-3ecd-4b58-ba24-deb8436cc329");
      assertThat(underTest.createKeyFor(SnapshotIdentifier.of(MyAgg.class, uuid)))
          .isEqualTo("sc_hugo_1_" + uuid);

      assertThat(underTest.createKeyFor(SnapshotIdentifier.of(TestAggregate.class, uuid)))
          .isEqualTo(
              "sc_org.factcast.core.snap.redisson.RedissonSnapshotCacheTest$TestAggregate_1_"
                  + uuid);

      assertThat(underTest.createKeyFor(SnapshotIdentifier.of(TestSnapshotProjection.class)))
          .isEqualTo(
              "sc_org.factcast.core.snap.redisson.RedissonSnapshotCacheTest$TestSnapshotProjection_1_snapshot");
    }

    @Test
    @SuppressWarnings("deprecation")
    void testTTLOnLegacy() {
      SnapshotIdentifier id = SnapshotIdentifier.of(TestAggregate.class, randomUUID());
      RBucket mockEmptyBucket = mock(RBucket.class);
      when(redisson.getBucket(underTest.createKeyFor(id), ByteArrayCodec.INSTANCE))
          .thenReturn(mockEmptyBucket);
      when(mockEmptyBucket.get()).thenReturn(null);
      RBucket mockLegacyBucket = mock(RBucket.class);
      when(redisson.getBucket(underTest.createLegacyKeyFor(id))).thenReturn(mockLegacyBucket);
      Snapshot snapshot = mock(Snapshot.class);
      when(mockLegacyBucket.get()).thenReturn(snapshot);
      when(snapshot.bytes()).thenReturn(new byte[] {1, 2, 3});
      when(snapshot.lastFact()).thenReturn(randomUUID());

      underTest.find(id); // touches it

      verify(mockLegacyBucket).expireAsync(Duration.ofDays(RETENTION_TIME_IN_DAYS));
    }

    @SneakyThrows
    private void sleep(long millis) {
      Thread.sleep(millis);
    }
  }

  @Nested
  class WhenCodecIsSet {

    private final ArgumentCaptor<Codec> argument = ArgumentCaptor.forClass(Codec.class);

    @BeforeEach
    void setup() {
      when(selector.selectSeralizerFor(any())).thenReturn(serializer);
      underTest =
          new RedissonSnapshotCache(
              redisson,
              selector,
              new RedissonSnapshotProperties()
                  .setSnapshotCacheRedissonCodec(
                      RedissonSnapshotProperties.RedissonCodec.Kryo5Codec)
                  .setDeleteSnapshotStaleForDays(90));
    }

    @Test
    void usesByteArrayCodecOnAggregate() {
      SnapshotIdentifier id = SnapshotIdentifier.of(TestAggregate.class, randomUUID());
      SnapshotData data = new SnapshotData(new byte[] {1, 2, 3}, serializer.id(), randomUUID());

      underTest.store(id, data);
      underTest.find(id);

      verify(redisson, times(2)).getBucket(any(), argument.capture());

      argument
          .getAllValues()
          .forEach(codec -> assertThat(codec).isInstanceOf(ByteArrayCodec.class));
    }

    @Test
    void usesByteArrayCodecOnSnapshot() {
      SnapshotIdentifier id = SnapshotIdentifier.of(TestSnapshotProjection.class);
      SnapshotData data = new SnapshotData(new byte[] {1, 2, 3}, serializer.id(), randomUUID());

      underTest.store(id, data);
      underTest.find(id);

      verify(redisson, times(2)).getBucket(any(), argument.capture());

      argument
          .getAllValues()
          .forEach(codec -> assertThat(codec).isInstanceOf(ByteArrayCodec.class));
    }
  }

  @ProjectionMetaData(revision = 1)
  public class TestAggregate extends Aggregate {}

  @ProjectionMetaData(revision = 1)
  public class TestSnapshotProjection implements SnapshotProjection {}

  @ProjectionMetaData(name = "hugo", revision = 1)
  static class MyAgg extends Aggregate {}
}
