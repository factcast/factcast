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

import static java.util.Arrays.asList;
import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.toList;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import config.RedissonProjectionConfiguration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.Data;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.factcast.core.Fact;
import org.factcast.core.event.EventConverter;
import org.factcast.core.subscription.Subscription;
import org.factcast.factus.Factus;
import org.factcast.factus.HandlerFor;
import org.factcast.factus.lock.LockedOperationAbortedException;
import org.factcast.factus.projection.Aggregate;
import org.factcast.factus.projection.LocalManagedProjection;
import org.factcast.itests.factus.event.TestAggregateIncremented;
import org.factcast.itests.factus.event.UserCreated;
import org.factcast.itests.factus.event.UserDeleted;
import org.factcast.itests.factus.proj.RedissonManagedUserNames;
import org.factcast.itests.factus.proj.SnapshotUserNames;
import org.factcast.itests.factus.proj.SubscribedUserNames;
import org.factcast.itests.factus.proj.TestAggregate;
import org.factcast.itests.factus.proj.UserCount;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ContextConfiguration(classes = {Application.class, RedissonProjectionConfiguration.class})
@EnableAutoConfiguration
@DirtiesContext(classMode = ClassMode.BEFORE_EACH_TEST_METHOD)
@Slf4j
public class FactusClientTest extends AbstractFactCastIntegrationTest {
  private static final long WAIT_TIME_FOR_ASYNC_FACT_DELIVERY = 1000;

  @Autowired Factus factus;

  @Autowired EventConverter eventConverter;

  @Autowired RedissonManagedUserNames externalizedUserNames;

  @Autowired SubscribedUserNames subscribedUserNames;

  @Autowired UserCount userCount;

  @Test
  public void allWaysToPublish() {

    UUID johnsId = randomUUID();

    factus.publish(new UserCreated(johnsId, "John"));

    factus.publish(
        asList(new UserCreated(randomUUID(), "Paul"), new UserCreated(randomUUID(), "George")));

    String payload = factus.publish(new UserCreated(randomUUID(), "Ringo"), Fact::jsonPayload);

    assertThatJson(payload).and(j -> j.node("userName").isEqualTo("Ringo"));

    List<String> morePayload =
        factus.publish(
            asList(new UserCreated(randomUUID(), "Mick"), new UserCreated(randomUUID(), "Keith")),
            list -> list.stream().map(Fact::jsonPayload).collect(toList()));

    assertThat(morePayload)
        .hasSize(2)
        .anySatisfy(p -> assertThatJson(p).and(j -> j.node("userName").isEqualTo("Mick")))
        .anySatisfy(p -> assertThatJson(p).and(j -> j.node("userName").isEqualTo("Keith")));

    factus.publish(eventConverter.toFact(new UserCreated(randomUUID(), "Brian")));

    factus.update(externalizedUserNames);

    assertThat(externalizedUserNames.count()).isEqualTo(7);
    assertThat(externalizedUserNames.contains("John")).isTrue();
    assertThat(externalizedUserNames.contains("Paul")).isTrue();
    assertThat(externalizedUserNames.contains("George")).isTrue();
    assertThat(externalizedUserNames.contains("Ringo")).isTrue();
    assertThat(externalizedUserNames.contains("Mick")).isTrue();
    assertThat(externalizedUserNames.contains("Keith")).isTrue();
    assertThat(externalizedUserNames.contains("Brian")).isTrue();
  }

  @Test
  public void testSubscription() throws Exception {

    subscribedUserNames.clear();

    factus.publish(new UserCreated(randomUUID(), "preexisting"));

    Subscription subscription = factus.subscribeAndBlock(subscribedUserNames);
    // nothing in there yet, so catchup must be received
    subscription.awaitCatchup();
    assertThat(subscribedUserNames.names()).hasSize(1);

    factus.publish(new UserCreated(randomUUID(), "Peter"));

    Thread.sleep(WAIT_TIME_FOR_ASYNC_FACT_DELIVERY);

    assertThat(subscribedUserNames.names()).hasSize(2).contains("preexisting").contains("Peter");

    factus.publish(new UserCreated(randomUUID(), "John"));
    Thread.sleep(WAIT_TIME_FOR_ASYNC_FACT_DELIVERY);

    assertThat(subscribedUserNames.names())
        .hasSize(3)
        .containsExactlyInAnyOrder("John", "Peter", "preexisting");

    subscription.close();
  }

