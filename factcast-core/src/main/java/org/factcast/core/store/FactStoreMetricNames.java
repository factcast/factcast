package org.factcast.core.store;

import lombok.Getter;
import lombok.NonNull;

/**
 * Constants based on a given prefix for metrics-names.
 * 
 * @author <uwe.schaefer@mercateo.com>
 *
 */
@Getter
public class FactStoreMetricNames {
    static final String PREFIX = "factstore.";

    final String type;

    final String factPublishingFailed;

    final String factPublishingLatency;

    final String factPublishingMeter;

    final String fetchLatency;

    final String seqLookupLatency;

    final String subscribeCatchup;

    final String subscribeFollow;

    final String connectionFailure;

    protected FactStoreMetricNames(@NonNull String type) {
        this.type = type;

        factPublishingFailed = PREFIX + type + ".publish.failure";
        factPublishingLatency = PREFIX + type + ".publish.fact.latency";
        factPublishingMeter = PREFIX + type + ".publish.fact.meter";

        fetchLatency = PREFIX + type + ".fetchById.latency";
        seqLookupLatency = PREFIX + type + ".seqLookup.latency";
        subscribeCatchup = PREFIX + type + ".subscribe.catchup";
        subscribeFollow = PREFIX + type + ".subscribe.follow";

        connectionFailure = PREFIX + type + ".pgsql.reconnect";
    }
}
