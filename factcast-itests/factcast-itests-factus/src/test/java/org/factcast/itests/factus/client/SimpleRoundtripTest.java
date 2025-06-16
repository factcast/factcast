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
import org.aspectj.lang.annotation.*;
import org.assertj.core.api.Assertions;
import org.factcast.factus.Factus;
import org.factcast.itests.TestFactusApplication;
import org.factcast.itests.factus.event.*;
import org.factcast.itests.factus.proj.UserCount;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(
    classes = {
      TestFactusApplication.class, // this broke the inner class instantiation
    })
@Slf4j
@DirtiesContext
class SimpleRoundtripTest extends AbstractFactCastIntegrationTest {

  @Autowired Factus factus;

  @Autowired UserCount userCount;

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

@Aspect
@Component
class SomeAspectAroundProjection {

  @Before("execution(* org.factcast.itests.factus.proj.UserCount.*(..))")
  public void nop() {}

  @Before("execution(* org.factcast.itests.factus.proj.UserCount.Handlers.*(..))")
  public void nopInner() {}
}
