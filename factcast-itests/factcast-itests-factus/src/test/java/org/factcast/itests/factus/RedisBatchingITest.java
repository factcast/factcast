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
import static org.assertj.core.api.Assertions.*;

import config.RedissonProjectionConfiguration;
import java.util.ArrayList;
import java.util.UUID;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.Factus;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.redis.batch.RedisBatched;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.factcast.itests.factus.event.UserCreated;
import org.factcast.itests.factus.event.UserDeleted;
import org.factcast.itests.factus.proj.BatchRedissonManagedUserNames;
import org.factcast.itests.factus.proj.BatchRedissonSubscribedUserNames;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBatch;
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
public class RedisBatchingITest extends AbstractFactCastIntegrationTest {
  @Autowired Factus factus;
  @Autowired RedissonClient redissonClient;
  final int NUMBER_OF_EVENTS = 10;

  @Nested
  class MixedEvents {
    @BeforeEach
    public void setup() {
      var l = new ArrayList<EventObject>(NUMBER_OF_EVENTS);
      for (int i = 0; i < NUMBER_OF_EVENTS; i++) {
        UUID id = randomUUID();
        l.add(new UserCreated(id, "" + i));
        if (i > 0) {
          l.add(new UserDeleted(id));
        }
      }
      log.info("publishing {} Events ", NUMBER_OF_EVENTS);
      factus.publish(l);
    }

    @SneakyThrows
    @Test
    public void mixedBatchManaged3() {
      BatchRedissonManagedUserNamesSize3 p = new BatchRedissonManagedUserNamesSize3(redissonClient);
      factus.update(p);
      assertThat(p.userNames().size()).isEqualTo(1);
    }

    @SneakyThrows
    @Test
    public void mixedBatchManaged2() {
      BatchRedissonManagedUserNamesSize2 p = new BatchRedissonManagedUserNamesSize2(redissonClient);
      factus.update(p);
      assertThat(p.userNames().size()).isEqualTo(1);
    }

    @SneakyThrows
    @Test
    public void mixedBatchSubscribed3() {
      BatchRedissonSubscribedUserNamesSize3 p =
          new BatchRedissonSubscribedUserNamesSize3(redissonClient);
      factus.subscribeAndBlock(p).awaitCatchup();
      assertThat(p.userNames().size()).isEqualTo(1);
    }

    @SneakyThrows
    @Test
    public void mixedBatchSubscribed2() {
      BatchRedissonSubscribedUserNamesSize2 p =
          new BatchRedissonSubscribedUserNamesSize2(redissonClient);
      factus.subscribeAndBlock(p).awaitCatchup();
      assertThat(p.userNames().size()).isEqualTo(1);
    }
  }

  @Nested
  class Managed {
    @BeforeEach
    public void setup() {
      var l = new ArrayList<EventObject>(NUMBER_OF_EVENTS);
      for (int i = 0; i < NUMBER_OF_EVENTS; i++) {
        l.add(new UserCreated(randomUUID(), "" + i));
      }
      log.info("publishing {} Events ", NUMBER_OF_EVENTS);
      factus.publish(l);
    }

    @SneakyThrows
    @Test
    public void bulkAppliesInBatch3() {
      BatchRedissonManagedUserNamesSize3 p = new BatchRedissonManagedUserNamesSize3(redissonClient);
      factus.update(p);

      assertThat(p.userNames().size()).isEqualTo(NUMBER_OF_EVENTS);
      assertThat(p.stateModifications()).isEqualTo(4); // expected at 3,6,9,10
    }

    @SneakyThrows
    @Test
    public void bulkAppliesInBatch2() {
      BatchRedissonManagedUserNamesSize2 p = new BatchRedissonManagedUserNamesSize2(redissonClient);
      factus.update(p);

      assertThat(p.userNames().size()).isEqualTo(NUMBER_OF_EVENTS);
      assertThat(p.stateModifications()).isEqualTo(5); // expected at 2,4,6,8,10
    }

    @SneakyThrows
    @Test
    public void discardsFaultyBulk() {
      BatchRedissonManagedUserNamesSizeBlowAt7th p =
          new BatchRedissonManagedUserNamesSizeBlowAt7th(redissonClient);

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
        l.add(new UserCreated(randomUUID(), "" + i));
      }
      log.info("publishing {} Events ", NUMBER_OF_EVENTS);
      factus.publish(l);
    }

