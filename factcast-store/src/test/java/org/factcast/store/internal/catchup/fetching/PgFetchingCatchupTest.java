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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.FactTransformersFactory;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.PgMetrics;
import org.factcast.store.internal.PgPostQueryMatcher;
import org.factcast.store.internal.listen.PgConnectionSupplier;
import org.factcast.store.internal.query.PgQueryBuilder;
import org.factcast.store.internal.rowmapper.PgFactExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.postgresql.jdbc.PgConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import lombok.SneakyThrows;

@ExtendWith(MockitoExtension.class)
class PgFetchingCatchupTest {

  @Mock PgConnectionSupplier connectionSupplier;
  @Mock StoreConfigurationProperties props;
  @Mock SubscriptionRequestTO req;
  @Mock PgPostQueryMatcher postQueryMatcher;
  @Mock SubscriptionImpl subscription;
  @Mock FactTransformersFactory factTransformersFactory;
  @Mock AtomicLong serial;
  @Mock PgMetrics metrics;
  // need to inject those manually:
  @Mock DataSourceFactory dataSourceFactory;
  @Mock JdbcTemplateFactory jdbcTemplateFactory;
  @Mock PagingPreparedStatementCallbackFactory pagingPreparedStatementCallbackFactory;
  @Mock Function<List<FactSpec>, PgQueryBuilder> pgQueryBuilderFactory;
  @Mock Function<AtomicLong, PgFactExtractor> pgFactExtractorFactory;

  @InjectMocks PgFetchingCatchup underTest;

  @Mock List<FactSpec> specs;
  @Mock PgConnection con;
  @Mock SingleConnectionDataSource dataSource;
  @Mock JdbcTemplate jdbcTemplate;
  @Mock FetchingPreparedStatementCallback fetchingPreparedStatementCallback;
  @Mock PgQueryBuilder pgQueryBuilder;
  @Mock PgFactExtractor pgFactExtractor;
  @Mock PreparedStatementSetter preparedStatementSetter;

  @BeforeEach
  @SneakyThrows
  void setup() {
    underTest.dataSourceFactory(dataSourceFactory);
    underTest.jdbcTemplateFactory(jdbcTemplateFactory);
    underTest.pagingPreparedStatementCallbackFactory(pagingPreparedStatementCallbackFactory);
    underTest.pgQueryBuilderFactory(pgQueryBuilderFactory);
    underTest.pgFactExtractorFactory(pgFactExtractorFactory);

    when(pgQueryBuilderFactory.apply(specs)).thenReturn(pgQueryBuilder);
    when(pgFactExtractorFactory.apply(serial)).thenReturn(pgFactExtractor);
    when(pagingPreparedStatementCallbackFactory.create(
            preparedStatementSetter,
            pgFactExtractor,
            props,
            factTransformersFactory,
            subscription,
            metrics,
            req,
            postQueryMatcher))
        .thenReturn(fetchingPreparedStatementCallback);
    when(pgQueryBuilder.createStatementSetter(serial)).thenReturn(preparedStatementSetter);
    when(pgQueryBuilder.createSQL()).thenReturn("SELECT * FROM FACT WHERE ...");
    when(req.specs()).thenReturn(specs);
  }

  @Test
  @SneakyThrows
  void connectionHandling() {
    when(connectionSupplier.get()).thenReturn(con);
    when(dataSourceFactory.create(con, true)).thenReturn(dataSource);
    when(jdbcTemplateFactory.create(dataSource)).thenReturn(jdbcTemplate);

    underTest.run();

    InOrder inOrder =
        inOrder(con, dataSourceFactory, jdbcTemplateFactory, jdbcTemplate, dataSource);

    inOrder.verify(con).setAutoCommit(false);
    // need to check we set suppressClose to true
    inOrder.verify(dataSourceFactory).create(con, true);
    inOrder.verify(jdbcTemplateFactory).create(dataSource);
    // now run query
    inOrder.verify(jdbcTemplate).execute(anyString(), any(FetchingPreparedStatementCallback.class));
    // clean up
    inOrder.verify(dataSource).destroy();
    inOrder.verify(con).close();
  }

  @Test
  @SneakyThrows
  void runQuery() {

    underTest.fetch(jdbcTemplate);

    InOrder inOrder =
        inOrder(
            pgQueryBuilderFactory,
            pgFactExtractorFactory,
            jdbcTemplate,
            pagingPreparedStatementCallbackFactory);

    inOrder.verify(pgQueryBuilderFactory).apply(specs);
    inOrder.verify(pgFactExtractorFactory).apply(serial);

    inOrder
        .verify(pagingPreparedStatementCallbackFactory)
        .create(
            preparedStatementSetter,
            pgFactExtractor,
            props,
            factTransformersFactory,
            subscription,
            metrics,
            req,
            postQueryMatcher);

    inOrder
        .verify(jdbcTemplate)
        .execute("SELECT * FROM FACT WHERE ...", fetchingPreparedStatementCallback);
  }
}
