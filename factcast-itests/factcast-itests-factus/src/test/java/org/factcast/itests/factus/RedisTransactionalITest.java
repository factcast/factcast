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
import lombok.val;
import org.factcast.factus.Factus;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.redis.tx.RedisTransactional;
import org.factcast.itests.factus.event.UserCreated;
import org.factcast.itests.factus.proj.TxRedissonManagedUserNames;
import org.factcast.itests.factus.proj.TxRedissonSubscribedUserNames;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.*;
import org.redisson.api.RMap;
import org.redisson.api.RTransaction;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ContextConfiguration(classes = {Application.class, RedissonProjectionConfiguration.class})
@EnableAutoConfiguration
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
      val l = new ArrayList<EventObject>(NUMBER_OF_EVENTS);
      for (int i = 0; i < NUMBER_OF_EVENTS; i++) {
        l.add(new UserCreated(randomUUID(), "" + i));
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
      TxRedissonManagedUserNamesSize2 p = new TxRedissonManagedUserNamesSize2(redissonClient) {};
      factus.update(p);

      assertThat(p.userNames().size()).isEqualTo(NUMBER_OF_EVENTS);
      assertThat(p.stateModifications()).isEqualTo(5); // expected at 2,4,6,8,10
    }

    @SneakyThrows
    @Test
    public void bulkAppliesInTransactionTimeout() {
      TxRedissonManagedUserNamesTimeout p =
          new TxRedissonManagedUserNamesTimeout(redissonClient) {};
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
      val l = new ArrayList<EventObject>(NUMBER_OF_EVENTS);
      for (int i = 0; i < NUMBER_OF_EVENTS; i++) {
        l.add(new UserCreated(randomUUID(), "" + i));
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
      TxRedissonSubscribedUserNamesSize2 p =
          new TxRedissonSubscribedUserNamesSize2(redissonClient) {};
      factus.subscribeAndBlock(p).awaitCatchup();

      assertThat(p.userNames().size()).isEqualTo(NUMBER_OF_EVENTS);
      assertThat(p.stateModifications()).isEqualTo(5); // expected at 2,4,6,8,10
    }

    @SneakyThrows
    @Test
    public void bulkAppliesInTransactionTimeout() {
      TxRedissonSubscribedUserNamesTimeout p =
          new TxRedissonSubscribedUserNamesTimeout(redissonClient) {};
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
    public void state(@NonNull UUID state) {
      stateModifications++;
      super.state(state);
    }
  }

  static class TrackingTxRedissonSubscribedUserNames extends TxRedissonSubscribedUserNames {
    public TrackingTxRedissonSubscribedUserNames(RedissonClient redisson) {
      super(redisson);
    }

    @Getter int stateModifications = 0;

    @Override
    public void state(@NonNull UUID state) {
      stateModifications++;
      super.state(state);
    }
  }

  @RedisTransactional(size = 2)
  static class TxRedissonManagedUserNamesSize2 extends TrackingTxRedissonManagedUserNames {
    public TxRedissonManagedUserNamesSize2(RedissonClient redisson) {
      super(redisson);
    }
  }

  @RedisTransactional(size = 3)
  static class TxRedissonManagedUserNamesSize3 extends TrackingTxRedissonManagedUserNames {
    public TxRedissonManagedUserNamesSize3(RedissonClient redisson) {
      super(redisson);
    }
  }

  @RedisTransactional(size = 3000000, timeout = 1000) // will flush after 800ms
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

  @RedisTransactional(size = 2)
  static class TxRedissonSubscribedUserNamesSize2 extends TrackingTxRedissonSubscribedUserNames {
    public TxRedissonSubscribedUserNamesSize2(RedissonClient redisson) {
      super(redisson);
    }
  }

  @RedisTransactional(size = 3)
  static class TxRedissonSubscribedUserNamesSize3 extends TrackingTxRedissonSubscribedUserNames {
    public TxRedissonSubscribedUserNamesSize3(RedissonClient redisson) {
      super(redisson);
    }
  }

  @RedisTransactional(size = 3000000, timeout = 1000) // will flush after 800ms
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

  @RedisTransactional(size = 5)
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

  @RedisTransactional(size = 5)
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