  @Test
  public void simpleAggregateRoundtrip() throws Exception {
    UUID aggregateId = randomUUID();
    assertThat(factus.find(TestAggregate.class, aggregateId)).isEmpty();

    factus
        .batch()
        // 8 increment events for test aggregate
        .add(new TestAggregateIncremented(aggregateId))
        .add(new TestAggregateIncremented(aggregateId))
        .add(new TestAggregateIncremented(aggregateId))
        .add(new TestAggregateIncremented(aggregateId))
        .add(new TestAggregateIncremented(aggregateId))
        .add(new TestAggregateIncremented(aggregateId))
        .add(new TestAggregateIncremented(aggregateId))
        .add(new TestAggregateIncremented(aggregateId))
        .add(new TestAggregateIncremented(randomUUID()))
        .add(new TestAggregateIncremented(randomUUID()))
        .execute();

    TestAggregate a = factus.fetch(TestAggregate.class, aggregateId);
    // We started with magic number 42, incremented 8 times -> magic number
    // should be 50
    assertThat(a.magicNumber()).isEqualTo(50);

    log.info(
        "now we're not expecting to see event processing due to the snapshot being up to "
            + "date");

    TestAggregate b = factus.fetch(TestAggregate.class, aggregateId);
    assertThat(b.magicNumber()).isEqualTo(50);
  }

  @Test
  public void simpleSnapshotProjectionRoundtrip() throws Exception {
    assertThat(factus.fetch(SnapshotUserNames.class)).isNotNull();

    UUID johnsId = randomUUID();
    factus
        .batch()
        .add(new UserCreated(johnsId, "John"))
        .add(new UserCreated(randomUUID(), "Paul"))
        .add(new UserCreated(randomUUID(), "George"))
        .add(new UserCreated(randomUUID(), "Ringo"))
        .execute();

    val fabFour = factus.fetch(SnapshotUserNames.class);
    assertThat(fabFour.count()).isEqualTo(4);
    assertThat(fabFour.contains("John")).isTrue();
    assertThat(fabFour.contains("Paul")).isTrue();
    assertThat(fabFour.contains("George")).isTrue();
    assertThat(fabFour.contains("Ringo")).isTrue();

    // sadly shot
    factus.publish(new UserDeleted(johnsId));

    val fabThree = factus.fetch(SnapshotUserNames.class);
    assertThat(fabThree.count()).isEqualTo(3);
    assertThat(fabThree.contains("John")).isFalse();
    assertThat(fabThree.contains("Paul")).isTrue();
    assertThat(fabThree.contains("George")).isTrue();
    assertThat(fabThree.contains("Ringo")).isTrue();
  }

  @Value
  static class UserCreateCMD {
    String userName;

    UUID userId;
  }

  @Test
  public void simpleProjectionLockingRoundtrip() throws Exception {
    SnapshotUserNames emptyUserNames = factus.fetch(SnapshotUserNames.class);
    assertThat(emptyUserNames).isNotNull();
    assertThat(emptyUserNames.count()).isEqualTo(0);

    UUID petersId = randomUUID();
    UserCreateCMD cmd = new UserCreateCMD("Peter", petersId);

    factus
        .withLockOn(SnapshotUserNames.class)
        .retries(5)
        .intervalMillis(50)
        .attempt(
            (names, tx) -> {
              if (names.contains(cmd.userName)) {
                tx.abort("baeh");
              } else {
                tx.publish(new UserCreated(cmd.userId, cmd.userName));
              }
            });

    assertThat(factus.fetch(SnapshotUserNames.class).count()).isEqualTo(1);

    factus.publish(new UserDeleted(petersId));

    assertThat(factus.fetch(SnapshotUserNames.class).count()).isEqualTo(0);
  }

  @Test
  public void testPublishSafeguard() throws Exception {

    assertThatThrownBy(
            () ->
                factus
                    .withLockOn(SnapshotUserNames.class)
                    .retries(5)
                    .intervalMillis(50)
                    .attempt(
                        (names, tx) -> {
                          // This must fail, as we didn't publish on the tx, but on
                          // factus
                          factus.publish(new UserCreated(randomUUID(), "Peter"));
                        }))
        .isInstanceOf(LockedOperationAbortedException.class);
  }

  @Test
  public void simpleManagedProjectionRoundtrip() throws Exception {
    // lets consider userCount a springbean

    assertThat(userCount.state()).isNull();
    assertThat(userCount.count()).isEqualTo(0);
    factus.update(userCount);

    int before = userCount.count();

    UUID one = randomUUID();
    UUID two = randomUUID();
    factus.batch().add(new UserCreated(one, "One")).add(new UserCreated(two, "Two")).execute();

    assertThat(userCount.count()).isEqualTo(before);
    factus.update(userCount);
    assertThat(userCount.count()).isEqualTo(before + 2);

    factus.publish(new UserDeleted(one));
    factus.update(userCount);
    assertThat(userCount.count()).isEqualTo(before + 1);

    factus.publish(new UserDeleted(two));
    factus.update(userCount);
    assertThat(userCount.count()).isEqualTo(before);
  }

