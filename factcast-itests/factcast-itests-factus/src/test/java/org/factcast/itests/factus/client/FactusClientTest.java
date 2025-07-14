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
package org.factcast.itests.factus.client;

import static java.util.Arrays.asList;
import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.toList;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.*;

import com.google.common.base.Stopwatch;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.store.RetryableException;
import org.factcast.core.subscription.Subscription;
import org.factcast.factus.*;
import org.factcast.factus.event.*;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.lock.LockedOperationAbortedException;
import org.factcast.factus.projection.*;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.factcast.itests.TestFactusApplication;
import org.factcast.itests.factus.config.RedissonProjectionConfiguration;
import org.factcast.itests.factus.event.*;
import org.factcast.itests.factus.event.film.*;
import org.factcast.itests.factus.proj.*;
import org.factcast.spring.boot.autoconfigure.snap.RedissonSnapshotCacheAutoConfiguration;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.*;
import org.redisson.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.shaded.org.awaitility.Awaitility;

@ContextConfiguration(
    classes = {
      TestFactusApplication.class,
      RedissonProjectionConfiguration.class,
      RedissonSnapshotCacheAutoConfiguration.class
    })
@Slf4j
class FactusClientTest extends AbstractFactCastIntegrationTest {
  private static final long WAIT_TIME_FOR_ASYNC_FACT_DELIVERY = 1000;

  @Autowired Factus factus;

  @Autowired EventConverter eventConverter;

  @Autowired RedissonTxManagedUserNames externalizedUserNames;
  @Autowired TxRedissonManagedUserNames transactionalExternalizedUserNames;
  @Autowired TxRedissonSubscribedUserNames transactionalExternalizedSubscribedUserNames;

  @Autowired UserCount userCount;
  @Autowired RedissonClient redissonClient;

  @Test
  void differentNamespaces() {
    factus.publish(new StarTrekCharacterCreated("Kirk"));
    factus.publish(new StarWarsCharacterCreated("Han"));
    factus.publish(new StarWarsCharacterCreated("Luke"));
    factus.publish(new IndianaJonesCharacterCreated("Indy"));
    factus.publish(new IndianaJonesCharacterCreated("Shorty"));

    LucasNames names = new LucasNames();
    factus.update(names);

    assertThat(names.userNames()).containsValues("Han", "Luke", "Indy", "Shorty");
  }

