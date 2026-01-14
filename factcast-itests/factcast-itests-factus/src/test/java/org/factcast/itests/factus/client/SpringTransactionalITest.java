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
package org.factcast.itests.factus.client;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.FactStreamPosition;
import org.factcast.factus.Factus;
import org.factcast.factus.Handler;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.projection.WriterToken;
import org.factcast.factus.projection.tx.TransactionException;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.factcast.factus.spring.tx.AbstractSpringTxManagedProjection;
import org.factcast.factus.spring.tx.AbstractSpringTxSubscribedProjection;
import org.factcast.factus.spring.tx.SpringTransactional;
import org.factcast.itests.TestFactusApplication;
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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest
@ContextConfiguration(classes = TestFactusApplication.class)
@EnableAutoConfiguration
@Slf4j
public class SpringTransactionalITest extends AbstractFactCastIntegrationTest {
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired PlatformTransactionManager platformTransactionManager;
  @Autowired Factus factus;

  final int NUMBER_OF_EVENTS = 10;

  @BeforeEach
  void setUp() {
    createTables();

    var l = new ArrayList<EventObject>(NUMBER_OF_EVENTS);
    for (int i = 0; i < NUMBER_OF_EVENTS; i++) {
      l.add(new UserCreated(randomUUID(), getClass().getSimpleName() + ":" + i));
    }
    log.info("publishing {} Events ", NUMBER_OF_EVENTS);
    factus.publish(l);
  }

  @Nested
  class Managed {
    @Test
    void testBulkSize3() {
      var s = new BulkSize3Projection(platformTransactionManager, jdbcTemplate);
      factus.update(s);

      assertThat(s.factStreamPositionModifications()).isEqualTo(4);
      assertThat(s.txSeen()).hasSize(4);
      assertThat(getUsers()).isEqualTo(NUMBER_OF_EVENTS);
    }

    @Test
    void testBulkSize5() {
      var s = new BulkSize5Projection(platformTransactionManager, jdbcTemplate);
      factus.update(s);

      assertThat(s.factStreamPositionModifications()).isEqualTo(2);
      assertThat(s.txSeen()).hasSize(2);
      assertThat(getUsers()).isEqualTo(NUMBER_OF_EVENTS);
    }

    @Test
    void testBulkSize10() {
      var s = new BulkSize10Projection(platformTransactionManager, jdbcTemplate);
      factus.update(s);

      assertThat(s.factStreamPositionModifications()).isEqualTo(1);
      assertThat(s.txSeen()).hasSize(1);
      assertThat(getUsers()).isEqualTo(NUMBER_OF_EVENTS);
    }

    @Test
    void testBulkSize20() {
      var s = new BulkSize20Projection(platformTransactionManager, jdbcTemplate);
      factus.update(s);

      assertThat(s.factStreamPositionModifications()).isEqualTo(1);
      assertThat(s.txSeen()).hasSize(1);
      assertThat(getUsers()).isEqualTo(NUMBER_OF_EVENTS);
    }

    @SneakyThrows
    @Test
    void rollsBack() {
      SpringTxProjectionSizeBlowAt7th p =
          new SpringTxProjectionSizeBlowAt7th(platformTransactionManager, jdbcTemplate);

      assertThat(getUsers()).isZero();

      try {
        factus.update(p);
      } catch (Throwable expected) {
        // ignore
      }

      assertThat(getUsers()).isEqualTo(6);
      assertThat(p.factStreamPositionModifications()).isEqualTo(2); // 5 and one
      assertThat(p.txSeen()).hasSize(3); // one rolled back, two committed
    }

    @ProjectionMetaData(revision = 1)
    @SpringTransactional(bulkSize = 3)
    class BulkSize3Projection extends AbstractTrackingUserProjection {
      public BulkSize3Projection(
          @NonNull PlatformTransactionManager platformTransactionManager,
          JdbcTemplate jdbcTemplate) {
        super(platformTransactionManager, jdbcTemplate);
      }
    }

    @ProjectionMetaData(revision = 1)
    @SpringTransactional(bulkSize = 5)
    class BulkSize5Projection extends AbstractTrackingUserProjection {
      public BulkSize5Projection(
          @NonNull PlatformTransactionManager platformTransactionManager,
          JdbcTemplate jdbcTemplate) {
        super(platformTransactionManager, jdbcTemplate);
      }
    }

    @ProjectionMetaData(revision = 1)
    @SpringTransactional(bulkSize = 20)
    class BulkSize20Projection extends AbstractTrackingUserProjection {
      public BulkSize20Projection(
          @NonNull PlatformTransactionManager platformTransactionManager,
          JdbcTemplate jdbcTemplate) {
        super(platformTransactionManager, jdbcTemplate);
      }
    }

