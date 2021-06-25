package org.factcast.itests.factus;

import com.google.common.base.Stopwatch;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.factcast.factus.Factus;
import org.factcast.factus.Handler;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.projection.ManagedProjection;
import org.factcast.factus.projection.WriterToken;
import org.factcast.factus.spring.tx.AbstractSpringTxManagedProjection;
import org.factcast.factus.spring.tx.SpringTransactional;
import org.factcast.itests.factus.event.UserCreated;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.util.UUID.*;

@SpringBootTest
@ContextConfiguration(classes = {Application.class})
@EnableAutoConfiguration
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Slf4j
class SpringTransactionalIPerfTest extends AbstractFactCastIntegrationTest {
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired PlatformTransactionManager platformTransactionManager;
  @Autowired Factus factus;

  public static PostgreSQLContainer postgreSQLContainer =
      new PostgreSQLContainer("postgres:11.5").withPassword("sa").withUsername("sa");

  static {
    postgreSQLContainer.start();

    System.setProperty("spring.datasource.url", postgreSQLContainer.getJdbcUrl());
    System.setProperty("spring.datasource.username", postgreSQLContainer.getUsername());
    System.setProperty("spring.datasource.password", postgreSQLContainer.getPassword());
  }

  private static List<Long> springTxManagedBS1 = new ArrayList<>();

  private static List<Long> springTxManagedBS50 = new ArrayList<>();

  private static List<Long> springTxManagedBS100 = new ArrayList<>();

  private static List<Long> customManaged = new ArrayList<>();

  static final int NUMBER_OF_EVENTS = 10000;

  @BeforeEach
  void setUp() {
    createTables();

    val l = new ArrayList<EventObject>(NUMBER_OF_EVENTS);
    for (int i = 0; i < NUMBER_OF_EVENTS; i++) {
      l.add(new UserCreated(randomUUID(), "" + i));
    }
    log.info("publishing {} Events ", NUMBER_OF_EVENTS);
    factus.publish(l);
  }

  @RepeatedTest(10)
  public void springTxBatchSize1() {
    val s = new BatchSize1(platformTransactionManager, jdbcTemplate);

    val sw = Stopwatch.createStarted();
    factus.update(s);
    springTxManagedBS1.add(sw.stop().elapsed(TimeUnit.MILLISECONDS));
  }

  @RepeatedTest(10)
  public void springTxBatchSize50() {
    val s = new BatchSize50(platformTransactionManager, jdbcTemplate);

    val sw = Stopwatch.createStarted();
    factus.update(s);
    springTxManagedBS50.add(sw.stop().elapsed(TimeUnit.MILLISECONDS));
  }

  @RepeatedTest(10)
  public void springTxBatchSize100() {
    val s = new BatchSize100(platformTransactionManager, jdbcTemplate);

    val sw = Stopwatch.createStarted();
    factus.update(s);
    springTxManagedBS100.add(sw.stop().elapsed(TimeUnit.MILLISECONDS));
  }

  @RepeatedTest(10)
  public void custom() {
    val s =
        new CustomUserProjection(jdbcTemplate, new TransactionTemplate(platformTransactionManager));

    val sw = Stopwatch.createStarted();
    factus.update(s);
    customManaged.add(sw.stop().elapsed(TimeUnit.MILLISECONDS));
  }

  @AfterAll
  static void after() {
    log.debug("Number of events: " + NUMBER_OF_EVENTS);
    log.debug(
        "CustomManaged   (BS1)   results (ms): "
            + customManaged.stream().map(String::valueOf).collect(Collectors.joining(", ")));

    log.debug(
        "SpringTxManaged (BS1)   results (ms): "
            + springTxManagedBS1.stream().map(String::valueOf).collect(Collectors.joining(", ")));

    log.debug(
        "SpringTxManaged (BS50)  results (ms): "
            + springTxManagedBS50.stream().map(String::valueOf).collect(Collectors.joining(", ")));

    log.debug(
        "SpringTxManaged (BS100) results (ms): "
            + springTxManagedBS100.stream().map(String::valueOf).collect(Collectors.joining(", ")));
  }

