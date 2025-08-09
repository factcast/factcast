/*
 * Copyright © 2017-2025 factcast.org
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
package org.factcast.store.internal.concurrency;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.*;
import lombok.*;
import org.factcast.core.Fact;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * This allows for more concurrency in the unconditional case, but holds all the guarantees from
 * LEGACY.
 *
 * <p>WARNING: In multi-instance setups, migration is not trivial and might require a downtime
 */
public class UnlockedCheckAndRollbackConcurrencyStrategy extends ConcurrencyStrategy {
  @NonNull private final PlatformTransactionManager platformTransactionManager;

  public UnlockedCheckAndRollbackConcurrencyStrategy(
      @NonNull PlatformTransactionManager platformTransactionManager, @NonNull JdbcTemplate jdbc) {
    super(jdbc);
    this.platformTransactionManager = platformTransactionManager;
  }

  @Override
  public void publish(@NonNull List<? extends Fact> factsToPublish) {
    TransactionTemplate tpl = new TransactionTemplate(platformTransactionManager);
    tpl.executeWithoutResult(
        ts -> {
          lockUnConditional();
          batchInsertFacts(factsToPublish);
        });
  }

  @Override
  public boolean publishIfUnchanged(
      @NonNull List<? extends Fact> factsToPublish, @NonNull Predicate<Long> isUnchanged) {

    TransactionTemplate tpl = new TransactionTemplate(platformTransactionManager);
    return Boolean.TRUE.equals(
        tpl.execute(
            ts -> {
              lockBoth();
              long ser = batchInsertFacts(factsToPublish);
              releaseLockForUnconditional();
              if (isUnchanged.test(ser)) {
                return true;
              } else {
                ts.setRollbackOnly();
                return false;
              }
            }));
  }

  private void lockBoth() {
    // TODO add metrics
    jdbc.execute(
        "SELECT pg_advisory_xact_lock("
            + AdvisoryLocks.PUBLISH.code()
            + ", "
            + AdvisoryLocks.PUBLISH_CONDITIONAL.code()
            + ")");
  }

  private void releaseLockForUnconditional() {
    jdbc.execute("SELECT pg_advisory_unlock(" + AdvisoryLocks.PUBLISH.code() + ")");
  }

  static AtomicLong count = new AtomicLong();

  private void lockUnConditional() {
    // TODO add metrics
    jdbc.execute("SELECT pg_advisory_xact_lock_shared(" + AdvisoryLocks.PUBLISH.code() + ")");
  }
}
