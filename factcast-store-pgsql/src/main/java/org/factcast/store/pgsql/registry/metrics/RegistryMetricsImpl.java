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
package org.factcast.store.pgsql.registry.metrics;

import java.util.function.Supplier;

import com.google.common.base.Stopwatch;

import io.micrometer.core.instrument.*;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;

@RequiredArgsConstructor
public class RegistryMetricsImpl implements RegistryMetrics {
    public static final String METRIC_NAME_OPERATIONS = "factcast.registry.operations";

    public static final String METRIC_NAME_EVENTS = "factcast.registry.events";

    public static final String TAG_NAME_KEY = "name";

    private final MeterRegistry meterRegistry;

    private Counter counter(@NonNull MetricEvent op, Tags tags) {
        val t = Tags
                .of(Tag.of(TAG_NAME_KEY, op.event()))
                .and(tags);

        return meterRegistry.counter(METRIC_NAME_EVENTS, t);
    }

    private Timer timer(@NonNull TimedOperation op, Tags tags) {
        val t = Tags
                .of(Tag.of(TAG_NAME_KEY, op.op()))
                .and(tags);

        return meterRegistry.timer(METRIC_NAME_OPERATIONS, t);
    }

    @Override
    public void timed(TimedOperation operation, Runnable fn) {
        timer(operation, null).record(fn);
    }

    @Override
    public void timed(TimedOperation operation, Tags tags, Runnable fn) {
        timer(operation, tags).record(fn);
    }

    @Override
    public <E extends Exception> void timed(TimedOperation operation, Class<E> exceptionClass,
            RunnableWithException<E> fn) throws E {
        timed(operation, exceptionClass, null, fn);
    }

    @Override
    public <E extends Exception> void timed(TimedOperation operation, Class<E> exceptionClass,
            Tags tags, RunnableWithException<E> fn) throws E {
        timed(operation, exceptionClass, tags, () -> {
            fn.run();

            return null;
        });
    }

    @Override
    public <T> T timed(TimedOperation operation, Supplier<T> fn) {
        return timer(operation, null).record(fn);
    }

    @Override
    public <T> T timed(TimedOperation operation, Tags tags, Supplier<T> fn) {
        return timer(operation, tags).record(fn);
    }

    @Override
    public <R, E extends Exception> R timed(TimedOperation operation, Class<E> exceptionClass,
            Tags tags, SupplierWithException<R, E> fn) throws E {
        val timer = timer(operation, tags);
        val sw = Stopwatch.createStarted();

        try {
            return fn.get();
        } finally {
            sw.stop();
            timer.record(sw.elapsed());
        }
    }

    @Override
    public <R, E extends Exception> R timed(TimedOperation operation, Class<E> exceptionClass,
            SupplierWithException<R, E> fn) throws E {
        return timed(operation, exceptionClass, null, fn);
    }

    @Override
    public void count(MetricEvent event, Tags tags) {
        counter(event, tags).increment();
    }

    @Override
    public void count(MetricEvent event) {
        count(event, null);
    }
}