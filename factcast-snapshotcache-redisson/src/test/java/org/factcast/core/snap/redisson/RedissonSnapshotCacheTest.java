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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.*;
import lombok.SneakyThrows;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotId;
import org.factcast.test.IntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.redisson.codec.Kryo5Codec;
import org.redisson.spring.starter.RedissonAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ContextConfiguration(classes = {RedissonAutoConfiguration.class, RedisAutoConfiguration.class})
@ExtendWith(SpringExtension.class)
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

  @SpyBean private RedissonClient redisson;

  private RedissonSnapshotCache underTest;

  @Nested
  class WhenGettingSnapshot {
    @BeforeEach
    void setup() {
      underTest = new RedissonSnapshotCache(redisson, props);
    }

    @Test
    void testGetNull() {
      assertThat(underTest.getSnapshot(SnapshotId.of("foo", UUID.randomUUID()))).isEmpty();
    }

    @Test
    void testGetPojo() {
      SnapshotId id = SnapshotId.of("foo", UUID.randomUUID());
      Snapshot snap = new Snapshot(id, UUID.randomUUID(), "foo".getBytes(), false);
      underTest.setSnapshot(snap);

      assertThat(underTest.getSnapshot(id)).isNotEmpty().hasValue(snap);
    }
  }

  @Nested
  class WhenClearingSnapshot {
    @BeforeEach
    void setup() {
      underTest = new RedissonSnapshotCache(redisson, props);
    }

    @Test
    void testClearPojo() {
      SnapshotId id = SnapshotId.of("foo", UUID.randomUUID());
      Snapshot snap = new Snapshot(id, UUID.randomUUID(), "foo".getBytes(), false);
      underTest.setSnapshot(snap);

      assertThat(underTest.getSnapshot(id)).isNotEmpty().hasValue(snap);

      underTest.clearSnapshot(id);

      assertThat(underTest.getSnapshot(id)).isEmpty();
    }
  }

  @Nested
  class WhenCompacting {
    private final int RETENTION_TIME_IN_DAYS = 95;

    @BeforeEach
    void setup() {
      underTest = new RedissonSnapshotCache(redisson, props);
    }

    @Test
    void testTTL() {

      int i = 1;
      SnapshotId s1 = SnapshotId.of("foo" + (i++), UUID.randomUUID());
      Snapshot snap1 = new Snapshot(s1, UUID.randomUUID(), "foo".getBytes(), false);

      SnapshotId s2 = SnapshotId.of("foo" + (i++), UUID.randomUUID());
      Snapshot snap2 = new Snapshot(s2, UUID.randomUUID(), "foo".getBytes(), false);

      underTest.setSnapshot(snap1);
      sleep(2000);
      underTest.setSnapshot(snap2);
      sleep(500); // wait for async op
      {
        // assert all buckets have a ttl
        long ttl1 = redisson.getBucket(underTest.createKeyFor(s1)).remainTimeToLive();
        long ttl2 = redisson.getBucket(underTest.createKeyFor(s2)).remainTimeToLive();

        assertThat(ttl1).isGreaterThan(7775990000L);
        assertThat(ttl2).isGreaterThan(7775990000L);
        assertThat(ttl1).isLessThanOrEqualTo(ttl2);
      }

      sleep(2000);

      underTest.getSnapshot(s1); // touches it

      sleep(500); // wait for async op
      {
        long ttl1 = redisson.getBucket(underTest.createKeyFor(s1)).remainTimeToLive();
        long ttl2 = redisson.getBucket(underTest.createKeyFor(s2)).remainTimeToLive();

        assertThat(ttl1).isGreaterThan(7775990000L);
        assertThat(ttl2).isGreaterThan(7775990000L);
        assertThat(ttl1).isGreaterThan(ttl2);
      }
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
      underTest =
          new RedissonSnapshotCache(
              redisson,
              new RedissonSnapshotProperties()
                  .setSnapshotCacheRedissonCodec(
                      RedissonSnapshotProperties.RedissonCodec.Kryo5Codec)
                  .setDeleteSnapshotStaleForDays(90));
    }

    @Test
    void testUsageOfCodecFromProperties() {
      SnapshotId id = SnapshotId.of("foo", UUID.randomUUID());
      Snapshot snap = new Snapshot(id, UUID.randomUUID(), "foo".getBytes(), false);

      underTest.setSnapshot(snap);
      underTest.getSnapshot(id);

      verify(redisson, times(1)).getMap(any(), argument.capture());
      verify(redisson, times(2)).getBucket(any(), argument.capture());

      argument.getAllValues().forEach(codec -> assertThat(codec).isInstanceOf(Kryo5Codec.class));
    }
  }
}
