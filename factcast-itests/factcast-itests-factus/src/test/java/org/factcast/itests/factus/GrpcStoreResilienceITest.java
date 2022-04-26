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

import static org.assertj.core.api.Assertions.*;

import eu.rekawek.toxiproxy.model.ToxicDirection;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.factcast.test.FactCastExtension;
import org.factcast.test.toxi.FactCastProxy;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ContextConfiguration(classes = {Application.class})
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class})
@TestPropertySource(
    properties =
        "factcast.grpc.client.resilience.retries=" + GrpcStoreResilienceITest.NUMBER_OF_RETRIES)
@Slf4j
class GrpcStoreResilienceITest extends AbstractFactCastIntegrationTest {
  static final int NUMBER_OF_RETRIES = 99;

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
                FactCastExtension.resetProxy();
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
                FactCastExtension.resetProxy();
              }
            },
            500);

    // should reconnect like hell
    assertThat(fc.enumerateNamespaces()).containsExactly("ns");
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
                FactCastExtension.resetProxy();
              }
            },
            500);

    // should reconnect like hell
    assertThat(fc.enumerateNamespaces()).containsExactly("ns");
  }

  @SneakyThrows
  private void sleep(long ms) {
    Thread.sleep(ms);
  }
}
