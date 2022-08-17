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

import static java.util.UUID.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.*;
import lombok.extern.slf4j.Slf4j;
import nl.altindag.console.ConsoleCaptor;
import org.factcast.core.DuplicateFactException;
import org.factcast.core.event.EventConverter;
import org.factcast.factus.Factus;
import org.factcast.itests.TestFactusApplication;
import org.factcast.itests.factus.config.RedissonProjectionConfiguration;
import org.factcast.itests.factus.event.UserCreated;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ContextConfiguration(
    classes = {TestFactusApplication.class, RedissonProjectionConfiguration.class})
@Slf4j
class PublishDuplicateTest extends AbstractFactCastIntegrationTest {

  @Autowired Factus factus;
  @Autowired EventConverter ser;

  @Test
  void descriptiveErrorOnPublishingDuplicates() {

    UUID id = randomUUID();

    var john = ser.toFact(new UserCreated("John"));
    var paul = ser.toFact(new UserCreated("Paul"));
    var george = ser.toFact(new UserCreated("George"));
    var ringo = ser.toFact(new UserCreated("Ringo"));

    factus.publish(john);
    factus.publish(paul);
    factus.publish(george);
    factus.publish(ringo);

    try (ConsoleCaptor consoleCaptor = new ConsoleCaptor()) {

      assertThatThrownBy(
              () -> {
                factus.publish(john);
              })
          .isInstanceOf(DuplicateFactException.class);

      // it should not have retried this several times.
      assertThat(
              consoleCaptor.getStandardOutput().stream()
                  // get the server output from docker
                  .filter(l -> l.contains("STDOUT:"))
                  // the actual exception as thrown by the jdbc driver
                  .filter(
                      line ->
                          line.contains(
                              "org.factcast.core.DuplicateFactException: PreparedStatementCallback; SQL [INSERT INTO fact(header,payload) VALUES")))
          .hasSize(1);
    }
  }
}
