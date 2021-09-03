package org.factcast.store.internal.catchup.fetching;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.factcast.core.Fact;
import org.factcast.core.subscription.FactTransformersFactory;
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
public class FetchingPreparedStatementCallback implements PreparedStatementCallback<Void> {

  @NonNull private final PreparedStatementSetter statementSetter;
  @NonNull private final PgFactExtractor extractor;
  @NonNull private final StoreConfigurationProperties props;
  @NonNull private final FactTransformersFactory factTransformersFactory;
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

    List<Fact> fetchedFacts = new ArrayList<>(props.getPageSize());

    while (true) {
      fetchFacts(resultSet, fetchedFacts);

      if (fetchedFacts.isEmpty()) {
        break;
      }

      var factTransformers = factTransformersFactory.createBatchFactTransformers(req, fetchedFacts);

      fetchedFacts.stream()
          .map(factTransformers::transformIfNecessary)
          .forEachOrdered(this::processFact);
    }
  }

  private void fetchFacts(@NonNull ResultSet resultSet, List<Fact> fetchedFacts)
      throws SQLException {

    fetchedFacts.clear();

    for (int i = 0; i < props.getPageSize(); i++) {
      // proceed to next record if there are more; otherwise stop
      if (!resultSet.next()) {
        return;
      }

      Fact fact = extractor.mapRow(resultSet, 0); // does not use the rowNum anyway
      fetchedFacts.add(fact);
    }
  }

  private void processFact(@NonNull Fact f) {
    if (postQueryMatcher.canBeSkipped() || postQueryMatcher.test(f)) {
      subscription.notifyElement(f);
      metrics.counter(StoreMetrics.EVENT.CATCHUP_FACT);
    } else {
      log.trace("{} filtered id={}", req, f.id());
    }
  }
}
