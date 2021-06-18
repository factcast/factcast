package org.factcast.itests.factus;

import static java.util.UUID.*;
import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.util.*;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.factcast.factus.Factus;
import org.factcast.factus.Handler;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.projection.WriterToken;
import org.factcast.factus.spring.tx.AbstractSpringTxManagedProjection;
import org.factcast.factus.spring.tx.AbstractSpringTxSubscribedProjection;
import org.factcast.factus.spring.tx.SpringTransactional;
import org.factcast.itests.factus.event.UserCreated;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@ContextConfiguration(classes = {Application.class})
@EnableAutoConfiguration
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Slf4j
class SpringTransactionalITest extends AbstractFactCastIntegrationTest {
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

  final int NUMBER_OF_EVENTS = 10;

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

  @Nested
  class Managed {
    @Test
    public void testBulkSize3() {
      val s = new BulkSize3Projection(platformTransactionManager, jdbcTemplate);
      factus.update(s);

      assertThat(s.stateModifications()).isEqualTo(4);
      assertThat(s.txSeen()).hasSize(4);
      assertThat(getUsers()).isEqualTo(NUMBER_OF_EVENTS);
    }

    @Test
    public void testBulkSize5() {
      val s = new BulkSize5Projection(platformTransactionManager, jdbcTemplate);
      factus.update(s);

      assertThat(s.stateModifications()).isEqualTo(2);
      assertThat(s.txSeen()).hasSize(2);
      assertThat(getUsers()).isEqualTo(NUMBER_OF_EVENTS);
    }

    @Test
    public void testBulkSize10() {
      val s = new BulkSize10Projection(platformTransactionManager, jdbcTemplate);
      factus.update(s);

      assertThat(s.stateModifications()).isEqualTo(1);
      assertThat(s.txSeen()).hasSize(1);
      assertThat(getUsers()).isEqualTo(NUMBER_OF_EVENTS);
    }

    @Test
    public void testBulkSize20() {
      val s = new BulkSize20Projection(platformTransactionManager, jdbcTemplate);
      factus.update(s);

      assertThat(s.stateModifications()).isEqualTo(1);
      assertThat(s.txSeen()).hasSize(1);
      assertThat(getUsers()).isEqualTo(NUMBER_OF_EVENTS);
    }

    @Test
    public void testBulkApplyTimeout() {
      val s = new SpringTxProjectionTimeout(platformTransactionManager, jdbcTemplate);
      factus.update(s);

      assertThat(s.stateModifications()).isEqualTo(2); // one for timeout, one for final flush
      assertThat(s.txSeen()).hasSize(2); // one for timeout, one for final flush
      assertThat(getUsers()).isEqualTo(NUMBER_OF_EVENTS);
    }

    @SneakyThrows
    @Test
    public void rollsBack() {
      SpringTxProjectionSizeBlowAt7th p =
          new SpringTxProjectionSizeBlowAt7th(platformTransactionManager, jdbcTemplate);

      assertThat(getUsers()).isEqualTo(0);

      try {
        factus.update(p);
      } catch (Throwable expected) {
        // ignore
      }

      // only first bulk (size = 5) should be executed
      assertThat(getUsers()).isEqualTo(5);
      assertThat(p.stateModifications()).isEqualTo(1);
      assertThat(p.txSeen()).hasSize(1);
    }

    @SpringTransactional(size = 3)
    class BulkSize3Projection extends AbstractTrackingUserProjection {
      public BulkSize3Projection(
          @NonNull PlatformTransactionManager platformTransactionManager,
          JdbcTemplate jdbcTemplate) {
        super(platformTransactionManager, jdbcTemplate);
      }
    }

    @SpringTransactional(size = 5)
    class BulkSize5Projection extends AbstractTrackingUserProjection {
      public BulkSize5Projection(
          @NonNull PlatformTransactionManager platformTransactionManager,
          JdbcTemplate jdbcTemplate) {
        super(platformTransactionManager, jdbcTemplate);
      }
    }

    @SpringTransactional(size = 20)
    class BulkSize20Projection extends AbstractTrackingUserProjection {
      public BulkSize20Projection(
          @NonNull PlatformTransactionManager platformTransactionManager,
          JdbcTemplate jdbcTemplate) {
        super(platformTransactionManager, jdbcTemplate);
      }
    }

