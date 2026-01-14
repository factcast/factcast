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
package org.factcast.store.internal.listen;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.*;
import org.factcast.store.internal.notification.*;
import org.postgresql.PGNotification;
import org.postgresql.jdbc.PgConnection;
import org.springframework.beans.factory.*;

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

  @NonNull final StoreConfigurationProperties props;

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
        try (PgConnection pc =
            pgConnectionSupplier.getUnpooledConnection("notification-receiver-loop")) {
          connectionSetup(pc);

          while (running.get()) {
            PGNotification[] notifications = receiveNotifications(pc);
            processNotifications(notifications);
          }
        } catch (Exception e) {
          if (running.get()) {
            log.warn("While waiting for Notifications", e);
            sleep();
          }
        }
      }
    }

    @SneakyThrows
    private void sleep() {
      TimeUnit.MILLISECONDS.sleep(props.getFactNotificationNewConnectionWaitTimeInMillis());
    }
  }

  private void connectionSetup(PgConnection pc) throws SQLException {
    setupPostgresListeners(pc);
    countDownLatch.countDown();
    informSubscribersAboutFreshConnection();
  }

  @VisibleForTesting
  protected void setupPostgresListeners(PgConnection pc) throws SQLException {
    try (PreparedStatement ps1 = pc.prepareStatement(PgConstants.LISTEN_INSERT_CHANNEL_SQL);
        PreparedStatement ps2 = pc.prepareStatement(PgConstants.LISTEN_ROUNDTRIP_CHANNEL_SQL);
        PreparedStatement ps3 =
            pc.prepareStatement(PgConstants.LISTEN_BLACKLIST_CHANGE_CHANNEL_SQL);
        PreparedStatement ps4 =
            pc.prepareStatement(PgConstants.LISTEN_SCHEMASTORE_CHANGE_CHANNEL_SQL);
        PreparedStatement ps5 =
            pc.prepareStatement(PgConstants.LISTEN_TRANSFORMATIONSTORE_CHANGE_CHANNEL_SQL);
        PreparedStatement ps6 = pc.prepareStatement(PgConstants.LISTEN_TRUNCATION_CHANNEL_SQL);
        PreparedStatement ps7 = pc.prepareStatement(PgConstants.LISTEN_UPDATE_CHANNEL_SQL)) {
      ps1.execute();
      ps2.execute();
      ps3.execute();
      ps4.execute();
      ps5.execute();
      ps6.execute();
      ps7.execute();
    }
  }

  // make sure subscribers did not miss anything while we reconnected
  @VisibleForTesting
  protected void informSubscribersAboutFreshConnection() {
    post(FactInsertionNotification.internal());
    post(BlacklistChangeNotification.internal());
    post(SchemaStoreChangeNotification.internal());
  }

  @VisibleForTesting
  protected void processNotifications(PGNotification[] notifications) {
    List<PGNotification> list = List.of(notifications);
    Predicate<PGNotification> isFactInsert =
        n -> PgConstants.CHANNEL_FACT_INSERT.equals(n.getName());

    List<PGNotification> nonFactInserts =
        list.stream().filter(Predicate.not(isFactInsert)).toList();

    // if there are more than 1 blacklist_change notifications in the array, we need only the last
    // one
    // note we filter BEFORE parsing to save some cpu cycles
    streamWithCompactedBlacklistChanges(nonFactInserts)
        .map(StoreNotification::createFrom)
        .filter(Objects::nonNull)
        .forEach(this::post);

    // it does not make any sense here to filter before parsing, so that we start with the mapping
    Stream<FactInsertionNotification> factInserts =
        list.stream()
            .filter(isFactInsert)
            .map(FactInsertionNotification::from)
            .filter(Objects::nonNull);
    compact(factInserts).forEach(this::post);
  }

  /** filters duplications regarding ns&type */
  @VisibleForTesting
  Stream<FactInsertionNotification> compact(Stream<FactInsertionNotification> factInserts) {
    Set<String> coordinatesIncluded = new HashSet<>();
    return factInserts.filter(n -> coordinatesIncluded.add(n.nsAndType()));
  }

  /** remove all but the last CHANNEL_BLACKLIST_CHANGE */
  @VisibleForTesting
  Stream<PGNotification> streamWithCompactedBlacklistChanges(List<PGNotification> nonFactInserts) {
    Optional<PGNotification> lastBCN =
        nonFactInserts.stream()
            .filter(n -> PgConstants.CHANNEL_BLACKLIST_CHANGE.equals(n.getName()))
            .reduce((a, b) -> b);

    if (lastBCN.isPresent()) {
      // delete all others
      PGNotification last = lastBCN.get();
      return nonFactInserts.stream()
          .filter(n -> !PgConstants.CHANNEL_BLACKLIST_CHANGE.equals(n.getName()) || n == last);
    } else {
      return nonFactInserts.stream();
    }
  }

  @VisibleForTesting
  void post(@NonNull StoreNotification n) {
    if (running.get()) {
      log.trace("posting to eventBus: {}", n);
      eventBus.post(n);
    }
  }

  // try to receive Postgres notifications until timeout is over. In case we
  // didn't receive any notification we
  // check if the database connection is still healthy
  @VisibleForTesting
  protected PGNotification[] receiveNotifications(PgConnection pc) throws SQLException {
    PGNotification[] notifications =
        pc.getNotifications(props.getFactNotificationBlockingWaitTimeInMillis());
    if (notifications == null || notifications.length == 0) {
      notifications = checkDatabaseConnectionHealthy(pc);
    }
    return notifications;
  }

  // sends a roundtrip notification to database and expects to receive at
  // least this
  // notification back
  @VisibleForTesting
  protected PGNotification[] checkDatabaseConnectionHealthy(PgConnection connection)
      throws SQLException {

    long start = System.nanoTime();

    connection.prepareCall(PgConstants.NOTIFY_ROUNDTRIP_SQL).execute();
    PGNotification[] notifications =
        connection.getNotifications(props.getFactNotificationMaxRoundTripLatencyInMillis());
    if (notifications == null || notifications.length == 0) {
      // missed the notifications from the DB, something is fishy
      // here....
      pgMetrics.counter(StoreMetrics.EVENT.MISSED_ROUNDTRIP).increment();
      throw new SQLException(
          "Missed roundtrip notification from channel '" + PgConstants.CHANNEL_ROUNDTRIP + "'");
    } else {
      // return since there might have also received channel notifications
      pgMetrics
          .timer(StoreMetrics.OP.NOTIFY_ROUNDTRIP)
          .record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
      return notifications;
    }
  }

  @Override
  public void afterPropertiesSet() {
    listen();
  }

  @Override
  public void destroy() {
    running.set(false);
    if (listenerThread != null) {
      listenerThread.interrupt();
    }
  }
}
