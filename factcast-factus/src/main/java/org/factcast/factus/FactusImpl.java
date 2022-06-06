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

import static org.factcast.factus.metrics.TagKeys.*;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import java.lang.reflect.Constructor;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.event.EventConverter;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionClosedException;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.factus.batch.DefaultPublishBatch;
import org.factcast.factus.batch.PublishBatch;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.lock.InLockedOperation;
import org.factcast.factus.lock.Locked;
import org.factcast.factus.lock.LockedOnSpecs;
import org.factcast.factus.metrics.FactusMetrics;
import org.factcast.factus.metrics.TimedOperation;
import org.factcast.factus.projection.*;
import org.factcast.factus.projector.Projector;
import org.factcast.factus.projector.ProjectorFactory;
import org.factcast.factus.serializer.SnapshotSerializer;
import org.factcast.factus.snapshot.AggregateSnapshotRepository;
import org.factcast.factus.snapshot.ProjectionSnapshotRepository;
import org.factcast.factus.snapshot.SnapshotSerializerSupplier;

/** Single entry point to the factus API. */
@RequiredArgsConstructor
@Slf4j
public class FactusImpl implements Factus {

  // maybe configurable some day ?
  public static final int PROGRESS_INTERVAL = 10000;

  private final FactCast fc;

  private final ProjectorFactory ehFactory;

  private final EventConverter eventConverter;

  private final AggregateSnapshotRepository aggregateSnapshotRepository;

  private final ProjectionSnapshotRepository projectionSnapshotRepository;

  private final SnapshotSerializerSupplier snapFactory;

  private final FactusMetrics factusMetrics;

  private final AtomicBoolean closed = new AtomicBoolean();

  private final Set<AutoCloseable> managedObjects = new HashSet<>();

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

    assertNotClosed();
    InLockedOperation.assertNotInLockedOperation();

