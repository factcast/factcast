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

import static org.factcast.factus.metrics.TagKeys.CLASS;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import io.micrometer.core.instrument.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.*;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.*;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.factus.batch.*;
import org.factcast.factus.event.*;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.lock.*;
import org.factcast.factus.lock.Locked;
import org.factcast.factus.metrics.*;
import org.factcast.factus.projection.*;
import org.factcast.factus.projector.*;
import org.factcast.factus.snapshot.*;

/** Single entry point to the factus API. */
@RequiredArgsConstructor
@Slf4j
public class FactusImpl implements Factus {

  // maybe configurable some day ?
  public static final int PROGRESS_INTERVAL = 10000;

  private final FactCast fc;

  private final ProjectorFactory ehFactory;

  private final EventConverter eventConverter;

  private final AggregateRepository aggregateSnapshotRepository;

  private final SnapshotRepository projectionSnapshotRepository;

  private final FactusMetrics factusMetrics;

  private final AtomicBoolean closed = new AtomicBoolean();

  private final Set<AutoCloseable> managedObjects =
      Collections.synchronizedSet(new LinkedHashSet<>());

  @Override
  public @NonNull PublishBatch batch() {
    return new DefaultPublishBatch(fc, eventConverter);
  }

  @Override
  public <T> T publish(@NonNull EventObject e, @NonNull Function<Fact, T> resultFn) {

    assertNotClosed();
    InLockedOperation.assertNotInLockedOperation();

    Fact factToPublish = eventConverter.toFact(e);
    fc.publish(factToPublish);
    return resultFn.apply(factToPublish);
  }

  private void assertNotClosed() {
    if (closed.get()) {
      // ISE is correct here, as this is not supposed to happen
      throw new IllegalStateException("Already closed.");
    }
  }

  @Override
  public void publish(@NonNull List<EventObject> eventPojos) {
    publish(eventPojos, f -> null);
  }

  @Override
  public void publish(@NonNull Fact f) {
    assertNotClosed();
    InLockedOperation.assertNotInLockedOperation();

    fc.publish(f);
  }

  @Override
  public <T> T publish(@NonNull List<EventObject> e, @NonNull Function<List<Fact>, T> resultFn) {

    assertNotClosed();
    InLockedOperation.assertNotInLockedOperation();

    List<Fact> facts = e.stream().map(eventConverter::toFact).collect(Collectors.toList());
    fc.publish(facts);
    return resultFn.apply(facts);
  }

  @Override
  public <P extends ManagedProjection> void update(
      @NonNull P managedProjection, @NonNull Duration maxWaitTime) {

    assertNotClosed();

    log.trace("updating managed projection {}", managedProjection.getClass());
    factusMetrics.timed(
        TimedOperation.MANAGED_PROJECTION_UPDATE_DURATION,
        Tags.of(Tag.of(CLASS, managedProjection.getClass().getName())),
        () ->
            managedProjection.withLock(
                () ->
                    catchupProjection(
                        managedProjection, managedProjection.factStreamPosition(), null)));
  }

  @Override
  public <P extends SubscribedProjection> Subscription subscribeAndBlock(
      @NonNull P subscribedProjection) {
    return subscribeAndBlock(subscribedProjection, Duration.ofMinutes(5));
  }

  @Override
  public <P extends SubscribedProjection> Subscription subscribeAndBlock(
      @NonNull P subscribedProjection, @NonNull Duration retryWaitTime) {

    assertNotClosed();
    InLockedOperation.assertNotInLockedOperation();

    while (!closed.get()) {
      WriterToken token = subscribedProjection.acquireWriteToken(retryWaitTime);
      if (token != null) {
        log.info("Acquired writer token for {}", subscribedProjection.getClass());
        Subscription subscription = doSubscribe(subscribedProjection, token);
        subscription.onClose(() -> tryClose(token)); // close token on subscription closing
        // close subscription on shutdown
        managedObjects.add(() -> tryClose(subscription));
        return subscription;
      } else {
        log.trace(
            "failed to acquire writer token for {}. Will keep trying.",
            subscribedProjection.getClass());
      }
    }
    // this might be thrown in case of shutdown
    throw new FactusClosedException(
        "Factus is closed. Halting attempts to acquire a writer token.");
  }

