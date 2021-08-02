package org.factcast.itests.factus;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.factcast.factus.Factus;
import org.factcast.itests.factus.event.UserCreated;
import org.factcast.itests.factus.event.UserDeleted;
import org.factcast.itests.factus.proj.RedissionTransactionalProjectionExample;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;

import static java.util.UUID.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class})
@Slf4j
public class RedissionTransactionalProjectionExampleITest
    extends AbstractFactCastIntegrationTest {

  @Autowired Factus factus;

  @Autowired RedissonClient redissonClient;

  @Test
  void getUsers() {
    val event1 = new UserCreated(randomUUID(), "Peter");
    val event2 = new UserCreated(randomUUID(), "Paul");
    val event3 = new UserCreated(randomUUID(), "Klaus");
    val event4 = new UserDeleted(event3.aggregateId());

    log.info("Publishing test events");
    factus.publish(Arrays.asList(event1, event2, event3, event4));

    val uut = new RedissionTransactionalProjectionExample.UserNames(redissonClient);
    factus.update(uut);
    val userNames = uut.getUserNames();

    assertThat(userNames).containsExactlyInAnyOrder("Peter", "Paul");
  }
}
