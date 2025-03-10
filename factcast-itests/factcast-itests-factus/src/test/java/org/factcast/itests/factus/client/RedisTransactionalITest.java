/*
 * Copyright © 2017-2022 factcast.org
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

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import nl.altindag.log.LogCaptor;
import org.factcast.core.FactStreamPosition;
import org.factcast.factus.Factus;
import org.factcast.factus.FactusImpl;
import org.factcast.factus.Handler;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.projection.WriterToken;
import org.factcast.factus.redis.tx.AbstractRedisTxManagedProjection;
import org.factcast.factus.redis.tx.RedisTransactional;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.factcast.itests.TestFactusApplication;
import org.factcast.itests.factus.config.RedissonProjectionConfiguration;
import org.factcast.itests.factus.event.UserCreated;
import org.factcast.itests.factus.event.UserDeleted;
import org.factcast.itests.factus.proj.TxRedissonManagedUserNames;
import org.factcast.itests.factus.proj.TxRedissonSubscribedUserNames;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.redisson.api.RTransaction;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.shaded.org.awaitility.Awaitility;

@SpringBootTest
@ContextConfiguration(
    classes = {TestFactusApplication.class, RedissonProjectionConfiguration.class})
@Slf4j
public class RedisTransactionalITest extends AbstractFactCastIntegrationTest {
  @Autowired Factus factus;
  @Autowired RedissonClient redissonClient;
  final int NUMBER_OF_EVENTS = 10;

  @Nested
  class Managed {
    @BeforeEach
    public void setup() {
      var l = new ArrayList<EventObject>(NUMBER_OF_EVENTS);
      for (int i = 0; i < NUMBER_OF_EVENTS; i++) {
        l.add(new UserCreated(randomUUID(), getClass().getSimpleName() + ":" + i));
      }
      log.info("publishing {} Events ", NUMBER_OF_EVENTS);
      factus.publish(l);
    }

    @SneakyThrows
    @Test
    public void bulkAppliesInTransaction3() {
      TxRedissonManagedUserNamesSize3 p = new TxRedissonManagedUserNamesSize3(redissonClient);
      factus.update(p);

      assertThat(p.userNames().size()).isEqualTo(NUMBER_OF_EVENTS);
      assertThat(p.stateModifications()).isEqualTo(4); // expected at 3,6,9,10
    }

    @SneakyThrows
    @Test
    public void bulkAppliesInTransaction2() {
      TxRedissonManagedUserNamesSize2 p = new TxRedissonManagedUserNamesSize2(redissonClient);
      factus.update(p);

      assertThat(p.userNames().size()).isEqualTo(NUMBER_OF_EVENTS);
      assertThat(p.stateModifications()).isEqualTo(5); // expected at 2,4,6,8,10
    }

    @SneakyThrows
    @Test
    public void rollsBack() {
      TxRedissonManagedUserNamesSizeBlowAt7th p =
          new TxRedissonManagedUserNamesSizeBlowAt7th(redissonClient);

      assertThat(p.userNames()).isEmpty();

      try {
        factus.update(p);
      } catch (Throwable expected) {
        // ignore
      }

      assertThat(p.userNames()).hasSize(6);
      assertThat(p.stateModifications()).isEqualTo(2); // 5+1
    }

    @Test
    public void txVisibility() {
      final var userId = randomUUID();

      factus.publish(List.of(new UserCreated(userId, "hugo"), new UserDeleted(userId)));

      final var proj = new TxMultipleHandler(redissonClient);
      assertDoesNotThrow(() -> factus.update(proj));
    }
  }

  @Nested
  class Subscribed {
    @BeforeEach
    public void setup() {
      var l = new ArrayList<EventObject>(NUMBER_OF_EVENTS);
      for (int i = 0; i < NUMBER_OF_EVENTS; i++) {
        l.add(new UserCreated(randomUUID(), getClass().getSimpleName() + ":" + i));
      }
      log.info("publishing {} Events ", NUMBER_OF_EVENTS);
      factus.publish(l);
    }

    @Test
    void testTokenReleaseAfterTooManyFailures_redis() throws Exception {
      org.factcast.itests.factus.client.RedisTransactionalITest
              .TxRedissonSubscribedUserNamesTokenExposedAndThrowsError
          subscribedUserNames =
              new org.factcast.itests.factus.client.RedisTransactionalITest
                  .TxRedissonSubscribedUserNamesTokenExposedAndThrowsError(redissonClient);

      factus.publish(new UserDeleted(UUID.randomUUID()));

      // The projection doesnt have a token yet
      assertThat(subscribedUserNames.token()).isNull();
      try (var logCaptor = LogCaptor.forClass(FactusImpl.class)) {
        logCaptor.setLogLevelToTrace();
        factus.subscribe(subscribedUserNames);

        // The projection acquiered a token and
        subscribedUserNames.latch.await();
        Awaitility.await()
            .until(
                () ->
                    !logCaptor.getTraceLogs().isEmpty()
                        && logCaptor
                            .getTraceLogs()
                            .get(0)
                            .contains(
                                "Closing AutoCloseable for class class org.factcast.factus.redis.RedisWriterToken"));
      }

      assertThat(subscribedUserNames.token() != null && subscribedUserNames.token().isValid())
          .isFalse();
    }

    @Test
    void testTokenReAcquired_Redis() throws Exception {
      org.factcast.itests.factus.client.RedisTransactionalITest
              .TxRedissonSubscribedUserNamesTokenExposed
          subscribedUserNames =
              new org.factcast.itests.factus.client.RedisTransactionalITest
                  .TxRedissonSubscribedUserNamesTokenExposed(redissonClient);

      factus.publish(new UserCreated(UUID.randomUUID(), "hugo"));

      // The projection doesnt have a token yet
      assertThat(subscribedUserNames.token()).isNull();
      factus.subscribe(subscribedUserNames);

      // The projection acquiered a token and
      subscribedUserNames.latch.await();
      assertThat(subscribedUserNames.token().isValid()).isTrue();

      // Wait a bit to avoid the race condition and close the token
      Thread.sleep(1000);
      // Manually close the token
      subscribedUserNames.token().close();
      Awaitility.await().until(() -> !subscribedUserNames.token().isValid());

      // New event and tries to re acquire token
      factus.publish(new UserCreated(UUID.randomUUID(), "hugo"));

      Awaitility.await().until(() -> subscribedUserNames.token().isValid());
    }

    @SneakyThrows
    @Test
    void bulkAppliesInTransaction3() {
      TxRedissonSubscribedUserNamesSize3 p = new TxRedissonSubscribedUserNamesSize3(redissonClient);
      factus.subscribeAndBlock(p).awaitCatchup();

      assertThat(p.userNames()).hasSize(NUMBER_OF_EVENTS);
      assertThat(p.stateModifications()).isEqualTo(4); // expected at 3,6,9,10
    }

    @SneakyThrows
    @Test
    void bulkAppliesInTransaction2() {
      TxRedissonSubscribedUserNamesSize2 p = new TxRedissonSubscribedUserNamesSize2(redissonClient);
      factus.subscribeAndBlock(p).awaitCatchup();

      assertThat(p.userNames()).hasSize(NUMBER_OF_EVENTS);
      assertThat(p.stateModifications()).isEqualTo(5); // expected at 2,4,6,8,10
    }

    @SneakyThrows
    @Test
    void rollsBack() {
      TxRedissonSubscribedUserNamesSizeBlowAt7th p =
          new TxRedissonSubscribedUserNamesSizeBlowAt7th(redissonClient);

      assertThat(p.userNames()).isEmpty();

      try {
        factus.subscribeAndBlock(p).awaitCatchup();
      } catch (Throwable expected) {
        // ignore
      }

      assertThat(p.userNames()).hasSize(6);
      assertThat(p.stateModifications()).isEqualTo(2);
    }
  }

  static class TrackingTxRedissonManagedUserNames extends TxRedissonManagedUserNames {
    public TrackingTxRedissonManagedUserNames(RedissonClient redisson) {
      super(redisson);
    }

    @Getter int stateModifications;

    @Override
    public void transactionalFactStreamPosition(@NotNull FactStreamPosition factStreamPosition) {
      stateModifications++;
      super.transactionalFactStreamPosition(factStreamPosition);
    }
  }

  static class TrackingTxRedissonSubscribedUserNames extends TxRedissonSubscribedUserNames {
    public TrackingTxRedissonSubscribedUserNames(RedissonClient redisson) {
      super(redisson);
    }

    @Getter int stateModifications;

    @Override
    public void transactionalFactStreamPosition(@NotNull FactStreamPosition factStreamPosition) {
      stateModifications++;
      super.transactionalFactStreamPosition(factStreamPosition);
    }
  }

  @ProjectionMetaData(revision = 1)
  @RedisTransactional(bulkSize = 2)
  static class TxRedissonManagedUserNamesSize2 extends TrackingTxRedissonManagedUserNames {
    public TxRedissonManagedUserNamesSize2(RedissonClient redisson) {
      super(redisson);
    }
  }

  @ProjectionMetaData(revision = 1)
  @RedisTransactional(bulkSize = 3)
  static class TxRedissonManagedUserNamesSize3 extends TrackingTxRedissonManagedUserNames {
    public TxRedissonManagedUserNamesSize3(RedissonClient redisson) {
      super(redisson);
    }
  }

  @ProjectionMetaData(revision = 1)
  @RedisTransactional(bulkSize = 2)
  static class TxRedissonSubscribedUserNamesSize2 extends TrackingTxRedissonSubscribedUserNames {
    public TxRedissonSubscribedUserNamesSize2(RedissonClient redisson) {
      super(redisson);
    }
  }

  @ProjectionMetaData(revision = 1)
  @RedisTransactional(bulkSize = 3)
  static class TxRedissonSubscribedUserNamesSize3 extends TrackingTxRedissonSubscribedUserNames {
    public TxRedissonSubscribedUserNamesSize3(RedissonClient redisson) {
      super(redisson);
    }
  }

  @ProjectionMetaData(revision = 1)
  @RedisTransactional(bulkSize = 5)
  static class TxRedissonManagedUserNamesSizeBlowAt7th extends TrackingTxRedissonManagedUserNames {
    private int count;

    public TxRedissonManagedUserNamesSizeBlowAt7th(RedissonClient redisson) {
      super(redisson);
    }

    @Override
    protected void apply(UserCreated created, RTransaction tx) {
      if (++count == 7) { // blow the second bulk
        throw new IllegalStateException("Bad luck");
      }
      super.apply(created, tx);
    }
  }

  @ProjectionMetaData(revision = 1)
  @RedisTransactional(bulkSize = 5)
  static class TxRedissonSubscribedUserNamesSizeBlowAt7th
      extends TrackingTxRedissonSubscribedUserNames {
    private int count;

    public TxRedissonSubscribedUserNamesSizeBlowAt7th(RedissonClient redisson) {
      super(redisson);
    }

    @Override
    protected void apply(UserCreated created, RTransaction tx) {
      if (++count == 7) { // blow the second bulk
        throw new IllegalStateException("Bad luck");
      }
      super.apply(created, tx);
    }
  }

  @Getter
  @ProjectionMetaData(revision = 1)
  @RedisTransactional(bulkSize = 1)
  static class TxRedissonSubscribedUserNamesTokenExposedAndThrowsError
      extends TrackingTxRedissonSubscribedUserNames {
    private CountDownLatch latch = new CountDownLatch(1);
    private WriterToken token;

    public TxRedissonSubscribedUserNamesTokenExposedAndThrowsError(RedissonClient redisson) {
      super(redisson);
    }

    @Override
    public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
      token = super.acquireWriteToken(maxWait);
      latch.countDown();
      return token;
    }

    @Override
    protected void apply(UserCreated created, RTransaction tx) {
      throw new IllegalArgumentException("user should be in map but wasnt");
    }
  }

  @Getter
  @ProjectionMetaData(revision = 1)
  @RedisTransactional(bulkSize = 1)
  static class TxRedissonSubscribedUserNamesTokenExposed
      extends TrackingTxRedissonSubscribedUserNames {

    private CountDownLatch latch = new CountDownLatch(1);
    private WriterToken token;

    public TxRedissonSubscribedUserNamesTokenExposed(RedissonClient redisson) {
      super(redisson);
    }

    @Override
    public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
      token = super.acquireWriteToken(maxWait);
      latch.countDown();
      return token;
    }
  }

  @ProjectionMetaData(revision = 1)
  @RedisTransactional
  static class TxMultipleHandler extends AbstractRedisTxManagedProjection {
    public TxMultipleHandler(RedissonClient redisson) {
      super(redisson);
    }

    @Handler
    void apply(UserCreated e, RTransaction tx) {
      tx.getSet("hugo").add(e.aggregateId());
    }

    @Handler
    void apply(UserDeleted e, RTransaction tx) {
      if (!tx.getSet("hugo").contains(e.aggregateId())) {
        throw new IllegalArgumentException("user should be in map but wasnt");
      }
    }
  }
}