  @Test
  public void simpleManagedProjectionRoundtrip_withLock() throws Exception {
    // lets consider userCount a springbean
    UserCount userCount = new UserCount();

    assertThat(userCount.state()).isNull();
    assertThat(userCount.count()).isEqualTo(0);
    factus.update(userCount);

    int before = userCount.count();

    UUID one = randomUUID();
    UUID two = randomUUID();
    factus.batch().add(new UserCreated(one, "One")).add(new UserCreated(two, "Two")).execute();

    assertThat(userCount.count()).isEqualTo(before);
    factus.update(userCount);
    assertThat(userCount.count()).isEqualTo(before + 2);

    factus.publish(new UserDeleted(one));
    factus.update(userCount);
    assertThat(userCount.count()).isEqualTo(before + 1);

    factus.publish(new UserDeleted(two));
    factus.update(userCount);
    assertThat(userCount.count()).isEqualTo(before);

    // we start 10 threads that try to (in an isolated fashion) lock and
    // increase. Starting with magic number 43, we would end up 53, but
    // we have a business rule that limits this to 50.
    Set<CompletableFuture<Void>> futures = new HashSet<>();
    for (UUID name : asList(one, two, one)) {

      futures.add(
          CompletableFuture.runAsync(
              () ->
                  factus
                      .withLockOn(userCount)
                      .attempt(
                          (ta, tx) -> {

                            // check business rule (yes it's sloppy)
                            if (userCount.count() > 0) {
                              // increment or
                              tx.publish(new UserDeleted(name));
                            } else {
                              // abort, according to business rule
                              tx.abort("aborting");
                            }
                          })));
    }

    // wait for all threads to succeed or abort
    waitForAllToTerminate(futures);

    // make sure business rule was properly applied
    factus.update(userCount);
    assertThat(userCount.count()).isEqualTo(0);
  }

  @Test
  void simpleExternalizedProjectionRoundtrip() {

    externalizedUserNames.clear();

    UUID johnsId = randomUUID();
    factus
        .batch()
        .add(new UserCreated(johnsId, "John"))
        .add(new UserCreated(randomUUID(), "Paul"))
        .add(new UserCreated(randomUUID(), "George"))
        .add(new UserCreated(randomUUID(), "Ringo"))
        .execute();

    val fabFour = externalizedUserNames;
    factus.update(fabFour);

    assertThat(fabFour.count()).isEqualTo(4);
    assertThat(fabFour.contains("John")).isTrue();
    assertThat(fabFour.contains("Paul")).isTrue();
    assertThat(fabFour.contains("George")).isTrue();
    assertThat(fabFour.contains("Ringo")).isTrue();

    // sadly shot
    factus.publish(new UserDeleted(johnsId));

    // publishing does not update a simple projection
    assertThat(fabFour.count()).isEqualTo(4);

    val fabThree = externalizedUserNames;
    factus.update(fabThree);

    assertThat(fabThree.count()).isEqualTo(3);
    assertThat(fabThree.contains("John")).isFalse();
    assertThat(fabThree.contains("Paul")).isTrue();
    assertThat(fabThree.contains("George")).isTrue();
    assertThat(fabThree.contains("Ringo")).isTrue();
  }

