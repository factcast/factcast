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
package org.factcast.store.internal.catchup.fetching;

import java.util.*;
import java.util.concurrent.atomic.*;

import org.factcast.core.Fact;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.PgMetrics;
import org.factcast.store.internal.PgPostQueryMatcher;
import org.factcast.store.internal.StoreMetrics.EVENT;
import org.factcast.store.internal.blacklist.PgBlacklist;
import org.factcast.store.internal.catchup.PgCatchup;
import org.factcast.store.internal.listen.PgConnectionSupplier;
import org.factcast.store.internal.query.PgQueryBuilder;
import org.factcast.store.internal.rowmapper.PgFactExtractor;
import org.postgresql.jdbc.PgConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import com.google.common.annotations.VisibleForTesting;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class PgFetchingCatchup implements PgCatchup {

  @NonNull final PgConnectionSupplier connectionSupplier;

  @NonNull final StoreConfigurationProperties props;

  @NonNull final SubscriptionRequestTO req;

  @NonNull final PgPostQueryMatcher postQueryMatcher;

  @NonNull final SubscriptionImpl subscription;

  @NonNull final AtomicLong serial;

  @NonNull final PgMetrics metrics;

  @NonNull final PgBlacklist blacklist;

  protected long factCounter = 0L;

  @SneakyThrows
  @Override
  public void run() {

    PgConnection connection = connectionSupplier.get();
    connection.setAutoCommit(false); // necessary for using cursors

    // connection may stay open quite a while, and we do not want a CPool to interfere
    SingleConnectionDataSource ds = new SingleConnectionDataSource(connection, true);

    try {
      var jdbc = new JdbcTemplate(ds);
      fetch(jdbc);
    } finally {
      ds.destroy();
    }
  }

  @VisibleForTesting
  void fetch(JdbcTemplate jdbc) {
    jdbc.setFetchSize(props.getPageSize());
    jdbc.setQueryTimeout(0); // disable query timeout
    var skipTesting = postQueryMatcher.canBeSkipped();

    PgQueryBuilder b = new PgQueryBuilder(req.specs());
    var extractor = new PgFactExtractor(serial);
    String catchupSQL = b.createSQL();
    jdbc.query(
        catchupSQL,
        b.createStatementSetter(serial),
        createRowCallbackHandler(skipTesting, extractor));
    metrics
        .counter(EVENT.CATCHUP_FACT)
        .increment(factCounter); // TODO this needs to TAG it for each subscription?
  }

  @VisibleForTesting
  RowCallbackHandler createRowCallbackHandler(boolean skipTesting, PgFactExtractor extractor) {
    return rs -> {
      Fact f = Objects.requireNonNull(extractor.mapRow(rs, 0)); // does not use the rowNum anyway
      if (blacklist.isBlocked(f.id())) {
        log.trace("{} filtered blacklisted id={}", req, f.id());
      } else {
        if (skipTesting || postQueryMatcher.test(f)) {
          subscription.notifyElement(f);
          factCounter++;
        } else {
          log.trace("{} filtered id={}", req, f.id());
        }
      }
    };
  }
}
