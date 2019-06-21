/*
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
package org.factcast.core;

import java.util.Collections;
import java.util.List;

import org.factcast.core.lock.LockedOperationBuilder;
import org.factcast.core.store.FactStore;

import lombok.NonNull;

/**
 * Main interface to work against as a client.
 * <p>
 * FactCast provides methods to publish Facts in a sync/async fashion, as well
 * as a subscription mechanism to listen for changes and catching up.
 *
 * @author uwe.schaefer@mercateo.com
 */
public interface FactCast extends ReadFactCast {

    void publish(@NonNull List<? extends Fact> factsToPublish);

    // / ---------- defaults
    default void publish(@NonNull Fact factToPublish) {
        publish(Collections.singletonList(factToPublish));
    }

    static FactCast from(@NonNull FactStore store) {
        return new DefaultFactCast(store);
    }

    static ReadFactCast fromReadOnly(@NonNull FactStore store) {
        return new DefaultFactCast(store);
    }

    default FactCast retry(int maxAttempts) {
        return retry(maxAttempts, Retry.DEFAULT_WAIT_TIME_MILLIS);
    }

    default FactCast retry(int maxAttempts, long minimumWaitIntervalMillis) {
        return Retry.wrap(this, maxAttempts, minimumWaitIntervalMillis);
    }

    LockedOperationBuilder lock(@NonNull String ns);

    LockedOperationBuilder lockGlobally();
}
