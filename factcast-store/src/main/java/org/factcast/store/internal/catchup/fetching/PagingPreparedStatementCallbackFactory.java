package org.factcast.store.internal.catchup.fetching;

import org.factcast.core.subscription.FactTransformers;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.PgMetrics;
import org.factcast.store.internal.PgPostQueryMatcher;
import org.factcast.store.internal.rowmapper.PgFactExtractor;
import org.springframework.jdbc.core.PreparedStatementSetter;

import lombok.NonNull;

public interface PagingPreparedStatementCallbackFactory {
  PagingPreparedStatementCallback create(
      @NonNull PreparedStatementSetter statementSetter,
      @NonNull PgFactExtractor extractor,
      @NonNull StoreConfigurationProperties props,
      @NonNull FactTransformers factTransformers,
      @NonNull SubscriptionImpl subscription,
      @NonNull PgMetrics metrics,
      @NonNull SubscriptionRequestTO req,
      @NonNull PgPostQueryMatcher postQueryMatcher);
}
