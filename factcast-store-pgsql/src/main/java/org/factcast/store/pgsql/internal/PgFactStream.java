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
package org.factcast.store.pgsql.internal;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.TransformationException;
import org.factcast.core.subscription.observer.FastForwardTarget;
import org.factcast.core.util.ExceptionHelper;
import org.factcast.store.pgsql.internal.StoreMetrics.VALUE;
import org.factcast.store.pgsql.internal.catchup.PgCatchupFactory;
import org.factcast.store.pgsql.internal.query.PgFactIdToSerialMapper;
import org.factcast.store.pgsql.internal.query.PgLatestSerialFetcher;
import org.factcast.store.pgsql.internal.query.PgQueryBuilder;
import org.factcast.store.pgsql.registry.transformation.chains.MissingTransformationInformation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowCallbackHandler;

/**
 * Creates and maintains a subscription.
 *
 * @author <uwe.schaefer@prisma-capacity.eu>
 */
@SuppressWarnings("UnstableApiUsage")
@Slf4j
@RequiredArgsConstructor
public class PgFactStream {

  final JdbcTemplate jdbcTemplate;

  final EventBus eventBus;

  final PgFactIdToSerialMapper idToSerMapper;

  final SubscriptionImpl subscription;

  final AtomicLong serial = new AtomicLong(0);

  final AtomicBoolean disconnected = new AtomicBoolean(false);

  final PgLatestSerialFetcher fetcher;

  final PgCatchupFactory pgCatchupFactory;
  final FastForwardTarget ffwdTarget;
  final PgMetrics metrics;

  CondensedQueryExecutor condensedExecutor;

  SubscriptionRequestTO request;

  PgPostQueryMatcher postQueryMatcher;

  void connect(@NonNull SubscriptionRequestTO request) {
    this.request = request;
    log.debug("{} connect subscription {}", request, request.dump());
    postQueryMatcher = new PgPostQueryMatcher(request);
    PgQueryBuilder q = new PgQueryBuilder(request.specs());
    initializeSerialToStartAfter();
    String sql = q.createSQL();
    PreparedStatementSetter setter = q.createStatementSetter(serial);
    RowCallbackHandler rsHandler = new FactRowCallbackHandler(subscription, postQueryMatcher);
    PgSynchronizedQuery query =
        new PgSynchronizedQuery(jdbcTemplate, sql, setter, rsHandler, serial, fetcher);
    catchupAndFollow(request, subscription, query);
  }

  private void initializeSerialToStartAfter() {
    Long startingSerial = request.startingAfter().map(idToSerMapper::retrieve).orElse(0L);
    serial.set(startingSerial);
    log.trace("{} setting starting point to SER={}", request, startingSerial);
  }

  @VisibleForTesting
  void catchupAndFollow(
      SubscriptionRequest request, SubscriptionImpl subscription, PgSynchronizedQuery query) {
    if (request.ephemeral()) {
      // just fast forward to the latest event published by now
      serial.set(fetcher.retrieveLatestSer());
    } else {
      try {
        catchup(postQueryMatcher);
        logCatchupTransformationStats();
      } catch (Throwable e) {
        // might help to find networking issues while catching up
        log.warn("{} While catching up: ", request, e);
        subscription.notifyError(e);
      }
    }

    long startedSer = 0;
    UUID startedId = null;
    if (request.startingAfter().isPresent()) {
      startedId = request.startingAfter().get();
      startedSer = idToSerMapper.retrieve(startedId);
    }

    // test for ffwd
    if (isConnected()) {
      if (!factsHaveBeenSent(startedSer, serial)) {
        // we have not sent any fact. check for ffwding

        UUID targetId = ffwdTarget.targetId();
        long targetSer = ffwdTarget.targetSer();
        long startSer = 0;

        if (targetId != null && (!targetId.equals(startedId) && (targetSer > startedSer))) {
          log.trace(
              "{} no facts applied – offering ffwd notification to fact id {}", request, targetId);
          subscription.notifyFastForward(targetId);
        }
      }
    }

    // propagate catchup
    if (isConnected()) {
      log.trace("{} signaling catchup", request);
      subscription.notifyCatchup();
    }
    if (isConnected()) {
      if (request.continuous()) {
        log.debug("{} entering follow mode", request);
        long delayInMs;
        if (request.maxBatchDelayInMs() < 1) {
          // ok, instant query after NOTIFY
          delayInMs = 0;
        } else {
          // spread consumers, so that they query at different points
          // in time, even if they get triggered at the same PIT, and
          // share the same latency requirements
          //
          // ok, that is unlikely to be necessary, but easy to do,
          // so...
          delayInMs =
              ((request.maxBatchDelayInMs() / 4L) * 3L)
                  + (long) (Math.abs(Math.random() * (request.maxBatchDelayInMs() / 4.0)));
          log.trace(
              "{} setting delay to {}, maxDelay was {}",
              request,
              delayInMs,
              request.maxBatchDelayInMs());
        }
        condensedExecutor = new CondensedQueryExecutor(delayInMs, query, this::isConnected);
        eventBus.register(condensedExecutor);
        // catchup phase 3 – make sure, we did not miss any fact due to
        // slow registration
        condensedExecutor.trigger();
      } else {
        subscription.notifyComplete();
        log.debug("Completed {}", request);
      }
    }
  }

