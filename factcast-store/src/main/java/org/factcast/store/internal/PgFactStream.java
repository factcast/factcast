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
package org.factcast.store.internal;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.FactStreamPosition;
import org.factcast.core.subscription.FactStreamHorizon;
import org.factcast.core.subscription.FactStreamInfo;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.*;
import org.factcast.core.util.ExceptionHelper;
import org.factcast.store.*;
import org.factcast.store.internal.catchup.*;
import org.factcast.store.internal.catchup.CatchupDataSource;
import org.factcast.store.internal.horizon.FactStreamHorizonProvider;
import org.factcast.store.internal.listen.PgConnectionSupplier;
import org.factcast.store.internal.logsuppression.LogSuppression;
import org.factcast.store.internal.pipeline.*;
import org.factcast.store.internal.query.PgFactIdToSerialMapper;
import org.factcast.store.internal.query.PgQueryBuilder;
import org.factcast.store.internal.telemetry.PgStoreTelemetry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.datasource.*;

/**
 * Creates and maintains a subscription.
 *
 * @author <uwe.schaefer@prisma-capacity.eu>
 */
@Slf4j
public class PgFactStream {

  final PgConnectionSupplier connectionSupplier;
  final OffloadDataSource offloadDataSource;
  final EventBus eventBus;
  final PgFactIdToSerialMapper idToSerMapper;
  final PgCatchupFactory pgCatchupFactory;
  final FactStreamHorizonProvider horizonProvider;
  final PushbackServerPipeline pipeline;
  final PgStoreTelemetry telemetry;

  @Getter(AccessLevel.PROTECTED)
  final SubscriptionRequestTO request;

  final LogSuppression logSuppression;

  QueryExecutor queryExecutor;

  @VisibleForTesting
  @Getter(AccessLevel.PROTECTED)
  final AtomicLong serial = new AtomicLong(0);

  final AtomicBoolean disconnected = new AtomicBoolean(false);

  @SuppressWarnings("java:S107")
  public PgFactStream(
      PgConnectionSupplier connectionSupplier,
      EventBus eventBus,
      PgFactIdToSerialMapper idToSerMapper,
      PgCatchupFactory pgCatchupFactory,
      FactStreamHorizonProvider horizonProvider,
      PushbackServerPipeline pipeline,
      PgStoreTelemetry telemetry,
      SubscriptionRequestTO request,
      LogSuppression logSuppression) {
    this(
        connectionSupplier,
        null,
        eventBus,
        idToSerMapper,
        pgCatchupFactory,
        horizonProvider,
        pipeline,
        telemetry,
        request,
        logSuppression);
  }

  @SuppressWarnings("java:S107")
  public PgFactStream(
      PgConnectionSupplier connectionSupplier,
      @Nullable OffloadDataSource offloadDataSource,
      EventBus eventBus,
      PgFactIdToSerialMapper idToSerMapper,
      PgCatchupFactory pgCatchupFactory,
      FactStreamHorizonProvider horizonProvider,
      PushbackServerPipeline pipeline,
      PgStoreTelemetry telemetry,
      SubscriptionRequestTO request,
      LogSuppression logSuppression) {
    this.connectionSupplier = connectionSupplier;
    this.eventBus = eventBus;
    this.idToSerMapper = idToSerMapper;
    this.pgCatchupFactory = pgCatchupFactory;
    this.horizonProvider = horizonProvider;
    // we need that subtype
    this.pipeline = pipeline;
    this.telemetry = telemetry;
    this.offloadDataSource = offloadDataSource;
    this.request = request;
    this.logSuppression = logSuppression;
  }

  void connect() {
    log.debug("{} connect subscription {}", request, request.dump());
    // signal connect
    telemetry.onConnect(request);
    initializeSerialToStartAfter();
    try {
      if (request.ephemeral()) {
        // just fast forward to the latest event published by now
        serial.set(horizonProvider.advance().factSerial());
      } else {
        doCatchup();
      }

      // propagate catchup signal
      if (isConnected()) {
        log.debug("{} signaling catchup", request);
        // signal catchup
        telemetry.onCatchup(request);
        pipeline.process(Signal.catchup());
      }

      if (isConnected()) follow(request, createPgSynchronizedQuery());

    } catch (PipelineAlreadyClosedException | CatchupException e) {
      if (pipeline.isClosed()) {
        log.debug("{} pipeline was closed, exiting.", request);
      } else throw ExceptionHelper.toRuntime(e);
    }
  }

