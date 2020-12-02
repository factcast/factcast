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
package org.factcast.store.pgsql.internal.listen;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.store.pgsql.PgConfigurationProperties;
import org.factcast.store.pgsql.internal.PgConstants;
import org.factcast.store.pgsql.internal.PgMetrics;
import org.factcast.store.pgsql.internal.PgMetrics.StoreMetrics.OP;
import org.postgresql.PGNotification;
import org.postgresql.jdbc.PgConnection;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

/**
 * Listens (sql LISTEN command) to a channel on Postgresql and passes a trigger on an EventBus.
 *
 * <p>This trigger then is supposed to "encourage" active subscriptions to query for new Facts from
 * PG.
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
@SuppressWarnings("UnstableApiUsage")
@Slf4j
@RequiredArgsConstructor
public class PgListener implements InitializingBean, DisposableBean {

  @NonNull final PgConnectionSupplier pgConnectionSupplier;

  @NonNull final EventBus eventBus;

  @NonNull final PgConfigurationProperties props;

  @NonNull final PgMetrics pgMetrics;

  private final AtomicBoolean running = new AtomicBoolean(true);

  private Thread listenerThread;

  private final CountDownLatch countDownLatch = new CountDownLatch(1);

  @VisibleForTesting
  protected void listen() {
    log.trace("Starting instance Listener");
    listenerThread = new Thread(new NotificationReceiverLoop(), "PG Instance Listener");
    listenerThread.setDaemon(true);
    listenerThread.setUncaughtExceptionHandler(
        (t, e) -> log.error("thread " + t + " encountered an unhandled exception", e));
    listenerThread.start();
    try {
      log.info("Waiting to establish postgres listener (max 15sec.)");
      boolean await = countDownLatch.await(15, TimeUnit.SECONDS);
      log.info("postgres listener " + (await ? "" : "not ") + "established");
    } catch (InterruptedException ignored) {
    }
  }

  @VisibleForTesting
  protected class NotificationReceiverLoop implements Runnable {

    @Override
    public void run() {
      while (running.get()) {

        // new connection
        try (PgConnection pc = pgConnectionSupplier.get()) {
          connectionSetup(pc);

          while (running.get()) {
            PGNotification[] notifications = receiveNotifications(pc);
            informSubscriberOfChannelNotifications(notifications);
          }
        } catch (Exception e) {
          if (running.get()) {
            log.warn("While waiting for Notifications", e);
            sleep();
          }
        }
      }
    }
  }

  private void connectionSetup(PgConnection pc) throws SQLException {
    setupPostgresListeners(pc);
    countDownLatch.countDown();
    informSubscribersAboutFreshConnection();
  }

  @VisibleForTesting
  protected void setupPostgresListeners(PgConnection pc) throws SQLException {
    try (PreparedStatement ps = pc.prepareStatement(PgConstants.LISTEN_SQL)) {
      ps.execute();
    }
    try (PreparedStatement ps = pc.prepareStatement(PgConstants.LISTEN_ROUNDTRIP_CHANNEL_SQL)) {
      ps.execute();
    }
  }

  // make sure subscribers did not miss anything while we reconnected
  @VisibleForTesting
  protected void informSubscribersAboutFreshConnection() {
    postEvent("scheduled-poll");
  }

  @VisibleForTesting
  protected void informSubscriberOfChannelNotifications(PGNotification[] notifications) {
    if (Arrays.stream(notifications).anyMatch(n -> PgConstants.CHANNEL_NAME.equals(n.getName()))) {
      log.trace("notifying consumers for '{}'", PgConstants.CHANNEL_NAME);
      postEvent(PgConstants.CHANNEL_NAME);
    }
  }

  // try to receive Postgres notifications until timeout is over. In case we
  // didn't receive any notification we
  // check if the database connection is still healthy
  @VisibleForTesting
  protected PGNotification[] receiveNotifications(PgConnection pc) throws SQLException {
    try {
      PGNotification[] notifications =
          pc.getNotifications(props.getFactNotificationBlockingWaitTimeInMillis());
      if (notifications == null) {
        notifications = checkDatabaseConnectionHealthy(pc);
      }
      return notifications;
    } catch (Exception e) {
      if (running.get()) throw e;
      else return new PGNotification[0];
    }
  }

  // sends a roundtrip notification to database and expects to receive at
  // least this
  // notification back
  @VisibleForTesting
  protected PGNotification[] checkDatabaseConnectionHealthy(PgConnection connection)
      throws SQLException {

    long start = System.nanoTime();

    connection.prepareCall(PgConstants.NOTIFY_ROUNDTRIP).execute();
    PGNotification[] notifications =
        connection.getNotifications(props.getFactNotificationMaxRoundTripLatencyInMillis());
    if (notifications == null) {
      // missed the notifications from the DB, something is fishy
      // here....
      pgMetrics.counter(OP.MISSED_ROUNDTRIP).increment();
      throw new SQLException(
          "Missed roundtrip notification from channel '"
              + PgConstants.ROUNDTRIP_CHANNEL_NAME
              + "'");
    } else {
      // return since there might have also received channel notifications
      pgMetrics
          .timer(OP.NOTIFY_ROUNDTRIP_LATENCY)
          .record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
      return notifications;
    }
  }

  @VisibleForTesting
  protected void sleep() {
    try {
      Thread.sleep(props.getFactNotificationNewConnectionWaitTimeInMillis());
    } catch (InterruptedException ignore) {
    }
  }

  @VisibleForTesting
  protected void postEvent(final String name) {
    if (running.get()) {
      eventBus.post(new FactInsertionEvent(name));
    }
  }

  @RequiredArgsConstructor
  public static class FactInsertionEvent {

    @SuppressWarnings("unused")
    @Getter
    final String name;
  }

  @Override
  public void afterPropertiesSet() {
    listen();
  }

  @Override
  public void destroy() {
    this.running.set(false);
    if (listenerThread != null) {
      listenerThread.interrupt();
    }
  }
}