  @Test
  void allWaysToPublish() {

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

  void measure(String s, Runnable r) {
    var sw = Stopwatch.createStarted();
    r.run();
    log.info("{} {}ms", s, sw.stop().elapsed().toMillis());
  }

  @SneakyThrows
  @Test
  void txBatchProcessingPerformance() {

    int MAX = 10000;
    var l = new ArrayList<EventObject>(MAX);
    log.info("preparing {} Events ", MAX);
    for (int i = 0; i < MAX; i++) {
      l.add(new UserCreated(randomUUID(), getClass().getSimpleName() + ":" + i));
    }
    log.info("publishing {} Events ", MAX);
    factus.publish(l);
    log.info("Cooldown of a sec");
    Thread.sleep(1000);

    {
      var sw = Stopwatch.createStarted();
      RedissonTxManagedUserNames p = new RedissonTxManagedUserNames(redissonClient);
      factus.update(p);
      log.info(
          "RedissonManagedUserNames {} {}", sw.stop().elapsed().toMillis(), p.userNames().size());
      p.clear();
      p.factStreamPosition(null);
    }
    {
      var sw = Stopwatch.createStarted();
      RedissonTxManagedUserNames p = new RedissonTxManagedUserNames(redissonClient);
      factus.update(p);
      log.info(
          "RedissonManagedUserNames {} {}", sw.stop().elapsed().toMillis(), p.userNames().size());
      p.clear();
      p.factStreamPosition(null);
    }
    // ----------tx
    {
      var sw = Stopwatch.createStarted();
      TxRedissonManagedUserNames p = new TxRedissonManagedUserNames(redissonClient);
      factus.update(p);
      log.info(
          "TxRedissonManagedUserNames {} {}", sw.stop().elapsed().toMillis(), p.userNames().size());
      p.clear();
      p.factStreamPosition(null);
    }

    {
      var sw = Stopwatch.createStarted();
      TxRedissonManagedUserNames p = new TxRedissonManagedUserNames(redissonClient);
      factus.update(p);
      log.info(
          "TxRedissonManagedUserNames {} {}", sw.stop().elapsed().toMillis(), p.userNames().size());
      p.clear();
      p.factStreamPosition(null);
    }

    // ------------ sub

    // ----------tx
    {
      var sw = Stopwatch.createStarted();
      TxRedissonSubscribedUserNames p = new TxRedissonSubscribedUserNames(redissonClient);
      var sub = factus.subscribeAndBlock(p);
      sub.awaitCatchup().close();
      log.info(
          "TxRedissonSubscribedUserNames {} {}",
          sw.stop().elapsed().toMillis(),
          p.userNames().size());
      p.clear();
      p.factStreamPosition(null);
    }

    {
      var sw = Stopwatch.createStarted();
      TxRedissonSubscribedUserNames p = new TxRedissonSubscribedUserNames(redissonClient);
      var sub = factus.subscribeAndBlock(p);
      sub.awaitCatchup().close();
      log.info(
          "TxRedissonSubscribedUserNames {} {}",
          sw.stop().elapsed().toMillis(),
          p.userNames().size());
      p.clear();
      p.factStreamPosition(null);
    }
  }

  @SneakyThrows
  @Test
  void redissionDigger() {

    RTransaction tx2 =
        redissonClient.createTransaction(
            TransactionOptions.defaults().timeout(1, TimeUnit.MINUTES));

    var tx = redissonClient.createBatch();

    var r = tx.getMap("schubba");
    measure(
        "honk",
        () -> {
          r.putAsync("a", "b");
          r.putAsync("a", "b");
          r.putAsync("a", "b");

          tx.execute();
        });
  }

  @Test
  void testSubscription() throws Exception {
    SubscribedUserNames subscribedUserNames = new SubscribedUserNames();
    subscribedUserNames.clear();

    factus.publish(new UserCreated(randomUUID(), "preexisting"));
    try (Subscription subscription = factus.subscribeAndBlock(subscribedUserNames)) {
      // nothing in there yet, so catchup must be received
      subscription.awaitCatchup();
      assertThat(subscribedUserNames.names()).hasSize(1);

      factus.publish(new UserCreated(randomUUID(), "Peter"));

      Thread.sleep(WAIT_TIME_FOR_ASYNC_FACT_DELIVERY);

      assertThat(subscribedUserNames.names()).hasSize(2).contains("preexisting", "Peter");

      factus.publish(new UserCreated(randomUUID(), "John"));
      Thread.sleep(WAIT_TIME_FOR_ASYNC_FACT_DELIVERY);

      assertThat(subscribedUserNames.names())
          .hasSize(3)
          .containsExactlyInAnyOrder("John", "Peter", "preexisting");
    }
  }

  @Test
  void simpleAggregateRoundtrip() {
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
  void simpleSnapshotProjectionRoundtrip() {
    assertThat(factus.fetch(SnapshotUserNames.class)).isNotNull();

    UUID johnsId = randomUUID();
    factus
        .batch()
        .add(new UserCreated(johnsId, "John"))
        .add(new UserCreated(randomUUID(), "Paul"))
        .add(new UserCreated(randomUUID(), "George"))
        .add(new UserCreated(randomUUID(), "Ringo"))
        .execute();

    var fabFour = factus.fetch(SnapshotUserNames.class);
    assertThat(fabFour.count()).isEqualTo(4);
    assertThat(fabFour.contains("John")).isTrue();
    assertThat(fabFour.contains("Paul")).isTrue();
    assertThat(fabFour.contains("George")).isTrue();
    assertThat(fabFour.contains("Ringo")).isTrue();

    // sadly shot
    factus.publish(new UserDeleted(johnsId));

    var fabThree = factus.fetch(SnapshotUserNames.class);
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
  void simpleProjectionLockingRoundtrip() {
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

    assertThat(factus.fetch(SnapshotUserNames.class).count()).isOne();

    factus.publish(new UserDeleted(petersId));

    assertThat(factus.fetch(SnapshotUserNames.class).count()).isZero();
  }

  @Test
  void testPublishSafeguard() {

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
  void simpleManagedProjectionRoundtrip() {
    // lets consider userCount a springbean

    assertThat(userCount.factStreamPosition()).isNull();
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
  void simpleManagedProjectionRoundtrip_withLock() {
    // lets consider userCount a springbean
    UserCount userCount = new UserCount();

    assertThat(userCount.factStreamPosition()).isNull();
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

    var fabFour = externalizedUserNames;
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

    var fabThree = externalizedUserNames;
    factus.update(fabThree);

    assertThat(fabThree.count()).isEqualTo(3);
    assertThat(fabThree.contains("John")).isFalse();
    assertThat(fabThree.contains("Paul")).isTrue();
    assertThat(fabThree.contains("George")).isTrue();
    assertThat(fabThree.contains("Ringo")).isTrue();
  }

  @Test
  void simpleAggregateLockRoundtrip() {
    UUID aggregateId = randomUUID();
    assertThat(factus.find(TestAggregate.class, aggregateId)).isEmpty();

    factus.publish(new TestAggregateIncremented(aggregateId));

    Optional<TestAggregate> optAggregate = factus.find(TestAggregate.class, aggregateId);
    assertThat(optAggregate).isNotEmpty();

    var aggregate = factus.fetch(TestAggregate.class, aggregateId);
    assertThat(aggregate.magicNumber()).isEqualTo(43);

    var a = factus.fetch(TestAggregate.class, aggregateId);

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

  @Test
  void simpleManagedProjectionPublishingFacts() {
    Fact fact = Fact.buildFrom(new UserCreated(randomUUID(), "One")).build();

    factus.publish(fact);

    factus.update(externalizedUserNames);

    assertThat(externalizedUserNames.count()).isOne();
    assertThat(externalizedUserNames.contains("One")).isTrue();
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

  @ProjectionMetaData(revision = 1)
  static class SimpleAggregate extends Aggregate {
    static final String ns = "ns";
    static final String type = "foo";

    transient int factsConsumed;

    @HandlerFor(ns = ns, type = type)
    void apply(Fact f) {
      factsConsumed++;
    }
  }

  static class SimpleManaged extends LocalManagedProjection {
    static final String ns = "ns";
    static final String type = "foo";

    transient int factsConsumed;

    @HandlerFor(ns = ns, type = type)
    void apply(Fact f) {
      factsConsumed++;
    }
  }

  @Test
  void afterUpdateOnSnapshotProjection() {
    UUID aggId = UUID.randomUUID();
    var f1 =
        Fact.builder()
            .aggId(aggId)
            .ns(SimpleAggregate.ns)
            .type(SimpleAggregate.type)
            .buildWithoutPayload();
    var f2 =
        Fact.builder()
            .aggId(aggId)
            .ns(SimpleAggregate.ns)
            .type(SimpleAggregate.type)
            .buildWithoutPayload();
    var f3 =
        Fact.builder()
            .aggId(aggId)
            .ns(SimpleAggregate.ns)
            .type(SimpleAggregate.type)
            .buildWithoutPayload();

    factus.publish(f1);
    factus.publish(f2);

    var a1 = factus.fetch(SimpleAggregate.class, aggId);
    assertThat(a1.factsConsumed).isEqualTo(2);

    var a2 = factus.fetch(SimpleAggregate.class, aggId);
    assertThat(a2.factsConsumed).isZero();

    factus.publish(f3);

    var a3 = factus.fetch(SimpleAggregate.class, aggId);
    assertThat(a3.factsConsumed).isOne();
  }

  @Test
  void waitsForFacts() throws Exception {
    SubscribedUserNames subscribedUserNames = new SubscribedUserNames();
    subscribedUserNames.clear();
    Duration timeout = Duration.ofSeconds(3);

    factus.publish(new UserCreated("Mark"));
    try (Subscription subscriptionWaiting = factus.subscribeAndBlock(subscribedUserNames)) {
      // nothing in there yet, so catchup must be received
      subscriptionWaiting.awaitCatchup();
      assertThat(subscribedUserNames.names()).hasSize(1);

      var factId1 = factus.publish(new UserCreated("Tom"), Fact::id);
      factus.waitFor(subscribedUserNames, factId1, timeout);
      assertThat(subscribedUserNames.names()).hasSize(2);

      var factId2 = factus.publish(new UserCreated("Sasha"), Fact::id);
      factus.waitFor(subscribedUserNames, factId2, timeout, i -> (long) Math.pow(10, i));
      assertThat(subscribedUserNames.names())
          .hasSize(3)
          .containsExactlyInAnyOrder("Sasha", "Tom", "Mark");
    }
  }

  @Test
  void waitingForFactFailsOnUnknownId() throws Exception {
    SubscribedUserNames subscribedUserNames = new SubscribedUserNames();
    subscribedUserNames.clear();
    Duration timeout = Duration.ofSeconds(3);

    factus.publish(new UserCreated("Kyle"));
    try (Subscription subscriptionWaiting = factus.subscribeAndBlock(subscribedUserNames)) {
      // nothing in there yet, so catchup must be received
      subscriptionWaiting.awaitCatchup();
      assertThat(subscribedUserNames.names()).hasSize(1);

      // wait for a fact id that does not exist
      UUID unknownFactId = randomUUID();
      assertThatThrownBy(() -> factus.waitFor(subscribedUserNames, unknownFactId, timeout))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void waitingForFactTimesOut() throws Exception {
    SubscribedUserNames subscribedUserNames = new SubscribedUserNames();
    subscribedUserNames.clear();
    Duration timeout = Duration.ofSeconds(1);

    factus.publish(new UserCreated("Kyle"));
    try (Subscription subscriptionWaiting = factus.subscribeAndBlock(subscribedUserNames)) {
      // nothing in there yet, so catchup must be received
      subscriptionWaiting.awaitCatchup();
      assertThat(subscribedUserNames.names()).hasSize(1);

      // publish an event that is not consumed by the subscribed projection
      var factId1 = factus.publish(new UserBored("Kenny"), Fact::id);
      assertThatThrownBy(() -> factus.waitFor(subscribedUserNames, factId1, timeout))
          .isInstanceOf(TimeoutException.class);
    }
  }

  @Test
  void testTokenReleaseAfterTooManyFailures() {
    OverrideAndFailSubscribedUserNames subscribedUserNames =
        new OverrideAndFailSubscribedUserNames();
    subscribedUserNames.clear();

    factus.publish(new UserDeleted(UUID.randomUUID()));

    assertThat(subscribedUserNames.isValid()).isFalse();
    factus.subscribeAndBlock(subscribedUserNames);
    try {
      // it should acquire the lock
      subscribedUserNames.latch.await();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    // the initial lock was closed
    Awaitility.await().until(() -> !subscribedUserNames.isValid()); // someone locked it
  }

  static class OverrideAndFailSubscribedUserNames extends SubscribedUserNames {

    CountDownLatch latch = new CountDownLatch(1);

    @Override
    public void apply(UserDeleted deleted, @Nullable String signee) {
      latch.countDown();
      throw new RetryableException(new Throwable("Test exception"));
    }
  }

  @Test
  void injectsMeta() throws Exception {
    AtomicReference<String> signee = new AtomicReference<>();
    SubscribedUserNames subscribedUserNames =
        new SubscribedUserNames() {
          @Override
          public void apply(UserDeleted deleted, @Meta("signee") @Nullable String metaSignee) {
            super.apply(deleted, metaSignee);
            signee.set(metaSignee);
          }
        };
    UserCreated kenny = new UserCreated("Kenny");
    factus.publish(kenny);

    UserDeleted deleted = new UserDeleted(kenny.aggregateId());
    // the boss signed off kyles deletion, bastard!
    Fact f = Fact.buildFrom(deleted).meta("signee", "theBoss").build();
    factus.publish(f);
    try (Subscription subscriptionWaiting = factus.subscribeAndBlock(subscribedUserNames)) {
      subscriptionWaiting.awaitCatchup();
    }

    assertThat(signee.get()).isEqualTo("theBoss");
  }

  static class ProjectionWithMutlipleMetaValues extends LocalSubscribedProjection {
    Collection<String> signee;
    List<String> affiliates;

    @Handler
    public void apply(UserCreated deleted, @Meta("affiliates") List<String> metaAffiliates) {
      affiliates = metaAffiliates;
    }

    @Handler
    public void apply(UserDeleted deleted, @Meta("signee") Collection<String> metaSignees) {
      signee = metaSignees;
    }
  }

  @Test
  void injectsMetaCollection() throws Exception {

    UserCreated kenny = new UserCreated("Kenny");
    factus.publish(kenny);

    ProjectionWithMutlipleMetaValues p = new ProjectionWithMutlipleMetaValues();

    UserDeleted deleted = new UserDeleted(kenny.aggregateId());
    // the boss signed off kyles deletion, bastard!
    Fact f =
        Fact.buildFrom(deleted)
            .addMeta("signee", "theBoss")
            .addMeta("signee", "theChef")
            .addMeta("signee", "theHeadOfSomething")
            .build();
    factus.publish(f);
    try (Subscription subscriptionWaiting = factus.subscribeAndBlock(p)) {
      subscriptionWaiting.awaitCatchup();
    }

    assertThat(p.signee).contains("theBoss", "theChef", "theHeadOfSomething");
    assertThat(p.affiliates).isEmpty();
  }

  @Test
  void testOverriddenNsSubscription() throws Exception {
    SubscribedUserNames subscribedUserNames = new OverrideNsSubscribedUserNames();
    subscribedUserNames.clear();

    factus.publish(new ShadowUserCreated("John"));
    factus.publish(new ShadowUserCreated("Paul"));
    factus.publish(new UserCreated("Pete"));
    try (Subscription subscription = factus.subscribeAndBlock(subscribedUserNames)) {
      // nothing in there yet, so catchup must be received
      subscription.awaitCatchup();

      assertThat(subscribedUserNames.names()).hasSize(2).containsExactlyInAnyOrder("Paul", "John");
    }
  }

  @Test
  void testParallelUpdateCallsForRedisTxProjections() {
    final var blockingRedisManagedUserNames = new BlockingRedisTxManagedUserNames(redissonClient);
    factus.publish(new UserCreated("John"));

    final Supplier<Boolean> executeUpdate =
        () -> {
          try {
            log.info("Updating blockingRedisManagedUserNames...");
            factus.update(blockingRedisManagedUserNames);
            log.info("finished updating blockingRedisManagedUserNames.");
          } catch (Exception e) {
            log.info("Error updating blockingRedisManagedUserNames: {}", e.getMessage(), e);
            return false;
          }

          return true;
        };

    final var u1 = CompletableFuture.supplyAsync(executeUpdate);
    final var u2 =
        CompletableFuture.supplyAsync(
            () -> {
              // wait a bit to run into event handling phase of u1
              sleep(250);
              return executeUpdate.get();
            });

    assertThat(u1.thenCombine(u2, (b1, b2) -> b1 && b2))
        .succeedsWithin(Duration.ofSeconds(5))
        .isEqualTo(true);
  }

  @SneakyThrows
  private static void sleep(long ms) {
    Thread.sleep(ms);
  }

  @Nested
  class Clock {

    @Test
    void currentTimeIsMonotonouslyIncreasing() {
      Instant before = factus.currentTime();
      Instant now = factus.currentTime();
      Instant after = factus.currentTime();

      assertThat(now).isAfterOrEqualTo(before).isBeforeOrEqualTo(after);
    }
  }

  @Nested
  class OnSuccess {

    private int countBefore;

    @BeforeEach
    void setup() {
      factus.update(userCount);
      countBefore = userCount.count();
    }

    @Test
    void happyPathWithCallback() {
      CountDownLatch latch = new CountDownLatch(1);
      factus
          .withLockOn(userCount)
          .attempt(
              (uc, tx) -> {
                tx.publish(new UserCreated("JohnLocked"));
                tx.onSuccess(latch::countDown);
              });

      assertThat(latch.getCount()).isZero();

      factus.update(userCount);
      assertThat(userCount.count()).isEqualTo(countBefore + 1);
    }

    @Test
    void happyPathWithoutCallback() {
      factus
          .withLockOn(userCount)
          .attempt(
              (uc, tx) -> {
                tx.publish(new UserCreated("JohnLocked"));
              });

      factus.update(userCount);
      assertThat(userCount.count()).isEqualTo(countBefore + 1);
    }

    @Test
    void callbackOnlyOnce() {
      CountDownLatch latch = new CountDownLatch(1);
      AtomicInteger executions = new AtomicInteger(0);
      AtomicInteger callbacks = new AtomicInteger(0);
      factus
          .withLockOn(userCount)
          .attempt(
              (uc, tx) -> {
                executions.incrementAndGet();

                CompletableFuture.runAsync(
                    () -> {
                      if (latch.getCount() == 1) {
                        // we're in the first execution
                        factus.publish(new UserCreated("John Published in parallel"));
                        latch.countDown();
                      }
                    });

                try {
                  latch.await();
                } catch (InterruptedException e) {
                  throw new RuntimeException(e);
                }

                tx.publish(new UserCreated("JohnLocked"));
                tx.onSuccess(callbacks::incrementAndGet);
              });

      factus.update(userCount);
      assertThat(userCount.count()).isEqualTo(countBefore + 2);

      assertThat(executions).hasValue(2);
      assertThat(callbacks).hasValue(1);
    }

    @Test
    void noCallbackOnAbort() {
      AtomicInteger executions = new AtomicInteger(0);
      AtomicInteger callbacks = new AtomicInteger(0);
      assertThatThrownBy(
              () -> {
                factus
                    .withLockOn(userCount)
                    .attempt(
                        (uc, tx) -> {
                          executions.incrementAndGet();
                          tx.onSuccess(callbacks::incrementAndGet);
                          tx.abort("oh dear");
                        });
              })
          .isInstanceOf(LockedOperationAbortedException.class);

      factus.update(userCount);
      assertThat(userCount.count()).isEqualTo(countBefore);

      assertThat(executions).hasValue(1);
      assertThat(callbacks).hasValue(0);
    }

    @Test
    void noCallbackOnStarvation() {
      AtomicInteger executions = new AtomicInteger(0);
      AtomicInteger callbacks = new AtomicInteger(0);
      AtomicReference<CountDownLatch> latch = new AtomicReference<>(new CountDownLatch(1));

      assertThatThrownBy(
              () -> {
                factus
                    .withLockOn(userCount)
                    .retries(3)
                    .attempt(
                        (uc, tx) -> {
                          executions.incrementAndGet();

                          CompletableFuture.runAsync(
                              () -> {
                                if (latch.get().getCount() == 1) {
                                  // we're in the first execution
                                  factus.publish(new UserCreated("John Published in parallel"));
                                  latch.get().countDown();
                                }
                              });

                          try {
                            latch.get().await();
                          } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                          }
                          // reset
                          latch.set(new CountDownLatch(1));

                          tx.publish(new UserCreated("JohnLocked"));
                          tx.onSuccess(callbacks::incrementAndGet);
                        });
              })
          .isInstanceOf(ConcurrentModificationException.class);

      factus.update(userCount);
      assertThat(userCount.count()).isEqualTo(countBefore + 3);

      assertThat(executions).hasValue(3);
      assertThat(callbacks).hasValue(0);
    }
  }
}