  private void createTables() {
    jdbcTemplate.execute("DROP TABLE IF EXISTS managed_projection;");
    jdbcTemplate.execute(
        "CREATE TABLE managed_projection (\n"
            + "\n"
            + "    name  varchar(255),\n"
            + "    state UUID,\n"
            + "\n"
            + "    PRIMARY KEY (name)\n"
            + ");");

    jdbcTemplate.execute("DROP TABLE IF EXISTS users;");
    jdbcTemplate.execute(
        "CREATE TABLE users (\n"
            + "\n"
            + "    name  varchar(255),\n"
            + "    id UUID,\n"
            + "\n"
            + "    PRIMARY KEY (id)\n"
            + ");");
  }

  @Slf4j
  static class CustomUserProjection implements ManagedProjection {
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    @Getter private int stateModifications = 0;

    @Getter private final Set<String> txSeen = new HashSet<>();

    public CustomUserProjection(
        JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate) {
      this.jdbcTemplate = jdbcTemplate;
      this.transactionTemplate = transactionTemplate;
    }

    @Handler
    void apply(UserCreated e) {
      jdbcTemplate.update(
          "INSERT INTO users (name,id) VALUES (?,?);", e.userName(), e.aggregateId());
    }

    @Override
    public UUID state() {
      try {
        return jdbcTemplate.queryForObject(
            "SELECT state FROM managed_projection WHERE name = ?", UUID.class, "foo");
      } catch (IncorrectResultSizeDataAccessException e) {
        // no state yet, just return null
        return null;
      }
    }

    @Override
    public void state(@NonNull UUID state) {
      jdbcTemplate.update(
          "INSERT INTO managed_projection (name, state) VALUES (?, ?) ON CONFLICT (name) DO UPDATE SET state = ?",
          "foo",
          state,
          state);
    }

    @Override
    public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
      return () -> {};
    }

    @Override
    public void executeUpdate(@NonNull Runnable update) {
      transactionTemplate.executeWithoutResult(txStatus -> update.run());
    }
  }

  @SpringTransactional(size = 1)
  static class BatchSize1 extends SpringTxUserProjection {
    public BatchSize1(
        @NonNull PlatformTransactionManager platformTransactionManager, JdbcTemplate jdbcTemplate) {
      super(platformTransactionManager, jdbcTemplate);
    }
  }

  @SpringTransactional(size = 50)
  static class BatchSize50 extends SpringTxUserProjection {
    public BatchSize50(
        @NonNull PlatformTransactionManager platformTransactionManager, JdbcTemplate jdbcTemplate) {
      super(platformTransactionManager, jdbcTemplate);
    }
  }

  @SpringTransactional(size = 100)
  static class BatchSize100 extends SpringTxUserProjection {
    public BatchSize100(
        @NonNull PlatformTransactionManager platformTransactionManager, JdbcTemplate jdbcTemplate) {
      super(platformTransactionManager, jdbcTemplate);
    }
  }

  @Slf4j
  abstract static class SpringTxUserProjection extends AbstractSpringTxManagedProjection {
    private final JdbcTemplate jdbcTemplate;
    @Getter private int stateModifications = 0;

    @Getter private final Set<String> txSeen = new HashSet<>();

    public SpringTxUserProjection(
        @NonNull PlatformTransactionManager platformTransactionManager, JdbcTemplate jdbcTemplate) {
      super(platformTransactionManager);
      this.jdbcTemplate = jdbcTemplate;
    }

    @Handler
    void apply(UserCreated e) {
      jdbcTemplate.update(
          "INSERT INTO users (name,id) VALUES (?,?);", e.userName(), e.aggregateId());
    }

    @Override
    public UUID state() {
      try {
        return jdbcTemplate.queryForObject(
            "SELECT state FROM managed_projection WHERE name = ?", UUID.class, "foo");
      } catch (IncorrectResultSizeDataAccessException e) {
        // no state yet, just return null
        return null;
      }
    }

    @Override
    public void state(@NonNull UUID state) {
      jdbcTemplate.update(
          "INSERT INTO managed_projection (name, state) VALUES (?, ?) ON CONFLICT (name) DO UPDATE SET state = ?",
          "foo",
          state,
          state);
    }

    @Override
    public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
      return () -> {};
    }
  }
}
