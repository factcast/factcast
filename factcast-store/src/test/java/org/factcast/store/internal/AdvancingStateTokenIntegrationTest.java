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
import org.factcast.core.Fact;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.store.StateToken;
import org.factcast.core.store.TokenStore;
import org.factcast.core.subscription.FactStreamHorizon;
import org.factcast.store.internal.horizon.FactStreamHorizonProvider;
import org.factcast.store.internal.listen.NudgeNotificationHandler;
import org.factcast.test.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringJUnitConfig(classes = PgTestConfiguration.class)
@TestPropertySource(properties = "factcast.store.lean-state-token.enabled=false")
@Sql(scripts = "/wipe.sql", config = @SqlConfig(separator = "#"))
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@IntegrationTest
class AdvancingStateTokenIntegrationTest {

  @Autowired FactStore store;
  @Autowired TokenStore tokenStore;
  @Autowired FactStreamHorizonProvider horizonProvider;
  @Autowired PlatformTransactionManager transactionManager;

  // Only token creation should refresh the horizon after publication in these tests.
  @MockitoBean NudgeNotificationHandler nudgeHandler;

  @BeforeEach
  void refreshHorizonAfterDatabaseWipe() {
    horizonProvider.advance();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void tokenCreationRefreshesStaleHorizonAndAcceptsUnchangedState(boolean useCurrentStateFor) {
    List<FactSpec> specs = List.of(FactSpec.ns("advancing-token"));
    FactStreamHorizon cached = horizonProvider.currentPrimary();
    store.publish(List.of(Fact.builder().ns("advancing-token").buildWithoutPayload()));
    assertThat(horizonProvider.currentPrimary()).isEqualTo(cached);

    StateToken token = useCurrentStateFor ? store.currentStateFor(specs) : store.stateFor(specs);

    assertThat(tokenStore.get(token).orElseThrow().serialOfLastMatchingFact())
        .isGreaterThan(cached.factSerial())
        .isEqualTo(horizonProvider.currentPrimary().factSerial());
    Fact conditionalFact = Fact.builder().ns("advancing-token").buildWithoutPayload();
    assertThat(store.publishIfUnchanged(List.of(conditionalFact), Optional.of(token))).isTrue();
    assertThat(store.fetchById(conditionalFact.id())).isPresent();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void earlierPublicationInCallerTransactionDoesNotInvalidateLaterToken(
      boolean useCurrentStateFor) {
    // 1. wipe.sql empties db, so "cached" starts at serial 0
    // 2. inside TX, publishing "initial" assigns serial 1 and holds the exclusive lock
    // 3. stateFor/currentStateFor calls advance(): "tokenSerial" must be 1, while the shared cache
    //    stays at 0 until commit
    // 4. publishIfUnchanged must accept "conditionalFact" because "initial" predates "token".
    // Reading
    //    serial 0 from "cached" instead would make validation reject our own "initial" fact at 1
    // 5. after commit, both "initial" and "conditionalFact" are visible; the shared horizon points
    // to
    //    "initial", since token creation advanced it before "conditionalFact" was published
    List<FactSpec> specs = List.of(FactSpec.ns("advancing-token"));
    FactStreamHorizon cached = horizonProvider.currentPrimary();
    Fact initial = Fact.builder().ns("advancing-token").buildWithoutPayload();
    Fact conditionalFact = Fact.builder().ns("advancing-token").buildWithoutPayload();
    TransactionTemplate tx = new TransactionTemplate(transactionManager);
    tx.setTimeout(5);

    tx.executeWithoutResult(
        ignored -> {
          store.publish(List.of(initial));
          StateToken token =
              useCurrentStateFor ? store.currentStateFor(specs) : store.stateFor(specs);
          long tokenSerial = tokenStore.get(token).orElseThrow().serialOfLastMatchingFact();

          // shared cache must not expose this tx's uncommitted horizon
          assertThat(horizonProvider.currentPrimary()).isEqualTo(cached);
          assertThat(store.publishIfUnchanged(List.of(conditionalFact), Optional.of(token)))
              .as(
                  "an earlier publication in the caller transaction must not invalidate a later token")
              .isTrue();
          assertThat(tokenSerial).isEqualTo(store.serialOf(initial.id()).orElseThrow());
          assertThat(horizonProvider.currentPrimary()).isEqualTo(cached);
        });

    assertThat(store.fetchById(initial.id())).isPresent();
    assertThat(store.fetchById(conditionalFact.id())).isPresent();
    assertThat(horizonProvider.currentPrimary().factId()).isEqualTo(initial.id());
  }
}
