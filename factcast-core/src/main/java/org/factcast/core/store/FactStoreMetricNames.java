package org.factcast.core.store;

import lombok.Getter;
import lombok.NonNull;

@Getter
public class FactStoreMetricNames {
    final String type;

    final String factPublishingFailed;

    final String factPublished;

    final String factPublishingLatency;

    protected FactStoreMetricNames(@NonNull String type) {
        this.type = type;
        factPublishingFailed = "factstore." + type + ".publish.failure";
        factPublished = "factstore." + type + ".publish.fact.count";
        factPublishingLatency = "factstore." + type + ".publish.fact.latency";
    }
}
