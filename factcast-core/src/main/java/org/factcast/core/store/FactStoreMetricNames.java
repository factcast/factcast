/**
 * Copyright © 2018 Mercateo AG (http://www.mercateo.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.factcast.core.store;

import lombok.Getter;
import lombok.NonNull;

/**
 * Constants based on a given prefix for metrics-names.
 *
 * @author <uwe.schaefer@mercateo.com>
 */
@Getter
public class FactStoreMetricNames {

    static final String PREFIX = "factstore.";

    final String type;

    final String factPublishingFailed;

    final String factPublishingLatency;

    final String factPublishingMeter;

    final String fetchLatency;

    final String namespaceLatency;

    final String typeLatency;

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
        namespaceLatency = PREFIX + type + ".distinctNamespaces.latency";
        typeLatency = PREFIX + type + ".distinctTypeByNamespace.latency";
        seqLookupLatency = PREFIX + type + ".seqLookup.latency";
        subscribeCatchup = PREFIX + type + ".subscribe.catchup";
        subscribeFollow = PREFIX + type + ".subscribe.follow";
        connectionFailure = PREFIX + type + ".pgsql.reconnect";
    }
}