    @SpringTransactional(size = 10)
    class BulkSize10Projection extends AbstractTrackingUserProjection {
      public BulkSize10Projection(
          @NonNull PlatformTransactionManager platformTransactionManager,
          JdbcTemplate jdbcTemplate) {
        super(platformTransactionManager, jdbcTemplate);
      }
    }

    @SpringTransactional(size = 3000000, timeout = 1000) // will flush after 800ms
    class SpringTxProjectionTimeout extends AbstractTrackingUserProjection {
      public SpringTxProjectionTimeout(
          PlatformTransactionManager platformTransactionManager, JdbcTemplate jdbcTemplate) {
        super(platformTransactionManager, jdbcTemplate);
      }

      @Override
      @SneakyThrows
      protected void apply(UserCreated created) {
        super.apply(created);

        Thread.sleep(100);
      }
    }

    @SpringTransactional(size = 5)
    class SpringTxProjectionSizeBlowAt7th extends AbstractTrackingUserProjection {
      private int count;

      public SpringTxProjectionSizeBlowAt7th(
          PlatformTransactionManager platformTransactionManager, JdbcTemplate jdbcTemplate) {
        super(platformTransactionManager, jdbcTemplate);
      }

      @Override
      protected void apply(UserCreated created) {
        if (++count == 7) { // blow the second bulk
          throw new IllegalStateException("Bad luck");
        }

        super.apply(created);
      }
    }
  }

  @Nested
  class Subscribed {
    @Test
    public void testBulkSize3() {
      val s = new BulkSize3Projection(platformTransactionManager, jdbcTemplate);
      factus.subscribeAndBlock(s).awaitCatchup();

      assertThat(s.stateModifications()).isEqualTo(4);
      assertThat(s.txSeen()).hasSize(4);
      assertThat(getUsers()).isEqualTo(NUMBER_OF_EVENTS);
    }

    @Test
    public void testBulkSize5() {
      val s = new BulkSize5Projection(platformTransactionManager, jdbcTemplate);
      factus.subscribeAndBlock(s).awaitCatchup();

      assertThat(s.stateModifications()).isEqualTo(2);
      assertThat(s.txSeen()).hasSize(2);
      assertThat(getUsers()).isEqualTo(NUMBER_OF_EVENTS);
    }

    @Test
    public void testBulkSize10() {
      val s = new BulkSize10Projection(platformTransactionManager, jdbcTemplate);
      factus.subscribeAndBlock(s).awaitCatchup();

      assertThat(s.stateModifications()).isEqualTo(1);
      assertThat(s.txSeen()).hasSize(1);
      assertThat(getUsers()).isEqualTo(NUMBER_OF_EVENTS);
    }

    @Test
    public void testBulkSize20() {
      val s = new BulkSize20Projection(platformTransactionManager, jdbcTemplate);
      factus.subscribeAndBlock(s).awaitCatchup();

      assertThat(s.stateModifications()).isEqualTo(1);
      assertThat(s.txSeen()).hasSize(1);
      assertThat(getUsers()).isEqualTo(NUMBER_OF_EVENTS);
    }

    @Test
    public void testBulkApplyTimeout() {
      val s = new SpringTxProjectionTimeout(platformTransactionManager, jdbcTemplate);
      factus.subscribeAndBlock(s).awaitCatchup();

      assertThat(s.stateModifications()).isEqualTo(2); // one for timeout, one for final flush
      assertThat(s.txSeen()).hasSize(2); // one for timeout, one for final flush
      assertThat(getUsers()).isEqualTo(NUMBER_OF_EVENTS);
    }

    @SneakyThrows
    @Test
    public void rollsBack() {
      SpringTxProjectionSizeBlowAt7th p =
          new SpringTxProjectionSizeBlowAt7th(platformTransactionManager, jdbcTemplate);

      assertThat(getUsers()).isEqualTo(0);

      try {
        factus.subscribeAndBlock(p).awaitCatchup();
      } catch (Throwable expected) {
        // ignore
      }

      // only first bulk (size = 5) should be executed
      assertThat(getUsers()).isEqualTo(5);
      assertThat(p.stateModifications()).isEqualTo(1);
      assertThat(p.txSeen()).hasSize(1);
    }

