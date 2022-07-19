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
package org.factcast.itests.factus;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.*;

import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.factus.Factus;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.event.EventSerializer;
import org.factcast.itests.factus.event.versioned.v2.UserCreated;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

import com.google.common.base.Stopwatch;

import lombok.extern.slf4j.Slf4j;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class})
@ContextConfiguration(classes = {Application.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Slf4j
class FactusClientFetchingTransformedTest extends AbstractFactCastIntegrationTest {

  private static final int MAX = 10000;
  @Autowired Factus ec;
  @Autowired FactCast fc;
  @Autowired EventSerializer es;

  @Test
  void testVersions_upcast() {
    log.info("Preparing");
    List<UUID> ids = new ArrayList<>();
    List<EventObject> facts = new ArrayList<>();
    for (int i = 0; i < MAX; i++) {
      UUID aggId = UUID.randomUUID();
      ids.add(aggId);
      facts.add(new org.factcast.itests.factus.event.versioned.v1.UserCreated(aggId, "foo"));
    }
    log.info("Publishing {} v1 events", MAX);
    List<UUID> factIds =
        ec.publish(facts, l -> l.stream().map(Fact::id).collect(Collectors.toList()));

    log.info("Picking (and transforming)");
    // pick several ones
    AtomicBoolean zebra = new AtomicBoolean();
    AtomicInteger count = new AtomicInteger();
    factIds.stream()
        .skip(MAX / 2)
        .forEach(
            k -> {
              boolean skip = zebra.get();
              zebra.set(!skip);
              // trigger transformation
              if (!skip) {
                Optional<Fact> fact = fc.fetchByIdAndVersion(k, 2);
                count.incrementAndGet();
              }
            });

    log.info("Number of pretransformed facts: {} ", count.get());

    Stopwatch sw = Stopwatch.createStarted();
    AtomicInteger index = new AtomicInteger(0);
    log.info("subscribing with 1/4 facts already transformed");
    fc.subscribe(
            SubscriptionRequest.catchup(FactSpec.from(UserCreated.class)).fromScratch(),
            f -> {
              int idx = index.get();
              UUID expected = ids.get(idx);
              UserCreated userCreated = es.deserialize(UserCreated.class, f.jsonPayload());
              // assert order
              assertThat(expected).isEqualTo(userCreated.aggregateId());
              // assert version
              assertThat(f.version()).isEqualTo(2);
              index.incrementAndGet();
            })
        .awaitCatchup();
    long timeUntilCatchupWithTransformation = sw.elapsed(TimeUnit.MILLISECONDS);

    assertThat(index).hasValue(MAX);
    index.set(0);

    log.info("re-subscribing all facts already transformed");
    sw = Stopwatch.createStarted();
    fc.subscribe(
            SubscriptionRequest.catchup(FactSpec.from(UserCreated.class)).fromScratch(),
            f -> {
              int idx = index.get();
              UUID expected = ids.get(idx);
              UserCreated userCreated = es.deserialize(UserCreated.class, f.jsonPayload());
              // assert order
              assertThat(expected).isEqualTo(userCreated.aggregateId());
              // assert version
              assertThat(f.version()).isEqualTo(2);
              index.incrementAndGet();
            })
        .awaitCatchup();
    long timeUntilCatchupWithoutTransformation = sw.elapsed(TimeUnit.MILLISECONDS);

    assertThat(index).hasValue(MAX);

    log.info(
        "RT with/without transformation: {}/{} ",
        timeUntilCatchupWithTransformation,
        timeUntilCatchupWithoutTransformation);

    if (timeUntilCatchupWithoutTransformation > timeUntilCatchupWithTransformation)
      log.error(
          "******** Unexpected Runtime behavior. Intentionally not failing the test here to avoid flaky CI builds, where the runtime parameters cannot be controlled, but we should have a closer look.");
  }
}
