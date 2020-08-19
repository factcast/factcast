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
package org.factcast.itests.factus;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotCache;
import org.factcast.core.snap.SnapshotId;
import org.factcast.factus.Factus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@SpringBootTest
@ContextConfiguration(classes = Application.class)
@Testcontainers
@Slf4j
@DirtiesContext(classMode = ClassMode.BEFORE_EACH_TEST_METHOD)
@AutoConfigureTestDatabase(replace = Replace.ANY)
@Sql(scripts = "classpath:/test-setup.sql")
public class FactusClientTest {

    static final Network _docker_network = Network.newNetwork();

    @Container
    static final PostgreSQLContainer _database_container = new PostgreSQLContainer<>(
            "postgres:11.5")
                    .withDatabaseName("fc")
                    .withUsername("fc")
                    .withPassword("fc")
                    .withNetworkAliases("db")
                    .withNetwork(_docker_network);

    @Container
    static final GenericContainer _factcast_container = new GenericContainer<>(
            "factcast/factcast:latest")
                    .withExposedPorts(9090)
                    .withFileSystemBind("./config", "/config/")
                    .withEnv("grpc.server.port", "9090")
                    .withEnv("factcast.security.enabled", "false")
                    .withEnv("spring.datasource.url", "jdbc:postgresql://db/fc?user=fc&password=fc")
                    .withNetwork(_docker_network)
                    .dependsOn(_database_container)
                    .withLogConsumer(new Slf4jLogConsumer(log))
                    .waitingFor(new HostPortWaitStrategy()
                            .withStartupTimeout(Duration.ofSeconds(180)));

    @BeforeAll
    public static void startContainers() throws InterruptedException {
        String address = "static://" +
                _factcast_container.getHost() + ":" +
                _factcast_container.getMappedPort(9090);
        System.setProperty("grpc.client.factstore.address", address);
    }

    @Autowired
    Factus ec;

    @Autowired
    SnapshotCache repository;

    @Test
    public void simpleSnapshotRoundtrip() throws Exception {
        SnapshotId id = new SnapshotId("test", UUID.randomUUID());
        // initially empty
        assertThat(repository.getSnapshot(id)).isEmpty();

        // set and retrieve
        repository.setSnapshot(new Snapshot(id, UUID.randomUUID(), "foo".getBytes(), false));
        Optional<Snapshot> snapshot = repository.getSnapshot(id);
        assertThat(snapshot).isNotEmpty();
        assertThat(snapshot.get().bytes()).isEqualTo("foo".getBytes());

        // overwrite and retrieve
        repository.setSnapshot(new Snapshot(id, UUID.randomUUID(), "bar".getBytes(), false));
        snapshot = repository.getSnapshot(id);
        assertThat(snapshot).isNotEmpty();
        assertThat(snapshot.get().bytes()).isEqualTo("bar".getBytes());

        // clear and make sure, it is cleared
        repository.clearSnapshot(id);
        assertThat(repository.getSnapshot(id)).isEmpty();

    }

    @Test
    public void simpleAggregateRoundtrip() throws Exception {
        UUID aggregateId = UUID.randomUUID();
        assertThat(ec.find(TestAggregate.class, aggregateId)).isEmpty();

        ec.batch()
                // 8 increment events for test aggregate
                .add(new TestAggregateWasIncremented(aggregateId))
                .add(new TestAggregateWasIncremented(aggregateId))
                .add(new TestAggregateWasIncremented(aggregateId))
                .add(new TestAggregateWasIncremented(aggregateId))
                .add(new TestAggregateWasIncremented(aggregateId))
                .add(new TestAggregateWasIncremented(aggregateId))
                .add(new TestAggregateWasIncremented(aggregateId))
                .add(new TestAggregateWasIncremented(aggregateId))
                .add(new TestAggregateWasIncremented(UUID.randomUUID()))
                .add(new TestAggregateWasIncremented(UUID.randomUUID()))

                .execute();

        TestAggregate a = ec.fetch(TestAggregate.class, aggregateId);
        // We started with magic number 42, incremented 8 times -> magic number
        // should be 50
        assertThat(a.magicNumber()).isEqualTo(50);

        log.info(
                "now we're not expecting to see event processing due to the snapshot being up to date");

        TestAggregate b = ec.fetch(TestAggregate.class, aggregateId);
        assertThat(b.magicNumber()).isEqualTo(50);

    }

