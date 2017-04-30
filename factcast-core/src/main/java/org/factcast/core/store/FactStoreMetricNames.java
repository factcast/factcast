package org.factcast.core.store;

import lombok.Getter;
import lombok.NonNull;

@Getter
public class FactStoreMetricNames {
    private final String type;

    protected FactStoreMetricNames(@NonNull String type) {
        this.type = type;
        factPublishingFailed = "factstore." + type + ".publish.failure";
        factPublished = "factstore." + type + ".publish.fact.count";
        factPublishingLatency = "factstore." + type + ".publish.fact.latency";
    }

    private final String factPublishingFailed;

    private final String factPublished;

    private final String factPublishingLatency;
}