  @Test
  public void simpleAggregateLockRoundtrip() throws Exception {
    UUID aggregateId = randomUUID();
    assertThat(factus.find(TestAggregate.class, aggregateId)).isEmpty();

    factus.publish(new TestAggregateIncremented(aggregateId));

    Optional<TestAggregate> optAggregate = factus.find(TestAggregate.class, aggregateId);
    assertThat(optAggregate).isNotEmpty();

    val aggregate = factus.fetch(TestAggregate.class, aggregateId);
    assertThat(aggregate.magicNumber()).isEqualTo(43);

    val a = factus.fetch(TestAggregate.class, aggregateId);

    // we start 10 threads that try to (in an isolated fashion) lock and
    // increase. Starting with magic number 43, we would end up 53, but
    // we have a business rule that limits this to 50.
    Set<CompletableFuture<Void>> futures = new HashSet<>();
    for (int i = 0; i < 5; i++) {
      String workerID = "Worker #" + i;

      futures.add(
          CompletableFuture.runAsync(
              () ->
                  factus
                      .withLockOn(a)
                      .attempt(
                          (ta, tx) -> {
                            log.info(workerID);

                            // check business rule
                            if (ta.magicNumber() < 50) {
                              // increment or
                              tx.publish(new TestAggregateIncremented(aggregateId));
                            } else {
                              // abort, according to business rule
                              tx.abort(
                                  "aborting "
                                      + workerID
                                      + ": magic number is too high already ("
                                      + ta.magicNumber()
                                      + ")");
                            }
                          })));
    }
    for (int i = 5; i < 10; i++) {
      String workerID = "Worker #" + i;

      futures.add(
          CompletableFuture.runAsync(
              () ->
                  factus
                      .withLockOn(TestAggregate.class, aggregateId)
                      .attempt(
                          (ta, tx) -> {
                            log.info(workerID);

                            // check business rule
                            if (ta.magicNumber() < 50) {
                              // increment or
                              tx.publish(new TestAggregateIncremented(aggregateId));
                            } else {
                              // abort, according to business rule
                              tx.abort(
                                  "aborting "
                                      + workerID
                                      + ": magic number is too high already ("
                                      + ta.magicNumber()
                                      + ")");
                            }
                          })));
    }

    // wait for all threads to succeed or abort
    waitForAllToTerminate(futures);

    // make sure business rule was properly applied (we have 50 instead of
    // 53)
    assertThat(factus.fetch(TestAggregate.class, aggregateId).magicNumber()).isEqualTo(50);
  }

  private void waitForAllToTerminate(Set<CompletableFuture<Void>> futures) {
    futures.forEach(
        f -> {
          try {
            f.get();
          } catch (Exception e) {
            log.warn(e.getMessage());
          }
        });
  }

  @Data
  static class SimpleAggregate extends Aggregate {
    static final String ns = "ns";
    static final String type = "foo";

    private transient int factsConsumed = 0;

    @Override
    public void afterUpdate(int numberOfFactsAppliedDuringUpdate) {
      this.factsConsumed = numberOfFactsAppliedDuringUpdate;
    }

    @HandlerFor(ns = ns, type = type)
    void apply(Fact f) {}
  }

  @Data
  static class SimpleManaged extends LocalManagedProjection {
    static final String ns = "ns";
    static final String type = "foo";

    private transient int factsConsumed = 0;

    @Override
    public void afterUpdate(int numberOfFactsAppliedDuringUpdate) {
      this.factsConsumed = numberOfFactsAppliedDuringUpdate;
    }

    @HandlerFor(ns = ns, type = type)
    void apply(Fact f) {}
  }

  @Test
  void afterUpdateOnSnapshotProjection() {
    UUID aggId = UUID.randomUUID();
    val f1 =
        Fact.builder()
            .aggId(aggId)
            .ns(SimpleAggregate.ns)
            .type(SimpleAggregate.type)
            .buildWithoutPayload();
    val f2 =
        Fact.builder()
            .aggId(aggId)
            .ns(SimpleAggregate.ns)
            .type(SimpleAggregate.type)
            .buildWithoutPayload();
    val f3 =
        Fact.builder()
            .aggId(aggId)
            .ns(SimpleAggregate.ns)
            .type(SimpleAggregate.type)
            .buildWithoutPayload();

    factus.publish(f1);
    factus.publish(f2);

    val a1 = factus.fetch(SimpleAggregate.class, aggId);
    assertThat(a1.factsConsumed).isEqualTo(2);

    val a2 = factus.fetch(SimpleAggregate.class, aggId);
    assertThat(a2.factsConsumed).isEqualTo(0);

    factus.publish(f3);

    val a3 = factus.fetch(SimpleAggregate.class, aggId);
    assertThat(a3.factsConsumed).isEqualTo(1);
  }

  @Test
  void afterUpdateOnManagedProjection() {
    UUID aggId = UUID.randomUUID();
    val f1 =
        Fact.builder()
            .aggId(aggId)
            .ns(SimpleManaged.ns)
            .type(SimpleManaged.type)
            .buildWithoutPayload();
    val f2 =
        Fact.builder()
            .aggId(aggId)
            .ns(SimpleManaged.ns)
            .type(SimpleManaged.type)
            .buildWithoutPayload();
    val f3 =
        Fact.builder()
            .aggId(aggId)
            .ns(SimpleManaged.ns)
            .type(SimpleManaged.type)
            .buildWithoutPayload();

    factus.publish(f1);
    factus.publish(f2);

    val m = spy(new SimpleManaged());

    factus.update(m);
    verify(m).afterUpdate(2);

    factus.update(m);
    verify(m).afterUpdate(0);

    factus.publish(f3);

    factus.update(m);
    verify(m).afterUpdate(1);
  }
}
