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
import org.factcast.factus.redis.batch.RedisBatched;
import org.factcast.itests.factus.event.UserCreated;
import org.factcast.itests.factus.event.UserDeleted;
import org.factcast.itests.factus.proj.BatchRedissonManagedUserNames;
import org.factcast.itests.factus.proj.BatchRedissonSubscribedUserNames;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.*;
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
public class RedisBatchingTest extends AbstractFactCastIntegrationTest {
  @Autowired Factus factus;
  @Autowired RedissonClient redissonClient;
  final int NUMBER_OF_EVENTS = 10;

  // TODO test rollback/discard behavior
  // TODO read state during bulk update

  @Nested
  class MixedEvents {
    @BeforeEach
    public void setup() {
      val l = new ArrayList<EventObject>(NUMBER_OF_EVENTS);
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
      val l = new ArrayList<EventObject>(NUMBER_OF_EVENTS);
      for (int i = 0; i < NUMBER_OF_EVENTS; i++) {
        l.add(new UserCreated(randomUUID(), "" + i));
      }
      log.info("publishing {} Events ", NUMBER_OF_EVENTS);
      factus.publish(l);
    }

    @SneakyThrows
    @Test
    public void batchesInBatch3() {
      BatchRedissonManagedUserNamesSize3 p = new BatchRedissonManagedUserNamesSize3(redissonClient);
      factus.update(p);

      assertThat(p.userNames().size()).isEqualTo(NUMBER_OF_EVENTS);
      assertThat(p.stateModifications()).isEqualTo(4); // expected at 3,6,9,10
    }

    @SneakyThrows
    @Test
    public void batchesInBatch2() {
      BatchRedissonManagedUserNamesSize2 p = new BatchRedissonManagedUserNamesSize2(redissonClient);
      factus.update(p);

      assertThat(p.userNames().size()).isEqualTo(NUMBER_OF_EVENTS);
      assertThat(p.stateModifications()).isEqualTo(5); // expected at 2,4,6,8,10
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
    public void batchesInBatch3() {
      BatchRedissonSubscribedUserNamesSize3 p =
          new BatchRedissonSubscribedUserNamesSize3(redissonClient);
      factus.subscribeAndBlock(p).awaitCatchup();

      assertThat(p.userNames().size()).isEqualTo(NUMBER_OF_EVENTS);
      assertThat(p.stateModifications()).isEqualTo(4); // expected at 3,6,9,10
    }

    @SneakyThrows
    @Test
    public void batchesInBatch2() {
      BatchRedissonSubscribedUserNamesSize2 p =
          new BatchRedissonSubscribedUserNamesSize2(redissonClient);
      factus.subscribeAndBlock(p).awaitCatchup();

      assertThat(p.userNames().size()).isEqualTo(NUMBER_OF_EVENTS);
      assertThat(p.stateModifications()).isEqualTo(5); // expected at 2,4,6,8,10
    }
  }
}

class TrackingBatchRedissonManagedUserNames extends BatchRedissonManagedUserNames {
  public TrackingBatchRedissonManagedUserNames(RedissonClient redisson) {
    super(redisson);
  }

  @Getter int stateModifications = 0;

  @Override
  public void state(@NonNull UUID state) {
    stateModifications++;
    super.state(state);
  }
}

class TrackingBatchRedissonSubscribedUserNames extends BatchRedissonSubscribedUserNames {
  public TrackingBatchRedissonSubscribedUserNames(RedissonClient redisson) {
    super(redisson);
  }

  @Getter int stateModifications = 0;

  @Override
  public void state(@NonNull UUID state) {
    stateModifications++;
    super.state(state);
  }
}

@RedisBatched(size = 2)
class BatchRedissonManagedUserNamesSize2 extends TrackingBatchRedissonManagedUserNames {
  public BatchRedissonManagedUserNamesSize2(RedissonClient redisson) {
    super(redisson);
  }
}

@RedisBatched(size = 3)
class BatchRedissonManagedUserNamesSize3 extends TrackingBatchRedissonManagedUserNames {
  public BatchRedissonManagedUserNamesSize3(RedissonClient redisson) {
    super(redisson);
  }
}

@RedisBatched(size = 2)
class BatchRedissonSubscribedUserNamesSize2 extends TrackingBatchRedissonSubscribedUserNames {
  public BatchRedissonSubscribedUserNamesSize2(RedissonClient redisson) {
    super(redisson);
  }
}

@RedisBatched(size = 3)
class BatchRedissonSubscribedUserNamesSize3 extends TrackingBatchRedissonSubscribedUserNames {
  public BatchRedissonSubscribedUserNamesSize3(RedissonClient redisson) {
    super(redisson);
  }
}