    @ProjectionMetaData(revision = 1)
    @SpringTransactional(bulkSize = 10)
    class BulkSize10Projection extends AbstractTrackingUserProjection {
      public BulkSize10Projection(
          @NonNull PlatformTransactionManager platformTransactionManager,
          JdbcTemplate jdbcTemplate) {
        super(platformTransactionManager, jdbcTemplate);
      }
    }

    @ProjectionMetaData(revision = 1)
    @SpringTransactional(bulkSize = 5)
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
    //    @SneakyThrows
    //    @Test
    //    public void txCommitsOnError() {
    //
    //      var subscribedProjection =
    //          new Subscribed.BulkSize3ProjectionErrorAt3(platformTransactionManager,
    // jdbcTemplate);
    //      assertThat(subscribedProjection.userCount()).isZero();
    //
    //      // exec this in another thread
    //      CompletableFuture.runAsync(
    //              () -> {
    //                Subscription subscription = factus.subscribeAndBlock(subscribedProjection);
    //                subscribedProjection.subscription((InternalSubscription) subscription);
    //
    //                assertThatThrownBy(subscription::awaitComplete)
    //                    .isInstanceOf(RuntimeException.class);
    //              })
    //          .get();
    //
    //      // make sure the first two are comitted
    //      assertThat(subscribedProjection.userCount()).isEqualTo(2);
    //    }

    @Test
    void testBulkSize3() throws Exception {
      var s = new BulkSize3Projection(platformTransactionManager, jdbcTemplate);
      try (var ignored = factus.subscribeAndBlock(s).awaitCatchup()) {

        assertThat(s.factStreamPositionModifications()).isEqualTo(4);
        assertThat(s.txSeen()).hasSize(4);
        assertThat(getUsers()).isEqualTo(NUMBER_OF_EVENTS);
      }
    }

    @Test
    void testBulkSize5() throws Exception {
      var s = new BulkSize5Projection(platformTransactionManager, jdbcTemplate);
      try (var ignored = factus.subscribeAndBlock(s).awaitCatchup()) {

        assertThat(s.factStreamPositionModifications()).isEqualTo(2);
        assertThat(s.txSeen()).hasSize(2);
        assertThat(getUsers()).isEqualTo(NUMBER_OF_EVENTS);
      }
    }

    @SneakyThrows
    @Test
    void testBulkSize10() {
      var s = new BulkSize10Projection(platformTransactionManager, jdbcTemplate);
      try (var ignored = factus.subscribeAndBlock(s).awaitCatchup()) {

        assertThat(s.factStreamPositionModifications()).isOne();
        assertThat(s.txSeen()).hasSize(1);
        assertThat(getUsers()).isEqualTo(NUMBER_OF_EVENTS);
      }
    }

    @SneakyThrows
    @Test
    void testBulkSize20() {
      var s = new BulkSize20Projection(platformTransactionManager, jdbcTemplate);
      try (var ignored = factus.subscribeAndBlock(s).awaitCatchup()) {

        assertThat(s.factStreamPositionModifications()).isOne();
        assertThat(s.txSeen()).hasSize(1);
        assertThat(getUsers()).isEqualTo(NUMBER_OF_EVENTS);
      }
    }

    @SneakyThrows
    @Test
    void rollsBack() {
      SpringTxProjectionSizeBlowAt7th p =
          new SpringTxProjectionSizeBlowAt7th(platformTransactionManager, jdbcTemplate);

      assertThat(getUsers()).isZero();

      try {
        factus.subscribeAndBlock(p).awaitCatchup().close();
      } catch (Throwable expected) {
        // ignore
      }

      assertThat(getUsers()).isEqualTo(6);
      assertThat(p.factStreamPositionModifications()).isEqualTo(2); // 5 and one
      assertThat(p.txSeen()).hasSize(3); // one rolled back, two committed
    }

    @ProjectionMetaData(revision = 1)
    @SpringTransactional(bulkSize = 3)
    static class BulkSize3Projection extends AbstractTrackingUserSubscribedProjection {
      public BulkSize3Projection(
          @NonNull PlatformTransactionManager platformTransactionManager,
          JdbcTemplate jdbcTemplate) {
        super(platformTransactionManager, jdbcTemplate);
      }
    }

    @ProjectionMetaData(revision = 1)
    @SpringTransactional(bulkSize = 5)
    static class BulkSize5Projection extends AbstractTrackingUserSubscribedProjection {
      public BulkSize5Projection(
          @NonNull PlatformTransactionManager platformTransactionManager,
          JdbcTemplate jdbcTemplate) {
        super(platformTransactionManager, jdbcTemplate);
      }
    }

