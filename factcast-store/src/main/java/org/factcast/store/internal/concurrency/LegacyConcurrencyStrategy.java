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
import java.util.function.*;
import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.store.internal.lock.FactTableWriteLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** behavior of factcast <=0.9.9 */
public class LegacyConcurrencyStrategy extends ConcurrencyStrategy {
  @NonNull private final PlatformTransactionManager platformTransactionManager;
  @NonNull private final FactTableWriteLock lock;

  public LegacyConcurrencyStrategy(
      @NonNull PlatformTransactionManager platformTransactionManager,
      @NonNull FactTableWriteLock lock,
      @NonNull JdbcTemplate jdbc) {
    super(jdbc);
    this.platformTransactionManager = platformTransactionManager;
    this.lock = lock;
  }

  @Override
  public void publish(@NonNull List<? extends Fact> factsToPublish) {
    TransactionTemplate tpl = new TransactionTemplate(platformTransactionManager);
    tpl.executeWithoutResult(
        ts -> {
          lock.acquireExclusiveTxLock();
          batchInsertFacts(factsToPublish);
        });
  }

  @SuppressWarnings("java:S4276")
  @Override
  public boolean publishIfUnchanged(
      @NonNull List<? extends Fact> factsToPublish, @NonNull Predicate<Long> isUnchanged) {

    TransactionTemplate tpl = new TransactionTemplate(platformTransactionManager);
    return Boolean.TRUE.equals(
        tpl.execute(
            ts -> {
              lock.acquireExclusiveTxLock();
              if (isUnchanged.test(null)) {
                batchInsertFacts(factsToPublish);
                return true;
              } else return false;
            }));
  }
}
