package org.factcast.store.pgsql.internal;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PGMetrics {

    String FACT_PUBLISHING_FAILED = "factstore.publish.failure";

    String FACT_PUBLISHED = "factstore.publish.fact.count";

}
