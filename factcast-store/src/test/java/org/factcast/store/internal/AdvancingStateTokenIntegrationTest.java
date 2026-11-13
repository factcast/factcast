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

@SpringJUnitConfig(classes = PgTestConfiguration.class)
@TestPropertySource(properties = "factcast.store.lean-state-token.enabled=false")
@Sql(scripts = "/wipe.sql", config = @SqlConfig(separator = "#"))
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@IntegrationTest
class AdvancingStateTokenIntegrationTest {

  @Autowired FactStore store;
  @Autowired TokenStore tokenStore;
  @Autowired FactStreamHorizonProvider horizonProvider;

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
    Fact decision = Fact.builder().ns("advancing-token").buildWithoutPayload();
    assertThat(store.publishIfUnchanged(List.of(decision), Optional.of(token))).isTrue();
    assertThat(store.fetchById(decision.id())).isPresent();
  }
}
