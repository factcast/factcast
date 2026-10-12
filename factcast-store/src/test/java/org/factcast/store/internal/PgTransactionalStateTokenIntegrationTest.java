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

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.factcast.core.Fact;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.store.TokenStore;
import org.factcast.store.internal.listen.PgConnectionSupplier;
import org.factcast.store.internal.lock.AdvisoryLocks;
import org.factcast.test.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@SpringJUnitConfig(classes = PgTestConfiguration.class)
@TestPropertySource(
    properties = {
      "spring.datasource.tomcat.max-active=1",
      "spring.datasource.tomcat.max-wait=2000"
    })
@Sql(scripts = "/wipe.sql", config = @SqlConfig(separator = "#"))
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@IntegrationTest
class PgTransactionalStateTokenIntegrationTest {

  private static final String NS = "transactional-state-token";

  @Autowired FactStore store;
  @Autowired TokenStore tokenStore;
  @Autowired org.apache.tomcat.jdbc.pool.DataSource dataSource;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager transactionManager;

  @Test
  void stateTokenCallsReuseOnlyAvailableConnection() {
    Fact fact = Fact.builder().ns(NS).type("existing").buildWithoutPayload();
    store.publish(List.of(fact));
    long serial = store.serialOf(fact.id()).orElseThrow();

    transaction()
        .executeWithoutResult(
            ignored -> {
              assertThat(dataSource.getActive()).isEqualTo(1);
              assertStateTokens(serial);
              assertThat(dataSource.getActive()).isEqualTo(1);
            });
  }

  @Test
  void concurrentStateTokenCallsCompleteWhenCallersOccupyEntirePool() throws Exception {
    dataSource.setMaxActive(3);
    CountDownLatch callersReady = new CountDownLatch(3);
    CountDownLatch startCalls = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(3);
    List<Future<?>> results = new ArrayList<>();
    try {
      for (int i = 0; i < 3; i++) {
        results.add(
            executor.submit(
                () ->
                    transaction()
                        .executeWithoutResult(
                            ignored -> {
                              callersReady.countDown();
                              awaitLatch(startCalls);
                              assertStateTokens(0);
                            })));
      }
      assertThat(callersReady.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(dataSource.getActive()).isEqualTo(3);
      startCalls.countDown();
      for (Future<?> result : results) {
        result.get(10, TimeUnit.SECONDS);
      }
    } finally {
      startCalls.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void stateTokenBarrierBlocksPublicationUntilCallerCommits() throws Exception {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try (var publisherConnection =
        new PgConnectionSupplier(dataSource).getUnpooledConnection("state-token-barrier-test")) {
      publisherConnection.setAutoCommit(false);
      int publisherPid = publisherConnection.getBackendPID();
      Fact fact = Fact.builder().ns(NS).type("after-state").buildWithoutPayload();
      AtomicReference<Future<?>> publication = new AtomicReference<>();
      transaction()
          .executeWithoutResult(
              ignored -> {
                assertStateTokens(0);
                publication.set(
                    executor.submit(
                        () -> {
                          try (var statement = publisherConnection.createStatement()) {
                            statement.execute(
                                "SELECT pg_advisory_xact_lock_shared("
                                    + AdvisoryLocks.PUBLISH.code()
                                    + ")");
                          }
                          try (var statement =
                              publisherConnection.prepareStatement(PgConstants.INSERT_FACT)) {
                            statement.setString(1, fact.jsonHeader());
                            statement.setString(2, fact.jsonPayload());
                            statement.executeUpdate();
                            publisherConnection.commit();
                          }
                          return null;
                        }));
                // Observe the actual advisory-lock wait, rather than a wait for a pool connection.
                // Poll on the caller's thread so the query uses its transaction-bound connection.
                await()
                    .pollInSameThread()
                    .atMost(Duration.ofSeconds(5))
                    .until(
                        () ->
                            Boolean.TRUE.equals(
                                jdbc.queryForObject(
                                    "SELECT EXISTS (SELECT 1 FROM pg_locks WHERE pid=? "
                                        + "AND locktype='advisory' AND granted=false)",
                                    Boolean.class,
                                    publisherPid)));
                assertThat(publication.get()).isNotDone();
              });

      publication.get().get(5, TimeUnit.SECONDS);
      assertThat(jdbc.queryForObject("SELECT count(*) FROM fact", Long.class)).isEqualTo(1);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void readOnlyCallerIsRejectedWithActionableMessage() {
    TransactionTemplate transaction = transaction();
    transaction.setReadOnly(true);
    assertUnsupportedCaller(transaction, "outside the read-only transaction");
  }

  @ParameterizedTest
  @ValueSource(
      ints = {
        TransactionDefinition.ISOLATION_REPEATABLE_READ,
        TransactionDefinition.ISOLATION_SERIALIZABLE
      })
  void snapshotIsolationCallerIsRejectedWithActionableMessage(int isolation) {
    TransactionTemplate transaction = transaction();
    transaction.setIsolationLevel(isolation);
    assertUnsupportedCaller(transaction, "READ_COMMITTED");
  }

  private void assertUnsupportedCaller(TransactionTemplate transaction, String message) {
    transaction.executeWithoutResult(
        ignored -> {
          assertThatThrownBy(() -> store.stateFor(List.of(FactSpec.ns(NS))))
              .isInstanceOf(IllegalTransactionStateException.class)
              .hasMessageContaining(message);
          assertThatThrownBy(() -> store.currentStateFor(List.of(FactSpec.ns(NS))))
              .isInstanceOf(IllegalTransactionStateException.class)
              .hasMessageContaining(message);
        });
  }

  private void assertStateTokens(long expectedSerial) {
    assertThat(
            tokenStore
                .get(store.stateFor(List.of(FactSpec.ns(NS))))
                .orElseThrow()
                .serialOfLastMatchingFact())
        .isEqualTo(expectedSerial);
    assertThat(
            tokenStore
                .get(store.currentStateFor(List.of(FactSpec.ns(NS))))
                .orElseThrow()
                .serialOfLastMatchingFact())
        .isEqualTo(expectedSerial);
  }

  private TransactionTemplate transaction() {
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    transaction.setTimeout(10);
    return transaction;
  }

  private static void awaitLatch(CountDownLatch latch) {
    try {
      assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError("Interrupted while waiting for state-token callers", e);
    }
  }
}
