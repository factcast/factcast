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

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.FactTransformers;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.PgMetrics;
import org.factcast.store.internal.PgPostQueryMatcher;
import org.factcast.store.internal.catchup.PgCatchup;
import org.factcast.store.internal.listen.PgConnectionSupplier;
import org.factcast.store.internal.query.PgQueryBuilder;
import org.factcast.store.internal.rowmapper.PgFactExtractor;
import org.postgresql.jdbc.PgConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import com.google.common.annotations.VisibleForTesting;

import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
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

  @NonNull final FactTransformers factTransformers;

  @NonNull final AtomicLong serial;

  @NonNull final PgMetrics metrics;

  @NonNull
  @Setter(value = AccessLevel.PACKAGE, onMethod = @__(@VisibleForTesting))
  DataSourceFactory dataSourceFactory = SingleConnectionDataSource::new;

  @NonNull
  @Setter(value = AccessLevel.PACKAGE, onMethod = @__(@VisibleForTesting))
  JdbcTemplateFactory jdbcTemplateFactory = JdbcTemplate::new;

  @NonNull
  @Setter(value = AccessLevel.PACKAGE, onMethod = @__(@VisibleForTesting))
  PagingPreparedStatementCallbackFactory pagingPreparedStatementCallbackFactory =
      PagingPreparedStatementCallback::new;

  @NonNull
  @Setter(value = AccessLevel.PACKAGE, onMethod = @__(@VisibleForTesting))
  Function<List<FactSpec>, PgQueryBuilder> pgQueryBuilderFactory = PgQueryBuilder::new;

  @NonNull
  @Setter(value = AccessLevel.PACKAGE, onMethod = @__(@VisibleForTesting))
  Function<AtomicLong, PgFactExtractor> pgFactExtractorFactory = PgFactExtractor::new;

  @SneakyThrows
  @Override
  public void run() {

    try (PgConnection connection = connectionSupplier.get()) {
      connection.setAutoCommit(false); // necessary for using cursors

      // connection may stay open quite a while, and we do not want a CPool to interfere
      SingleConnectionDataSource ds = dataSourceFactory.create(connection, true);

      try {
        var jdbc = jdbcTemplateFactory.create(ds);
        fetch(jdbc);
      } finally {
        ds.destroy();
      }
    }
  }

  @VisibleForTesting
  void fetch(JdbcTemplate jdbc) {

    var b = pgQueryBuilderFactory.apply(req.specs());
    var extractor = pgFactExtractorFactory.apply(serial);
    var catchupSQL = b.createSQL();

    jdbc.execute(
        catchupSQL,
        pagingPreparedStatementCallbackFactory.create(
            b.createStatementSetter(serial),
            extractor,
            props,
            factTransformers,
            subscription,
            metrics,
            req,
            postQueryMatcher));
  }
}