    Duration interval = Duration.ofMinutes(5); // TODO should be a property?
    while (!closed.get()) {
      WriterToken token = subscribedProjection.acquireWriteToken(interval);
      if (token != null) {
        log.info("Acquired writer token for {}", subscribedProjection.getClass());
        Subscription subscription = doSubscribe(subscribedProjection, token);
        // close token & subscription on shutdown
        managedObjects.add(
            new AutoCloseable() {
              @Override
              public void close() {
                tryClose(subscription);
                tryClose(token);
              }

              private void tryClose(AutoCloseable c) {
                try {
                  c.close();
                } catch (Exception ignore) {
                  // intentional
                }
              }
            });
        return new TokenAwareSubscription(subscription, token);
      } else {
        log.trace(
            "failed to acquire writer token for {}. Will keep trying.",
            subscribedProjection.getClass());
      }
    }
    throw new IllegalStateException("Already closed");
  }

  @SneakyThrows
  private <P extends SubscribedProjection> Subscription doSubscribe(
      @NonNull P subscribedProjection, @NonNull WriterToken token) {
    Projector<P> handler = ehFactory.create(subscribedProjection);
    FactObserver fo =
        new AbstractFactObserver(subscribedProjection, PROGRESS_INTERVAL, factusMetrics) {

          UUID lastFactIdApplied = null;

          @Override
          public void onNextFact(@NonNull Fact element) {
            if (token.isValid()) {
              lastFactIdApplied = element.id();
              handler.apply(element);
            } else {
              // token is no longer valid
              throw new IllegalStateException("WriterToken is no longer valid.");
            }
          }

          @Override
          public void onCatchupSignal() {
            handler.onCatchup(lastFactIdApplied);
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
          public void onFastForward(@NonNull UUID factIdToFfwdTo) {
            subscribedProjection.factStreamPosition(factIdToFfwdTo);
          }
        };

    return fc.subscribe(
        SubscriptionRequest.follow(handler.createFactSpecs())
            .fromNullable(subscribedProjection.factStreamPosition()),
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

    SnapshotSerializer ser = snapFactory.retrieveSerializer(projectionClass);

    Optional<Snapshot> latest = projectionSnapshotRepository.findLatest(projectionClass);

    P projection;
    if (latest.isPresent()) {
      Snapshot snap = latest.get();
      projection = ser.deserialize(projectionClass, snap.bytes());
      projection.onAfterRestore();
    } else {
      log.trace("Creating initial projection version for {}", projectionClass);
      projection = instantiate(projectionClass);
    }

    // catchup
    UUID state =
        catchupProjection(
            projection,
            latest.map(Snapshot::lastFact).orElse(null),
            new IntervalSnapshotter<SnapshotProjection>(Duration.ofSeconds(30)) {
              @Override
              void createSnapshot(SnapshotProjection projection, UUID state) {
                projection.onBeforeSnapshot();
                projectionSnapshotRepository.put(projection, state);
              }
            });
    if (state != null) {
      projection.onBeforeSnapshot();
      projectionSnapshotRepository.put(projection, state);
    }
    return projection;
  }

  @Override
  @SneakyThrows
  public <A extends Aggregate> Optional<A> find(Class<A> aggregateClass, UUID aggregateId) {
    return factusMetrics.timed(
        TimedOperation.FIND_DURATION,
        Tags.of(Tag.of(CLASS, aggregateClass.getName())),
        () -> doFind(aggregateClass, aggregateId));
  }

  @SneakyThrows
  private <A extends Aggregate> Optional<A> doFind(Class<A> aggregateClass, UUID aggregateId) {
    assertNotClosed();

    SnapshotSerializer ser = snapFactory.retrieveSerializer(aggregateClass);

    Optional<Snapshot> latest = aggregateSnapshotRepository.findLatest(aggregateClass, aggregateId);
    Optional<A> optionalA =
        latest
            .map(as -> ser.deserialize(aggregateClass, as.bytes()))
            .map(peek(Aggregate::onAfterRestore));

    A aggregate = optionalA.orElseGet(() -> initial(aggregateClass, aggregateId));

    UUID state =
        catchupProjection(
            aggregate,
            latest.map(Snapshot::lastFact).orElse(null),
            new IntervalSnapshotter<Aggregate>(Duration.ofSeconds(30)) {
              @Override
              void createSnapshot(Aggregate projection, UUID state) {
                projection.onBeforeSnapshot();
                aggregateSnapshotRepository.put(projection, state);
              }
            });
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
      aggregate.onBeforeSnapshot();
      aggregateSnapshotRepository.putBlocking(aggregate, state);
      return Optional.of(aggregate);
    }
  }

  @SneakyThrows
  private <P extends Projection> UUID catchupProjection(
      @NonNull P projection, UUID stateOrNull, @Nullable BiConsumer<P, UUID> afterProcessing) {
    Projector<P> handler = ehFactory.create(projection);
    AtomicReference<UUID> factId = new AtomicReference<>();
    AtomicInteger factCount = new AtomicInteger(0);

    FactObserver fo =
        new AbstractFactObserver(projection, PROGRESS_INTERVAL, factusMetrics) {
          UUID id = null;

          @Override
          public void onNextFact(@NonNull Fact element) {
            id = element.id();
            handler.apply(element);
            factId.set(id);
            if (afterProcessing != null) {
              afterProcessing.accept(projection, id);
            }
            factCount.incrementAndGet();
          }

          @Override
          public void onComplete() {
            projection.onComplete();
          }

          @Override
          public void onCatchupSignal() {
            handler.onCatchup(id);
            projection.onCatchup();
          }

          @Override
          public void onError(@NonNull Throwable exception) {
            projection.onError(exception);
          }

          @Override
          public void onFastForward(@NonNull UUID factIdToFfwdTo) {
            if (projection instanceof FactStreamPositionAware) {
              ((FactStreamPositionAware) projection).factStreamPosition(factIdToFfwdTo);
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
    return factId.get();
  }

  @VisibleForTesting
  @SneakyThrows
  protected <A extends Aggregate> A initial(Class<A> aggregateClass, UUID aggregateId) {
    log.trace(
        "Creating initial aggregate version for {} with id {}",
        aggregateClass.getSimpleName(),
        aggregateId);
    A a = instantiate(aggregateClass);
    AggregateUtil.aggregateId(a, aggregateId);
    return a;
  }

  @NonNull
  @SneakyThrows
  private <P extends SnapshotProjection> P instantiate(Class<P> projectionClass) {
    Constructor<P> con = projectionClass.getDeclaredConstructor();
    con.setAccessible(true);
    return con.newInstance();
  }

  @Override
  public void close() {
    if (closed.getAndSet(true)) {
      log.warn("close is being called more than once!?");
    } else {
      ArrayList<AutoCloseable> closeables = new ArrayList<>(managedObjects);
      for (AutoCloseable c : closeables) {
        try {
          c.close();
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
  public <A extends Aggregate> Locked<A> withLockOn(Class<A> aggregateClass, UUID id) {
    A fresh =
        factusMetrics.timed(
            TimedOperation.FIND_DURATION,
            Tags.of(Tag.of(CLASS, aggregateClass.getName())),
            () -> find(aggregateClass, id).orElse(instantiate(aggregateClass)));
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

  private <T> UnaryOperator<T> peek(Consumer<T> c) {
    return x -> {
      c.accept(x);
      return x;
    };
  }

  @RequiredArgsConstructor
  static class TokenAwareSubscription implements Subscription {
    final Subscription delegate;
    final WriterToken token;

    @Override
    public void close() throws Exception {
      try {
        delegate.close();
      } finally {
        token.close();
      }
    }

    @Override
    public Subscription awaitCatchup() throws SubscriptionClosedException {
      return delegate.awaitCatchup();
    }

    @Override
    public Subscription awaitCatchup(long waitTimeInMillis)
        throws SubscriptionClosedException, TimeoutException {
      return delegate.awaitCatchup(waitTimeInMillis);
    }

    @Override
    public Subscription awaitComplete() throws SubscriptionClosedException {
      return delegate.awaitComplete();
    }

    @Override
    public Subscription awaitComplete(long waitTimeInMillis)
        throws SubscriptionClosedException, TimeoutException {
      return delegate.awaitComplete(waitTimeInMillis);
    }
  }
}
