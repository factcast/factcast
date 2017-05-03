package org.factcast.core.store;

import lombok.Getter;
import lombok.NonNull;

@Getter
public class FactStoreMetricNames {
    static final String PREFIX = "factstore.";

    final String type;

    final String factPublishingFailed;

    final String factPublishingLatency;

    final String factPublishingMeter;

    final String fetchLatency;

    final String subscribeCatchup;

    final String subscribeFollow;

    final String connectionFailure;

    protected FactStoreMetricNames(@NonNull String type) {
        this.type = type;

        factPublishingFailed = PREFIX + type + ".publish.failure";
        factPublishingLatency = PREFIX + type + ".publish.fact.latency";
        factPublishingMeter = PREFIX + type + ".publish.fact.meter";

        fetchLatency = PREFIX + type + ".fetchById.latency";
        subscribeCatchup = PREFIX + type + ".subscribe.catchup";
        subscribeFollow = PREFIX + type + ".subscribe.follow";

        connectionFailure = PREFIX + type + ".pgsql.reconnect";
    }
}
