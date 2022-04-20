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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.factcast.test.toxi.FactCastProxy;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ContextConfiguration(classes = {Application.class})
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class})
@Slf4j
class SubscriptionReconnectionTest extends AbstractFactCastIntegrationTest {

  private static final int MAX_FACTS = 10000;
  private static final long LATENCY = 5000;
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
    Stopwatch sw = Stopwatch.createStarted();
    // fetchAll();
    long rt = sw.stop().elapsed(TimeUnit.MILLISECONDS);

    proxy.toxics().limitData("limit", ToxicDirection.DOWNSTREAM, 1024);

    sw = Stopwatch.createStarted();
    fetchAll();
    Thread.sleep(30000000L);

    long rtWithReconnect = sw.stop().elapsed(TimeUnit.MILLISECONDS);

    assertThat(rtWithReconnect).isGreaterThan(rt);

    log.info("Runtime with/without reconnect: {}/{}", rtWithReconnect, rt);
  }

  private void fetchAll() {
    fetchAll(f -> {});
  }

  private void fetchAll(FactObserver o) {
    log.info("Fetching");
    AtomicInteger count = new AtomicInteger(0);
    SubscriptionRequest req = SubscriptionRequest.catchup(FactSpec.ns("ns")).fromScratch();
    fc.subscribe(
            req,
            f -> {
              count.incrementAndGet();
              o.onNext(f);
            })
        .awaitComplete();

    assertThat(count).hasValue(MAX_FACTS);
  }
}
