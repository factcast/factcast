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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.factcast.core.DuplicateFactException;
import org.factcast.core.Fact;
import org.factcast.core.store.FactStore;
import org.factcast.test.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.*;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

@SpringJUnitConfig(classes = PgTestConfiguration.class)
@TestPropertySource(properties = "factcast.store.publish-batched.enabled=true")
@Sql(scripts = "/wipe.sql", config = @SqlConfig(separator = "#"))
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@IntegrationTest
class PgBatchedPublicationIntegrationTest {

  private static final String NS = "batched-atomicity";

  @Autowired FactStore store;
  @MockitoSpyBean JdbcTemplate jdbc;

  @Test
  void duplicateRollsBackOnlyItsPublicationAndOtherPublicationsInTheSameBatchCommit()
      throws Exception {
    Fact existing = fact();
    store.publish(List.of(existing));

    List<Fact> firstPublication = List.of(fact(), fact());
    // Put the duplicate last so earlier inserts must roll back, including when the driver
    // rewrites the first two inserts into one statement and executes the third separately.
    List<Fact> failingPublication = List.of(fact(), fact(), existing);
    List<Fact> lastPublication = List.of(fact());
    PgFactStore target = AopTestUtils.getUltimateTargetObject(store);
    PublicationQueue queue = (PublicationQueue) ReflectionTestUtils.getField(target, "queue");
    assertThat(queue).isNotNull();

    ExecutorService publishers = Executors.newFixedThreadPool(3);
    try {
      Future<?> first;
      Future<?> failing;
      Future<?> last;
      // flush() holds this monitor; enqueueing uses a different one. Keep the flusher paused
      // until all three API calls are queued, so this exercises one combined batch reliably.
      synchronized (queue) {
        first = publishers.submit(() -> store.publish(firstPublication));
        await().atMost(Duration.ofSeconds(10)).until(() -> queue.queueSize() == 1);
        failing = publishers.submit(() -> store.publish(failingPublication));
        await().atMost(Duration.ofSeconds(10)).until(() -> queue.queueSize() == 2);
        last = publishers.submit(() -> store.publish(lastPublication));
        await().atMost(Duration.ofSeconds(10)).until(() -> queue.queueSize() == 3);
        assertThat(first.isDone()).isFalse();
        assertThat(failing.isDone()).isFalse();
        assertThat(last.isDone()).isFalse();
      }

      // These futures wrap the public publish calls, including waiting for their commits.
      first.get(10, TimeUnit.SECONDS);
      last.get(10, TimeUnit.SECONDS);
      assertThatThrownBy(() -> failing.get(10, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(DuplicateFactException.class);

      verify(jdbc)
          .batchUpdate(
              eq(PgConstants.INSERT_FACT),
              eq(
                  List.of(
                      firstPublication.get(0),
                      firstPublication.get(1),
                      failingPublication.get(0),
                      failingPublication.get(1),
                      existing,
                      lastPublication.get(0))),
              eq(Integer.MAX_VALUE),
              any());

      // A separate connection sees only committed facts, never a partial failed publication.
      assertThat(
              jdbc.query(
                  "SELECT (header ->> 'id')::uuid FROM fact WHERE header ->> 'ns' = ?",
                  (rs, rowNum) -> rs.getObject(1, UUID.class),
                  NS))
          .containsExactlyInAnyOrder(
              existing.id(),
              firstPublication.get(0).id(),
              firstPublication.get(1).id(),
              lastPublication.get(0).id());
    } finally {
      publishers.shutdownNow();
      assertThat(publishers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }
  }

  private static Fact fact() {
    return Fact.builder().ns(NS).buildWithoutPayload();
  }
}
