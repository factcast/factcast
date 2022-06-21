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
package org.factcast.store.internal.tail;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

import javax.sql.DataSource;

import org.factcast.core.store.FactStore;
import org.factcast.store.internal.PgTestConfiguration;
import org.factcast.store.test.IntegrationTest;
import org.factcast.test.Slf4jHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import lombok.SneakyThrows;
import slf4jtest.TestLogger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.factcast.store.internal.PgConstants.*;

@ContextConfiguration(classes = {PgTestConfiguration.class})
@ExtendWith(SpringExtension.class)
@IntegrationTest
@Sql(scripts = "/wipe.sql", config = @SqlConfig(separator = "#"))
class PGTailIndexManagerImplIntTest {

  @Autowired FactStore fs;
  @Autowired PGTailIndexManager tailManager;
  @Autowired DataSource dataSource;
  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  void happyPathWithoutExceptions() {
    var now = System.currentTimeMillis();

    tailManager.triggerTailCreation();

    assertThat(indexFound(now)).isTrue();
  }

  @Test
  @SneakyThrows
  @DisabledForJreRange(min = JRE.JAVA_9)
  void doesNotCreateIndexConcurrently() {
    TestLogger logger = Slf4jHelper.replaceLogger(tailManager);

    var c = dataSource.getConnection();
    c.setAutoCommit(false);

    ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(2);

    try {
      var s = c.createStatement();
      s.setFetchSize(1);
      var resultSet = s.executeQuery("select * from fact limit 2;");
      while (resultSet.next()) {}

      var before = System.currentTimeMillis();

      assertThat(indexFound(before)).isFalse();

      // from this moment on, there should be an active tx on the fact table, and the first
      // call of the index creation should block:
      Future<?> blockingIndexCreation = executor.submit(tailManager::triggerTailCreation);

      var waitUntil = System.currentTimeMillis() + 20_000;

      do {
        Thread.sleep(50);
        if (waitUntil < System.currentTimeMillis()) {
          throw new RuntimeException("Waited for 20 seconds, but no index found");
        }
      } while (!indexFound(before));

      executor.submit(tailManager::triggerTailCreation).get(1, TimeUnit.MINUTES);

      // should still be running!
      assertThat(blockingIndexCreation.isDone()).isFalse();
      assertThat(blockingIndexCreation.isCancelled()).isFalse();

      // while the index creation is still running, all indices should still be invalid
      assertThat(allIndicesInvalid(before)).isTrue();

      // we should only see that from the second call, but not from the blocking one
      assertThat(logger.lines()).filteredOn("text", "Done with tail index maintenance").hasSize(1);

      s.close();
      c.commit();

      // after closing transaction, this should terminate soon
      blockingIndexCreation.get(1, TimeUnit.MINUTES);

      // we should finally have one index, and it should be valid
      assertOnlyOneIndexAndIsValid(before);

      assertThat(logger.lines()).filteredOn("text", "Triggering tail index maintenance").hasSize(2);
      assertThat(logger.lines())
          .filteredOn(l -> l.text.startsWith("Creating tail index"))
          .hasSize(1);

      // No exception should have happened
      assertThat(logger.lines()).filteredOn(l -> l.text.startsWith("Error creating")).isEmpty();
      assertThat(logger.lines()).filteredOn(l -> l.text.startsWith("After error")).isEmpty();

      // now we should see it twice
      assertThat(logger.lines()).filteredOn("text", "Done with tail index maintenance").hasSize(2);

    } catch (RuntimeException e) {
      c.rollback();
      throw e;
    }
  }

  private void assertOnlyOneIndexAndIsValid(long epoc) {
    List<Map<String, Object>> indices =
        jdbcTemplate.queryForList(LIST_FACT_INDEXES_WITH_VALIDATION).stream()
            .filter(m -> m.get(INDEX_NAME_COLUMN).toString().compareTo(tailIndexName(epoc)) > 0)
            .collect(Collectors.toList());

    assertThat(indices).hasSize(1).extracting(m -> m.get(VALID_COLUMN)).containsExactly(IS_VALID);
  }

  private boolean allIndicesInvalid(long epoc) {
    return jdbcTemplate.queryForList(LIST_FACT_INDEXES_WITH_VALIDATION).stream()
        .filter(m -> m.get(INDEX_NAME_COLUMN).toString().compareTo(tailIndexName(epoc)) > 0)
        .allMatch(r -> r.get(VALID_COLUMN).equals(IS_INVALID));
  }

  private boolean indexFound(long before) {
    return jdbcTemplate.queryForList(LIST_FACT_INDEXES_WITH_VALIDATION).stream()
        .anyMatch(m -> m.get(INDEX_NAME_COLUMN).toString().compareTo(tailIndexName(before)) >= 0);
  }
}
