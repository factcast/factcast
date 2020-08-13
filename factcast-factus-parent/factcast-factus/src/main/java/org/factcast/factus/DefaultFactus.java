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
package org.factcast.factus;

import java.lang.reflect.Constructor;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.factus.applier.EventApplier;
import org.factcast.factus.applier.EventApplierFactory;
import org.factcast.factus.batch.DefaultPublishBatch;
import org.factcast.factus.batch.PublishBatch;
import org.factcast.factus.lock.InLockedOperation;
import org.factcast.factus.lock.Locked;
import org.factcast.factus.projection.*;
import org.factcast.factus.serializer.EventSerializer;
import org.factcast.factus.snapshot.AggregateSnapshotRepository;
import org.factcast.factus.snapshot.ProjectionSnapshotRepository;
import org.factcast.factus.snapshot.SnapshotSerializerSupplier;
import org.jetbrains.annotations.NotNull;

import com.google.common.annotations.VisibleForTesting;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Single entry point to the factus API.
 */
@RequiredArgsConstructor
@Slf4j
public class DefaultFactus implements Factus {
    final FactCast fc;

    final EventApplierFactory ehFactory;

    final EventSerializer serializer;

    final AggregateSnapshotRepository aggregateSnapshotRepository;

    final ProjectionSnapshotRepository projectionSnapshotRepository;

    final SnapshotSerializerSupplier snapFactory;

    private final AtomicBoolean closed = new AtomicBoolean();

    private final Set<AutoCloseable> managedObjects = new HashSet<>();

    @Override
    public PublishBatch batch() {
        return new DefaultPublishBatch(fc, serializer);
    }

    @Override
    public <T> T publish(@NonNull EventPojo e, @NonNull Function<Fact, T> resultFn) {

        assertNotClosed();
        InLockedOperation.assertNotInLockedOperation();

        Fact factToPublish = e.toFact(serializer);
        fc.publish(factToPublish);
        return resultFn.apply(factToPublish);
    }

    private void assertNotClosed() {
        if (closed.get()) {
            throw new IllegalStateException("Already closed.");
        }
    }

    @Override
    public void publish(@NonNull List<EventPojo> eventPojos) {
        publish(eventPojos, f -> null);
    }

    @Override
    public void publish(@NonNull Fact f) {
        assertNotClosed();
        InLockedOperation.assertNotInLockedOperation();

        fc.publish(f);
    }

    @Override
    public <T> T publish(@NonNull List<EventPojo> e, @NonNull Function<List<Fact>, T> resultFn) {

        assertNotClosed();
        InLockedOperation.assertNotInLockedOperation();

        List<Fact> facts = e.stream()
                .map(p -> p.toFact(serializer))
                .collect(Collectors.toList());
        fc.publish(facts);
        return resultFn.apply(facts);
    }

    @Override
    public <P extends ManagedProjection> void update(
            @NonNull P managedProjection,
            @NonNull Duration maxWaitTime) {

        assertNotClosed();

        log.trace("updating local projection {}", managedProjection.getClass());
        managedProjection.withLock(() -> catchupProjection(managedProjection, managedProjection
                .state(), maxWaitTime));
    }

    @Override
    public <P extends SubscribedProjection> Subscription subscribe(
            @NonNull P subscribedProjection) {

        assertNotClosed();
        InLockedOperation.assertNotInLockedOperation();

        Duration INTERVAL = Duration.ofMinutes(5); // TODO needed?
        while (!closed.get()) {
            if (subscribedProjection.acquireWriteToken(INTERVAL) != null) {
                Subscription e = doSubscribe(subscribedProjection);
                managedObjects.add(e);
                return e;
            }
        }
        throw new IllegalStateException("Already closed");
    }

    @SneakyThrows
    private <P extends SubscribedProjection> Subscription doSubscribe(P subscribedProjection) {
        EventApplier<P> handler = ehFactory.create(subscribedProjection);
        FactObserver fo = new FactObserver() {
            @Override
            public void onNext(@NonNull Fact element) {
                handler.apply(element);
                subscribedProjection.state(element.id());
            }

            @Override
            public void onCatchup() {
                subscribedProjection.onCatchup();
            }

            @Override
            public void onComplete() {
                subscribedProjection.onComplete();
            }

            @Override
            public void onError(@NonNull Throwable exception) {
                subscribedProjection.onError(exception);
            }
        };

        return fc.subscribe(
                SubscriptionRequest
                        .catchup(handler.createFactSpecs())
                        .fromNullable(subscribedProjection.state()), fo)
                .awaitComplete(FactusConstants.FOREVER.toMillis());
    }

