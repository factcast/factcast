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
package org.factcast.core.lock;

import java.util.*;

import org.factcast.core.lock.WithOptimisticLock.OptimisticRetriesExceededException;
import org.factcast.core.store.FactStore;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class LockedOperationBuilder {
    @NonNull
    final FactStore store;

    final String ns;

    public OnBuilderStep on(@NonNull UUID aggId, UUID... otherAggIds) {
        LinkedList<UUID> ids = new LinkedList<>();
        ids.add(aggId);
        ids.addAll(Arrays.asList(otherAggIds));
        return new OnBuilderStep(ids);
    }

    public OnBuilderStep on(@NonNull Collection<UUID> aggIds) {
        LinkedList<UUID> ids = new LinkedList<>();
        ids.addAll(aggIds);
        return new OnBuilderStep(ids);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public class OnBuilderStep {
        @Getter(value = AccessLevel.PROTECTED)
        private final List<UUID> ids;

        public WithOptimisticLock optimistic() {
            return new WithOptimisticLock(store, ns, ids);
        }

        // we MIGHT add pessimistic if we REALLY REALLY have to

        /**
         * convenience method that uses optimistic locking with defaults.
         * Alternatively, you can call optimistic() to get control over the
         * optimistic settings.
         *
         * @param operation
         *            will be attempted to be executed, maybe many times
         * @return id of the last fact published
         * @throws OptimisticRetriesExceededException
         *             if max number of retries are reached
         * @throws ExceptionAfterPublish
         *             if andThen-block throws an exception
         * @throws AttemptAbortedException
         *             if calling Attempt.abort, operation will not be retried
         */
        public @NonNull PublishingResult attempt(@NonNull Attempt operation)
                throws OptimisticRetriesExceededException,
                ExceptionAfterPublish, AttemptAbortedException {
            return optimistic().attempt(operation);
        }

    }
}
