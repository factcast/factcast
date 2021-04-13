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
package org.factcast.store.pgsql.internal.catchup.paged;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.factcast.core.Fact;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.TransformationException;
import org.factcast.store.pgsql.PgConfigurationProperties;
import org.factcast.store.pgsql.internal.PgPostQueryMatcher;
import org.factcast.store.pgsql.internal.catchup.PgCatchUpPrepare;
import org.factcast.store.pgsql.internal.catchup.PgCatchup;
import org.factcast.store.pgsql.internal.listen.PgConnectionSupplier;
import org.factcast.store.pgsql.registry.transformation.chains.MissingTransformationInformation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

@Slf4j
@RequiredArgsConstructor
public class PgPagedCatchup implements PgCatchup {

  @NonNull final PgConnectionSupplier connectionSupplier;

  @NonNull final PgConfigurationProperties props;

  @NonNull final SubscriptionRequestTO request;

  @NonNull final PgPostQueryMatcher postQueryMatcher;

  @NonNull final SubscriptionImpl subscription;

  @NonNull final AtomicLong serial;

  @SneakyThrows
  @Override
  public void run() {

    SingleConnectionDataSource ds = new SingleConnectionDataSource(connectionSupplier.get(), true);
    try {
      val jdbc = new JdbcTemplate(ds);

      jdbc.execute("CREATE TEMPORARY TABLE catchup(ser bigint)");

      PgCatchUpPrepare prep = new PgCatchUpPrepare(jdbc, request);
      // first collect all the sers
      val numberOfFactsToCatchUp = prep.prepareCatchup(serial);
      // and AFTERWARDs create the inmem index
      jdbc.execute("CREATE INDEX catchup_tmp_idx1 ON catchup(ser ASC)"); // improves perf on sorting

      val skipTesting = postQueryMatcher.canBeSkipped();

      if (numberOfFactsToCatchUp > 0) {
        try {
          PgCatchUpFetchPage fetch = new PgCatchUpFetchPage(jdbc, props.getPageSize(), request);
          List<Fact> facts;
          do {
            facts = fetch.fetchFacts(serial);

            for (Fact f : facts) {
              UUID factId = f.id();
              if (skipTesting || postQueryMatcher.test(f)) {
                try {
                  subscription.notifyElement(f);
                } catch (MissingTransformationInformation | TransformationException e) {
                  log.warn("{} transformation error: {}", request, e.getMessage());
                  subscription.notifyError(e);
                  throw e;
                } catch (Throwable e) {
                  // debug level, because it happens regularly
                  // on
                  // disconnecting clients.
                  log.debug("{} exception from subscription: {}", request, e.getMessage());
                  try {
                    subscription.close();
                  } catch (Exception e1) {
                    log.warn(
                        "{} exception while closing subscription: {}", request, e1.getMessage());
                  }
                  throw e;
                }
              } else {
                log.trace("{} filtered id={}", request, factId);
              }
            }
          } while (!facts.isEmpty());

        } catch (Exception e) {
          log.error("While fetching ", e);
        }
      }
    } finally {
      ds.destroy();
    }
  }
}
