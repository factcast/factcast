package org.factcast.store.internal.catchup.fetching;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.core.TestFact;
import org.factcast.core.subscription.FactTransformers;
import org.factcast.core.subscription.FactTransformersFactory;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.TransformationException;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.PgMetrics;
import org.factcast.store.internal.PgPostQueryMatcher;
import org.factcast.store.internal.StoreMetrics;
import org.factcast.store.internal.rowmapper.PgFactExtractor;
import org.factcast.test.Slf4jHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.PreparedStatementSetter;

import lombok.SneakyThrows;
import slf4jtest.TestLogger;

@ExtendWith(MockitoExtension.class)
class FetchingPreparedStatementCallbackTest {
  @Mock PreparedStatementSetter statementSetters;
  @Mock PgFactExtractor extractor;
  @Mock StoreConfigurationProperties props;
  @Mock SubscriptionImpl subscription;
  @Mock FactTransformersFactory factTransformersFactory;
  @Mock PgMetrics metrics;
  @Mock SubscriptionRequestTO req;
  @Mock PgPostQueryMatcher postQueryMatcher;

  @InjectMocks FetchingPreparedStatementCallback underTest;

  @Mock FactTransformers factTransformers;
  @Mock FactTransformers factTransformers2;
  @Mock PreparedStatement preparedStatement;
  @Mock ResultSet rs;

  @BeforeEach
  @SneakyThrows
  void setup() {
    Mockito.when(props.getPageSize()).thenReturn(2);
    when(preparedStatement.executeQuery()).thenReturn(rs);
  }

  @Test
  @SneakyThrows
  void setsCorrectFetchSize() {
    when(rs.next()).thenReturn(false);
    underTest.doInPreparedStatement(preparedStatement);
    verify(preparedStatement).setFetchSize(2);
  }

  @Test
  @SneakyThrows
  void setsCorrectQueryTimeout() {
    when(rs.next()).thenReturn(false);
    underTest.doInPreparedStatement(preparedStatement);
    verify(preparedStatement).setQueryTimeout(0);
  }

  @SneakyThrows
  @Test
  void skipsPostQueryMatching() {
    // INIT
    when(postQueryMatcher.canBeSkipped()).thenReturn(true);
    // return true, so if skipTesting is ignored, we would actually notify the subscription
    when(rs.next()).thenReturn(true, false);

    Fact testFact = new TestFact();
    when(extractor.mapRow(same(rs), anyInt())).thenReturn(testFact);
    when(factTransformersFactory.createBatchFactTransformers(req, newArrayList(testFact)))
        .thenReturn(factTransformers);
    when(factTransformers.transformIfNecessary(any()))
        .thenAnswer(inv -> inv.getArgument(0, Fact.class));

    when(preparedStatement.executeQuery()).thenReturn(rs);

    // RUN
    underTest.doInPreparedStatement(preparedStatement);

    // ASSERT
    InOrder inOrder =
        inOrder(
            statementSetters,
            preparedStatement,
            rs,
            factTransformersFactory,
            factTransformers,
            extractor,
            postQueryMatcher,
            subscription,
            metrics);

    inOrder.verify(statementSetters).setValues(preparedStatement);

    inOrder.verify(preparedStatement).setQueryTimeout(0);
    inOrder.verify(preparedStatement).setFetchSize(props.getPageSize());

    inOrder.verify(preparedStatement).executeQuery();

    inOrder.verify(rs).next();

    inOrder.verify(extractor).mapRow(rs, 0);

    inOrder.verify(factTransformersFactory).createBatchFactTransformers(eq(req), any());

    inOrder.verify(factTransformers).transformIfNecessary(testFact);

    inOrder.verify(postQueryMatcher).canBeSkipped();

    inOrder.verify(subscription).notifyElement(testFact);
    inOrder.verify(metrics).counter(StoreMetrics.EVENT.CATCHUP_FACT);

    inOrder.verify(rs).next();

    inOrder.verify(rs).close();

    verifyNoMoreInteractions(postQueryMatcher);
  }

  @SneakyThrows
  @Test
  void filtersInPostQueryMatching() {
    // INIT
    TestLogger logger = Slf4jHelper.replaceLogger(underTest);
    when(req.toString()).thenReturn("request123");

    when(rs.next()).thenReturn(true, false);

    Fact testFact = new TestFact();
    when(extractor.mapRow(same(rs), anyInt())).thenReturn(testFact);
    when(postQueryMatcher.canBeSkipped()).thenReturn(false);
    when(postQueryMatcher.test(testFact)).thenReturn(false);
    when(factTransformersFactory.createBatchFactTransformers(req, newArrayList(testFact)))
        .thenReturn(factTransformers);

    when(factTransformers.transformIfNecessary(any()))
        .thenAnswer(inv -> inv.getArgument(0, Fact.class));

    when(preparedStatement.executeQuery()).thenReturn(rs);

    // RUN
    underTest.doInPreparedStatement(preparedStatement);

    // ASSERT
    InOrder inOrder =
        inOrder(
            statementSetters,
            preparedStatement,
            rs,
            factTransformersFactory,
            factTransformers,
            extractor,
            postQueryMatcher);

    inOrder.verify(statementSetters).setValues(preparedStatement);

    inOrder.verify(preparedStatement).setQueryTimeout(0);
    inOrder.verify(preparedStatement).setFetchSize(props.getPageSize());

    inOrder.verify(preparedStatement).executeQuery();

    inOrder.verify(rs).next();

    inOrder.verify(extractor).mapRow(rs, 0);

    inOrder.verify(factTransformersFactory).createBatchFactTransformers(eq(req), any());

    inOrder.verify(factTransformers).transformIfNecessary(testFact);

    inOrder.verify(postQueryMatcher).canBeSkipped();

    inOrder.verify(postQueryMatcher).test(testFact);

    inOrder.verify(rs).next();

    inOrder.verify(rs).close();

    verifyNoInteractions(subscription);

    assertThat(logger.contains("request123 filtered id=" + testFact.id())).isTrue();
  }

