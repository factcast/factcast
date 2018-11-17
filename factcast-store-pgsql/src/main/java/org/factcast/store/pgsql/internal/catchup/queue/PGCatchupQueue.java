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
package org.factcast.store.pgsql.internal.catchup.queue;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.factcast.core.Fact;

public class PGCatchupQueue {

    @lombok.experimental.Delegate
    final BlockingQueue<Fact> queue;

    final AtomicBoolean done = new AtomicBoolean(false);

    public PGCatchupQueue(int capacity) {
        queue = new LinkedBlockingQueue<>(capacity);
    }

    public boolean isDone() {
        return done.get();
    }

    public void notifyDone() {
        done.set(true);
    }
}
