package org.factcast.store.internal.tail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.factcast.store.internal.PgConstants.INDEX_NAME_COLUMN;
import static org.factcast.store.internal.PgConstants.LIST_FACT_INDEXES_WITH_VALIDATION;
import static org.factcast.store.internal.PgConstants.tailIndexName;

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.factcast.core.store.FactStore;
import org.factcast.store.internal.PgTestConfiguration;
import org.factcast.store.test.IntegrationTest;
import org.factcast.test.Slf4jHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import lombok.SneakyThrows;
import slf4jtest.TestLogger;

@ContextConfiguration(classes = {PgTestConfiguration.class})
@Sql(scripts = "/test_schema.sql", config = @SqlConfig(separator = "#"))
@ExtendWith(SpringExtension.class)
@IntegrationTest
class PGTailIndexManagerImplIntTest {

  @Autowired FactStore fs;
  @Autowired PGTailIndexManager tailManager;
  @Autowired DataSource dataSource;
  @Autowired JdbcTemplate jdbcTemplate;

  // TODO: use logger
  TestLogger logger;

  @BeforeEach
  void setup() {
    logger = Slf4jHelper.replaceLogger(tailManager);
  }

  @Test
  void happyPathWithoutExceptions() {
    var now = System.currentTimeMillis();

    tailManager.triggerTailCreation();

    assertThat(indexFound(now)).isTrue();
  }

  @Test
  @SneakyThrows
  void doesNotCreateIndexConcurrently() {
    var c = dataSource.getConnection();
    c.setAutoCommit(false);

    ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(2);

    try {
      var s = c.createStatement();
      s.setFetchSize(1);
      var resultSet = s.executeQuery("select * from fact limit 2;");

      var before = System.currentTimeMillis();

      assertThat(indexFound(before)).isFalse();

      // from this moment on, there should be an active tx on the fact table, and the first
      // call of the index creation should block:
      Future<?> blockingIndexCreation = executor.submit(tailManager::triggerTailCreation);

      var waitUntil = System.currentTimeMillis() + 20_000;

      while (!indexFound(before)) {
        Thread.sleep(50);
        if (waitUntil < System.currentTimeMillis()) {
          throw new RuntimeException("Waited for 20 seconds, but no index found");
        }
      }

      executor.submit(tailManager::triggerTailCreation).get(1, TimeUnit.MINUTES);

      // should still be running!
      assertThat(blockingIndexCreation.isDone()).isFalse();
      assertThat(blockingIndexCreation.isCancelled()).isFalse();

      s.close();
      c.commit();

      blockingIndexCreation.get(1, TimeUnit.MINUTES);

      assertThat(indexFound(before)).isTrue();

    } catch (RuntimeException e) {
      c.rollback();
      throw e;
    }
  }

  private boolean indexFound(long before) {
    return jdbcTemplate.queryForList(LIST_FACT_INDEXES_WITH_VALIDATION).stream()
        .anyMatch(m -> m.get(INDEX_NAME_COLUMN).toString().compareTo(tailIndexName(before)) > 0);
  }
}
