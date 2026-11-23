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
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.lock.FactTableWriteLock;
import org.factcast.test.IntegrationTest;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.*;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;

@SpringJUnitConfig(
    classes = {
      PgTestConfiguration.class,
      PgPublicationTransactionIntegrationTest.LockTimeoutConfiguration.class
    })
@TestPropertySource(properties = "factcast.store.publish-batched.flush-delay-millis=0")
@Sql(scripts = "/wipe.sql", config = @SqlConfig(separator = "#"))
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@IntegrationTest
class PgPublicationTransactionIntegrationTest {

  @Autowired FactStore store;
  @Autowired StoreConfigurationProperties properties;
  @Autowired PlatformTransactionManager transactionManager;

  @ParameterizedTest(name = "conditional publication with batching={0}")
  @ValueSource(booleans = {false, true})
  @Timeout(10)
  void unchangedTokenCanBePublishedWithoutWaitingForItsOwnTransactionLock(boolean batching) {
    properties.getPublishBatched().setEnabled(batching);
    var token = store.stateFor(List.of(FactSpec.ns("conditional-transaction")));
    Fact fact = Fact.builder().ns("conditional-transaction").buildWithoutPayload();

    // Token validation and insertion must share the transaction that holds the exclusive lock.
    assertThat(store.publishIfUnchanged(List.of(fact), Optional.of(token))).isTrue();

    assertThat(store.fetchById(fact.id())).isPresent();
  }

  @Configuration(proxyBeanMethods = false)
  static class LockTimeoutConfiguration {
    @Bean
    @Primary
    FactTableWriteLock boundedPublicationLock(
        @Qualifier("factTableWriteLock") FactTableWriteLock delegate, JdbcTemplate jdbc) {
      return () -> {
        // Keep real PostgreSQL locking, but turn a self-deadlock into a bounded test failure.
        // SET LOCAL also covers queue-worker transactions without altering later pool users.
        jdbc.execute("SET LOCAL lock_timeout = '1s'");
        delegate.acquireExclusiveTXLock();
      };
    }
  }
}
