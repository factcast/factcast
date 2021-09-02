package org.factcast.store.internal.catchup.fetching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.factcast.core.Fact;
import org.factcast.core.TestFact;
import org.factcast.core.subscription.FactTransformers;
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
class PagingPreparedStatementCallbackTest {
  @Mock PreparedStatementSetter statementSetters;
  @Mock PgFactExtractor extractor;
  @Mock StoreConfigurationProperties props;
  @Mock FactTransformers factTransformers;
  @Mock SubscriptionImpl subscription;
  @Mock PgMetrics metrics;
  @Mock SubscriptionRequestTO req;
  @Mock PgPostQueryMatcher postQueryMatcher;

  @InjectMocks PagingPreparedStatementCallback underTest;

  @Mock PreparedStatement preparedStatement;
  @Mock ResultSet rs;

  @BeforeEach
  @SneakyThrows
  void setup() {
    Mockito.when(props.getPageSize()).thenReturn(47);
    when(preparedStatement.executeQuery()).thenReturn(rs);
  }

  @Test
  @SneakyThrows
  void setsCorrectFetchSize() {
    when(rs.next()).thenReturn(false);
    underTest.doInPreparedStatement(preparedStatement);
    verify(preparedStatement).setFetchSize(47);
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
    when(rs.next()).thenReturn(true, false);

    Fact testFact = new TestFact();
    when(extractor.mapRow(same(rs), anyInt())).thenReturn(testFact);
    // return true, so if skipTesting is ignored, we would actually notify the subscription
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
    when(factTransformers.transformIfNecessary(any()))
        .thenAnswer(inv -> inv.getArgument(0, Fact.class));

    when(preparedStatement.executeQuery()).thenReturn(rs);

    // RUN
    underTest.doInPreparedStatement(preparedStatement);

    // ASSERT
    InOrder inOrder =
        inOrder(
            statementSetters, preparedStatement, rs, factTransformers, extractor, postQueryMatcher);

    inOrder.verify(statementSetters).setValues(preparedStatement);

    inOrder.verify(preparedStatement).setQueryTimeout(0);
    inOrder.verify(preparedStatement).setFetchSize(props.getPageSize());

    inOrder.verify(preparedStatement).executeQuery();

    inOrder.verify(rs).next();

    inOrder.verify(extractor).mapRow(rs, 0);

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
    when(rs.next()).thenReturn(true, false);
    Fact testFact = new TestFact();
    when(extractor.mapRow(same(rs), anyInt())).thenReturn(testFact);
    when(postQueryMatcher.canBeSkipped()).thenReturn(false);
    when(postQueryMatcher.test(testFact)).thenReturn(true);
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
            factTransformers,
            subscription,
            extractor,
            postQueryMatcher,
            metrics);

    inOrder.verify(statementSetters).setValues(preparedStatement);

    inOrder.verify(preparedStatement).setQueryTimeout(0);
    inOrder.verify(preparedStatement).setFetchSize(props.getPageSize());

    inOrder.verify(preparedStatement).executeQuery();

    inOrder.verify(rs).next();

    inOrder.verify(extractor).mapRow(rs, 0);

    inOrder.verify(factTransformers).transformIfNecessary(testFact);

    inOrder.verify(postQueryMatcher).canBeSkipped();

    inOrder.verify(postQueryMatcher).test(testFact);

    inOrder.verify(subscription).notifyElement(testFact);
    inOrder.verify(metrics).counter(StoreMetrics.EVENT.CATCHUP_FACT);

    inOrder.verify(rs).next();

    inOrder.verify(rs).close();
  }

  @SneakyThrows
  @Test
  void notifiesTransformationException() {

    when(rs.next()).thenReturn(true, false);
    Fact testFact = new TestFact();
    when(extractor.mapRow(same(rs), anyInt())).thenReturn(testFact);
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
