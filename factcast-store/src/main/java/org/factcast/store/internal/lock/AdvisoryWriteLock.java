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

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;

@RequiredArgsConstructor
@SuppressWarnings("java:S2077")
public class AdvisoryWriteLock implements FactTableWriteLock {
  private static final String LOCK_EXCLUSIVE_SQL =
      "SELECT pg_advisory_xact_lock(" + AdvisoryLocks.PUBLISH.code() + ")";

  private final JdbcTemplate tpl;

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void acquireExclusiveTXLock() {
    LockState state =
        TransactionSynchronizationManager.getSynchronizations().stream()
            .filter(LockState.class::isInstance)
            .map(LockState.class::cast)
            .filter(candidate -> candidate.owner == this)
            .findFirst()
            .orElseGet(
                () -> {
                  LockState created = new LockState(this);
                  TransactionSynchronizationManager.registerSynchronization(created);
                  return created;
                });
    if (!state.lockHeld) {
      tpl.execute(LOCK_EXCLUSIVE_SQL);
      state.lockHeld = true;
    }
  }

  // Conditional publication acquires this lock for validation and then reaches the publication
  // path, which asks for the same lock again. Remembering successful acquisition avoids that
  // redundant database round trip while keeping validation and insertion under one exclusive lock.
  //
  // A synchronization's presence alone does not prove that PostgreSQL still holds the lock:
  // rolling back to a savepoint releases transaction locks acquired after that savepoint, but
  // Spring keeps the synchronization registered until the outer transaction completes. Without
  // invalidation, a later publication could skip locking and commit out of serial order with
  // another writer. Readers using the maximum committed serial could then skip a late-committing
  // fact; conditional publication would also lose its protection against concurrent changes.
  //
  // Keep mutable state per transaction and lock instance, and clear it on savepoint rollback.
  // Clearing it even when acquisition predates the savepoint is deliberately conservative: the
  // next acquisition may be redundant, but ordinary repeated acquisitions still execute no SQL.
  // Set the flag only after successful acquisition so a failed attempt can never suppress a retry.
  // This relies on Spring-managed savepoints (including nested transactions); direct JDBC or SQL
  // savepoint operations bypass Spring's callbacks and cannot safely use this cached lock state.
  private static final class LockState implements TransactionSynchronization {
    private final AdvisoryWriteLock owner;
    private boolean lockHeld;

    private LockState(AdvisoryWriteLock owner) {
      this.owner = owner;
    }

    @Override
    public void savepointRollback(Object savepoint) {
      // PostgreSQL can release the lock on rollback; reacquire on the next publication.
      // Invalidate conservatively even when the lock predates this Spring-managed savepoint.
      lockHeld = false;
    }
  }
}
