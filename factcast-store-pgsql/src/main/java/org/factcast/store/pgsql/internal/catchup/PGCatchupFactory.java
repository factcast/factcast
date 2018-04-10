package org.factcast.store.pgsql.internal.catchup;

import java.util.concurrent.atomic.AtomicLong;

import org.factcast.core.Fact;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.pgsql.internal.PGPostQueryMatcher;

import lombok.NonNull;

public interface PGCatchupFactory {

    PGCatchup create(@NonNull SubscriptionRequestTO request,
            @NonNull PGPostQueryMatcher postQueryMatcher,
            @NonNull SubscriptionImpl<Fact> subscription, @NonNull AtomicLong serial);

}