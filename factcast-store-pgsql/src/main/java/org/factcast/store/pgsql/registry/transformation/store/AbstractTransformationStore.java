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
package org.factcast.store.pgsql.registry.transformation.store;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.factcast.store.pgsql.registry.transformation.TransformationConflictException;
import org.factcast.store.pgsql.registry.transformation.TransformationSource;
import org.factcast.store.pgsql.registry.transformation.TransformationStore;
import org.factcast.store.pgsql.registry.transformation.TransformationStoreListener;

import lombok.AccessLevel;
import lombok.Getter;

public abstract class AbstractTransformationStore implements TransformationStore {

    private final List<TransformationStoreListener> listeners = new CopyOnWriteArrayList<>();

    @Getter(value = AccessLevel.PROTECTED)
    private final ExecutorService executorService = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    @Override
    public final void store(TransformationSource source, String transformation)
            throws TransformationConflictException {
        doStore(source, transformation);
        // uses task per listener to avoid a listener throwing an exception
        // spoil the whole thing
        listeners.forEach(t -> executorService.submit(() -> {
            t.notifyFor(source.toKey());
        }));

    }

    protected abstract void doStore(TransformationSource source, String transformation);

    @Override
    public void register(TransformationStoreListener listener) {
        listeners.add(listener);
    }

    @Override
    public void unregister(TransformationStoreListener listener) {
        listeners.remove(listener);
    }

}
