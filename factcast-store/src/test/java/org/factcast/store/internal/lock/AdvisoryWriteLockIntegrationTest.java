/*
 * Copyright © 2017-2020 factcast.org
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
package org.factcast.store.internal.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import javax.sql.DataSource;
import org.factcast.store.internal.PgTestConfiguration;
import org.factcast.test.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringJUnitConfig(PgTestConfiguration.class)
@IntegrationTest
class AdvisoryWriteLockIntegrationTest {
  private static final String LOCK_SQL =
      "SELECT pg_advisory_xact_lock(" + AdvisoryLocks.PUBLISH.code() + ")";

  @Autowired DataSource dataSource;
  @Autowired PlatformTransactionManager transactionManager;

  @Test
  void repeatedAcquisitionsExecuteOnlyOneLockQuery() {
    var jdbc = spy(new JdbcTemplate(dataSource));
    var lock = new AdvisoryWriteLock(jdbc);
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              lock.acquireExclusiveTXLock();
              lock.acquireExclusiveTXLock();
              assertThat(anotherTransactionCanAcquireLock()).isFalse();
            });
    verify(jdbc, times(1)).execute(LOCK_SQL);
    assertThat(anotherTransactionCanAcquireLock()).isTrue();
  }

  @Test
  void reacquiresAfterSavepointRollbackAndThenSkipsRepeatedAcquisition() {
    var jdbc = spy(new JdbcTemplate(dataSource));
    var lock = new AdvisoryWriteLock(jdbc);
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              Object savepoint = status.createSavepoint();
              lock.acquireExclusiveTXLock();
              assertThat(anotherTransactionCanAcquireLock()).isFalse();
              status.rollbackToSavepoint(savepoint);
              assertThat(anotherTransactionCanAcquireLock()).isTrue();
              lock.acquireExclusiveTXLock();
              assertThat(anotherTransactionCanAcquireLock()).isFalse();
              lock.acquireExclusiveTXLock();
              status.releaseSavepoint(savepoint);
            });
    verify(jdbc, times(2)).execute(LOCK_SQL);
    assertThat(anotherTransactionCanAcquireLock()).isTrue();
  }

  private boolean anotherTransactionCanAcquireLock() {
    // Borrow directly to probe from a different connection, outside Spring's caller transaction.
    try (var connection = dataSource.getConnection();
        var statement = connection.createStatement();
        var result =
            statement.executeQuery(
                "SELECT pg_try_advisory_xact_lock(" + AdvisoryLocks.PUBLISH.code() + ")")) {
      result.next();
      return result.getBoolean(1);
    } catch (java.sql.SQLException e) {
      throw new AssertionError(e);
    }
  }
}
