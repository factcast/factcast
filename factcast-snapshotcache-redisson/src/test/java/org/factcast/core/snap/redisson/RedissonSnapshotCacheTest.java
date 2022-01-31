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

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.Mock;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.spring.starter.RedissonAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ContextConfiguration(classes = {RedissonAutoConfiguration.class, RedisAutoConfiguration.class})
@ExtendWith(SpringExtension.class)
@Testcontainers
class RedissonSnapshotCacheTest {

  @SuppressWarnings("rawtypes")
  @Container
  static final GenericContainer redis =
      new GenericContainer<>("redis:5.0.3-alpine").withExposedPorts(6379);

  @Mock private RMap<String, Long> index;

  @BeforeAll
  public static void startContainers() throws InterruptedException {
    System.setProperty("spring.redis.host", redis.getHost());
    System.setProperty("spring.redis.port", String.valueOf(redis.getMappedPort(6379)));
  }

  @Autowired private RedissonClient redisson;

  private RedissonSnapshotCache underTest;

  @Nested
  class WhenGettingSnapshot {

    private @NonNull SnapshotId id;

    @BeforeEach
    void setup() {
      underTest = new RedissonSnapshotCache(redisson, 90);
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
    private @NonNull SnapshotId id;

    @BeforeEach
    void setup() {
      underTest = new RedissonSnapshotCache(redisson, 90);
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
      underTest = new RedissonSnapshotCache(redisson, 90);
    }

    @Test
    void testTTL() {

      int i = 1;
      SnapshotId s1 = SnapshotId.of("foo" + (i++), UUID.randomUUID());
      Snapshot snap1 = new Snapshot(s1, UUID.randomUUID(), "foo".getBytes(), false);

      SnapshotId s2 = SnapshotId.of("foo" + (i++), UUID.randomUUID());
      Snapshot snap2 = new Snapshot(s2, UUID.randomUUID(), "foo".getBytes(), false);

      underTest.setSnapshot(snap1);
      sleep(10);
      underTest.setSnapshot(snap2);

      {
        // assert all buckets have a ttl
        long ttl1 = redisson.getBucket(underTest.createKeyFor(s1)).remainTimeToLive();
        long ttl2 = redisson.getBucket(underTest.createKeyFor(s2)).remainTimeToLive();

        assertThat(ttl1).isGreaterThan(7775990000L);
        assertThat(ttl2).isGreaterThan(7775990000L);
        assertThat(ttl1).isLessThanOrEqualTo(ttl2);
      }

      sleep(500);

      underTest.getSnapshot(s1); // touches it

      sleep(100); // wait fro async op
      {
        long ttl1 = redisson.getBucket(underTest.createKeyFor(s1)).remainTimeToLive();
        long ttl2 = redisson.getBucket(underTest.createKeyFor(s2)).remainTimeToLive();

        assertThat(ttl1).isGreaterThan(ttl2);
      }
    }

    @SneakyThrows
    private void sleep(long millis) {
      Thread.sleep(millis);
    }
  }
}
