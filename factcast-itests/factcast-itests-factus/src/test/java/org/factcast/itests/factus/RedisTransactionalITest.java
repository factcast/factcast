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
package org.factcast.itests.factus;

import static java.util.UUID.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import config.RedissonProjectionConfiguration;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.subscription.InternalSubscription;
import org.factcast.core.subscription.Subscription;
import org.factcast.factus.Factus;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.redis.tx.RedisTransactional;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.factcast.itests.factus.event.UserCreated;
import org.factcast.itests.factus.proj.TxRedissonManagedUserNames;
import org.factcast.itests.factus.proj.TxRedissonSubscribedUserNames;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.redisson.api.RMap;
import org.redisson.api.RTransaction;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ContextConfiguration(classes = {Application.class, RedissonProjectionConfiguration.class})
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class})
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
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
    public void bulkCommitsOnError() {

      TxRedissonManagedUserSubscribedSize3Error2 subscribedProjection =
          new TxRedissonManagedUserSubscribedSize3Error2(redissonClient);
      assertThat(subscribedProjection.userNames().size()).isZero();

      // exec this in another thread
      CompletableFuture.runAsync(
              () -> {
                Subscription subscription = factus.subscribeAndBlock(subscribedProjection);
                subscribedProjection.subscription((InternalSubscription) subscription);

                assertThatThrownBy(subscription::awaitComplete)
                    .isInstanceOf(RuntimeException.class);
              })
          .get();

      // make sure the first two are comitted
      assertThat(subscribedProjection.userNames()).hasSize(2);
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
    public void bulkAppliesInTransactionTimeout() {
      TxRedissonManagedUserNamesTimeout p = new TxRedissonManagedUserNamesTimeout(redissonClient);
      factus.update(p);

      assertThat(p.userNames().size()).isEqualTo(NUMBER_OF_EVENTS);
      assertThat(p.stateModifications()).isEqualTo(2); // one for timeout, one for final flush
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

      // only first bulk (size = 5) should be executed
      assertThat(p.userNames().size()).isEqualTo(5);
      assertThat(p.stateModifications()).isEqualTo(1);
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

    @SneakyThrows
    @Test
    public void bulkAppliesInTransaction3() {
      TxRedissonSubscribedUserNamesSize3 p = new TxRedissonSubscribedUserNamesSize3(redissonClient);
      factus.subscribeAndBlock(p).awaitCatchup();

      assertThat(p.userNames().size()).isEqualTo(NUMBER_OF_EVENTS);
      assertThat(p.stateModifications()).isEqualTo(4); // expected at 3,6,9,10
    }

    @SneakyThrows
    @Test
    public void bulkAppliesInTransaction2() {
      TxRedissonSubscribedUserNamesSize2 p = new TxRedissonSubscribedUserNamesSize2(redissonClient);
      factus.subscribeAndBlock(p).awaitCatchup();

      assertThat(p.userNames().size()).isEqualTo(NUMBER_OF_EVENTS);
      assertThat(p.stateModifications()).isEqualTo(5); // expected at 2,4,6,8,10
    }

    @SneakyThrows
    @Test
    public void bulkAppliesInTransactionTimeout() {
      TxRedissonSubscribedUserNamesTimeout p =
          new TxRedissonSubscribedUserNamesTimeout(redissonClient);
      factus.subscribeAndBlock(p).awaitCatchup();

      assertThat(p.userNames().size()).isEqualTo(NUMBER_OF_EVENTS);
      assertThat(p.stateModifications()).isEqualTo(2); // one for timeout, one for final flush
    }

    @SneakyThrows
    @Test
    public void rollsBack() {
      TxRedissonSubscribedUserNamesSizeBlowAt7th p =
          new TxRedissonSubscribedUserNamesSizeBlowAt7th(redissonClient);

      assertThat(p.userNames()).isEmpty();

      try {
        factus.subscribeAndBlock(p).awaitCatchup();
      } catch (Throwable expected) {
        // ignore
      }

      // only first bulk (size = 5) should be executed
      assertThat(p.userNames().size()).isEqualTo(5);
      assertThat(p.stateModifications()).isEqualTo(1);
    }
  }

  static class TrackingTxRedissonManagedUserNames extends TxRedissonManagedUserNames {
    public TrackingTxRedissonManagedUserNames(RedissonClient redisson) {
      super(redisson);
    }

    @Getter int stateModifications = 0;

    @Override
    public void factStreamPosition(@NonNull UUID factStreamPosition) {
      stateModifications++;
      super.factStreamPosition(factStreamPosition);
    }
  }

  static class TrackingTxRedissonSubscribedUserNames extends TxRedissonSubscribedUserNames {
    public TrackingTxRedissonSubscribedUserNames(RedissonClient redisson) {
      super(redisson);
    }

    @Getter int stateModifications = 0;

    @Override
    public void factStreamPosition(@NonNull UUID factStreamPosition) {
      stateModifications++;
      super.factStreamPosition(factStreamPosition);
    }
  }

  @ProjectionMetaData(serial = 1)
  @RedisTransactional(bulkSize = 2)
  static class TxRedissonManagedUserNamesSize2 extends TrackingTxRedissonManagedUserNames {
    public TxRedissonManagedUserNamesSize2(RedissonClient redisson) {
      super(redisson);
    }
  }

  @ProjectionMetaData(serial = 1)
  @RedisTransactional(bulkSize = 3)
  static class TxRedissonManagedUserNamesSize3 extends TrackingTxRedissonManagedUserNames {
    public TxRedissonManagedUserNamesSize3(RedissonClient redisson) {
      super(redisson);
    }
  }

  @ProjectionMetaData(serial = 1)
  @RedisTransactional(bulkSize = 3)
  static class TxRedissonManagedUserSubscribedSize3Error2
      extends TrackingTxRedissonSubscribedUserNames {
    @Setter private InternalSubscription subscription;

    public TxRedissonManagedUserSubscribedSize3Error2(RedissonClient redisson) {
      super(redisson);
    }

    int count = 0;

    @Override
    protected void apply(UserCreated created, RTransaction tx) {
      if (count++ < 2) super.apply(created, tx);
      else subscription.notifyError(new IOException("oh dear"));
    }
  }

  @ProjectionMetaData(serial = 1)
  @RedisTransactional(bulkSize = 3000000, timeout = 1000) // will flush after 800ms
  static class TxRedissonManagedUserNamesTimeout extends TrackingTxRedissonManagedUserNames {
    public TxRedissonManagedUserNamesTimeout(RedissonClient redisson) {
      super(redisson);
    }

    @Override
    @SneakyThrows
    protected void apply(UserCreated created, RTransaction tx) {
      RMap<UUID, String> userNames = tx.getMap(redisKey(), codec);
      userNames.fastPut(created.aggregateId(), created.userName());

      Thread.sleep(100);
    }
  }

  @ProjectionMetaData(serial = 1)
  @RedisTransactional(bulkSize = 2)
  static class TxRedissonSubscribedUserNamesSize2 extends TrackingTxRedissonSubscribedUserNames {
    public TxRedissonSubscribedUserNamesSize2(RedissonClient redisson) {
      super(redisson);
    }
  }

  @ProjectionMetaData(serial = 1)
  @RedisTransactional(bulkSize = 3)
  static class TxRedissonSubscribedUserNamesSize3 extends TrackingTxRedissonSubscribedUserNames {
    public TxRedissonSubscribedUserNamesSize3(RedissonClient redisson) {
      super(redisson);
    }
  }

  @ProjectionMetaData(serial = 1)
  @RedisTransactional(bulkSize = 3000000, timeout = 1000) // will flush after 800ms
  static class TxRedissonSubscribedUserNamesTimeout extends TrackingTxRedissonSubscribedUserNames {
    public TxRedissonSubscribedUserNamesTimeout(RedissonClient redisson) {
      super(redisson);
    }

    @Override
    @SneakyThrows
    protected void apply(UserCreated created, RTransaction tx) {
      RMap<UUID, String> userNames = tx.getMap(redisKey(), codec);
      userNames.fastPut(created.aggregateId(), created.userName());

      Thread.sleep(100);
    }
  }

  @ProjectionMetaData(serial = 1)
  @RedisTransactional(bulkSize = 5)
  static class TxRedissonManagedUserNamesSizeBlowAt7th extends TrackingTxRedissonManagedUserNames {
    private int count;

    public TxRedissonManagedUserNamesSizeBlowAt7th(RedissonClient redisson) {
      super(redisson);
    }

    @Override
    protected void apply(UserCreated created, RTransaction tx) {
      if (count++ == 8) { // blow the second bulk
        throw new IllegalStateException("Bad luck");
      }
      super.apply(created, tx);
    }
  }

  @ProjectionMetaData(serial = 1)
  @RedisTransactional(bulkSize = 5)
  static class TxRedissonSubscribedUserNamesSizeBlowAt7th
      extends TrackingTxRedissonSubscribedUserNames {
    private int count;

    public TxRedissonSubscribedUserNamesSizeBlowAt7th(RedissonClient redisson) {
      super(redisson);
    }

    @Override
    protected void apply(UserCreated created, RTransaction tx) {
      if (count++ == 8) { // blow the second bulk
        throw new IllegalStateException("Bad luck");
      }
      super.apply(created, tx);
    }
  }
}
