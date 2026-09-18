/*
 * Copyright © 2017-2026 factcast.org
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
package org.factcast.store.internal.listen;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.*;
import org.factcast.store.internal.horizon.FactStreamHorizon;
import org.factcast.store.internal.horizon.FactStreamHorizonProvider;
import org.factcast.store.internal.notification.*;
import org.springframework.beans.factory.*;
import org.springframework.jdbc.core.*;

@Slf4j
public class NudgeNotificationHandler implements DisposableBean {
  static final String BASE_EXISTS_SQL = "SELECT (exists(select 1 from notification where ser=?))";
  private final @NonNull EventBus bus;
  private final @NonNull JdbcTemplate jdbc;
  private final @NonNull StoreConfigurationProperties props;
  private final @NonNull PgMetrics metrics;
  private final @NonNull FactStreamHorizonProvider horizonProvider;
  @VisibleForTesting protected final AtomicLong notificationSer = new AtomicLong(0);
  @VisibleForTesting protected final Timer timer = new Timer(true);
  // this we need in order to skip obsolete tasks
  @VisibleForTesting protected final AtomicLong timerVersion = new AtomicLong(0);
  @VisibleForTesting protected final AtomicLong requestedRefresh = new AtomicLong(0);
  @VisibleForTesting protected final AtomicLong completedRefresh = new AtomicLong(0);
  private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);
  private io.micrometer.core.instrument.@NonNull Timer metricsTimer;

  public NudgeNotificationHandler(
      @NonNull EventBus bus,
      @NonNull JdbcTemplate jdbc,
      @NonNull StoreConfigurationProperties props,
      @NonNull PgMetrics metrics,
      @NonNull FactStreamHorizonProvider horizonProvider) {
    this(bus, jdbc, props, metrics, horizonProvider, !props.isReadOnlyModeEnabled());
  }

  @VisibleForTesting
  NudgeNotificationHandler(
      @NonNull EventBus bus,
      @NonNull JdbcTemplate jdbc,
      @NonNull StoreConfigurationProperties props,
      @NonNull PgMetrics metrics,
      @NonNull FactStreamHorizonProvider horizonProvider,
      boolean scheduleCleanupTask) {
    this.bus = bus;
    this.jdbc = jdbc;
    this.props = props;
    this.metrics = metrics;
    this.horizonProvider = horizonProvider;
    bus.register(this);
    if (scheduleCleanupTask)
      timer.scheduleAtFixedRate(new ScheduledCleanup(), 0, Duration.ofMinutes(1).toMillis());
    metricsTimer = metrics.timer(StoreMetrics.OP.SELECT_DISTINCT_NOTIFICATIONS);
  }

  @Override
  public void destroy() throws Exception {
    bus.unregister(this);
    timer.cancel();
  }

  @Subscribe
  public void nudge(NudgeNotification nudgeNotification) {
    log.trace("Nudge received");
    fetchPairsAndDispatch();

    // this makes all currently schedule tasks just return. unfortunately, we cannot cancel tasks on
    // a timer without also canceling the timer itself
    long version = timerVersion.incrementAndGet();
    long interval = props.getMaxNotificationPollLatencyInMillis();

    for (long i = 0; i < 100; i = i + interval) {
      timer.schedule(new ScheduledPoll(version), i + interval);
    }
  }

  @RequiredArgsConstructor
  class ScheduledPoll extends TimerTask {
    final long version;

    @Override
    public void run() {
      if (version == timerVersion.get()) {
        // only if we're in the current 100msec window
        try {
          fetchPairsAndDispatch();
        } catch (RuntimeException e) {
          log.warn("Scheduled notification poll failed", e);
        }
      }
    }
  }

  class ScheduledCleanup extends TimerTask {
    @Override
    public void run() {
      try {
        jdbc.execute("CALL notificationCleanup()");
      } catch (RuntimeException e) {
        log.warn("Scheduled notification cleanup failed", e);
      }
    }
  }

  public record FetchNotificationTuple(long max, String ns, String type) {
    public StoreNotification toFactInsertionNotification() {
      return FactInsertionNotification.internal(ns(), type());
    }
  }

  void fetchPairsAndDispatch() {
    requestedRefresh.incrementAndGet();
    drainRefreshRequests();
  }

  private void drainRefreshRequests() {
    RuntimeException firstFailure = null;
    while (refreshInProgress.compareAndSet(false, true)) {
      try {
        long generation;
        do {
          generation = requestedRefresh.get();
          try {
            fetchPairsAndDispatchOnce();
          } catch (RuntimeException e) {
            if (firstFailure == null) firstFailure = e;
          } finally {
            completedRefresh.set(generation);
          }
        } while (requestedRefresh.get() != generation);
      } finally {
        refreshInProgress.set(false);
      }

      // A request can arrive between the last generation check and releasing the single-flight
      // flag. In that case this thread picks it up unless the requesting thread already did.
      if (completedRefresh.get() == requestedRefresh.get()) {
        if (firstFailure != null) throw firstFailure;
        return;
      }
    }
  }

  private void fetchPairsAndDispatchOnce() {
    FactStreamHorizon horizon = horizonProvider.advance();
    long lowerSerial = notificationSer.get();
    long horizonSerial = horizon.notificationSerial();

    boolean cursorMissing =
        lowerSerial > 0
            && Boolean.FALSE.equals(
                jdbc.queryForObject(BASE_EXISTS_SQL, Boolean.class, lowerSerial));

    if (cursorMissing) {
      log.trace("No reliable notification cursor, waking all subscribers");
      bus.post(FactInsertionNotification.internal());
      notificationSer.set(horizonSerial);
      return;
    }

    if (horizonSerial <= lowerSerial) return;

    if (lowerSerial == 0) {
      log.trace("No reliable notification cursor, waking all subscribers");
      bus.post(FactInsertionNotification.internal());
      notificationSer.set(horizonSerial);
      return;
    }

    final var timerSample = metrics.startSample();
    List<FetchNotificationTuple> tuples =
        jdbc.query(
            "SELECT max(ser) as max,ns,type FROM notification "
                + "WHERE notification.ser > ? AND notification.ser <= ? "
                + "GROUP BY DISTINCT(ns,type) ORDER BY max",
            DataClassRowMapper.newInstance(FetchNotificationTuple.class),
            lowerSerial,
            horizonSerial);

    timerSample.stop(metricsTimer);
    if (!tuples.isEmpty()) {
      log.trace("Fetched {} notification{}", tuples.size(), tuples.size() > 1 ? "s" : "");
      tuples.forEach(t -> bus.post(t.toFactInsertionNotification()));
    }
    notificationSer.set(horizonSerial);
  }
}