    @SneakyThrows
    @Test
    public void bulkAppliesInBatch3() {
      BatchRedissonSubscribedUserNamesSize3 p =
          new BatchRedissonSubscribedUserNamesSize3(redissonClient);
      factus.subscribeAndBlock(p).awaitCatchup();

      assertThat(p.stateModifications()).isEqualTo(4); // expected at 3,6,9,10
      assertThat(p.userNames().size()).isEqualTo(NUMBER_OF_EVENTS);
    }

    @SneakyThrows
    @Test
    public void bulkAppliesInBatch2() {
      BatchRedissonSubscribedUserNamesSize2 p =
          new BatchRedissonSubscribedUserNamesSize2(redissonClient);
      factus.subscribeAndBlock(p).awaitCatchup();

      assertThat(p.stateModifications()).isEqualTo(5); // expected at 2,4,6,8,10
      assertThat(p.userNames().size()).isEqualTo(NUMBER_OF_EVENTS);
    }

    @SneakyThrows
    @Test
    public void discardsFaultyBulk() {
      BatchRedissonSubscribedUserNamesSizeBlowAt7th p =
          new BatchRedissonSubscribedUserNamesSizeBlowAt7th(redissonClient);

      assertThat(p.userNames()).isEmpty();

      try {
        factus.subscribeAndBlock(p).awaitCatchup();
      } catch (Throwable expected) {
        // ignore
      }

      // only first bulk (size = 5) should be executed
      assertThat(p.stateModifications()).isEqualTo(1);
      assertThat(p.userNames().size()).isEqualTo(5);
    }
  }

  static class TrackingBatchRedissonManagedUserNames extends BatchRedissonManagedUserNames {
    public TrackingBatchRedissonManagedUserNames(RedissonClient redisson) {
      super(redisson);
    }

    @Getter int stateModifications = 0;

    @Override
    public void factStreamPosition(@NonNull UUID factStreamPosition) {
      stateModifications++;
      super.factStreamPosition(factStreamPosition);
    }
  }

  static class TrackingBatchRedissonSubscribedUserNames extends BatchRedissonSubscribedUserNames {
    public TrackingBatchRedissonSubscribedUserNames(RedissonClient redisson) {
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
  @RedisBatched(bulkSize = 2)
  static class BatchRedissonManagedUserNamesSize2 extends TrackingBatchRedissonManagedUserNames {
    public BatchRedissonManagedUserNamesSize2(RedissonClient redisson) {
      super(redisson);
    }

    @Override
    public void factStreamPosition(@NonNull UUID factStreamPosition) {

      super.factStreamPosition(factStreamPosition);
    }
  }

  @ProjectionMetaData(serial = 1)
  @RedisBatched(bulkSize = 3)
  static class BatchRedissonManagedUserNamesSize3 extends TrackingBatchRedissonManagedUserNames {
    public BatchRedissonManagedUserNamesSize3(RedissonClient redisson) {
      super(redisson);
    }
  }

  @ProjectionMetaData(serial = 1)
  @RedisBatched(bulkSize = 2)
  static class BatchRedissonSubscribedUserNamesSize2
      extends TrackingBatchRedissonSubscribedUserNames {
    public BatchRedissonSubscribedUserNamesSize2(RedissonClient redisson) {
      super(redisson);
    }
  }

  @ProjectionMetaData(serial = 1)
  @RedisBatched(bulkSize = 3)
  static class BatchRedissonSubscribedUserNamesSize3
      extends TrackingBatchRedissonSubscribedUserNames {
    public BatchRedissonSubscribedUserNamesSize3(RedissonClient redisson) {
      super(redisson);
    }
  }

  @ProjectionMetaData(serial = 1)
  @RedisBatched(bulkSize = 5)
  static class BatchRedissonManagedUserNamesSizeBlowAt7th
      extends TrackingBatchRedissonManagedUserNames {
    private int count;

    public BatchRedissonManagedUserNamesSizeBlowAt7th(RedissonClient redisson) {
      super(redisson);
    }

    @Override
    protected void apply(UserCreated created, RBatch tx) {
      if (count++ == 8) { // blow the second bulk
        throw new IllegalStateException("Bad luck");
      }
      super.apply(created, tx);
    }
  }

  @ProjectionMetaData(serial = 1)
  @RedisBatched(bulkSize = 5)
  static class BatchRedissonSubscribedUserNamesSizeBlowAt7th
      extends TrackingBatchRedissonSubscribedUserNames {
    private int count;

    public BatchRedissonSubscribedUserNamesSizeBlowAt7th(RedissonClient redisson) {
      super(redisson);
    }

    @Override
    protected void apply(UserCreated created, RBatch tx) {
      if (count++ == 8) { // blow the second bulk
        throw new IllegalStateException("Bad luck");
      }
      super.apply(created, tx);
    }
  }
}
