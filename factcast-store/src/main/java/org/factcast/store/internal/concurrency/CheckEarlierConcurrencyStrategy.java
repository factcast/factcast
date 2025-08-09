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

import com.google.common.annotations.VisibleForTesting;
import java.util.*;
import java.util.function.*;
import lombok.*;
import org.factcast.core.Fact;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * This is superexperimental, and for the daring people out there only.
 *
 * <pre>
 * unconditional:
 * - locks shared
 * conditional
 * - locks exclusive
 * - inserts
 * - releases lock
 * - waits for older conditional transactions to finish¹
 * - tests
 * - commits/rollsback based on the test
 * </pre>
 *
 * ¹relies on pg_stats_activity
 */
@SuppressWarnings("SpringTransactionalMethodCallsInspection")
public class CheckEarlierConcurrencyStrategy extends ConcurrencyStrategy {
  @NonNull private final PlatformTransactionManager platformTransactionManager;

  public CheckEarlierConcurrencyStrategy(
      @NonNull PlatformTransactionManager platformTransactionManager, @NonNull JdbcTemplate jdbc) {
    super(jdbc);
    this.platformTransactionManager = platformTransactionManager;
  }

  @Override
  public void publish(@NonNull List<? extends Fact> factsToPublish) {
    TransactionTemplate tpl = new TransactionTemplate(platformTransactionManager);
    tpl.executeWithoutResult(
        ts -> {
          lockShared();
          batchInsertFacts(factsToPublish);
        });
  }

  @Override
  @SuppressWarnings("java:S4276")
  public boolean publishIfUnchanged(
      @NonNull List<? extends Fact> factsToPublish, @NonNull Predicate<Long> isUnchanged) {
    TransactionTemplate tpl = new TransactionTemplate(platformTransactionManager);
    jdbc.execute("SET APPLICATION_NAME = 'fc_cond_ins'");
    try {
      return Boolean.TRUE.equals(
          tpl.execute(
              ts -> {
                long ser;
                lock();
                try {
                  ser = batchInsertFacts(factsToPublish);
                } finally {
                  // release lock early, now that we have assigned the serials
                  unlock();
                }

                // wait for earlier
                boolean readyToCheck =
                    jdbc.queryForObject("SELECT waitForEarlierConditionalInserts()", boolean.class);

                if (readyToCheck && isUnchanged.test(ser)) {
                  return true;
                } else {
                  ts.setRollbackOnly();
                  return false;
                }
              }));

    } finally {
      jdbc.execute("SET APPLICATION_NAME = ''");
    }
  }

  @VisibleForTesting
  protected void unlock() {
    // TODO add metrics
    jdbc.execute("SELECT pg_advisory_unlock(" + AdvisoryLocks.PUBLISH.code() + ")");
  }

  @VisibleForTesting
  protected void lockShared() {
    // TODO add metrics
    jdbc.execute("SELECT pg_advisory_xact_lock_shared(" + AdvisoryLocks.PUBLISH.code() + ")");
  }

  @VisibleForTesting
  protected void lock() {
    // TODO add metrics
    jdbc.execute("SELECT pg_advisory_xact_lock(" + AdvisoryLocks.PUBLISH.code() + ")");
  }
}
