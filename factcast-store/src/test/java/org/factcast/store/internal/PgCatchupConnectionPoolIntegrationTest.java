/*
 * Copyright © 2017-2026 factcast.org
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
package org.factcast.store.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.factcast.core.Fact;
import org.factcast.core.FactStreamPosition;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.*;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.store.internal.listen.NudgeNotificationHandler;
import org.factcast.store.internal.listen.PgListener;
import org.factcast.test.IntegrationTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.*;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = PgTestConfiguration.class)
@TestPropertySource(
    properties = {
      "spring.datasource.tomcat.max-active=1",
      "spring.datasource.tomcat.max-wait=1000",
      "factcast.store.catchup-strategy=CURSOR"
    })
@Sql(scripts = "/wipe.sql", config = @SqlConfig(separator = "#"))
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@IntegrationTest
class PgCatchupConnectionPoolIntegrationTest {

  @Autowired FactStore store;
  @Autowired DataSource dataSource;

  // Catch-up needs no live notifications. Keep background polling out of the one-slot pool.
  @MockitoBean PgListener listener;
  @MockitoBean NudgeNotificationHandler nudgeHandler;

  @ParameterizedTest(name = "single-connection catch-up with fast-forward={0}")
  @ValueSource(booleans = {false, true})
  void filteredCatchupCompletesWithOneConnectionEvenWhenFastForwardIsNeeded(
      boolean needsFastForward) throws Exception {
    assertThat(dataSource.getMaxActive()).isEqualTo(1);
    Fact matching = Fact.builder().ns("pool-catchup").buildWithoutPayload();
    Fact latest =
        needsFastForward ? Fact.builder().ns("unrelated").buildWithoutPayload() : matching;
    store.publish(needsFastForward ? List.of(matching, latest) : List.of(matching));
    long latestSerial = store.serialOf(latest.id()).orElseThrow();
    List<Fact> received = new CopyOnWriteArrayList<>();
    List<FactStreamPosition> fastForwards = new CopyOnWriteArrayList<>();
    AtomicReference<Throwable> error = new AtomicReference<>();
    CountDownLatch finished = new CountDownLatch(1);
    FactObserver observer =
        new FactObserver() {
          @Override
          public void onNext(Fact fact) {
            received.add(fact);
          }

          @Override
          public void onFastForward(FactStreamPosition position) {
            fastForwards.add(position);
          }

          @Override
          public void onComplete() {
            finished.countDown();
          }

          @Override
          public void onError(Throwable exception) {
            error.set(exception);
            finished.countDown();
          }
        };

    // One catch-up in a one-slot pool deterministically reproduces the same condition as
    // concurrent catch-ups occupying every slot of a larger pool.
    try (Subscription subscription =
        store.subscribe(
            SubscriptionRequestTO.from(
                SubscriptionRequest.catchup(FactSpec.ns("pool-catchup")).fromScratch()),
            observer)) {
      assertThat(finished.await(5, TimeUnit.SECONDS)).as("catch-up must terminate").isTrue();
      assertThat(error.get()).as("catch-up must not borrow a second pool connection").isNull();
      subscription.awaitComplete(1000);
      assertThat(received).extracting(Fact::id).containsExactly(matching.id());
      if (needsFastForward) {
        assertThat(fastForwards).containsExactly(FactStreamPosition.of(latest.id(), latestSerial));
      } else {
        assertThat(fastForwards).isEmpty();
      }
    }
  }
}
