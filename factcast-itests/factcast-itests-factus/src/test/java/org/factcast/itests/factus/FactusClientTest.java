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

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotId;
import org.factcast.core.snap.SnapshotRepository;
import org.factcast.factus.Factus;
import org.junit.jupiter.api.*;
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
    SnapshotRepository repository;

    @Test
    public void simpleSnapshotRoundtrip() throws Exception {

        SnapshotId id = new SnapshotId("test", UUID.randomUUID());
        // initially empty
        assertThat(repository.getSnapshot(id)).isEmpty();

        // set and retrieve
        repository.setSnapshot(id, UUID.randomUUID(), "foo".getBytes());
        Optional<Snapshot> snapshot = repository.getSnapshot(id);
        assertThat(snapshot).isNotEmpty();
        assertThat(snapshot.get().bytes()).isEqualTo("foo".getBytes());

        // overwrite and retrieve
        repository.setSnapshot(id, UUID.randomUUID(), "bar".getBytes());
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
        assertThat(ec.fetch(TestAggregate.class, aggregateId)).isEmpty();
        assertThat(ec.fetch(TestAggregate.class, aggregateId)).isEmpty();

        ec.batch()
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

        Optional<TestAggregate> optionalTestAggregate = ec.fetch(TestAggregate.class,
                aggregateId);
        assertThat(optionalTestAggregate).isNotEmpty();
        assertThat(optionalTestAggregate.get().magicNumber()).isEqualTo(50);

        log.info(
                "now we're not expecting to see event processing due to the snapshot being up to date");

        Optional<TestAggregate> refetch = ec.fetch(TestAggregate.class, aggregateId);
        assertThat(optionalTestAggregate).isNotEmpty();
        assertThat(optionalTestAggregate.get().magicNumber()).isEqualTo(50);

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

    @Test
    public void simpleManagedProjectionRoundtrip() throws Exception {

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
        assertThat(ec.fetch(TestAggregate.class, aggregateId)).isEmpty();

        ec.publish(new TestAggregateWasIncremented(aggregateId));
        assertThat(ec.fetch(TestAggregate.class, aggregateId)).isNotEmpty();
        assertThat(ec.fetch(TestAggregate.class, aggregateId).get().magicNumber()).isEqualTo(43);

        // we start 10 threads that try to (in an isolated fashion) lock and
        // increase
        Set<CompletableFuture<Void>> futures = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            String workerID = "Worker #" + i;

            futures.add(CompletableFuture.runAsync(() -> ec.lockAggregate(aggregateId)
                    .attempt(tx -> {

                        log.info(workerID);
                        sleepRandomMillis();

                        // fetch
                        TestAggregate fetch = tx.fetch(TestAggregate.class, aggregateId).get();
                        // check business rule
                        if (fetch.magicNumber() < 50) {
                            // increment or
                            tx.publish(new TestAggregateWasIncremented(aggregateId));
                        } else {
                            // abort, according to business rule
                            tx.abort("aborting " + workerID + ": magic number is too high already ("
                                    + fetch.magicNumber() + ")");
                        }
                    })));
        }

        // wait for all threads to succeed or abort
        waitForAllToTerminate(futures);

        assertThat(ec.fetch(TestAggregate.class, aggregateId).get().magicNumber()).isEqualTo(50);

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
