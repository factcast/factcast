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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.atomic.AtomicLong;

import org.factcast.core.Fact;
import org.factcast.core.TestFact;
import org.factcast.core.subscription.FactTransformers;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.TransformationException;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.PgMetrics;
import org.factcast.store.internal.PgPostQueryMatcher;
import org.factcast.store.internal.listen.PgConnectionSupplier;
import org.factcast.store.internal.rowmapper.PgFactExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.postgresql.jdbc.PgConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;

import lombok.NonNull;
import lombok.SneakyThrows;

@ExtendWith(MockitoExtension.class)
class PgFetchingCatchupTest {

  @Mock @NonNull PgConnectionSupplier connectionSupplier;
  @Mock @NonNull StoreConfigurationProperties props;
  @Mock @NonNull SubscriptionRequestTO req;
  @Mock @NonNull PgPostQueryMatcher postQueryMatcher;
  @Mock @NonNull SubscriptionImpl subscription;
  @Mock @NonNull FactTransformers factTransformers;
  @Mock @NonNull AtomicLong serial;
  @Mock @NonNull PgMetrics metrics;
  @InjectMocks PgFetchingCatchup underTest;

  @Mock @NonNull PreparedStatementSetter statementSetters;
  @Mock PgFactExtractor extractor;

  @Nested
  class WhenRunning {
    @BeforeEach
    void setup() {}

    @SneakyThrows
    @Test
    void connectionHandling() {
      PgConnection con = mock(PgConnection.class);
      when(connectionSupplier.get()).thenReturn(con);

      final var uut = spy(underTest);
      doNothing().when(uut).fetch(any());

      uut.run();

      verify(con).setAutoCommit(false);
      verify(con).close();
    }
  }

  @Nested
  class WhenFetching {
    @Mock @NonNull JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
      Mockito.when(props.getPageSize()).thenReturn(47);
    }

    @Test
    @SneakyThrows
    void setsCorrectFetchSize() {

      final var cbh =
          underTest.createPreparedStatementCallbackHandler(statementSetters, true, extractor);

      PreparedStatement preparedStatement = mock(PreparedStatement.class);
      ResultSet rs = mock(ResultSet.class);
      when(preparedStatement.executeQuery()).thenReturn(rs);

      cbh.doInPreparedStatement(preparedStatement);

      verify(preparedStatement).setFetchSize(eq(props.getPageSize()));
    }

