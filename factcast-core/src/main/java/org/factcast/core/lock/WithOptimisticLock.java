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
package org.factcast.core.lock;

import java.util.*;

import org.factcast.core.*;
import org.factcast.core.store.*;

import lombok.*;
import lombok.experimental.*;
import lombok.extern.slf4j.*;

@Slf4j
@RequiredArgsConstructor
@Accessors(fluent = true, chain = true)
public class WithOptimisticLock {
    @NonNull
    @Getter(value = AccessLevel.PROTECTED)
    private final FactStore store;

    @Getter(value = AccessLevel.PROTECTED)
    private final String ns;

    @NonNull
    @Getter(value = AccessLevel.PROTECTED)
    private final List<UUID> ids;

    @Setter
    private int retry = 10;

    @Setter
    private long interval = 0;

    private int count = 0;

    @NonNull
    public UUID attempt(@NonNull Attempt operation) throws AttemptAbortedException,
            OptimisticRetriesExceededException,
            ExceptionAfterPublish {
        while (++count <= retry) {

            // fetch current state
            StateToken token = store.stateFor(ids, Optional.ofNullable(ns));
            try {

                // execute the business logic
                // in case an AttemptAbortedException is thrown, just pass it
                // through
                IntermediatePublishResult r = runAndWrapException(operation);

                UUID lastFactId = lastFactId(r.factsToPublish());

                // try to publish
                if (store.publishIfUnchanged(r.factsToPublish(), Optional.of(token))) {

                    // publishing worked
                    // now run the 'andThen' operation
                    try {
                        r.andThen().ifPresent(Runnable::run);
                    } catch (Throwable e) {
                        throw new ExceptionAfterPublish(lastFactId, e);
                    }

                    // and return the lastFactId for reference
                    return lastFactId;

                } else {
                    sleep();
                }
            } finally {
                store.invalidate(token);
            }
        }

        throw new OptimisticRetriesExceededException(retry);
    }

    private IntermediatePublishResult runAndWrapException(Attempt operation)
            throws AttemptAbortedException {

        try {
            IntermediatePublishResult ret = operation.call();
            if (ret == null) {
                // Attempt should not return null, this is an abuse of the API.
                log.error(
                        "Attempt should not return null, this is an abuse of the API. We will however treat it as an abort. Please fix the problem!");
                throw new AttemptAbortedException(
                        "Attempt aborted due to null-return. No message given.");
            }
            return ret;
        } catch (Exception e) {
            if (!AttemptAbortedException.class.isAssignableFrom(e.getClass()))
                throw new AttemptAbortedException(e);
            else
                throw e;
        }
    }

    @SneakyThrows
    private void sleep() {
        if (interval > 0)
            Thread.sleep(interval);
    }

    private UUID lastFactId(@NonNull List<Fact> factsToPublish) {

        if (factsToPublish.isEmpty())
            throw new IllegalArgumentException("Need to actually publish a Fact");

        return factsToPublish.get(factsToPublish.size() - 1).id();
    }

    @Getter
    public static final class OptimisticRetriesExceededException extends
            ConcurrentModificationException {

        private static final long serialVersionUID = 1L;

        private final int retries;

        public OptimisticRetriesExceededException(int retry) {
            super("Exceeded the maximum number of retrys allowed (" + retry + ")");
            retries = retry;
        }

    }
}