    @Test
    public void simpleSnapshotProjectionRoundtrip() throws Exception {
        assertThat(ec.fetch(UserNames.class)).isNotNull();

        UUID johnsId = UUID.randomUUID();
        ec.batch()
                .add(new UserCreated(johnsId, "John"))
                .add(new UserCreated(UUID.randomUUID(), "Paul"))
                .add(new UserCreated(UUID.randomUUID(), "George"))
                .add(new UserCreated(UUID.randomUUID(), "Ringo"))
                .execute();

        val fabFour = ec.fetch(UserNames.class);
        assertThat(fabFour.count()).isEqualTo(4);
        assertThat(fabFour.contains("John")).isTrue();
        assertThat(fabFour.contains("Paul")).isTrue();
        assertThat(fabFour.contains("George")).isTrue();
        assertThat(fabFour.contains("Ringo")).isTrue();

        // sadly shot
        ec.publish(new UserDeleted(johnsId));

        val fabThree = ec.fetch(UserNames.class);
        assertThat(fabThree.count()).isEqualTo(3);
        assertThat(fabThree.contains("John")).isFalse();
        assertThat(fabThree.contains("Paul")).isTrue();
        assertThat(fabThree.contains("George")).isTrue();
        assertThat(fabThree.contains("Ringo")).isTrue();

        fabThree.allUserIdsForDeletingInTest().forEach(id -> ec.publish(new UserDeleted(id)));

    }

    @Value
    class UserCreateCMD {
        String userName;

        UUID userId;
    }

    @Test
    public void simpleProjectionLockingRoundtrip() throws Exception {
        /*
         * TODO:
         *
         * - emptyUserNames is actually empty
         *
         * - UserNames is 1 after first publish
         *
         * - UserNames is 0 after publish of delete
         */
        UserNames emptyUserNames = ec.fetch(UserNames.class);
        assertThat(emptyUserNames).isNotNull();

        UUID petersId = UUID.randomUUID();
        UserCreateCMD cmd = new UserCreateCMD("Peter", petersId);

        ec.withLockOn(UserNames.class)
                .retries(5)
                .intervalMillis(50)
                .attempt((names, tx) -> {

                    if (names.contains(cmd.userName)) {
                        tx.abort("baeh");
                    } else {
                        tx.publish(new UserCreated(cmd.userId, cmd.userName));
                    }

                });

        ec.publish(new UserDeleted(petersId));

    }

    @Test
    public void simpleManagedProjectionRoundtrip() throws Exception {
        /*
         * TODO: test again after ec.update
         */

        // lets consider userCount a springbean
        UserCount userCount = new UserCount();

        assertThat(userCount.state()).isNull();
        assertThat(userCount.count()).isEqualTo(0);
        ec.update(userCount);

        int before = userCount.count();

        UUID one = UUID.randomUUID();
        UUID two = UUID.randomUUID();
        ec.batch()
                .add(new UserCreated(one, "One"))
                .add(new UserCreated(two, "Two"))
                .execute();

        assertThat(userCount.count()).isEqualTo(before);
        ec.update(userCount);
        assertThat(userCount.count()).isEqualTo(before + 2);

        ec.publish(new UserDeleted(one));
        ec.update(userCount);
        assertThat(userCount.count()).isEqualTo(before + 1);

        ec.publish(new UserDeleted(two));
        ec.update(userCount);
        assertThat(userCount.count()).isEqualTo(before);

    }

