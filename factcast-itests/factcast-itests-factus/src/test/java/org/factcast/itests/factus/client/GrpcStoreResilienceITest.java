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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Lists;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import nl.altindag.log.LogCaptor;
import org.factcast.client.grpc.GrpcFactStore;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.itests.TestFactusApplication;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.factcast.test.toxi.FactCastProxy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ContextConfiguration(classes = TestFactusApplication.class)
@TestPropertySource(
    properties =
        "factcast.grpc.client.resilience.attempts=" + GrpcStoreResilienceITest.NUMBER_OF_ATTEMPTS)
@Slf4j
class GrpcStoreResilienceITest extends AbstractFactCastIntegrationTest {
  static final int NUMBER_OF_ATTEMPTS = 99;

  private static final int MAX_FACTS = 10000;
  private static final long LATENCY = 2000;
  @Autowired FactCast fc;
  FactCastProxy proxy;

  @BeforeEach
  void setup() {
    log.info("Publishing");
    List<Fact> facts = new ArrayList<>(MAX_FACTS);
    for (int i = 0; i < MAX_FACTS; i++) {
      facts.add(Fact.builder().ns("ns").type("type").buildWithoutPayload());
    }
    fc.publish(facts);
  }

  @SneakyThrows
  @Test
  void testRetryBehaviorWithoutResponse() {

    // break upstream call
    proxy.toxics().limitData("break upstream", ToxicDirection.UPSTREAM, 1024);

    new Timer()
        .schedule(
            new TimerTask() {
              @Override
              public void run() {
                // heal the communication
                log.info("repairing proxy");
                proxy.reset();
              }
            },
            500);

    List<Fact> facts = new ArrayList<>(MAX_FACTS);
    for (int i = 0; i < MAX_FACTS; i++) {
      facts.add(Fact.builder().ns("ns").type("type").buildWithoutPayload());
    }
    // should reconnect like hell
    fc.publish(facts);
  }

  @SneakyThrows
  @Test
  void testConcurrentRetryBehaviorWithoutResponse() {
    LogCaptor logCaptor = LogCaptor.forClass(GrpcFactStore.class);

    // break upstream call
    proxy.toxics().timeout("immediate reset", ToxicDirection.UPSTREAM, 10);

    new Timer()
        .schedule(
            new TimerTask() {
              @Override
              public void run() {
                // heal the communication
                log.info("repairing proxy");
                proxy.reset();
              }
            },
            1000);

    List<Fact> facts = new ArrayList<>(MAX_FACTS);
    for (int i = 0; i < MAX_FACTS; i++) {
      facts.add(Fact.builder().ns("ns").type("type").buildWithoutPayload());
    }
    List<List<Fact>> factPartitions = Lists.partition(facts, MAX_FACTS / 4);
    // Reconnect with 4 concurrent threads
    CountDownLatch latch = new CountDownLatch(4);
    ExecutorService threads = Executors.newFixedThreadPool(3);
    for (int i = 0; i < 4; i++) {
      final List<Fact> f = factPartitions.get(i);
      threads.submit(
          () -> {
            fc.publish(f);
            latch.countDown();
          });
    }

    assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
    assertThat(logCaptor.getInfoLogs()).containsOnlyOnce("Handshake successful.");
  }

  @SneakyThrows
  @Test
  void testRetryBehaviorWithResponse() {

    fc.publish(Fact.builder().ns("ns").type("type").buildWithoutPayload());

    // break upstream call
    proxy.toxics().resetPeer("immediate reset", ToxicDirection.UPSTREAM, 1);

    new Timer()
        .schedule(
            new TimerTask() {
              @Override
              public void run() {
                // heal the communication
                log.info("repairing proxy");
                proxy.reset();
              }
            },
            500);

    // should reconnect like hell
    assertThat(fc.enumerateNamespaces()).isNotEmpty(); // data might be coming from schemareg
  }

  @SneakyThrows
  @Test
  void testRetryBehaviorWithResponseBreakingDownstream() {

    fc.publish(Fact.builder().ns("ns").type("type").buildWithoutPayload());

    // break upstream call
    proxy.toxics().limitData("break every byte", ToxicDirection.DOWNSTREAM, 1);

    new Timer()
        .schedule(
            new TimerTask() {
              @Override
              public void run() {
                // heal the communication
                log.info("repairing proxy");
                proxy.reset();
              }
            },
            500);

    // should reconnect like hell
    assertThat(fc.enumerateNamespaces()).isNotEmpty();
  }

  @SneakyThrows
  private void sleep(long ms) {
    Thread.sleep(ms);
  }
}
