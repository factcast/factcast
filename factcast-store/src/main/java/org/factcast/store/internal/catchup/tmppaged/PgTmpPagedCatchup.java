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
package org.factcast.store.internal.catchup.tmppaged;

import com.google.common.annotations.VisibleForTesting;
import java.util.*;
import java.util.concurrent.atomic.*;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.PgMetrics;
import org.factcast.store.internal.PgPostQueryMatcher;
import org.factcast.store.internal.StoreMetrics;
import org.factcast.store.internal.blacklist.PgBlacklist;
import org.factcast.store.internal.catchup.PgCatchup;
import org.factcast.store.internal.listen.PgConnectionSupplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

@Slf4j
@RequiredArgsConstructor
public class PgTmpPagedCatchup implements PgCatchup {

  @NonNull final PgConnectionSupplier connectionSupplier;
  @NonNull final StoreConfigurationProperties props;
  @NonNull final SubscriptionRequestTO request;
  @NonNull final PgPostQueryMatcher postQueryMatcher;
  @NonNull final SubscriptionImpl subscription;
  @NonNull final AtomicLong serial;
  @NonNull final PgMetrics metrics;
  @NonNull final PgBlacklist blacklist;

  @SneakyThrows
  @Override
  public void run() {

    SingleConnectionDataSource ds = new SingleConnectionDataSource(connectionSupplier.get(), true);
    try {
      var jdbc = new JdbcTemplate(ds);
      fetch(jdbc);
    } finally {
      ds.destroy();
    }
  }

  @VisibleForTesting
  void fetch(JdbcTemplate jdbc) {
    long factCounter = 0L;
    jdbc.execute("CREATE TEMPORARY TABLE catchup(ser bigint)");

    PgCatchUpPrepare prep = new PgCatchUpPrepare(jdbc, request);
    // first collect all the sers
    var numberOfFactsToCatchUp = prep.prepareCatchup(serial);
    // and AFTERWARDs create the inmem index
    jdbc.execute("CREATE INDEX catchup_tmp_idx1 ON catchup(ser ASC)"); // improves perf on sorting

    var skipTesting = postQueryMatcher.canBeSkipped();

    if (numberOfFactsToCatchUp > 0) {
      PgCatchUpFetchTmpPage fetch = new PgCatchUpFetchTmpPage(jdbc, props.getPageSize(), request);
      List<Fact> facts;
      do {
        facts = fetch.fetchFacts(serial);

        for (Fact f : facts) {

          UUID factId = f.id();
          if (blacklist.isBlocked(factId)) {
            log.trace("{} filtered blacklisted id={}", request, factId);
          } else {
            if (skipTesting || postQueryMatcher.test(f)) {
              subscription.notifyElement(f);
              factCounter++;
            } else {
              log.trace("{} filtered id={}", request, factId);
            }
          }
        }
      } while (!facts.isEmpty());
      metrics
          .counter(StoreMetrics.EVENT.CATCHUP_FACT)
          .increment(factCounter); // TODO this needs to TAG it for each subscription?
    }
  }
}
