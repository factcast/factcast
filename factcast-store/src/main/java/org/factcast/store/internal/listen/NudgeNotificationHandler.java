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
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;
import lombok.*;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.notification.*;
import org.springframework.beans.factory.*;
import org.springframework.jdbc.core.JdbcTemplate;

@RequiredArgsConstructor
public class NudgeNotificationHandler implements SmartInitializingSingleton, DisposableBean {
  private static final String BASE_EXISTS_SQL =
      "SELECT (exists(select 1 from notification where ser=?))";
  private final @NonNull EventBus bus;
  private final @NonNull JdbcTemplate jdbc;
  private final @NonNull StoreConfigurationProperties props;
  @VisibleForTesting protected final StampedLock lock = new StampedLock();
  @VisibleForTesting protected final AtomicLong notificationSer = new AtomicLong(0);
  @VisibleForTesting protected final Timer timer = new Timer(true);
  // this we need in order to skip obsolete tasks
  @VisibleForTesting protected final AtomicLong timerVersion = new AtomicLong(0);

  @Override
  public void destroy() throws Exception {
    bus.unregister(this);
    timer.cancel();
  }

  @Override
  public void afterSingletonsInstantiated() {
    bus.register(this);
    timer.scheduleAtFixedRate(new ScheduledCleanup(), 0, Duration.ofMinutes(1).toMillis());
  }

  @Subscribe
  public void nudge(NudgeNotification nudgeNotification) {
    if (notificationSer.get() == 0
        || Boolean.FALSE.equals(
            jdbc.queryForObject(BASE_EXISTS_SQL, Boolean.class, notificationSer.get()))) {
      // in both cases, we need all subscriptions to fetch
      // and set the notificationSer to current max
      Long max = jdbc.queryForObject("SELECT max(ser) FROM notification", Long.class);
      if (max != null) notificationSer.set(max.longValue());

      bus.post(FactInsertionNotification.internal());
    } else {
      fetchPairsAndDispatch();
    }

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
        fetchPairsAndDispatch();
      }
    }
  }

  class ScheduledCleanup extends TimerTask {
    @Override
    public void run() {
      jdbc.execute("select notificationCleanup()");
    }
  }

  public record FetchNotificationTuple(long max, long ser, String ns, String type) {
    public StoreNotification toFactInsertionNotification() {
      return FactInsertionNotification.internal(ns(), type());
    }
  }

  void fetchPairsAndDispatch() {
    // we're trying to avoid query storms here, as well as raceconditions on notificationSer.set
    // we're not using synchronized in order to not stack up useless queries.
    long lockStamp = lock.tryWriteLock();
    if (lockStamp != 0) {
      try {
        // TODO we certainly want a metric emitted from here
        List<FetchNotificationTuple> results =
            jdbc.queryForList(
                "SELECT max(ser) as ser,ns,type FROM notification WHERE ser > ? GROUP BY DISTINCT(ns,type) ORDER BY ser",
                FetchNotificationTuple.class);

        results.forEach(
            t -> {
              bus.post(t.toFactInsertionNotification());
              notificationSer.set(t.ser());
            });
      } finally {
        lock.unlockWrite(lockStamp);
      }
    }
  }
}
