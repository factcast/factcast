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
package org.factcast.highlevel.projection;

import java.util.UUID;
import java.util.function.Supplier;

import lombok.NonNull;

public abstract class LocalManagedProjection implements ManagedProjection {
    private final Object mutex = new Object();

    private UUID state = null;

    @Override
    public final UUID state() {
        return this.state;
    }

    @Override
    public final void state(@NonNull UUID state) {
        this.state = state;
    }

    @Override
    public final void withLock(@NonNull Runnable r) {
        withLock(() -> {
            r.run();
            return null;
        });
    }

    public <T> T withLock(Supplier<T> supplier) {
        synchronized (mutex) {
            return supplier.get();
        }
    }

}
