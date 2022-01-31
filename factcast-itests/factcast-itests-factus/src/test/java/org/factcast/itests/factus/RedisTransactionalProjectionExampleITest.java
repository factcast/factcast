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

import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.Factus;
import org.factcast.itests.factus.event.UserCreated;
import org.factcast.itests.factus.event.UserDeleted;
import org.factcast.itests.factus.proj.RedisTransactionalProjectionExample;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class})
@Slf4j
public class RedisTransactionalProjectionExampleITest extends AbstractFactCastIntegrationTest {

  @Autowired Factus factus;

  @Autowired RedissonClient redissonClient;

  @Test
  void getUsers() {
    var event1 = new UserCreated(randomUUID(), "Peter");
    var event2 = new UserCreated(randomUUID(), "Paul");
    var event3 = new UserCreated(randomUUID(), "Klaus");
    var event4 = new UserDeleted(event3.aggregateId());

    log.info("Publishing test events");
    factus.publish(Arrays.asList(event1, event2, event3, event4));

    var uut = new RedisTransactionalProjectionExample.UserNames(redissonClient);
    factus.update(uut);
    var userNames = uut.getUserNames();

    assertThat(userNames).containsExactlyInAnyOrder("Peter", "Paul");
  }
}