  @SneakyThrows
  private <P extends SubscribedProjection> Subscription doSubscribe(
      @NonNull P subscribedProjection, @NonNull WriterToken token) {
    Projector<P> handler = ehFactory.create(subscribedProjection);

    FactObserver fo =
        new AbstractFactObserver(subscribedProjection, PROGRESS_INTERVAL, factusMetrics) {

          FactStreamPosition lastPositionApplied = null;

          @Override
          public void onNextFacts(@NonNull List<Fact> elements) {
            assertTokenIsValid();
            handler.apply(elements);
            lastPositionApplied = FactStreamPosition.from(Iterables.getLast(elements));
          }

          private void assertTokenIsValid() {
            if (!token.isValid()) {
              throw new IllegalStateException("WriterToken is no longer valid.");
            }
          }

          @Override
          public void onCatchupSignal() {
            handler.onCatchup(lastPositionApplied);
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

          @Override
          public void onFastForward(@NonNull FactStreamPosition factIdToFfwdTo) {
            if (factIdToFfwdTo.isAfter(lastPositionApplied)) {
              subscribedProjection.factStreamPosition(factIdToFfwdTo);
            }
          }
        };

    return fc.subscribe(
        SubscriptionRequest.follow(handler.createFactSpecs())
            .fromNullable(
                Optional.ofNullable(subscribedProjection.factStreamPosition())
                    .map(FactStreamPosition::factId)
                    .orElse(null)),
        fo);
  }

  @Override
  @SneakyThrows
  public <P extends SnapshotProjection> @NonNull P fetch(Class<P> projectionClass) {
    return factusMetrics.timed(
        TimedOperation.FETCH_DURATION,
        Tags.of(Tag.of(CLASS, projectionClass.getName())),
        () -> dofetch(projectionClass));
  }

  @SneakyThrows
  private <P extends SnapshotProjection> P dofetch(Class<P> projectionClass) {
    assertNotClosed();

    // ugly, fix hierarchy?
    if (Aggregate.class.isAssignableFrom(projectionClass)) {
      throw new IllegalArgumentException(
          "Method confusion: UUID aggregateId is missing as a second parameter for aggregates");
    }

    ProjectionAndState<P> projectionAndState =
        projectionSnapshotRepository
            .findLatest(projectionClass)
            .orElseGet(
                () -> ProjectionAndState.of(ReflectionUtils.instantiate(projectionClass), null));

    // catchup
    P projection = projectionAndState.projectionInstance();
    UUID state =
        catchupProjection(
            projection,
            projectionAndState.lastFactIdApplied(),
            new IntervalSnapshotter<SnapshotProjection>(Duration.ofSeconds(30)) {
              @Override
              void createSnapshot(SnapshotProjection projection, UUID state) {
                projectionSnapshotRepository.store(projection, state);
              }
            });

    if (state != null) {
      // was updated during catchup
      projectionSnapshotRepository.store(projection, state);
    }
    return projection;
  }

  @Override
  @SneakyThrows
  @NonNull
  public <A extends Aggregate> Optional<A> find(
      @NonNull Class<A> aggregateClass, @NonNull UUID aggregateId) {
    return factusMetrics.timed(
        TimedOperation.FIND_DURATION,
        Tags.of(Tag.of(CLASS, aggregateClass.getName())),
        () -> doFind(aggregateClass, aggregateId));
  }

  @SneakyThrows
  private <A extends Aggregate> Optional<A> doFind(Class<A> aggregateClass, UUID aggregateId) {
    assertNotClosed();

    ProjectionAndState<A> projectionAndState =
        aggregateSnapshotRepository
            .findLatest(aggregateClass, aggregateId)
            .orElseGet(() -> ProjectionAndState.of(initial(aggregateClass, aggregateId), null));

    A aggregate = projectionAndState.projectionInstance();
    UUID state =
        catchupProjection(
            aggregate,
            projectionAndState.lastFactIdApplied(),
            new IntervalSnapshotter<Aggregate>(Duration.ofSeconds(30)) {
              @Override
              void createSnapshot(Aggregate projection, UUID state) {
                aggregateSnapshotRepository.store(aggregate, state);
              }
            });
    if (state != null) {
      aggregateSnapshotRepository.store(aggregate, state);
    } else {
      // special behavior for aggregates, if no event has ever been applied, we return empty
      if (projectionAndState.lastFactIdApplied() == null) {
        return Optional.empty();
      }
    }

    return Optional.of(aggregate);
  }

  private <P extends Projection> void catchupProjection(
      @NonNull P projection,
      FactStreamPosition stateOrNull,
      @Nullable BiConsumer<P, UUID> afterProcessing) {
    catchupProjection(
        projection,
        Optional.ofNullable(stateOrNull).map(FactStreamPosition::factId).orElse(null),
        afterProcessing);
  }

  /**
   * @return null if no fact was applied
   */
  @SneakyThrows
  @VisibleForTesting
  protected <P extends Projection> UUID catchupProjection(
      @NonNull P projection,
      @Nullable UUID stateOrNull,
      @Nullable BiConsumer<P, UUID> afterProcessing) {
    Projector<P> handler = ehFactory.create(projection);
    AtomicInteger factCount = new AtomicInteger(0);
    AtomicReference<FactStreamPosition> positionOfLastFactApplied = new AtomicReference<>();

    FactObserver fo =
        new AbstractFactObserver(projection, PROGRESS_INTERVAL, factusMetrics) {

          @Override
          public void onNextFacts(@NonNull List<Fact> elements) {
            handler.apply(elements);
            FactStreamPosition pos = FactStreamPosition.from(Iterables.getLast(elements));
            positionOfLastFactApplied.set(pos);

            if (afterProcessing != null) {
              afterProcessing.accept(projection, pos.factId());
            }
            factCount.incrementAndGet();
          }

          @Override
          public void onComplete() {
            flush();
            projection.onComplete();
          }

          @Override
          public void onCatchupSignal() {
            flush();
            handler.onCatchup(positionOfLastFactApplied.get());
            projection.onCatchup();
          }

          @Override
          public void onError(@NonNull Throwable exception) {
            flush();
            projection.onError(exception);
          }

          @Override
          public void onFastForward(@NonNull FactStreamPosition factIdToFfwdTo) {
            flush();

            FactStreamPosition factStreamPosition = positionOfLastFactApplied.get();
            if (factIdToFfwdTo.isAfter(factStreamPosition)) {
              if (projection instanceof FactStreamPositionAware) {
                ((FactStreamPositionAware) projection).factStreamPosition(factIdToFfwdTo);
              }

              // only persist ffwd if we ever had a state or applied facts in this catchup
              if (stateOrNull != null || factStreamPosition != null) {
                positionOfLastFactApplied.set(factIdToFfwdTo);
              }
            }
          }
        };

    List<FactSpec> factSpecs = handler.createFactSpecs();

    // the sole purpose of this synchronization is to make sure that writes from the fact delivery
    // thread are guaranteed to be visible when leaving the block
    //
    synchronized (projection) {
      fc.subscribe(SubscriptionRequest.catchup(factSpecs).fromNullable(stateOrNull), fo)
          .awaitComplete();
    }
    return Optional.ofNullable(positionOfLastFactApplied.get())
        .map(FactStreamPosition::factId)
        .orElse(null);
  }

  @VisibleForTesting
  @SneakyThrows
  protected <A extends Aggregate> A initial(Class<A> aggregateClass, UUID aggregateId) {
    log.trace(
        "Creating initial aggregate version for {} with id {}",
        aggregateClass.getSimpleName(),
        aggregateId);
    A a = ReflectionUtils.instantiate(aggregateClass);
    AggregateUtil.aggregateId(a, aggregateId);
    return a;
  }

  @Override
  public void close() {
    log.debug("factus is being closed");
    if (closed.getAndSet(true)) {
      log.warn("close is being called more than once!?");
    } else {
      ArrayList<AutoCloseable> closeables = new ArrayList<>(managedObjects);
      for (AutoCloseable c : closeables) {
        try {
          if (c != null) {
            c.close();
          }
        } catch (Exception e) {
          // needs to be swallowed
          log.warn("While closing {} of type {}:", c, c.getClass().getName(), e);
        }
      }
    }
  }

  @Override
  public Fact toFact(@NonNull EventObject e) {
    return eventConverter.toFact(e);
  }

  @Override
  public <M extends ManagedProjection> Locked<M> withLockOn(@NonNull M managedProjection) {
    Projector<M> applier = ehFactory.create(managedProjection);
    List<FactSpec> specs = applier.createFactSpecs();
    return new Locked<>(fc, this, managedProjection, specs, factusMetrics);
  }

  @Override
  public <A extends Aggregate> Locked<A> withLockOn(@NonNull Class<A> aggregateClass, UUID id) {
    A fresh =
        find(aggregateClass, id)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        String.format(
                            "Aggregate %s with id %s does not exist.",
                            aggregateClass.getSimpleName(), id)));
    Projector<SnapshotProjection> snapshotProjectionEventApplier = ehFactory.create(fresh);
    List<FactSpec> specs = snapshotProjectionEventApplier.createFactSpecs();
    return new Locked<>(fc, this, fresh, specs, factusMetrics);
  }

  @Override
  public <P extends SnapshotProjection> Locked<P> withLockOn(@NonNull Class<P> projectionClass) {
    P fresh = fetch(projectionClass);
    Projector<SnapshotProjection> snapshotProjectionEventApplier = ehFactory.create(fresh);
    List<FactSpec> specs = snapshotProjectionEventApplier.createFactSpecs();
    return new Locked<>(fc, this, fresh, specs, factusMetrics);
  }

  @Override
  public LockedOnSpecs withLockOn(@NonNull FactSpec spec, @NonNull FactSpec... additional) {
    LinkedList<FactSpec> l = new LinkedList<>();
    l.add(spec);
    if (additional != null) {
      l.addAll(Arrays.asList(additional));
    }
    return withLockOn(l);
  }

  @Override
  public LockedOnSpecs withLockOn(@NonNull List<FactSpec> specs) {
    Preconditions.checkArgument(!specs.isEmpty(), "Argument specs must not be empty");
    return new LockedOnSpecs(fc, this, specs, factusMetrics);
  }

  @Override
  public OptionalLong serialOf(@NonNull UUID factId) {
    return fc.serialOf(factId);
  }

  @Override
  @NonNull
  public FactStore store() {
    return fc.store();
  }

  private void tryClose(AutoCloseable c) {
    try {
      log.trace("Closing AutoCloseable for class {}", c.getClass());
      c.close();
    } catch (Exception e) {
      log.warn("Error while closing AutoCloseable for {}", c.getClass(), e);
    }
  }

  abstract static class IntervalSnapshotter<P extends SnapshotProjection>
      implements BiConsumer<P, UUID> {

    private final Duration duration;
    private Instant nextSnapshot;

    IntervalSnapshotter(Duration duration) {
      this.duration = duration;
      nextSnapshot = Instant.now().plus(duration);
    }

    @Override
    public void accept(P projection, UUID uuid) {
      Instant now = Instant.now();
      if (now.isAfter(nextSnapshot)) {
        nextSnapshot = now.plus(duration);
        createSnapshot(projection, uuid);
      }
    }

    abstract void createSnapshot(P projection, UUID state);
  }
}
