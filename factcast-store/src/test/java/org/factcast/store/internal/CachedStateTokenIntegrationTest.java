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
import javax.sql.DataSource;
import org.factcast.core.Fact;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.store.StateToken;
import org.factcast.core.store.TokenStore;
import org.factcast.core.subscription.FactStreamHorizon;
import org.factcast.store.internal.horizon.FactStreamHorizonProvider;
import org.factcast.store.internal.horizon.PgFactStreamHorizonProvider;
import org.factcast.store.internal.listen.NudgeNotificationHandler;
import org.factcast.store.internal.lock.FactTableWriteLock;
import org.factcast.test.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;

@SpringJUnitConfig(classes = PgTestConfiguration.class)
@Sql(scripts = "/wipe.sql", config = @SqlConfig(separator = "#"))
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@IntegrationTest
class CachedStateTokenIntegrationTest {

  private static final List<FactSpec> SPECS = List.of(FactSpec.ns("cached-token"));

  @Autowired FactStore store;
  @Autowired TokenStore tokenStore;
  @Autowired FactStreamHorizonProvider horizonProvider;
  @Autowired DataSource dataSource;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired FactTableWriteLock writeLock;
  @Autowired PgMetrics metrics;
  @Autowired PlatformTransactionManager transactionManager;

  // Keep refreshes explicit so these tests do not race with notification-driven advancement.
  @MockitoBean NudgeNotificationHandler nudgeHandler;

  @BeforeEach
  void refreshHorizonAfterDatabaseWipe() {
    horizonProvider.advance();
  }

  @Test
  void staleTokensRejectAlreadyCommittedMatchingFactsUntilCacheRefresh() {
    Fact initial = Fact.builder().ns("cached-token").buildWithoutPayload();
    store.publish(List.of(initial));
    FactStreamHorizon cached = horizonProvider.advance();
    store.publish(List.of(Fact.builder().ns("cached-token").buildWithoutPayload()));
    Fact decision = Fact.builder().ns("cached-token").buildWithoutPayload();

    for (StateToken token : List.of(store.stateFor(SPECS), store.currentStateFor(SPECS))) {
      assertThat(tokenStore.get(token).orElseThrow().serialOfLastMatchingFact())
          .isEqualTo(cached.factSerial());
      assertThat(store.publishIfUnchanged(List.of(decision), Optional.of(token))).isFalse();
      assertThat(tokenStore.get(token)).isEmpty();
    }
    assertThat(store.fetchById(decision.id())).isEmpty();
    assertThat(horizonProvider.currentPrimary()).isEqualTo(cached);

    horizonProvider.advance();
    StateToken refreshedToken = store.currentStateFor(SPECS);
    assertThat(store.publishIfUnchanged(List.of(decision), Optional.of(refreshedToken))).isTrue();
    assertThat(store.fetchById(decision.id())).isPresent();
  }

  @Test
  void unrelatedFactsBeyondAnEmptyCachedHorizonDoNotCauseRejection() {
    store.publish(List.of(Fact.builder().ns("unrelated").buildWithoutPayload()));
    Fact decision = Fact.builder().ns("cached-token").buildWithoutPayload();
    StateToken token = store.currentStateFor(SPECS);

    assertThat(tokenStore.get(token).orElseThrow().serialOfLastMatchingFact()).isZero();
    assertThat(horizonProvider.currentPrimary()).isEqualTo(FactStreamHorizon.empty());
    assertThat(store.publishIfUnchanged(List.of(decision), Optional.of(token))).isTrue();
    assertThat(store.fetchById(decision.id())).isPresent();
  }

  @Test
  void tokensUseLocalCacheEvenWhenAnotherProviderHasAdvancedThePersistedHorizon() {
    FactStreamHorizon cached = horizonProvider.currentPrimary();
    Fact matching = Fact.builder().ns("cached-token").buildWithoutPayload();
    store.publish(List.of(matching));

    FactStreamHorizonProvider otherProvider =
        new PgFactStreamHorizonProvider(
            dataSource, jdbcTemplate, writeLock, metrics, transactionManager);
    FactStreamHorizon persisted = otherProvider.advance();
    assertThat(persisted.factSerial()).isGreaterThan(cached.factSerial());

    for (StateToken token : List.of(store.stateFor(SPECS), store.currentStateFor(SPECS))) {
      assertThat(tokenStore.get(token).orElseThrow().serialOfLastMatchingFact())
          .isEqualTo(cached.factSerial());
      assertThat(store.publishIfUnchanged(List.of(), Optional.of(token))).isFalse();
    }
    assertThat(horizonProvider.currentPrimary()).isEqualTo(cached);
    assertThat(horizonProvider.readFrom(dataSource)).isEqualTo(persisted);
  }
}
