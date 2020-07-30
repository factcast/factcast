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
package org.factcast.highlevel;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.highlevel.applier.EventApplier;
import org.factcast.highlevel.applier.EventApplierFactory;
import org.factcast.highlevel.batch.DefaultPublishBatch;
import org.factcast.highlevel.batch.PublishBatch;
import org.factcast.highlevel.projection.Aggregate;
import org.factcast.highlevel.projection.ManagedProjection;
import org.factcast.highlevel.projection.Projection;
import org.factcast.highlevel.projection.SnapshotProjection;
import org.factcast.highlevel.serializer.EventSerializer;
import org.factcast.highlevel.snapshot.AggregateSnapshot;
import org.factcast.highlevel.snapshot.AggregateSnapshotRepository;
import org.factcast.highlevel.snapshot.ProjectionSnapshot;
import org.factcast.highlevel.snapshot.ProjectionSnapshotRepository;
import org.jetbrains.annotations.NotNull;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Single entry point to the highlevel API.
 */
@RequiredArgsConstructor
@Slf4j
public class DefaultEventCast implements EventCast {
    final FactCast fc;

    final EventApplierFactory ehFactory;

    final EventSerializer serializer;

    final AggregateSnapshotRepository aggregateSnapshotRepository;

    final ProjectionSnapshotRepository projectionSnapshotRepository;

    @Override
    public PublishBatch batch() {
        return new DefaultPublishBatch(fc, serializer);
    }

    @Override
    public void publish(@NonNull EventPojo e) {
        publish(e, f -> null);
    }

    @Override
    public <T> T publish(@NonNull EventPojo e, @NonNull Function<Fact, T> resultFn) {
        Fact factToPublish = e.toFact(serializer);
        fc.publish(factToPublish);
        return resultFn.apply(factToPublish);
    }

    @Override
    public void publish(@NonNull List<EventPojo> e) {
        publish(e, f -> null);
    }

    @Override
    public <T> T publish(@NonNull List<EventPojo> e, @NonNull Function<List<Fact>, T> resultFn) {
        List<Fact> facts = StreamSupport.stream(e.spliterator(), false)
                .map(p -> p.toFact(serializer))
                .collect(Collectors.toList());
        fc.publish(facts);
        return resultFn.apply(facts);
    }

    @Override
    @SneakyThrows
    public <P extends ManagedProjection> void update(@NonNull P managedProjection) {
        log.trace("updating local projection {}", managedProjection.getClass());
        catchupProjection(managedProjection, managedProjection.state());
    }

    @Override
    public <P extends SnapshotProjection> P fetch(Class<P> projectionClass) {
        // TODO ugly, fix hierarchy?
        if (Aggregate.class.isAssignableFrom(projectionClass)) {
            throw new IllegalArgumentException(
                    "Method confusion: UUID aggregateId is missing as a second parameter for aggregates");
        }

        Optional<ProjectionSnapshot<P>> latest = projectionSnapshotRepository.findLatest(
                projectionClass);

        P projection;
        if (latest.isPresent()) {
            projection = ProjectionSnapshot.deserialize(latest.get());
        } else {
            log.trace("Creating initial projection version for {}", projectionClass);
            projection = initialProjection(projectionClass);
        }

        // catchup
        UUID factUuid = catchupProjection(projection, latest.map(ProjectionSnapshot::factId)
                .orElse(null));
        if (factUuid != null) {
            ProjectionSnapshot<P> currentSnap = new ProjectionSnapshot<P>(projectionClass,
                    factUuid, projection);
            // TODO concurrency control
            projectionSnapshotRepository.putBlocking(currentSnap);
        }
        return projection;
    }

    @Override
    public <A extends Aggregate> Optional<A> fetch(
            Class<A> aggregateClass,
            UUID aggregateId) {
        Optional<AggregateSnapshot<A>> latest = aggregateSnapshotRepository.findLatest(
                aggregateClass, aggregateId);
        A aggregate = latest.map(AggregateSnapshot::deserialize)
                .orElseGet(() -> initial(
                        aggregateClass, aggregateId));

        UUID factUuid = catchupProjection(aggregate, latest.map(AggregateSnapshot::factId)
                .orElse(null));
        if (factUuid == null) {
            // nothing new

            if (!latest.isPresent()) {
                // nothing before
                return Optional.empty();
            } else {
                // just return what we got
                return Optional.of(aggregate);
            }
        } else {
            AggregateSnapshot<A> currentSnap = new AggregateSnapshot<A>(aggregateClass,
                    factUuid, aggregate);
            // TODO concurrency control
            aggregateSnapshotRepository.putBlocking(aggregateId, currentSnap);
            return Optional.of(aggregate);
        }
    }

    private <P extends Projection> UUID catchupProjection(@NonNull P projection, UUID stateOrNull) {
        EventApplier<P> handler = ehFactory.create(projection);
        AtomicReference<UUID> factId = new AtomicReference<>();
        FactObserver fo = new FactObserver() {
            // TODO what about error control?
            @Override
            public void onNext(@NonNull Fact element) {
                handler.apply(element);
                factId.set(element.id());
            }
        };

        Subscription s = fc.subscribe(
                SubscriptionRequest
                        .catchup(handler.createFactSpecs())
                        .fromNullable(stateOrNull), fo)
                .awaitComplete();
        return factId.get();
    }

    List<Field> getAllFields(Class clazz) {
        if (clazz == null) {
            return Collections.emptyList();
        }

        val result = new LinkedList<Field>();
        result.addAll(Arrays.asList(clazz.getDeclaredFields()));
        result.addAll(getAllFields(clazz.getSuperclass()));
        return result;
    }

    @SneakyThrows
    private <A extends Aggregate> A initial(Class<A> aggregateClass, UUID aggregateId) {
        log.trace("Creating initial aggregate version for {} with id {}", aggregateClass
                .getSimpleName(), aggregateId);
        Constructor<A> con = aggregateClass.getDeclaredConstructor();
        con.setAccessible(true);
        A a = con.newInstance();

        Optional<Field> first = getAllFields(aggregateClass).stream()
                .filter(f -> "id".equals(f.getName()))
                .findFirst();
        if (first.isPresent()) {
            Field field = first.get();
            field.setAccessible(true);
            field.set(a, aggregateId);
        } else {
            throw new IllegalArgumentException("Aggregate " + aggregateClass
                    + " needs a field named 'id'");
        }

        return a;
    }

    @NotNull
    @SneakyThrows
    private <P extends SnapshotProjection> P initialProjection(Class<P> projectionClass) {
        Constructor<P> con = projectionClass.getDeclaredConstructor();
        con.setAccessible(true);
        return con.newInstance();
    }
}
