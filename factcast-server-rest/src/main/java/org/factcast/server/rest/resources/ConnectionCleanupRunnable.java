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
package org.factcast.server.rest.resources;

import java.util.concurrent.CompletableFuture;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConnectionCleanupRunnable implements Runnable {

    private final FactsObserver observer;

    private final CompletableFuture<Void> future;

    public ConnectionCleanupRunnable(@NonNull FactsObserver observer,
            @NonNull CompletableFuture<Void> future) {
        this.observer = observer;
        this.future = future;
    }

    @Override
    public void run() {
        if (!observer.isConnectionAlive()) {
            observer.unsubscribe();
            future.complete(null);
            log.debug("unsubscribe pending connection");
        }

    }

}
