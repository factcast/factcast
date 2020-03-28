/*
 * Copyright © 2017-2020 factcast.org
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
package org.factcast.core.subscription;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.factcast.core.spec.FactSpec;

import lombok.NonNull;

/**
 * Defines a request for Subscription.
 *
 * see {@link FluentSubscriptionRequest}, {@link SubscriptionRequestTO}
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
public interface SubscriptionRequest {

    long maxBatchDelayInMs();

    boolean continuous();

    boolean ephemeral();

    Optional<UUID> startingAfter();

    List<FactSpec> specs();

    String debugInfo();

    // ------------
    static SpecBuilder follow(@NonNull FactSpec specification) {
        return new FluentSubscriptionRequest.Builder(new FluentSubscriptionRequest()).follow(
                specification);
    }

    static SpecBuilder follow(long maxBatchDelayInMs, @NonNull FactSpec specification) {
        FluentSubscriptionRequest toBuild = new FluentSubscriptionRequest();
        toBuild.maxBatchDelayInMs = maxBatchDelayInMs;
        return new FluentSubscriptionRequest.Builder(toBuild).follow(specification);
    }

    static SpecBuilder catchup(@NonNull FactSpec specification) {
        return new FluentSubscriptionRequest.Builder(new FluentSubscriptionRequest()).catchup(
                specification);
    }

    // convenience
    static SpecBuilder catchup(@NonNull Collection<FactSpec> specification) {
        return new FluentSubscriptionRequest.Builder(new FluentSubscriptionRequest()).catchup(
                specification);
    }

    static SpecBuilder follow(@NonNull Collection<FactSpec> specification) {
        return new FluentSubscriptionRequest.Builder(new FluentSubscriptionRequest()).follow(
                specification);
    }
}
