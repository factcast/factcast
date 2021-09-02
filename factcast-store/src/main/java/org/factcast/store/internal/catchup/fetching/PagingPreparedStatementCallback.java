package org.factcast.store.internal.catchup.fetching;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.factcast.core.Fact;
import org.factcast.core.subscription.FactTransformers;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.PgMetrics;
import org.factcast.store.internal.PgPostQueryMatcher;
import org.factcast.store.internal.StoreMetrics;
import org.factcast.store.internal.rowmapper.PgFactExtractor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.PreparedStatementSetter;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class PagingPreparedStatementCallback implements PreparedStatementCallback<Void> {

  @NonNull private final PreparedStatementSetter statementSetter;
  @NonNull private final PgFactExtractor extractor;
  @NonNull private final StoreConfigurationProperties props;
  @NonNull private final FactTransformers factTransformers;
  @NonNull private final SubscriptionImpl subscription;
  @NonNull private final PgMetrics metrics;
  @NonNull private final SubscriptionRequestTO req;
  @NonNull private final PgPostQueryMatcher postQueryMatcher;

  @Override
  public Void doInPreparedStatement(@NonNull PreparedStatement ps)
      throws SQLException, DataAccessException {

    statementSetter.setValues(ps);

    ps.setQueryTimeout(0); // disable query timeout
    ps.setFetchSize(props.getPageSize());

    try (var resultSet = ps.executeQuery()) {
      iterateOverResultSet(resultSet);
    }

    return null;
  }

  private void iterateOverResultSet(@NonNull ResultSet resultSet) throws SQLException {

    while (resultSet.next()) {
      Fact f = extractor.mapRow(resultSet, 0); // does not use the rowNum anyway
      Fact transformed = factTransformers.transformIfNecessary(f);

      if (postQueryMatcher.canBeSkipped() || postQueryMatcher.test(transformed)) {
        subscription.notifyElement(transformed);
        metrics.counter(StoreMetrics.EVENT.CATCHUP_FACT);
      } else {
        log.trace("{} filtered id={}", req, transformed.id());
      }
    }
  }
}
