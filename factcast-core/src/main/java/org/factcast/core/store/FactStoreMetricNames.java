package org.factcast.core.store;

import lombok.Getter;
import lombok.NonNull;

@Getter
public class FactStoreMetricNames {
    static final String PREFIX = "factstore.";

    final String type;

    final String factPublishingFailed;

    final String factPublished;

    final String factPublishingLatency;

    protected FactStoreMetricNames(@NonNull String type) {
        this.type = type;

        factPublishingFailed = PREFIX + type + ".publish.failure";
        factPublished = PREFIX + type + ".publish.fact.count";
        factPublishingLatency = PREFIX + type + ".publish.fact.latency";
    }
}
