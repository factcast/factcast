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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AdvisoryWriteLockTest {
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final AdvisoryWriteLock lock = new AdvisoryWriteLock(jdbc);
  private static final String SQL =
      "SELECT pg_advisory_xact_lock(" + AdvisoryLocks.PUBLISH.code() + ")";

  @BeforeEach
  void beginTransaction() {
    TransactionSynchronizationManager.initSynchronization();
  }

  @AfterEach
  void endTransaction() {
    TransactionSynchronizationManager.clearSynchronization();
  }

  @Test
  void repeatedAcquisitionsWithinOneTransactionExecuteOnce() {
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {});
    lock.acquireExclusiveTXLock();
    lock.acquireExclusiveTXLock();
    verify(jdbc).execute(SQL);
  }

  @Test
  void failedAcquisitionDoesNotSuppressRetry() {
    var failure = new IllegalStateException("lock failed");
    doThrow(failure).doNothing().when(jdbc).execute(SQL);
    assertThatThrownBy(lock::acquireExclusiveTXLock).isSameAs(failure);
    lock.acquireExclusiveTXLock();
    lock.acquireExclusiveTXLock();
    verify(jdbc, times(2)).execute(SQL);
  }

  @Test
  void savepointRollbackRequiresReacquisition() {
    lock.acquireExclusiveTXLock();
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(sync -> sync.savepointRollback(new Object()));
    lock.acquireExclusiveTXLock();
    lock.acquireExclusiveTXLock();
    verify(jdbc, times(2)).execute(SQL);
  }

  @Test
  void lockStateBelongsToItsOwner() {
    var otherJdbc = mock(JdbcTemplate.class);
    var otherLock = new AdvisoryWriteLock(otherJdbc);
    lock.acquireExclusiveTXLock();
    otherLock.acquireExclusiveTXLock();
    lock.acquireExclusiveTXLock();
    otherLock.acquireExclusiveTXLock();
    verify(jdbc).execute(SQL);
    verify(otherJdbc).execute(SQL);
  }

  @Test
  void newTransactionDoesNotReusePreviousLockState() {
    lock.acquireExclusiveTXLock();
    TransactionSynchronizationManager.clearSynchronization();
    TransactionSynchronizationManager.initSynchronization();
    lock.acquireExclusiveTXLock();
    verify(jdbc, times(2)).execute(SQL);
  }
}
