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
package org.factcast.store.internal.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class AdvisoryWriteLockTest {

  private static final String LOCK_SHARED_SQL =
      "SELECT pg_advisory_xact_lock_shared(" + AdvisoryLocks.PUBLISH.code() + ")";

  private static final String LOCK_EXCLUSIVE_SQL =
      "SELECT pg_advisory_xact_lock(" + AdvisoryLocks.PUBLISH.code() + ")";

  @Mock private JdbcTemplate tpl;

  @InjectMocks private AdvisoryWriteLock underTest;

  @BeforeEach
  void startTransactionSynchronization() {
    TransactionSynchronizationManager.initSynchronization();
  }

  @AfterEach
  void stopTransactionSynchronization() {
    TransactionSynchronizationManager.clearSynchronization();
  }

  @Nested
  class AcquireSharedTXLock {

    @Test
    void executesSharedAdvisoryLockStatement() {
      underTest.acquireSharedTXLock();

      verify(tpl).execute(LOCK_SHARED_SQL);
      assertThat(underTest.isExclusiveTXLockHeld()).isFalse();
    }
  }

  @Nested
  class AcquireExclusiveTXLock {

    @Test
    void executesExclusiveAdvisoryLockStatement() {
      underTest.acquireExclusiveTXLock();

      verify(tpl).execute(LOCK_EXCLUSIVE_SQL);
      assertThat(underTest.isExclusiveTXLockHeld()).isTrue();
    }
  }
}
