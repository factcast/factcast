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
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.factcast.core.Fact;
import org.factcast.core.lock.Attempt;
import org.factcast.core.lock.WithOptimisticLock;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.store.State;
import org.factcast.core.store.StateToken;
import org.factcast.core.store.TokenStore;
import org.factcast.core.subscription.*;
import org.factcast.test.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = PgTestConfiguration.class)
@Sql(scripts = "/wipe.sql", config = @SqlConfig(separator = "#"))
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@IntegrationTest
final class StateTokenIntegrationTest {

  private static final String NS = "state-token";

  @Autowired FactStore store;
  @Autowired TokenStore tokenStore;

  @Test
  void tokenWithoutMatchesIgnoresUnrelatedFactsAndDetectsLaterChanges() {
    Fact unrelated = Fact.builder().ns("unrelated").buildWithoutPayload();
    store.publish(List.of(unrelated));
    long latestSerial = store.serialOf(unrelated.id()).orElseThrow();
    List<FactSpec> specs = List.of(FactSpec.ns(NS));

    StateToken unchangedToken = store.stateFor(specs);
    StateToken changedToken = store.stateFor(specs);
    State state = tokenStore.get(unchangedToken).orElseThrow();
    assertThat(state.serialOfLastMatchingFact()).isEqualTo(latestSerial);

    store.publish(List.of(Fact.builder().ns("unrelated").buildWithoutPayload()));
    assertThat(store.publishIfUnchanged(List.of(), Optional.of(unchangedToken))).isTrue();

    store.publish(List.of(Fact.builder().ns(NS).buildWithoutPayload()));
    assertThat(store.publishIfUnchanged(List.of(), Optional.of(changedToken))).isFalse();
  }

  @Test
  void tokenCapturedAfterProjectionCatchupCannotDetectTheEarlierGap() {
    List<FactSpec> specs = List.of(FactSpec.ns(NS));
    store.publish(List.of(Fact.builder().ns(NS).buildWithoutPayload()));
    List<Fact> projection = new CopyOnWriteArrayList<>();
    store
        .subscribe(
            SubscriptionRequestTO.from(SubscriptionRequest.catchup(specs).fromScratch()),
            projection::add)
        .awaitComplete();
    assertThat(projection).hasSize(1);

    store.publish(
        List.of(
            Fact.builder().ns(NS).buildWithoutPayload(),
            Fact.builder().ns(NS).buildWithoutPayload(),
            Fact.builder().ns(NS).buildWithoutPayload()));
    StateToken tooLate = store.currentStateFor(specs);
    Fact staleDecision = Fact.builder().ns(NS).build("{\"observed\":" + projection.size() + "}");

    // A token protects only changes after its capture, not the client's earlier projection read.
    assertThat(store.publishIfUnchanged(List.of(staleDecision), Optional.of(tooLate))).isTrue();
    assertThat(projection).hasSize(1);
    assertThat(store.fetchById(staleDecision.id())).isPresent();
  }

  @Test
  void optimisticAttemptRefreshesAgainAfterConflictBeforePublishingDecision() throws Exception {
    List<FactSpec> specs = List.of(FactSpec.ns(NS));
    store.publish(List.of(Fact.builder().ns(NS).buildWithoutPayload()));
    AtomicInteger attempts = new AtomicInteger();
    List<Fact> projection = new CopyOnWriteArrayList<>();

    var result =
        new WithOptimisticLock(store, specs)
            .retry(2)
            .attempt(
                () -> {
                  projection.clear();
                  store
                      .subscribe(
                          SubscriptionRequestTO.from(
                              SubscriptionRequest.catchup(specs).fromScratch()),
                          projection::add)
                      .awaitComplete();
                  if (attempts.incrementAndGet() == 1) {
                    assertThat(projection).hasSize(1);
                    store.publish(List.of(Fact.builder().ns(NS).buildWithoutPayload()));
                  }
                  return Attempt.publish(
                      Fact.builder().ns(NS).build("{\"observed\":" + projection.size() + "}"));
                });

    assertThat(attempts).hasValue(2);
    assertThat(projection).hasSize(2);
    assertThat(result.publishedFacts())
        .singleElement()
        .satisfies(fact -> assertThat(fact.jsonPayload()).isEqualTo("{\"observed\":2}"));
  }
}
