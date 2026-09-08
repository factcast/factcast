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
package org.factcast.factus.spring.tx.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.factcast.core.FactStreamPosition;
import org.factcast.factus.projection.WriterToken;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class AbstractSpringJdbcProjectionTest {

  @Mock private PlatformTransactionManager platformTransactionManager;
  @Mock private JdbcTemplate jdbcTemplate;

  private static RowMapper<FactStreamPosition> anyRowMapper() {
    return ArgumentMatchers.any();
  }

  @Nested
  class ManagedProjectionWiring {

    @BeforeEach
    void stubDataSource() {
      when(jdbcTemplate.getDataSource()).thenReturn(mock(DataSource.class));
    }

    @Test
    void readsItsPositionFromTheManagedTable() {
      MyManagedProjection uut = new MyManagedProjection(platformTransactionManager, jdbcTemplate);
      FactStreamPosition position = FactStreamPosition.of(UUID.randomUUID(), 7L);
      when(jdbcTemplate.query(anyString(), anyRowMapper(), eq("managed_1")))
          .thenReturn(List.of(position));

      assertThat(uut.factStreamPosition()).isEqualTo(position);
      verify(jdbcTemplate)
          .query(
              eq("SELECT state, serial FROM managed_projection WHERE name = ?"),
              anyRowMapper(),
              eq("managed_1"));
    }

    @Test
    void writesItsPositionToTheManagedTable() {
      MyManagedProjection uut = new MyManagedProjection(platformTransactionManager, jdbcTemplate);
      UUID factId = UUID.randomUUID();

      uut.factStreamPosition(FactStreamPosition.of(factId, 7L));

      verify(jdbcTemplate)
          .update(
              "UPDATE managed_projection SET state = ?, serial = ? WHERE name = ?",
              factId,
              7L,
              "managed_1");
    }

    @Test
    void honoursCustomTableNames() {
      MyManagedProjection uut =
          new MyManagedProjection(
              platformTransactionManager, jdbcTemplate, "my_locks", "my_positions");

      uut.factStreamPosition();

      verify(jdbcTemplate)
          .query(
              eq("SELECT state, serial FROM my_positions WHERE name = ?"),
              anyRowMapper(),
              eq("managed_1"));
    }
  }

  @Nested
  class SubscribedProjectionWiring {

    @BeforeEach
    void stubDataSource() {
      when(jdbcTemplate.getDataSource()).thenReturn(mock(DataSource.class));
    }

    @Test
    void readsItsPositionFromTheSubscribedTable() {
      MySubscribedProjection uut =
          new MySubscribedProjection(platformTransactionManager, jdbcTemplate);

      uut.factStreamPosition();

      verify(jdbcTemplate)
          .query(
              eq("SELECT state, serial FROM subscribed_projection WHERE name = ?"),
              anyRowMapper(),
              eq("subscribed_1"));
    }
  }

  @Nested
  class SubscribedProjectionLock {

    @Mock private LockProvider lockProvider;
    @Mock private ScheduledExecutorService scheduler;

    private MySubscribedProjection uut;

    @BeforeEach
    void setUp() {
      uut =
          new MySubscribedProjection(
              platformTransactionManager,
              new JdbcWriterTokenManager(
                  lockProvider,
                  "subscribed_1",
                  Duration.ofSeconds(60),
                  Duration.ZERO,
                  scheduler,
                  JdbcWriterTokenManager.Timing.SYSTEM),
              new JdbcFactStreamPosition(
                  jdbcTemplate, JdbcFactStreamPosition.ProjectionType.SUBSCRIBED, "subscribed_1"));
    }

    @Test
    void hasNoLockBeforeAcquiringOne() {
      assertThat(uut.hasLock()).isFalse();
    }

    @Test
    void hasNoLockAfterAFailedAcquisition() {
      when(lockProvider.lock(any(LockConfiguration.class))).thenReturn(Optional.empty());

      assertThat(uut.acquireWriteToken(Duration.ZERO)).isNull();
      assertThat(uut.hasLock()).isFalse();
    }

    @Test
    void hasLockWhileTheTokenIsValid() throws Exception {
      when(lockProvider.lock(any(LockConfiguration.class)))
          .thenReturn(Optional.of(mock(SimpleLock.class)));

      WriterToken token = uut.acquireWriteToken(Duration.ZERO);

      assertThat(token).isNotNull();
      assertThat(uut.hasLock()).isTrue();

      token.close();
      assertThat(uut.hasLock()).isFalse();
    }
  }

  @ProjectionMetaData(name = "managed", revision = 1)
  static class MyManagedProjection extends AbstractSpringJdbcManagedProjection {
    MyManagedProjection(
        PlatformTransactionManager platformTransactionManager, JdbcTemplate jdbcTemplate) {
      super(platformTransactionManager, jdbcTemplate);
    }

    MyManagedProjection(
        PlatformTransactionManager platformTransactionManager,
        JdbcTemplate jdbcTemplate,
        String lockTableName,
        String positionTableName) {
      super(platformTransactionManager, jdbcTemplate, lockTableName, positionTableName);
    }
  }

  @ProjectionMetaData(name = "subscribed", revision = 1)
  static class MySubscribedProjection extends AbstractSpringJdbcSubscribedProjection {
    MySubscribedProjection(
        PlatformTransactionManager platformTransactionManager, JdbcTemplate jdbcTemplate) {
      super(platformTransactionManager, jdbcTemplate);
    }

    MySubscribedProjection(
        PlatformTransactionManager platformTransactionManager,
        JdbcWriterTokenManager writerTokenManager,
        JdbcFactStreamPosition factStreamPosition) {
      super(platformTransactionManager, writerTokenManager, factStreamPosition);
    }
  }
}