    @Override
    @SneakyThrows
    public <P extends SnapshotProjection> P fetch(Class<P> projectionClass) {
        assertNotClosed();

        // ugly, fix hierarchy?
        if (Aggregate.class.isAssignableFrom(projectionClass)) {
            throw new IllegalArgumentException(
                    "Method confusion: UUID aggregateId is missing as a second parameter for aggregates");
        }

        val ser = snapFactory.retrieveSerializer(projectionClass);

        Optional<Snapshot> latest = projectionSnapshotRepository.findLatest(
                projectionClass);

        P projection;
        if (latest.isPresent()) {
            Snapshot snap = latest.get();

            projection = ser.deserialize(projectionClass, snap.bytes());
        } else {
            log.trace("Creating initial projection version for {}", projectionClass);
            projection = instantiate(projectionClass);
        }

        // catchup
        UUID state = catchupProjection(projection, latest.map(Snapshot::lastFact)
                .orElse(null), FactusConstants.FOREVER);
        if (state != null) {
            projectionSnapshotRepository.put(projection, state);
        }
        return projection;
    }

    @Override
    @SneakyThrows
    public <A extends Aggregate> Optional<A> find(Class<A> aggregateClass, UUID aggregateId) {
        assertNotClosed();

        val ser = snapFactory.retrieveSerializer(aggregateClass);

        Optional<Snapshot> latest = aggregateSnapshotRepository.findLatest(
                aggregateClass, aggregateId);
        Optional<A> optionalA = latest
                .map(as -> ser.deserialize(aggregateClass, as.bytes()));
        // noinspection
        A aggregate = optionalA
                .orElseGet(() -> this.<A> initial(aggregateClass, aggregateId));

        UUID state = catchupProjection(aggregate, latest.map(Snapshot::lastFact)
                .orElse(null), FactusConstants.FOREVER);
        if (state == null) {
            // nothing new

            if (!latest.isPresent()) {
                // nothing before
                return Optional.empty();
            } else {
                // just return what we got
                return Optional.of(aggregate);
            }
        } else {
            // concurrency control decided to be irrelevant here
            aggregateSnapshotRepository.putBlocking(aggregate, state);
            return Optional.of(aggregate);
        }
    }

    @SneakyThrows
    private <P extends Projection> UUID catchupProjection(
            @NonNull P projection, UUID stateOrNull,
            Duration maxWait) {
        EventApplier<P> handler = ehFactory.create(projection);
        AtomicReference<UUID> factId = new AtomicReference<>();
        FactObserver fo = new FactObserver() {
            @Override
            public void onNext(@NonNull Fact element) {
                handler.apply(element);
                factId.set(element.id());
            }

            @Override
            public void onComplete() {
                projection.onComplete();
            }

            @Override
            public void onCatchup() {
                projection.onCatchup();
            }

            @Override
            public void onError(@NonNull Throwable exception) {
                projection.onError(exception);
            }
        };

        fc.subscribe(
                SubscriptionRequest
                        .catchup(handler.createFactSpecs())
                        .fromNullable(stateOrNull), fo)
                .awaitComplete(maxWait.toMillis());
        return factId.get();
    }

    @VisibleForTesting
    @SneakyThrows
    protected <A extends Aggregate> A initial(Class<A> aggregateClass, UUID aggregateId) {
        log.trace("Creating initial aggregate version for {} with id {}", aggregateClass
                .getSimpleName(), aggregateId);
        A a = instantiate(aggregateClass);
        AggregateUtil.aggregateId(a, aggregateId);
        return a;
    }

    @NotNull
    @SneakyThrows
    private <P extends SnapshotProjection> P instantiate(Class<P> projectionClass) {
        Constructor<P> con = projectionClass.getDeclaredConstructor();
        con.setAccessible(true);
        return con.newInstance();
    }

    @Override
    public void close() {
        if (this.closed.getAndSet(true)) {
            log.warn("close is being called more than once!?");
        } else {
            ArrayList<AutoCloseable> closeables = new ArrayList<>(managedObjects);
            for (AutoCloseable c : closeables) {
                try {
                    c.close();
                } catch (Exception e) {
                    // needs to be swallowed
                    log.warn("While closing {} of type {}:", c, c.getClass().getCanonicalName(), e);
                }
            }
        }
    }

    @Override
    public Fact toFact(@NonNull EventPojo e) {
        return e.toFact(serializer);
    }

    @Override
    public <M extends ManagedProjection> Locked<M> withLockOn(M managedProjection) {
        val applier = ehFactory.create(managedProjection);
        List<FactSpec> specs = applier
                .createFactSpecs();
        return new Locked<>(fc, this, managedProjection, specs);

    }

    @Override
    public <A extends Aggregate> Locked<A> withLockOn(Class<A> aggregateClass, UUID id) {
        A fresh = find(aggregateClass, id).orElse(instantiate(aggregateClass));
        EventApplier<SnapshotProjection> snapshotProjectionEventApplier = ehFactory.create(fresh);
        List<FactSpec> specs = snapshotProjectionEventApplier.createFactSpecs();
        return new Locked<>(fc, this, fresh, specs);
    }

    @Override
    public <P extends SnapshotProjection> Locked<P> withLockOn(@NonNull Class<P> projectionClass) {
        P fresh = fetch(projectionClass);
        EventApplier<SnapshotProjection> snapshotProjectionEventApplier = ehFactory.create(fresh);
        List<FactSpec> specs = snapshotProjectionEventApplier.createFactSpecs();
        return new Locked<>(fc, this, fresh, specs);
    }
}
