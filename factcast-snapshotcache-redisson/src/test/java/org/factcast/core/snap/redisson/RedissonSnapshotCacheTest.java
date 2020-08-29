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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.Mock;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import lombok.NonNull;
import lombok.SneakyThrows;

@SpringBootTest(classes = Application.class)
@ExtendWith(SpringExtension.class)
@Testcontainers
class RedissonSnapshotCacheTest {

    // new GenericContainer(DockerImageName.parse("redis:5.0.3-alpine"))
    // .withExposedPorts(6379)

    @SuppressWarnings("rawtypes")
    @Container
    static final GenericContainer redis = new GenericContainer<>("redis:5.0.3-alpine")
            .withExposedPorts(6379);

    @Mock
    private RMap<String, Long> index;

    @BeforeAll
    public static void startContainers() throws InterruptedException {
        System.setProperty("spring.redis.host", redis.getHost());
        System.setProperty("spring.redis.port", String.valueOf(redis.getMappedPort(6379)));
    }

    @Autowired
    private RedissonClient redisson;

    private RedissonSnapshotCache underTest;

    @Nested
    class WhenGettingSnapshot {

        private @NonNull SnapshotId id;

        @BeforeEach
        void setup() {
            underTest = new RedissonSnapshotCache(redisson);
        }

        @Test
        void testGetNull() {
            assertThat(underTest.getSnapshot(new SnapshotId("foo", UUID.randomUUID()))).isEmpty();
        }

        @Test
        void testGetPojo() {
            SnapshotId id = new SnapshotId("foo", UUID.randomUUID());
            Snapshot snap = new Snapshot(id, UUID.randomUUID(), "foo".getBytes(), false);
            underTest.setSnapshot(snap);

            assertThat(underTest.getSnapshot(id))
                    .isNotEmpty()
                    .hasValue(snap);
        }

    }

    @Nested
    class WhenClearingSnapshot {
        private @NonNull SnapshotId id;

        @BeforeEach
        void setup() {
            underTest = new RedissonSnapshotCache(redisson);
        }

        @Test
        void testClearPojo() {
            SnapshotId id = new SnapshotId("foo", UUID.randomUUID());
            Snapshot snap = new Snapshot(id, UUID.randomUUID(), "foo".getBytes(), false);
            underTest.setSnapshot(snap);

            assertThat(underTest.getSnapshot(id))
                    .isNotEmpty()
                    .hasValue(snap);

            underTest.clearSnapshot(id);

            assertThat(underTest.getSnapshot(id))
                    .isEmpty();

        }

    }

    @Nested
    class WhenCompacting {
        private final int RETENTION_TIME_IN_DAYS = 95;

        @BeforeEach
        void setup() {
            underTest = new RedissonSnapshotCache(redisson);
        }

        @Test
        void testCompactionThreashold() {

            int i = 1;
            SnapshotId s1 = new SnapshotId("foo" + (i++), UUID.randomUUID());
            Snapshot snap1 = new Snapshot(s1, UUID.randomUUID(), "foo".getBytes(), false);

            SnapshotId s2 = new SnapshotId("foo" + (i++), UUID.randomUUID());
            Snapshot snap2 = new Snapshot(s2, UUID.randomUUID(), "foo".getBytes(), false);

            SnapshotId s3 = new SnapshotId("foo" + (i++), UUID.randomUUID());
            Snapshot snap3 = new Snapshot(s3, UUID.randomUUID(), "foo".getBytes(), false);

            underTest.setSnapshot(snap1);
            underTest.setSnapshot(snap2);
            underTest.setSnapshot(snap3);

            sleep(500);

            underTest.getSnapshot(s2); // touches it

            sleep(100); // wait for async op to complete

            underTest.removeEntriesUntouchedSince(System.currentTimeMillis() - 300); // should
                                                                                     // leave
                                                                                     // snap2

            assertThat(underTest.getSnapshot(s1)).isEmpty();
            assertThat(underTest.getSnapshot(s3)).isEmpty();

            assertThat(underTest.getSnapshot(s2)).isNotEmpty().hasValue(snap2);

            return;
        }

        @SneakyThrows
        private void sleep(long millis) {
            Thread.sleep(millis);
        }
    }

}