  @VisibleForTesting
  @NotNull
  PgSynchronizedQuery createPgSynchronizedQuery() {
    PgQueryBuilder q = new PgQueryBuilder(request.specs());
    String sql = q.createBoundedSQL();
    log.trace("created query SQL for {} - SQL={}", request.specs(), sql);
    return new PgSynchronizedQuery(
        request.debugInfo(),
        pipeline,
        connectionSupplier,
        sql,
        horizonSerial -> q.createBoundedStatementSetter(serial, horizonSerial),
        this::isConnected,
        serial,
        horizonProvider);
  }

  @VisibleForTesting
  void initializeSerialToStartAfter() {
    Optional<UUID> idRequestedToStartAfter = request.startingAfter();
    Long startingSerial = idRequestedToStartAfter.map(idToSerMapper::retrieve).orElse(0L);
    serial.set(startingSerial);
    log.trace("{} setting starting point to SER={}", request, startingSerial);
  }

  @VisibleForTesting
  @SuppressWarnings("java:S2245")
  void follow(@NonNull SubscriptionRequestTO request, @NonNull PgSynchronizedQuery query) {
    if (isConnected()) {
      if (request.continuous()) {
        log.debug("{} entering follow mode", request);
        // signal follow
        telemetry.onFollow(request);
        queryExecutor = createQueryExecutor(request, query);
        eventBus.register(queryExecutor);
        // catchup phase 3 – make sure, we did not miss any fact due to
        // slow registration
        queryExecutor.trigger();
      } else {
        pipeline.process(Signal.complete());
        log.debug("{} completed", request);
        // signal complete
        telemetry.onComplete(request);
      }
    }
  }

  @VisibleForTesting
  @NonNull
  QueryExecutor createQueryExecutor(
      @NonNull SubscriptionRequest request, @NonNull PgSynchronizedQuery query) {
    return new QueryExecutor(query, this::isConnected, request.specs());
  }

  @VisibleForTesting
  @NonNull
  List<ConnectionModifier> catchupConnectionModifiers(@NonNull SubscriptionRequest request) {
    return List.of(
        ConnectionModifier.withCustomPlanForced(),
        ConnectionModifier.withAutoCommitDisabled(),
        ConnectionModifier.withApplicationName(request.debugInfo()));
  }

  @VisibleForTesting
  void fastForward(@NonNull FactStreamHorizon atTheStartOfQuery) {
    if (isConnected()) {

      UUID targetId = atTheStartOfQuery.factId();
      long targetSer = atTheStartOfQuery.factSerial();

      // there is no need to check for the start id, as it'll be
      // contained in serial or smaller, see initializeSerialToStartAfter
      long currentSerial = serial.get();

      if (targetId != null && currentSerial < targetSer) {
        log.debug("{} sending ffwd to id {} (serial {})", request, targetId, targetSer);
        pipeline.process(Signal.of(FactStreamPosition.of(targetId, targetSer)));

        // this is basically an internal ffwd:
        serial.compareAndSet(currentSerial, targetSer);
      }
    }
  }

  @VisibleForTesting
  void doCatchup() throws CatchupException {
    try (var suppression = logSuppression.forCatchup(request)) {
      if (!isConnected()) return;

      FactStreamHorizon horizon = horizonProvider.advance();
      sendFactStreamInfo(horizon);

      if (!isConnected()) return;

      // It is essential to provide SCDS to the catchup strategies.
      // The supplier indirection is use in order to lazily create a pool from primary in order not
      // to block a connection during P1 if it was offloaded
      try (PrimaryDataSourceSupplier primary =
          new PrimaryDataSourceSupplier(
              () -> createCatchupDataSource(connectionSupplier.dataSource(), pipeline))) {

        // Phase 1
        long phase1HighwaterMark = executePhaseOne(primary, horizon);

        if (!isConnected()) return;

        catchupPhaseTwo(primary, phase1HighwaterMark, horizon);

        // now that phase 1&2 are done, we can ffwd to the initial HWM on the primary
        fastForward(horizon);
      }
    } catch (PipelineAlreadyClosedException e) {
      throw new CatchupException(e);
    }
  }

  private void sendFactStreamInfo(@NonNull FactStreamHorizon horizon)
      throws PipelineAlreadyClosedException {
    // send FactStreamInfo if requested
    if (request.streamInfo()) {
      FactStreamInfo factStreamInfo = new FactStreamInfo(serial.get(), horizon.factSerial());
      pipeline.process(Signal.of(factStreamInfo));
    }
  }

