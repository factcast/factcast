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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import eu.rekawek.toxiproxy.model.ToxicDirection;
import io.grpc.StatusRuntimeException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import nl.altindag.console.ConsoleCaptor;
import nl.altindag.log.LogCaptor;
import org.factcast.client.grpc.GrpcFactStore;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.RetryableException;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionClosedException;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.itests.TestFactusApplication;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.factcast.test.toxi.FactCastProxy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ContextConfiguration(classes = TestFactusApplication.class)
@TestPropertySource(
    properties = {
      "factcast.grpc.client.resilience.attempts="
          + SubscriptionReconnectionITest.NUMBER_OF_ATTEMPTS,
      // some tests here assume event handling happens sequential even on networking level
      "factcast.grpc.client.catchup-batchsize=1"
    })
@Slf4j
class SubscriptionReconnectionITest extends AbstractFactCastIntegrationTest {
  static final int NUMBER_OF_ATTEMPTS = 30;

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
  void subscribeWorksWithoutAndWithLatency() {
    fetchAll();

    proxy.toxics().latency("some latency", ToxicDirection.UPSTREAM, LATENCY);

    assertThatNoException().isThrownBy(this::fetchAll);
  }

  @SneakyThrows
  @Test
  @Disabled("https://github.com/factcast/factcast/issues/2749")
  void subscribeWithReconnect() {
    log.info(
        "Using FcProxy {} {}:{}",
        proxy.getName(),
        proxy.getContainerIpAddress(),
        proxy.getProxyPort());
    LogCaptor logCaptor = LogCaptor.forClass(GrpcFactStore.class);
    logCaptor.setLogLevelToDebug();

    var count = new AtomicInteger();

    fetchAll(
        f -> {
          if (f.serial() == MAX_FACTS / 4) {
            proxy.disable();
            sleep(100);
            proxy.enable();
          }
          log.info("Got {}", f.serial());
          count.incrementAndGet();
        });

    assertThat(count.get()).isEqualTo(MAX_FACTS);
    assertThat(logCaptor.getInfoLogs()).containsOnlyOnce("Handshake successful.");
  }

  @SneakyThrows
  @Test
  void followWithReconnectAfterCatchup() {
    LogCaptor logCaptor = LogCaptor.forClass(GrpcFactStore.class);
    var count = new AtomicInteger();

    try (var ignored = follow(f -> count.incrementAndGet())) {

      await().atMost(10, SECONDS).untilAsserted(() -> assertThat(count.get()).isEqualTo(MAX_FACTS));

      proxy.disable();
      sleep(100);
      proxy.enable();
      fc.publish(Fact.builder().ns("ns").type("type").buildWithoutPayload());

      await().atMost(1, SECONDS).untilAsserted(() -> assertThat(count.get()).isEqualTo(MAX_FACTS));
    }

    assertThat(logCaptor.getInfoLogs()).containsOnlyOnce("Handshake successful.");
  }

  @SneakyThrows
  @Test
  @Disabled("https://github.com/factcast/factcast/issues/2749")
  void subscribeWithFailingReconnect() {
    try (var consoleCaptor = new ConsoleCaptor()) {
      var count = new AtomicInteger();
      assertThatThrownBy(
              () ->
                  fetchAll(
                      f -> {
                        if (f.serial() == MAX_FACTS / 32) {
                          try {
                            // let it repeatedly fail after each 1k sent...
                            proxy.toxics().limitData("limit", ToxicDirection.DOWNSTREAM, 1024);
                          } catch (IOException e) {
                            throw new RuntimeException(e);
                          }
                          // and don't turn it on again
                        }
                        log.info("Got {}", f.serial());
                        count.incrementAndGet();
                      }))
          .isInstanceOfAny(
              SubscriptionClosedException.class,
              StatusRuntimeException.class,
              RetryableException.class);
      assertThat(count.get()).isLessThan(MAX_FACTS);

      await()
          .atMost(10, SECONDS)
          .untilAsserted(
              () ->
                  assertThat(
                          consoleCaptor.getStandardOutput().stream()
                              .filter(e -> e.contains("Trying to resubscribe")))
                      .hasSize(NUMBER_OF_ATTEMPTS - 1));
    }
  }

  private void fetchAll() {
    fetchAll(f -> {});
  }

  @SneakyThrows
  private void fetchAll(FactObserver o) {
    log.info("Fetching");
    SubscriptionRequest req = SubscriptionRequest.catchup(FactSpec.ns("ns")).fromScratch();
    fc.subscribe(
            req,
            f -> {
              o.onNext(f);
            })
        .awaitComplete()
        .close();
  }

  private Subscription follow(FactObserver o) {
    log.info("Following");
    SubscriptionRequest req = SubscriptionRequest.follow(FactSpec.ns("ns")).fromScratch();
    return fc.subscribe(req, o::onNext);
  }

  @SneakyThrows
  private void sleep(long ms) {
    Thread.sleep(ms);
  }
}