    @Test
    @SneakyThrows
    void setsCorrectQueryTimeout() {

      final var cbh =
          underTest.createPreparedStatementCallbackHandler(statementSetters, true, extractor);

      PreparedStatement preparedStatement = mock(PreparedStatement.class);
      ResultSet rs = mock(ResultSet.class);
      when(preparedStatement.executeQuery()).thenReturn(rs);

      cbh.doInPreparedStatement(preparedStatement);

      verify(preparedStatement).setQueryTimeout(0);
    }
  }

  @Nested
  class WhenCreatingRowCallbackHandler {
    final boolean SKIP_TESTING = true;
    @Mock PgFactExtractor extractor;

    @BeforeEach
    void setup() {}

    @SneakyThrows
    @Test
    void skipsPostQueryMatching() {
      // INIT
      final var cbh =
          underTest.createPreparedStatementCallbackHandler(
              statementSetters, SKIP_TESTING, extractor);
      ResultSet rs = mock(ResultSet.class);
      when(rs.next()).thenReturn(true, false);

      Fact testFact = new TestFact();
      when(extractor.mapRow(same(rs), anyInt())).thenReturn(testFact);
      // return true, so if skipTesting is ignored, we would actually notify the subscription
      when(factTransformers.transformIfNecessary(any()))
          .thenAnswer(inv -> inv.getArgument(0, Fact.class));

      PreparedStatement preparedStatement = mock(PreparedStatement.class);
      when(preparedStatement.executeQuery()).thenReturn(rs);

      // RUN
      cbh.doInPreparedStatement(preparedStatement);

      // ASSERT
      InOrder inOrder =
          inOrder(
              statementSetters,
              preparedStatement,
              rs,
              factTransformers,
              extractor,
              postQueryMatcher);

      inOrder.verify(statementSetters).setValues(preparedStatement);

      inOrder.verify(preparedStatement).setQueryTimeout(0);
      inOrder.verify(preparedStatement).setFetchSize(props.getPageSize());

      inOrder.verify(preparedStatement).executeQuery();

      inOrder.verify(rs).next();

      inOrder.verify(extractor).mapRow(rs, 0);

      inOrder.verify(factTransformers).transformIfNecessary(testFact);

      verify(subscription).notifyElement(testFact);

      inOrder.verify(rs).next();

      inOrder.verify(rs).close();

      verifyNoInteractions(postQueryMatcher);
    }

    @SneakyThrows
    @Test
    void filtersInPostQueryMatching() {
      // INIT
      final var cbh =
          underTest.createPreparedStatementCallbackHandler(statementSetters, false, extractor);
      ResultSet rs = mock(ResultSet.class);
      when(rs.next()).thenReturn(true, false);

      Fact testFact = new TestFact();
      when(extractor.mapRow(same(rs), anyInt())).thenReturn(testFact);
      when(postQueryMatcher.test(testFact)).thenReturn(false);
      when(factTransformers.transformIfNecessary(any()))
          .thenAnswer(inv -> inv.getArgument(0, Fact.class));

      PreparedStatement preparedStatement = mock(PreparedStatement.class);
      when(preparedStatement.executeQuery()).thenReturn(rs);

      // RUN
      cbh.doInPreparedStatement(preparedStatement);

      // ASSERT
      InOrder inOrder =
          inOrder(
              statementSetters,
              preparedStatement,
              rs,
              factTransformers,
              extractor,
              postQueryMatcher);

      inOrder.verify(statementSetters).setValues(preparedStatement);

      inOrder.verify(preparedStatement).setQueryTimeout(0);
      inOrder.verify(preparedStatement).setFetchSize(props.getPageSize());

      inOrder.verify(preparedStatement).executeQuery();

      inOrder.verify(rs).next();

      inOrder.verify(extractor).mapRow(rs, 0);

      inOrder.verify(factTransformers).transformIfNecessary(testFact);

      inOrder.verify(postQueryMatcher).test(testFact);

      inOrder.verify(rs).next();

      inOrder.verify(rs).close();

      verifyNoInteractions(subscription);
    }

    @SneakyThrows
    @Test
    void notifies() {
      final var cbh =
          underTest.createPreparedStatementCallbackHandler(statementSetters, false, extractor);
      ResultSet rs = mock(ResultSet.class);
      when(rs.next()).thenReturn(true, false);
      Fact testFact = new TestFact();
      when(extractor.mapRow(same(rs), anyInt())).thenReturn(testFact);
      when(postQueryMatcher.test(testFact)).thenReturn(true);
      when(factTransformers.transformIfNecessary(any()))
          .thenAnswer(inv -> inv.getArgument(0, Fact.class));

      PreparedStatement preparedStatement = mock(PreparedStatement.class);
      when(preparedStatement.executeQuery()).thenReturn(rs);

      // RUN
      cbh.doInPreparedStatement(preparedStatement);

      // ASSERT
      InOrder inOrder =
          inOrder(
              statementSetters,
              preparedStatement,
              rs,
              factTransformers,
              subscription,
              extractor,
              postQueryMatcher);

      inOrder.verify(statementSetters).setValues(preparedStatement);

      inOrder.verify(preparedStatement).setQueryTimeout(0);
      inOrder.verify(preparedStatement).setFetchSize(props.getPageSize());

      inOrder.verify(preparedStatement).executeQuery();

      inOrder.verify(rs).next();

      inOrder.verify(extractor).mapRow(rs, 0);

      inOrder.verify(factTransformers).transformIfNecessary(testFact);

      inOrder.verify(postQueryMatcher).test(testFact);

      verify(subscription).notifyElement(testFact);

      inOrder.verify(rs).next();

      inOrder.verify(rs).close();
    }

    @SneakyThrows
    @Test
    void notifiesTransformationException() {
      final var cbh =
          underTest.createPreparedStatementCallbackHandler(statementSetters, false, extractor);
      ResultSet rs = mock(ResultSet.class);
      when(rs.next()).thenReturn(true, false);
      Fact testFact = new TestFact();
      when(extractor.mapRow(same(rs), anyInt())).thenReturn(testFact);
      doThrow(TransformationException.class).when(factTransformers).transformIfNecessary(any());

      PreparedStatement preparedStatement = mock(PreparedStatement.class);
      when(preparedStatement.executeQuery()).thenReturn(rs);

      // just test that it'll be escalated unchanged from the code,
      // so that it can be handled in PgSubscriptionFactory
      assertThatThrownBy(
              () -> {
                cbh.doInPreparedStatement(preparedStatement);
              })
          .isInstanceOf(TransformationException.class);
    }
  }
}