    @Test
    void simpleJpaProjectionRoundtrip() {

        UUID johnsId = UUID.randomUUID();
        ec.batch()
                .add(new UserCreated(johnsId, "John"))
                .add(new UserCreated(UUID.randomUUID(), "Paul"))
                .add(new UserCreated(UUID.randomUUID(), "George"))
                .add(new UserCreated(UUID.randomUUID(), "Ringo"))
                .execute();

        val fabFour = jpaUserNames;
        ec.update(fabFour);

        assertThat(fabFour.count()).isEqualTo(4);
        assertThat(fabFour.contains("John")).isTrue();
        assertThat(fabFour.contains("Paul")).isTrue();
        assertThat(fabFour.contains("George")).isTrue();
        assertThat(fabFour.contains("Ringo")).isTrue();

        // sadly shot
        ec.publish(new UserDeleted(johnsId));

        // TODO: test that publishing itself does not change the projection

        val fabThree = jpaUserNames;
        ec.update(fabThree);

        assertThat(fabThree.count()).isEqualTo(3);
        assertThat(fabThree.contains("John")).isFalse();
        assertThat(fabThree.contains("Paul")).isTrue();
        assertThat(fabThree.contains("George")).isTrue();
        assertThat(fabThree.contains("Ringo")).isTrue();

        fabThree.allUserIdsForDeletingInTest().forEach(id -> ec.publish(new UserDeleted(id)));
    }

    @Autowired
    JpaUserNames jpaUserNames;

    @Test
    public void simpleAggregateLockRoundtrip() throws Exception {
        UUID aggregateId = UUID.randomUUID();
        assertThat(ec.find(TestAggregate.class, aggregateId)).isEmpty();

        ec.publish(new TestAggregateWasIncremented(aggregateId));

        Optional<TestAggregate> optAggregate = ec.find(TestAggregate.class, aggregateId);
        assertThat(optAggregate).isNotEmpty();

        val aggregate = ec.fetch(TestAggregate.class, aggregateId);
        assertThat(aggregate.magicNumber()).isEqualTo(43);

        // TODO: why do we need to fetch another one, rather than reusing
        // "aggregate"?
        val a = ec.fetch(TestAggregate.class, aggregateId);

        // we start 10 threads that try to (in an isolated fashion) lock and
        // increase. Starting with magic number 43, we would end up 53, but
        // we have a business rule that limits this to 50.
        Set<CompletableFuture<Void>> futures = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            String workerID = "Worker #" + i;

            futures.add(CompletableFuture.runAsync(() -> ec.withLockOn(a)
                    .attempt((ta, tx) -> {

                        log.info(workerID);
                        // TODO: Should it be part of the test to enforce that
                        // at least one thread
                        // publishes while being in a stale state, and the code
                        // inside of attempt is re-run?
                        // Then sleepRandomMillis might not be enough.
                        // If it is not, then sleepRandomMillies is actually not
                        // needed?
                        //
                        sleepRandomMillis();

                        // check business rule
                        if (ta.magicNumber() < 50) {
                            // increment or
                            tx.publish(new TestAggregateWasIncremented(aggregateId));
                        } else {
                            // abort, according to business rule
                            tx.abort("aborting " + workerID + ": magic number is too high already ("
                                    + ta.magicNumber() + ")");
                        }
                    })));
        }

        // wait for all threads to succeed or abort
        waitForAllToTerminate(futures);

        // make sure business rule was properly applied (we have 50 instead of
        // 53)
        assertThat(ec.fetch(TestAggregate.class, aggregateId).magicNumber()).isEqualTo(50);

    }

    private void waitForAllToTerminate(Set<CompletableFuture<Void>> futures) {
        futures.forEach(f -> {
            try {
                f.get();
            } catch (Exception e) {
                log.warn(e.getMessage());
            }
        });
    }

    private void sleepRandomMillis() {
        try {
            Thread.sleep((long) (Math.random() * 1000));
        } catch (InterruptedException e) {
        }
    }
}
