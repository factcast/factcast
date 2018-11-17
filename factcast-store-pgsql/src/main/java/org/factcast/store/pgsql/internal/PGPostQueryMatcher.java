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
package org.factcast.store.pgsql.internal;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.factcast.core.Fact;
import org.factcast.core.spec.FactSpecMatcher;
import org.factcast.core.subscription.SubscriptionRequest;

import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

/**
 * Predicate to filter Facts selected by the database query.
 *
 * For PG, we can safely assume that only those rows are returned from the DB,
 * that match the queryable criteria. The only untested thing is the
 * script-match which can be skipped, if no FactSpec has a scripted filter.
 *
 * @author uwe.schaefer@mercateo.com
 */
@Slf4j
public class PGPostQueryMatcher implements Predicate<Fact> {

    @Getter
    @Accessors(fluent = true)
    final boolean canBeSkipped;

    final List<FactSpecMatcher> matchers = new LinkedList<>();

    PGPostQueryMatcher(@NonNull SubscriptionRequest req) {
        canBeSkipped = req.specs().stream().noneMatch(s -> s.jsFilterScript() != null);
        if (canBeSkipped) {
            log.trace("{} post query filtering has been disabled", req);
        } else {
            this.matchers.addAll(req.specs().stream().map(FactSpecMatcher::new).collect(Collectors
                    .toList()));
        }
    }

    @Override
    public boolean test(Fact input) {
        return canBeSkipped || matchers.stream().anyMatch(m -> m.test(input));
    }
}