  @SneakyThrows
  @Test
  void notifies() {
    when(rs.next()).thenReturn(true, true, true, false, false);

    Fact testFact1 =
        new TestFact(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            emptySet(),
            "type",
            0,
            "ns",
            "{}",
            emptyMap(),
            null);

    Fact testFact2 =
        new TestFact(
            UUID.fromString("22222222-2222-2222-2222-222222222222"),
            emptySet(),
            "type",
            0,
            "ns",
            "{}",
            emptyMap(),
            null);

    Fact testFact3 =
        new TestFact(
            UUID.fromString("33333333-3333-3333-3333-333333333333"),
            emptySet(),
            "type",
            0,
            "ns",
            "{}",
            emptyMap(),
            null);

    Fact transformedFact4 =
        new TestFact(
            UUID.fromString("44444444-4444-4444-4444-444444444444"),
            emptySet(),
            "type",
            0,
            "ns",
            "{}",
            emptyMap(),
            null);

    when(extractor.mapRow(same(rs), anyInt())).thenReturn(testFact1, testFact2, testFact3);
    when(postQueryMatcher.canBeSkipped()).thenReturn(false);

    when(postQueryMatcher.test(testFact1)).thenReturn(true);
    when(postQueryMatcher.test(testFact2)).thenReturn(true);
    when(postQueryMatcher.test(transformedFact4)).thenReturn(true);

    when(factTransformersFactory.createBatchFactTransformers(
            req, newArrayList(testFact1, testFact2)))
        .thenReturn(factTransformers);

    when(factTransformersFactory.createBatchFactTransformers(req, newArrayList(testFact3)))
        .thenReturn(factTransformers2);

    when(factTransformers.transformIfNecessary(any()))
        .thenAnswer(inv -> inv.getArgument(0, Fact.class));

    // the third event should be transformed
    when(factTransformers2.transformIfNecessary(any())).thenReturn(transformedFact4);

    when(preparedStatement.executeQuery()).thenReturn(rs);

    // RUN
    underTest.doInPreparedStatement(preparedStatement);

    // ASSERT
    InOrder inOrder =
        inOrder(
            statementSetters,
            preparedStatement,
            rs,
            factTransformersFactory,
            factTransformers,
            factTransformers2,
            subscription,
            extractor,
            postQueryMatcher,
            metrics);

    inOrder.verify(statementSetters).setValues(preparedStatement);

    inOrder.verify(preparedStatement).setQueryTimeout(0);
    inOrder.verify(preparedStatement).setFetchSize(props.getPageSize());

    inOrder.verify(preparedStatement).executeQuery();

    // get first batch
    inOrder.verify(rs).next();
    inOrder.verify(extractor).mapRow(rs, 0);
    inOrder.verify(rs).next();
    inOrder.verify(extractor).mapRow(rs, 0);

    inOrder.verify(factTransformersFactory).createBatchFactTransformers(eq(req), any());

    inOrder.verify(factTransformers).transformIfNecessary(testFact1);
    inOrder.verify(postQueryMatcher).canBeSkipped();
    inOrder.verify(postQueryMatcher).test(testFact1);
    inOrder.verify(subscription).notifyElement(testFact1);
    inOrder.verify(metrics).counter(StoreMetrics.EVENT.CATCHUP_FACT);

    inOrder.verify(factTransformers).transformIfNecessary(testFact2);
    inOrder.verify(postQueryMatcher).canBeSkipped();
    inOrder.verify(postQueryMatcher).test(testFact2);
    inOrder.verify(subscription).notifyElement(testFact2);
    inOrder.verify(metrics).counter(StoreMetrics.EVENT.CATCHUP_FACT);

    // get second batch
    inOrder.verify(rs).next();
    inOrder.verify(extractor).mapRow(rs, 0);
    // this time we get false, so stop fetching for this batch
    inOrder.verify(rs).next();

    inOrder.verify(factTransformersFactory).createBatchFactTransformers(eq(req), any());

    // will transform testFact3 to transformedFact3, which should be used afterwards
    inOrder.verify(factTransformers2).transformIfNecessary(testFact3);
    inOrder.verify(postQueryMatcher).canBeSkipped();
    inOrder.verify(postQueryMatcher).test(transformedFact4);
    inOrder.verify(subscription).notifyElement(transformedFact4);
    inOrder.verify(metrics).counter(StoreMetrics.EVENT.CATCHUP_FACT);

    inOrder.verify(rs).close();
  }

  @SneakyThrows
  @Test
  void notifiesTransformationException() {

    when(rs.next()).thenReturn(true, false);
    Fact testFact = new TestFact();
    when(extractor.mapRow(same(rs), anyInt())).thenReturn(testFact);
    when(factTransformersFactory.createBatchFactTransformers(req, newArrayList(testFact)))
        .thenReturn(factTransformers);
    doThrow(TransformationException.class).when(factTransformers).transformIfNecessary(any());

    when(preparedStatement.executeQuery()).thenReturn(rs);

    // just test that it'll be escalated unchanged from the code,
    // so that it can be handled in PgSubscriptionFactory
    assertThatThrownBy(
            () -> {
              underTest.doInPreparedStatement(preparedStatement);
            })
        .isInstanceOf(TransformationException.class);
  }
}