    @ProjectionMetaData(revision = 1)
    @SpringTransactional(bulkSize = 20)
    static class BulkSize20Projection extends AbstractTrackingUserSubscribedProjection {
      public BulkSize20Projection(
          @NonNull PlatformTransactionManager platformTransactionManager,
          JdbcTemplate jdbcTemplate) {
        super(platformTransactionManager, jdbcTemplate);
      }
    }

    @ProjectionMetaData(revision = 1)
    @SpringTransactional(bulkSize = 10)
    static class BulkSize10Projection extends AbstractTrackingUserSubscribedProjection {
      public BulkSize10Projection(
          @NonNull PlatformTransactionManager platformTransactionManager,
          JdbcTemplate jdbcTemplate) {
        super(platformTransactionManager, jdbcTemplate);
      }
    }

    @ProjectionMetaData(revision = 1)
    @SpringTransactional(bulkSize = 5)
    static class SpringTxProjectionSizeBlowAt7th extends AbstractTrackingUserSubscribedProjection {
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
        """
        CREATE TABLE managed_projection (

            name  varchar(255),
            fact_stream_position UUID,

            PRIMARY KEY (name)
        );\
        """);

    jdbcTemplate.execute("DROP TABLE IF EXISTS users;");
    jdbcTemplate.execute(
        """
        CREATE TABLE users (

            name  varchar(255),
            id UUID,

            PRIMARY KEY (id)
        );\
        """);
  }

  @Slf4j
  abstract static class AbstractTrackingUserProjection extends AbstractSpringTxManagedProjection {
    private final JdbcTemplate jdbcTemplate;
    @Getter private int factStreamPositionModifications;

    @Getter private final Set<String> txSeen = new HashSet<>();

    public AbstractTrackingUserProjection(
        @NonNull PlatformTransactionManager platformTransactionManager, JdbcTemplate jdbcTemplate) {
      super(platformTransactionManager);
      this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void begin() throws TransactionException {
      super.begin();
      txSeen.add(jdbcTemplate.queryForObject("select txid_current()", String.class));
    }

    @Handler
    void apply(UserCreated e) {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();

      jdbcTemplate.update(
          "INSERT INTO users (name,id) VALUES (?,?);", e.userName(), e.aggregateId());
    }

    @Override
    public FactStreamPosition factStreamPosition() {
      try {
        return FactStreamPosition.withoutSerial(
            jdbcTemplate.queryForObject(
                "SELECT fact_stream_position FROM managed_projection WHERE name = ?",
                UUID.class,
                getScopedName().asString()));
      } catch (IncorrectResultSizeDataAccessException e) {
        // no position yet, just return null
        return null;
      }
    }

    @Override
    public void factStreamPosition(@NonNull FactStreamPosition factStreamPosition) {
      log.debug("set fact stream position");
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      factStreamPositionModifications++;

      jdbcTemplate.update(
          "INSERT INTO managed_projection (name, fact_stream_position) VALUES (?, ?) ON CONFLICT"
              + " (name) DO UPDATE SET fact_stream_position = ?",
          getScopedName().asString(),
          factStreamPosition.factId(),
          factStreamPosition.factId());
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
    @Getter private int factStreamPositionModifications;

    @Getter private final Set<String> txSeen = new HashSet<>();

    public AbstractTrackingUserSubscribedProjection(
        @NonNull PlatformTransactionManager platformTransactionManager, JdbcTemplate jdbcTemplate) {
      super(platformTransactionManager);
      this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void begin() throws TransactionException {
      super.begin();
      txSeen.add(jdbcTemplate.queryForObject("select txid_current()", String.class));
    }

    @Handler
    void apply(UserCreated e) {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();

      jdbcTemplate.update(
          "INSERT INTO users (name,id) VALUES (?,?);", e.userName(), e.aggregateId());
    }

    @Override
    public FactStreamPosition factStreamPosition() {
      try {
        return FactStreamPosition.withoutSerial(
            jdbcTemplate.queryForObject(
                "SELECT fact_stream_position FROM managed_projection WHERE name = ?",
                UUID.class,
                getScopedName().asString()));
      } catch (IncorrectResultSizeDataAccessException e) {
        // no position yet, just return null
        return null;
      }
    }

    @Override
    public void factStreamPosition(@NonNull FactStreamPosition state) {
      jdbcTemplate.update(
          "INSERT INTO managed_projection (name, fact_stream_position) VALUES (?, ?) "
              + "ON CONFLICT (name) DO UPDATE SET fact_stream_position = ?",
          getScopedName().asString(),
          state.factId(),
          state.factId());
    }

    @Override
    public void transactionalFactStreamPosition(@NonNull FactStreamPosition factStreamPosition) {
      factStreamPositionModifications++;
      super.transactionalFactStreamPosition(factStreamPosition);
    }

    @Override
    public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
      return () -> {};
    }
  }
}
