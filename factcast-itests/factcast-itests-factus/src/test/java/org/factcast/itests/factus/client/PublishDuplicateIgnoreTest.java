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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Lists;
import java.util.*;
import java.util.concurrent.atomic.*;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import nl.altindag.console.ConsoleCaptor;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.event.EventConverter;
import org.factcast.factus.Factus;
import org.factcast.factus.Handler;
import org.factcast.factus.projection.LocalManagedProjection;
import org.factcast.itests.TestFactusApplication;
import org.factcast.itests.factus.config.RedissonProjectionConfiguration;
import org.factcast.itests.factus.event.UserCreated;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ContextConfiguration(
    classes = {TestFactusApplication.class, RedissonProjectionConfiguration.class})
@TestPropertySource(properties = {"factcast.grpc.client.ignore-duplicate-facts:true"})
@Slf4j
class PublishDuplicateIgnoreTest extends AbstractFactCastIntegrationTest {

  @Autowired FactCast factCast;
  @Autowired Factus factus;
  @Autowired EventConverter ser;

  private Fact john;
  private Fact paul;
  private Fact george;
  private Fact ringo;
  private ArrayList<Fact> beatles;

  @BeforeEach
  void setup() {
    john = ser.toFact(new UserCreated("John"));
    paul = ser.toFact(new UserCreated("Paul"));
    george = ser.toFact(new UserCreated("George"));
    ringo = ser.toFact(new UserCreated("Ringo"));

    beatles = Lists.newArrayList(john, paul, george, ringo);
  }

  @Test
  void skipsSingleDuplicate() {

    factCast.publish(john);
    factCast.publish(paul);
    factCast.publish(george);
    factCast.publish(ringo);

    try (ConsoleCaptor consoleCaptor = new ConsoleCaptor()) {

      factCast.publish(john);

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

  @SneakyThrows
  @Test
  void fallsBackToSinglePublish() {

    factCast.publish(ringo);

    try (ConsoleCaptor consoleCaptor = new ConsoleCaptor()) {

      factCast.publish(beatles);

      // give it a bit of time for sysout on the server to be flushed
      Thread.sleep(100);

      // it should have seen dups 2 times, one for the batch, second for ringo
      assertThat(
              consoleCaptor.getStandardOutput().stream()
                  // get the server output from docker
                  .filter(l -> l.contains("STDOUT:"))
                  // the actual exception as thrown by the jdbc driver
                  .filter(
                      line ->
                          line.contains(
                              "org.factcast.core.DuplicateFactException: PreparedStatementCallback; SQL [INSERT INTO fact(header,payload) VALUES")))
          .hasSize(2);
    }

    var cp = new CountingProjection();
    factus.update(cp);
    assertThat(cp.count()).hasValue(4);
  }

  @SneakyThrows
  @Test
  void fallsBackToSinglePublishWithMultipleDuplicates() {

    factCast.publish(george);
    factCast.publish(john);

    try (ConsoleCaptor consoleCaptor = new ConsoleCaptor()) {

      factCast.publish(beatles);

      // give it a bit of time for sysout on the server to be flushed
      Thread.sleep(100);

      // it should have seen dups 3 times, one for the batch, two for the already inserted ones.
      assertThat(
              consoleCaptor.getStandardOutput().stream()
                  // get the server output from docker
                  .filter(l -> l.contains("STDOUT:"))
                  // the actual exception as thrown by the jdbc driver
                  .filter(
                      line ->
                          line.contains(
                              "org.factcast.core.DuplicateFactException: PreparedStatementCallback; SQL [INSERT INTO fact(header,payload) VALUES")))
          .hasSize(3);
    }

    var cp = new CountingProjection();
    factus.update(cp);
    assertThat(cp.count()).hasValue(4);
  }

  static class CountingProjection extends LocalManagedProjection {
    @Getter final AtomicInteger count = new AtomicInteger();

    @Handler
    void apply(UserCreated uc) {
      count.incrementAndGet();
    }
  }
}
