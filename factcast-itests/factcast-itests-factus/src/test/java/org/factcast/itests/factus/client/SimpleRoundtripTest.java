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

import java.util.UUID;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.factcast.factus.Factus;
import org.factcast.factus.event.EventConverter;
import org.factcast.itests.TestFactusApplication;
import org.factcast.itests.factus.config.RedissonProjectionConfiguration;
import org.factcast.itests.factus.event.*;
import org.factcast.itests.factus.proj.*;
import org.factcast.spring.boot.autoconfigure.snap.RedissonSnapshotCacheAutoConfiguration;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(
    classes = {
      TestFactusApplication.class,
      RedissonProjectionConfiguration.class,
      RedissonSnapshotCacheAutoConfiguration.class
    })
@Slf4j
class SimpleRoundtripTest extends AbstractFactCastIntegrationTest {

  @Autowired Factus factus;

  @Autowired EventConverter eventConverter;

  @Autowired RedissonTxManagedUserNames externalizedUserNames;

  @Autowired UserCount userCount;
  @Autowired RedissonClient redissonClient;

  @Test
  void simpleRoundTrip() {
    UUID johnsId = randomUUID();
    factus.publish(new UserCreated(johnsId, "John"));
    factus.publish(asList(new UserCreated(randomUUID(), "Paul")));
    factus.publish(new UserDeleted(johnsId));
    factus.update(userCount);
    Assertions.assertThat(userCount.count()).isEqualTo(1);
  }

  @SneakyThrows
  private static void sleep(long ms) {
    Thread.sleep(ms);
  }
}
