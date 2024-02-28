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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.grpc.StatusRuntimeException;
import java.util.ArrayList;
import java.util.UUID;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.factcast.core.FactStreamPosition;
import org.factcast.factus.Factus;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.redis.tx.RedisTransactional;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.factcast.itests.TestFactusApplication;
import org.factcast.itests.factus.config.RedissonProjectionConfiguration;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

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
    void setup() {
      var l = new ArrayList<EventObject>(NUMBER_OF_EVENTS);
      for (int i = 0; i < NUMBER_OF_EVENTS; i++) {
        l.add(new UserCreated(randomUUID(), getClass().getSimpleName() + ":" + i));
      }
      log.info("publishing {} Events ", NUMBER_OF_EVENTS);
      factus.publish(l);
    }

    @SneakyThrows
    @Test
    void bulkAppliesInTransaction3() {
      TxRedissonManagedUserNamesSize3 p = new TxRedissonManagedUserNamesSize3(redissonClient);
      factus.update(p);

      assertThat(p.userNames()).hasSize(NUMBER_OF_EVENTS);
      assertThat(p.stateModifications()).isEqualTo(4); // expected at 3,6,9,10
    }

    @SneakyThrows
    @Test
    void bulkAppliesInTransaction2() {
      TxRedissonManagedUserNamesSize2 p = new TxRedissonManagedUserNamesSize2(redissonClient);
      factus.update(p);

      assertThat(p.userNames()).hasSize(NUMBER_OF_EVENTS);
      assertThat(p.stateModifications()).isEqualTo(5); // expected at 2,4,6,8,10
    }

    @SneakyThrows
    @Test
    void bulkAppliesInTransactionTimeout() {
      TxRedissonManagedUserNamesTimeout p = new TxRedissonManagedUserNamesTimeout(redissonClient);
      assertThatThrownBy(() -> factus.update(p)).isInstanceOf(StatusRuntimeException.class);

      // factStreamPosition was called once, inside the transaction, but its effect
      // will have been rolled back as commit() fails with a timeout
      assertThat(p.stateModifications()).isOne();
      // therefore the FSP must be unset
      Assertions.assertThat(p.factStreamPosition()).isNull();
    }

    @SneakyThrows
    @Test
    void rollsBack() {
      TxRedissonManagedUserNamesSizeBlowAt7th p =
          new TxRedissonManagedUserNamesSizeBlowAt7th(redissonClient);

      assertThat(p.userNames()).isEmpty();

      try {
        factus.update(p);
      } catch (Throwable expected) {
        // ignore
      }

      // only first bulk (size = 5) should be executed
      assertThat(p.userNames()).hasSize(6); // 1-5 + 6
      assertThat(p.stateModifications()).isEqualTo(2); // 5,6
    }
  }

  @Nested
  class Subscribed {
    @BeforeEach
    void setup() {
      var l = new ArrayList<EventObject>(NUMBER_OF_EVENTS);
      for (int i = 0; i < NUMBER_OF_EVENTS; i++) {
        l.add(new UserCreated(randomUUID(), getClass().getSimpleName() + ":" + i));
      }
      log.info("publishing {} Events ", NUMBER_OF_EVENTS);
      factus.publish(l);
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
    void bulkAppliesInTransactionTimeout() {
      TxRedissonSubscribedUserNamesTimeout p =
          new TxRedissonSubscribedUserNamesTimeout(redissonClient);
      Assertions.assertThatThrownBy(() -> factus.subscribeAndBlock(p).awaitCatchup())
          .isInstanceOf(StatusRuntimeException.class);

      assertThat(p.userNames()).isEmpty();

      // factStreamPosition was called once, inside the transaction, but its effect
      // will have been rolled back as commit() fails with a timeout
      assertThat(p.stateModifications()).isOne();
      // therefore the FSP must be unset
      Assertions.assertThat(p.factStreamPosition()).isNull();
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

      // first bulk (size = 5) should be applied successfully
      // second bulk (size = 5) should have the first fact applied (retry after error))
      assertThat(p.userNames()).hasSize(7); // [0,6]
      assertThat(p.stateModifications()).isEqualTo(2); // 0-4 and 5-6
    }
  }

  static class TrackingTxRedissonManagedUserNames extends TxRedissonManagedUserNames {
    public TrackingTxRedissonManagedUserNames(RedissonClient redisson) {
      super(redisson);
    }

    @Getter int stateModifications = 0;

    @Override
    public void factStreamPosition(FactStreamPosition factStreamPosition) {
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
    public void factStreamPosition(FactStreamPosition factStreamPosition) {
      System.out.println(factStreamPosition);
      stateModifications++;
      super.factStreamPosition(factStreamPosition);
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
  @RedisTransactional(timeout = 50, bulkSize = 100)
  static class TxRedissonManagedUserNamesTimeout extends TrackingTxRedissonManagedUserNames {
    public TxRedissonManagedUserNamesTimeout(RedissonClient redisson) {
      super(redisson);
    }

    @Override
    @SneakyThrows
    protected void apply(UserCreated created, RTransaction tx) {
      RMap<UUID, String> userNames = tx.getMap(redisKey(), codec);
      userNames.put(created.aggregateId(), created.userName());

      Thread.sleep(100);
    }

    @Override
    protected void commit(@NonNull RTransaction runningTransaction) {
      super.commit(runningTransaction);
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
  @RedisTransactional(timeout = 150, bulkSize = 100)
  static class TxRedissonSubscribedUserNamesTimeout extends TrackingTxRedissonSubscribedUserNames {
    public TxRedissonSubscribedUserNamesTimeout(RedissonClient redisson) {
      super(redisson);
    }

    @Override
    @SneakyThrows
    protected void apply(UserCreated created, RTransaction tx) {
      RMap<UUID, String> userNames = tx.getMap(redisKey(), codec);
      userNames.put(created.aggregateId(), created.userName());

      Thread.sleep(100);
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
      if (count++ == 7) { // blow the second bulk
        throw new IllegalStateException("Bad luck");
      }
      super.apply(created, tx);
    }
  }
}
