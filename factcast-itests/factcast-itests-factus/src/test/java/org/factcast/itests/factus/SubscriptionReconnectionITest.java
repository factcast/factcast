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

import com.google.common.base.Stopwatch;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import io.grpc.StatusRuntimeException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.SubscriptionClosedException;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.factcast.test.FactCastExtension;
import org.factcast.test.SLF4JTestSpy;
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
        "factcast.grpc.client.resilience.retries="
            + SubscriptionReconnectionITest.NUMBER_OF_RETRIES)
@Slf4j
class SubscriptionReconnectionITest extends AbstractFactCastIntegrationTest {
  static final int NUMBER_OF_RETRIES = 30;

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
  void subscribeWithLatency() {
    Stopwatch sw = Stopwatch.createStarted();
    fetchAll();
    long rtWithoutLatency = sw.stop().elapsed(TimeUnit.MILLISECONDS);

    proxy.toxics().latency("some latency", ToxicDirection.UPSTREAM, LATENCY);

    sw = Stopwatch.createStarted();
    fetchAll();
    long rtWithLatency = sw.stop().elapsed(TimeUnit.MILLISECONDS);

    assertThat(rtWithoutLatency).isLessThan(LATENCY);
    assertThat(rtWithLatency).isGreaterThan(LATENCY);

    log.info("Runtime with/without latency: {}/{}", rtWithLatency, rtWithoutLatency);
  }

  @SneakyThrows
  @Test
  void subscribeWithReconnect() {
    log.info(
        "Using FcProxy {} {}:{}",
        proxy.getName(),
        proxy.getContainerIpAddress(),
        proxy.getProxyPort());

    fetchAll();
    Stopwatch sw = Stopwatch.createStarted();
    fetchAll();
    long rt = sw.stop().elapsed(TimeUnit.MILLISECONDS);
    var count = new AtomicInteger();

    sw = Stopwatch.createStarted();
    fetchAll(
        f -> {
          if (f.serial() == MAX_FACTS / 4) {
            FactCastExtension.setProxyState(proxy.getName(), false);
            sleep(100);
            FactCastExtension.setProxyState(proxy.getName(), true);
          }
          log.info("Got {}", f.serial());
          count.incrementAndGet();
        });

    long rtWithReconnect = sw.stop().elapsed(TimeUnit.MILLISECONDS);

    assertThat(rtWithReconnect).isGreaterThan(rt);
    assertThat(count.get()).isEqualTo(MAX_FACTS);

    log.info("Runtime with/without reconnect: {}/{}", rtWithReconnect, rt);
  }

  @SneakyThrows
  @Test
  void subscribeWithFailingReconnect() {
    try (var spy = SLF4JTestSpy.attach()) {

      fetchAll();
      Stopwatch sw = Stopwatch.createStarted();
      var count = new AtomicInteger();
      fetchAll();

      sw = Stopwatch.createStarted();
      assertThatThrownBy(
              () ->
                  fetchAll(
                      f -> {
                        if (f.serial() == MAX_FACTS / 8) {
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
          .isInstanceOfAny(SubscriptionClosedException.class, StatusRuntimeException.class);
      assertThat(count.get()).isLessThan(MAX_FACTS);

      assertThat(
              spy.stream().filter(e -> e.getFormattedMessage().contains("Trying to resubscribe")))
          .hasSize(NUMBER_OF_RETRIES);
    }
  }

  private void fetchAll() {
    fetchAll(f -> {});
  }

  private void fetchAll(FactObserver o) {
    log.info("Fetching");
    SubscriptionRequest req = SubscriptionRequest.catchup(FactSpec.ns("ns")).fromScratch();
    fc.subscribe(
            req,
            f -> {
              o.onNext(f);
            })
        .awaitComplete();
  }

  @SneakyThrows
  private void sleep(long ms) {
    Thread.sleep(ms);
  }
}