  @VisibleForTesting
  boolean factsHaveBeenSent(long startedAt, AtomicLong serial) {

    if (serial.get() == 0 || serial.get() == startedAt) {
      // nothing has been sent out
      return false;
    }

    return true;
  }

  @VisibleForTesting
  void catchup(PgPostQueryMatcher postQueryMatcher) {
    if (isConnected()) {
      log.trace("{} catchup phase1 - historic facts staring with SER={}", request, serial.get());
      pgCatchupFactory.create(request, postQueryMatcher, subscription, serial, metrics).run();
    }
    if (isConnected()) {
      log.trace("{} catchup phase2 - facts since connect (SER={})", request, serial.get());
      pgCatchupFactory.create(request, postQueryMatcher, subscription, serial, metrics).run();
    }
  }

  @VisibleForTesting
  enum RatioLogLevel {
    DEBUG,
    INFO,
    WARN
  }

  @VisibleForTesting
  void logCatchupTransformationStats() {
    if (subscription.factsTransformed().get() > 0) {
      long sum = subscription.factsTransformed().get() + subscription.factsNotTransformed().get();
      long transf = subscription.factsTransformed().get();
      long ratio = Math.round(100.0 / sum * transf);
      RatioLogLevel level = calculateLogLevel(sum, ratio);

      switch (level) {
        case DEBUG:
          log.debug("{} CatchupTransformationRatio: {}%, ({}/{})", request, ratio, transf, sum);
          break;
        case INFO:
          log.info("{} CatchupTransformationRatio: {}%, ({}/{})", request, ratio, transf, sum);
          break;
        case WARN:
          log.warn("{} CatchupTransformationRatio: {}%, ({}/{})", request, ratio, transf, sum);
          break;
        default:
          throw new IllegalArgumentException("switch fall-through. THIS IS A BUG! " + level);
      }
    }
  }

  @VisibleForTesting
  RatioLogLevel calculateLogLevel(long sum, long ratio) {
    // only bother sending metrics or raising the level if we did some significant catchup
    RatioLogLevel level = RatioLogLevel.DEBUG;
    if (sum >= 50) {
      metrics.measure(VALUE.CATCHUP_TRANSFORMATION_RATIO, ratio);

      if (ratio >= 20.0) {
        level = RatioLogLevel.WARN;
      } else if (ratio >= 10.0) {
        level = RatioLogLevel.INFO;
      }
    }
    return level;
  }

  private boolean isConnected() {
    return !disconnected.get();
  }

  public synchronized void close() {
    log.trace("{} disconnecting ", request);
    disconnected.set(true);
    if (condensedExecutor != null) {
      eventBus.unregister(condensedExecutor);
      condensedExecutor.cancel();
      condensedExecutor = null;
    }
    log.debug("{} disconnected ", request);
  }

  @RequiredArgsConstructor
  private class FactRowCallbackHandler implements RowCallbackHandler {

    final SubscriptionImpl subscription;

    final PgPostQueryMatcher postQueryMatcher;

    @SuppressWarnings("NullableProblems")
    @Override
    public void processRow(ResultSet rs) throws SQLException {
      if (isConnected()) {
        if (rs.isClosed()) {
          throw new IllegalStateException(
              "ResultSet already closed. We should not have got here. THIS IS A BUG!");
        }
        Fact f = PgFact.from(rs);
        UUID factId = f.id();
        if (postQueryMatcher.test(f)) {
          try {
            subscription.notifyElement(f);
            log.trace("{} notifyElement called with id={}", request, factId);
          } catch (MissingTransformationInformation | TransformationException e) {
            log.warn("{} transformation error: {}", request, e.getMessage());
            subscription.notifyError(e);
            // will vanish, because this is called from a timer.
            throw e;
          } catch (Throwable e) {
            // debug level, because it happens regularly on
            // disconnecting clients.
            // TODO add sid
            log.debug("{} exception from subscription: {}", request, e.getMessage());
            try {
              subscription.close();
            } catch (Exception e1) {
              // TODO add sid
              log.warn("{} exception while closing subscription: {}", request, e1.getMessage());
            }
            // close result set in order to release DB resources as
            // early as possible
            rs.close();
            throw ExceptionHelper.toRuntime(e);
          }
        } else {
          // TODO add sid
          log.trace("{} filtered id={}", request, factId);
        }
        serial.set(rs.getLong(PgConstants.COLUMN_SER));
      }
    }
  }
}
