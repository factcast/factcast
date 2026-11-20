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
import jakarta.annotation.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.*;
import org.factcast.store.internal.notification.*;
import org.springframework.beans.factory.*;
import org.springframework.jdbc.core.*;

@Slf4j
public class NudgeNotificationHandler implements DisposableBean {
  static final String BASE_EXISTS_SQL = "SELECT (exists(select 1 from notification where ser=?))";
  static final String MAX_NOTIFICATION_SERIAL = "SELECT COALESCE(MAX(ser),0) FROM notification";
  private final @NonNull EventBus bus;
  private final @NonNull JdbcTemplate jdbc;
  private final @NonNull StoreConfigurationProperties props;
  private final @NonNull PgMetrics metrics;
  @VisibleForTesting protected final AtomicLong notificationSer = new AtomicLong(0);
  @VisibleForTesting protected final Timer timer = new Timer(true);
  // this we need in order to skip obsolete tasks
  @VisibleForTesting protected final AtomicLong timerVersion = new AtomicLong(0);
  private final AtomicBoolean refreshRequested = new AtomicBoolean(false);
  private final ReentrantLock refreshLock = new ReentrantLock();
  private final io.micrometer.core.instrument.@NonNull Timer metricsTimer;
  private final AtomicLong lastPoll = new AtomicLong(0);

  public NudgeNotificationHandler(
      @NonNull EventBus bus,
      @NonNull JdbcTemplate jdbc,
      @NonNull StoreConfigurationProperties props,
      @NonNull PgMetrics metrics) {
    this(bus, jdbc, props, metrics, !props.isReadOnlyModeEnabled());
  }

  @VisibleForTesting
  NudgeNotificationHandler(
      @NonNull EventBus bus,
      @NonNull JdbcTemplate jdbc,
      @NonNull StoreConfigurationProperties props,
      @NonNull PgMetrics metrics,
      boolean scheduleCleanupTask) {
    this.bus = bus;
    this.jdbc = jdbc;
    this.props = props;
    this.metrics = metrics;
    bus.register(this);

    if (scheduleCleanupTask) {
      // even though a smaller notification table is better for overall performance, we keep up to 5
      // mins old notifications in there because of a possible, but not very likely race condition,
      // that is based on transactions taking >60sec to commit after inserting a fact.
      int cleanupIntervalInMinutes = 5;
      timer.scheduleAtFixedRate(
          new ScheduledCleanup(), 0, Duration.ofMinutes(cleanupIntervalInMinutes).toMillis());
    }

    // fallback that makes sure nothing is missed, even if last commit took >100msec & it is
    // followed by silence.
    timer.scheduleAtFixedRate(new CatchAllTask(), 0, 500);

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
    long interval = props.getFactNotificationPollLatencyInMillis();

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
    refreshRequested.set(true);
    drainRefreshRequests();
  }

  private void drainRefreshRequests() {
    // EventBus dispatch can request another refresh on this thread. Let the current pass finish
    // updating its cursor before processing that request.
    if (refreshLock.isHeldByCurrentThread()) return;

    RuntimeException exception = null;

    // Keep the accumulated failures while picking up requests arriving during unlock.
    while (refreshLock.tryLock()) {
      try {
        exception = processPendingRefreshRequests(exception);
      } finally {
        refreshLock.unlock();
      }

      if (!refreshRequested.get()) break;
    }

    if (exception != null) throw exception;
  }

  private RuntimeException processPendingRefreshRequests(@Nullable RuntimeException exception) {
    // The caller holds refreshLock. Clear before work so incoming requests trigger another pass.
    while (refreshRequested.getAndSet(false)) {
      try {
        fetchPairsAndDispatchOnce();
        lastPoll.set(System.currentTimeMillis());
      } catch (RuntimeException e) {
        exception = accumulateFailure(exception, e);
      }
    }
    return exception;
  }

  private static RuntimeException accumulateFailure(
      @Nullable RuntimeException firstFailure, @Nonnull RuntimeException nextFailure) {
    if (firstFailure == null) return nextFailure;
    if (firstFailure != nextFailure) firstFailure.addSuppressed(nextFailure);
    return firstFailure;
  }

  private void fetchPairsAndDispatchOnce() {
    long lowerSerial = notificationSer.get();
    // Writers hold the exclusive publication lock until commit, so committed maxima are ordered.
    long upperSerial =
        Objects.requireNonNull(jdbc.queryForObject(MAX_NOTIFICATION_SERIAL, Long.class));
    if (upperSerial < lowerSerial) {
      // Cleanup or a sequence reset can lower the maximum. Wake subscribers before rebasing.
      log.trace("Notification maximum moved backwards, waking all subscribers");
      bus.post(FactInsertionNotification.internal());
      notificationSer.set(upperSerial);
      return;
    }
    boolean baseLineMissing =
        lowerSerial > 0
            && Boolean.FALSE.equals(
                jdbc.queryForObject(BASE_EXISTS_SQL, Boolean.class, lowerSerial));

    //noinspection StatementWithEmptyBody
    if (upperSerial > 0) {
      if ((baseLineMissing || lowerSerial == 0)) {
        log.trace("No reliable notification baseline, waking all subscribers");
        bus.post(FactInsertionNotification.internal());
        notificationSer.set(upperSerial);
        return;
      }
    } else {
      // if nothing happened for a long time, all notifications might have been deleted.
      // in that case, we do not want to wake subscribers,
    }

    if (upperSerial <= lowerSerial) return;

    final var timerSample = metrics.startSample();
    try {
      List<FetchNotificationTuple> tuples =
          jdbc.query(
              "SELECT max(ser) as max,ns,type FROM notification "
                  + "WHERE notification.ser > ? AND notification.ser <= ? "
                  + "GROUP BY DISTINCT(ns,type) ORDER BY max",
              DataClassRowMapper.newInstance(FetchNotificationTuple.class),
              lowerSerial,
              upperSerial);

      if (!tuples.isEmpty()) {
        log.trace("Fetched {} notification{}", tuples.size(), tuples.size() > 1 ? "s" : "");
        tuples.forEach(t -> bus.post(t.toFactInsertionNotification()));
      }
      notificationSer.set(upperSerial);
    } finally {
      timerSample.stop(metricsTimer);
    }
  }

  class CatchAllTask extends TimerTask {

    @Override
    public void run() {
      if (System.currentTimeMillis() - lastPoll.get()
          > props.getFactNotificationSafetyPollLatencyInMillis()) {
        try {
          fetchPairsAndDispatch();
        } catch (RuntimeException e) {
          log.warn("Scheduled safety notification poll failed", e);
        }
      }
    }
  }
}