  private long executePhaseOne(PrimaryDataSourceSupplier primary, FactStreamHorizon primaryHorizon)
      throws CatchupException {
    if (offloadDataSource != null) {
      // we're creating a SCDS for offload, that we destroy right after
      try (SingleConnectionDataSource secondary =
          createCatchupDataSource(offloadDataSource, pipeline)) {

        // While it is very unlikely, that by reading from the secondary, we get a higher serial,
        // it is not entirely impossible.
        FactStreamHorizon phaseOneHorizon =
            FactStreamHorizon.min(primaryHorizon, horizonProvider.read(secondary));

        return catchupPhaseOne(secondary, phaseOneHorizon);
      } catch (SQLException | DataAccessException | PipelineAlreadyClosedException e) {
        // SQLException is interesting, as we cannot distinguish between a cancellation and a
        // temporary error with the offload datasource, that would make it reasonable to fall back
        // to the primary.
        //
        // We decide to escalate if the pipeline was closed, so that the primary isn't tried
        if (pipeline.isClosed()) throw new CatchupException(e);
        else {
          log.error("Error during catchup phase 1 on offload data source. Skipping phase one.", e);
          return serial.get();
        }
      } catch (Exception any) {
        throw new CatchupException(any);
      }
    }

    // either we have a tmp failure on secondary, or secondary is not defined.
    try {
      return catchupPhaseOne(primary.get(), primaryHorizon);
    } catch (Exception any) {
      throw new CatchupException(any);
    }
  }

  @VisibleForTesting
  void catchupPhaseTwo(
      PrimaryDataSourceSupplier primary, long phase1HighwaterMark, FactStreamHorizon primaryHorizon)
      throws CatchupException {
    // proceed to phase 2 on the primary
    PgCatchup pgCatchup =
        pgCatchupFactory.create(
            request,
            pipeline,
            serial,
            primaryHorizon,
            primary.get(),
            PgCatchupFactory.Phase.PHASE_2);
    // before starting to run phase2, we'll ffwd to what phase1 returned as HWM.
    // while this might seem to be a minor optimization, it matters when phase1 found no
    // matching fact at all. Without ffwd, we would need to recheck all facts from ser
    // *again*.
    pgCatchup.fastForward(phase1HighwaterMark);
    try {
      pgCatchup.run();
    } catch (Exception e) {
      throw new CatchupException(e);
    }
  }

  @SneakyThrows
  @VisibleForTesting
  CatchupDataSource createCatchupDataSource(
      @NonNull DataSource ds, PushbackServerPipeline pipeline) {
    return new CatchupDataSource(ds.getConnection(), catchupConnectionModifiers(request), pipeline);
  }

  @VisibleForTesting
  long catchupPhaseOne(
      @NonNull SingleConnectionDataSource dataSourceToUseForP1, @NonNull FactStreamHorizon horizon)
      throws SQLException, PipelineAlreadyClosedException {
    long from = serial.get();
    if (horizon.factSerial() <= from) {
      // it does not make any sense to try to query for data we know is not there.
      // this may happen a lot, if the offload datasource has a considerable lag.
      return from;
    } else {

      pgCatchupFactory
          .create(
              request,
              pipeline,
              serial,
              horizon,
              dataSourceToUseForP1,
              PgCatchupFactory.Phase.PHASE_1)
          .run();

      // serial may be smaller when no fact near the horizon matched. In that case, continue
      // from the bound so phase 2 does not query the already covered range again.
      //
      // Note that any kind of exceptional behavior like cancellation, random SQLExceptions or the
      // like are expect to THROW, so that a "between phases ffwd" only happens, if we know that
      // there a cannot be any matches between ser and hwm, if hwm is greater.
      return Math.max(serial.get(), horizon.factSerial());
    }
  }

  @VisibleForTesting
  boolean isConnected() {
    return !disconnected.get();
  }

  public synchronized void close() {
    log.trace("{} disconnecting ", request);
    disconnected.set(true);
    if (queryExecutor != null) {
      eventBus.unregister(queryExecutor);
      queryExecutor.cancel();
      queryExecutor = null;
    }
    log.debug("{} disconnected ", request);

    // free pipeline resources
    //
    // note that this also signals to upstream threads that the pipeline can no longer accept new
    // elements and the respective process can be terminated.
    pipeline.close();

    // signal close
    telemetry.onClose(request);
  }
}