    @SpringTransactional(size = 3)
    class BulkSize3Projection extends AbstractTrackingUserSubscribedProjection {
      public BulkSize3Projection(
          @NonNull PlatformTransactionManager platformTransactionManager,
          JdbcTemplate jdbcTemplate) {
        super(platformTransactionManager, jdbcTemplate);
      }
    }

    @SpringTransactional(size = 5)
    class BulkSize5Projection extends AbstractTrackingUserSubscribedProjection {
      public BulkSize5Projection(
          @NonNull PlatformTransactionManager platformTransactionManager,
          JdbcTemplate jdbcTemplate) {
        super(platformTransactionManager, jdbcTemplate);
      }
    }

    @SpringTransactional(size = 20)
    class BulkSize20Projection extends AbstractTrackingUserSubscribedProjection {
      public BulkSize20Projection(
          @NonNull PlatformTransactionManager platformTransactionManager,
          JdbcTemplate jdbcTemplate) {
        super(platformTransactionManager, jdbcTemplate);
      }
    }

    @SpringTransactional(size = 10)
    class BulkSize10Projection extends AbstractTrackingUserSubscribedProjection {
      public BulkSize10Projection(
          @NonNull PlatformTransactionManager platformTransactionManager,
          JdbcTemplate jdbcTemplate) {
        super(platformTransactionManager, jdbcTemplate);
      }
    }

    @SpringTransactional(size = 3000000, timeout = 1000) // will flush after 800ms
    class SpringTxProjectionTimeout extends AbstractTrackingUserSubscribedProjection {
      public SpringTxProjectionTimeout(
          PlatformTransactionManager platformTransactionManager, JdbcTemplate jdbcTemplate) {
        super(platformTransactionManager, jdbcTemplate);
      }

      @Override
      @SneakyThrows
      protected void apply(UserCreated created) {
        super.apply(created);

        Thread.sleep(100);
      }
    }

    @SpringTransactional(size = 5)
    class SpringTxProjectionSizeBlowAt7th extends AbstractTrackingUserSubscribedProjection {
      private int count;

      public SpringTxProjectionSizeBlowAt7th(
          PlatformTransactionManager platformTransactionManager, JdbcTemplate jdbcTemplate) {
        super(platformTransactionManager, jdbcTemplate);
      }

      @Override
      protected void apply(UserCreated created) {
        if (++count == 7) { // blow the second bulk
          throw new IllegalStateException("Bad luck");
        }

        super.apply(created);
      }
    }
  }

  private int getUsers() {
    return jdbcTemplate.queryForObject("select count(*) from users", Integer.class);
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
  abstract static class AbstractTrackingUserProjection extends AbstractSpringTxManagedProjection {
    private final JdbcTemplate jdbcTemplate;
    @Getter private int stateModifications = 0;

    @Getter private final Set<String> txSeen = new HashSet<>();

    public AbstractTrackingUserProjection(
        @NonNull PlatformTransactionManager platformTransactionManager, JdbcTemplate jdbcTemplate) {
      super(platformTransactionManager);
      this.jdbcTemplate = jdbcTemplate;
    }

    @Handler
    void apply(UserCreated e) {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();

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
      log.debug("set state");
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      stateModifications++;

      txSeen.add(jdbcTemplate.queryForObject("select txid_current()", String.class));

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

  @Slf4j
  abstract static class AbstractTrackingUserSubscribedProjection
      extends AbstractSpringTxSubscribedProjection {
    private final JdbcTemplate jdbcTemplate;
    @Getter private int stateModifications = 0;

    @Getter private final Set<String> txSeen = new HashSet<>();

    public AbstractTrackingUserSubscribedProjection(
        @NonNull PlatformTransactionManager platformTransactionManager, JdbcTemplate jdbcTemplate) {
      super(platformTransactionManager);
      this.jdbcTemplate = jdbcTemplate;
    }

    @Handler
    void apply(UserCreated e) {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();

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
      log.debug("set state");
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      stateModifications++;

      txSeen.add(jdbcTemplate.queryForObject("select txid_current()", String.class));

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
