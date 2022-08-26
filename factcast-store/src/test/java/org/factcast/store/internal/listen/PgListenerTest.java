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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.eventbus.EventBus;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.PgConstants;
import org.factcast.store.internal.PgMetrics;
import org.factcast.store.internal.listen.PgListener.FactInsertionSignal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.postgresql.PGNotification;
import org.postgresql.core.Notification;
import org.postgresql.jdbc.PgConnection;

@SuppressWarnings("UnstableApiUsage")
@ExtendWith(MockitoExtension.class)
public class PgListenerTest {

  private static final Predicate<Object> IS_FACT_INSERT = f -> f instanceof FactInsertionSignal;
  private static final Predicate<Object> NOT_SCHEDULED_POLL =
      f -> !((FactInsertionSignal) f).name().equals("scheduled-poll");

  @Mock PgConnectionSupplier pgConnectionSupplier;

  @Mock EventBus eventBus;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  PgConnection conn;

  @Mock PreparedStatement ps;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  PgMetrics registry;

  final StoreConfigurationProperties props = new StoreConfigurationProperties();

  @Captor ArgumentCaptor<Object> factCaptor;

  @Test
  public void postgresListenersAreSetup() throws SQLException {
    when(conn.prepareStatement(anyString()).execute()).thenReturn(true);

    PgListener pgListener = new PgListener(pgConnectionSupplier, eventBus, props, registry);
    pgListener.setupPostgresListeners(conn);

    verify(conn.prepareStatement(anyString()), times(2)).execute();
  }

  @Test
  public void subscribersAreInformedViaInternalEvent() {
    PgListener pgListener = new PgListener(pgConnectionSupplier, eventBus, props, registry);
    pgListener.informSubscribersAboutFreshConnection();

    verify(eventBus, atLeastOnce()).post(factCaptor.capture());
    assertThat(
            factCaptor.getAllValues().stream()
                .anyMatch(
                    e ->
                        e instanceof FactInsertionSignal
                            && ((FactInsertionSignal) e).name().equals("scheduled-poll")))
        .isTrue();
  }

  @Test
  public void receivedNotificationsArePassedThrough() throws SQLException {
    // there are some notifications
    when(conn.getNotifications(anyInt()))
        .thenReturn(new PGNotification[] {new Notification("some notification", 1)});
    PgListener pgListener = spy(new PgListener(pgConnectionSupplier, eventBus, props, registry));

    PGNotification[] pgNotifications = pgListener.receiveNotifications(conn);

    // no health check was required
    verify(pgListener, never()).checkDatabaseConnectionHealthy(any());
    assertEquals(1, pgNotifications.length);
    assertEquals("some notification", pgNotifications[0].getName());
  }

  @Test
  public void whenReceiveTimeoutExpiresHealthCheckIsExecuted() throws SQLException {
    // arrange
    PgListener pgListener = spy(new PgListener(pgConnectionSupplier, eventBus, props, registry));
    // no notifications received after timeout expired
    when(conn.getNotifications(anyInt())).thenReturn(null);
    // health check returned something
    doReturn(new PGNotification[] {new Notification("some roundtrip notification", 1)})
        .when(pgListener)
        .checkDatabaseConnectionHealthy(any());

    // act
    PGNotification[] pgNotifications = pgListener.receiveNotifications(conn);

    // assert
    // health check was called
    verify(pgListener, times(1)).checkDatabaseConnectionHealthy(any());
    assertEquals(1, pgNotifications.length);
    assertEquals("some roundtrip notification", pgNotifications[0].getName());
  }

  @Test
  public void onSuccessfulHealthCheckNotificationsArePassedThrough() throws SQLException {
    when(conn.prepareCall(PgConstants.NOTIFY_ROUNDTRIP).execute()).thenReturn(true);
    // we found some notifications
    when(conn.getNotifications(anyInt()))
        .thenReturn(new PGNotification[] {new Notification("some notification", 1)});

    PgListener pgListener = new PgListener(pgConnectionSupplier, eventBus, props, registry);
    PGNotification[] pgNotifications = pgListener.checkDatabaseConnectionHealthy(conn);

    assertEquals(1, pgNotifications.length);
    assertEquals("some notification", pgNotifications[0].getName());
  }

  @Test
  public void throwsSqlExceptionWhenNoRoundtripNotificationWasReceived() throws SQLException {
    when(conn.prepareCall(PgConstants.NOTIFY_ROUNDTRIP).execute()).thenReturn(true);
    // no answer from the database
    when(conn.getNotifications(anyInt())).thenReturn(null);

    Assertions.assertThrows(
        SQLException.class,
        () -> {
          PgListener pgListener = new PgListener(pgConnectionSupplier, eventBus, props, registry);
          pgListener.checkDatabaseConnectionHealthy(conn);
        });
  }

  @Test
  public void subscribersAreOnlyInformedAboutNewFactsInDatabase() {
    PGNotification[] receivedNotifications =
        new PGNotification[] {
          new Notification("some notification", 1, "{}"),
          new Notification(PgConstants.CHANNEL_FACT_INSERT, 1, "{}")
        };

    PgListener pgListener = new PgListener(pgConnectionSupplier, eventBus, props, registry);
    pgListener.processNotifications(receivedNotifications);

    verify(eventBus, times(1)).post(factCaptor.capture());
    assertThat(factCaptor.getAllValues().stream().anyMatch(e -> e instanceof FactInsertionSignal))
        .isTrue();
  }

  @Test
  public void otherNotificationsAreIgnored() {
    PGNotification[] receivedNotifications =
        new PGNotification[] {
          new Notification("some notification", 1, "{}"),
          new Notification("some other notification", 1, "{}")
        };

    PgListener pgListener = new PgListener(pgConnectionSupplier, eventBus, props, registry);
    pgListener.processNotifications(receivedNotifications);

    verify(eventBus, never()).post(any(FactInsertionSignal.class));
  }

  @Test
  public void notificationLoopHandlesSqlException() throws SQLException {
    when(pgConnectionSupplier.get())
        .thenThrow(SQLException.class, RuntimeException.class, Error.class);

    PgListener pgListener = new PgListener(pgConnectionSupplier, eventBus, props, registry);
    PgListener.NotificationReceiverLoop notificationReceiverLoop =
        pgListener.new NotificationReceiverLoop();

    Assertions.assertThrows(Error.class, notificationReceiverLoop::run);
    verify(pgConnectionSupplier, times(3)).get();
  }

  @Test
  void testNotify() throws Exception {

    CountDownLatch latch = new CountDownLatch(1);

    when(pgConnectionSupplier.get()).thenReturn(conn);
    when(conn.prepareStatement(anyString())).thenReturn(ps);
    when(conn.prepareCall(anyString()).execute()).thenReturn(true);
    when(conn.getNotifications(anyInt()))
        .thenReturn(
            new PGNotification[] { //
              new Notification(PgConstants.CHANNEL_FACT_INSERT, 1, "{}"), //
              // let us test a broken one
              new Notification(PgConstants.CHANNEL_FACT_INSERT, 1, "{"), //
              new Notification(
                  PgConstants.CHANNEL_FACT_INSERT,
                  1,
                  "{\"ns\":\"namespace\",\"type\":\"theType\"}"),

              // should trigger other notification:
              new Notification(
                  PgConstants.CHANNEL_FACT_INSERT,
                  1,
                  "{\"ns\":\"namespace\",\"type\":\"theOtherType\"}")
            },
            new PGNotification[] {
              new Notification(PgConstants.CHANNEL_FACT_INSERT, 2, "{}"),
              new Notification(
                  PgConstants.CHANNEL_FACT_INSERT,
                  1,
                  "{\"ns\":\"namespace\",\"type\":\"theOtherType\"}"),
              // recurring notifications in second array:
              new Notification(
                  PgConstants.CHANNEL_FACT_INSERT,
                  1,
                  "{\"ns\":\"namespace\",\"type\":\"theType\"}"),
            }, //
            new PGNotification[] {new Notification(PgConstants.CHANNEL_FACT_INSERT, 3, "{}")},
            new PGNotification[] {new Notification(PgConstants.CHANNEL_ROUNDTRIP, 3, "{}")},
            new PGNotification[] {},
            new PGNotification[] {},
            new PGNotification[] {})
        .thenAnswer(
            i -> {
              latch.countDown();
              return null;
            });

    PgListener pgListener = new PgListener(pgConnectionSupplier, eventBus, props, registry);
    pgListener.listen();
    latch.await(1, TimeUnit.MINUTES);
    pgListener.destroy();

    verify(eventBus, atLeastOnce()).post(factCaptor.capture());
    var allEvents = factCaptor.getAllValues();

    // first event is the general wakeup to the subscribers after startup
    assertThat(
            factCaptor.getAllValues().stream()
                .anyMatch(
                    e ->
                        e instanceof FactInsertionSignal
                            && ((FactInsertionSignal) e).name().equals("scheduled-poll")))
        .isTrue();
    // events 2 - incl. 4 are notifies
    assertTrue(
        allEvents.stream()
            .filter(e -> !(e instanceof PgListener.BlacklistChangeSignal))
            .allMatch(IS_FACT_INSERT));

    // in total there are only 3 notifies
    // TODO delete or assert?
    long totalNotifyCount = allEvents.stream().filter(IS_FACT_INSERT).count();

    // grouped by tx id, ns and type
    assertThat(allEvents.stream().filter(IS_FACT_INSERT).filter(NOT_SCHEDULED_POLL))
        .containsExactlyInAnyOrder(
            new FactInsertionSignal(PgConstants.CHANNEL_FACT_INSERT, null, null),
            new FactInsertionSignal(PgConstants.CHANNEL_FACT_INSERT, null, null),
            new FactInsertionSignal(PgConstants.CHANNEL_FACT_INSERT, null, null),
            new FactInsertionSignal(PgConstants.CHANNEL_FACT_INSERT, "namespace", "theType"),
            new FactInsertionSignal(PgConstants.CHANNEL_FACT_INSERT, "namespace", "theOtherType"),
            new FactInsertionSignal(PgConstants.CHANNEL_FACT_INSERT, "namespace", "theType"),
            new FactInsertionSignal(PgConstants.CHANNEL_FACT_INSERT, "namespace", "theOtherType"));
  }

  @Test
  void testNotifySchemaStoreChange() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    PGNotification validNotification =
        new Notification(
            PgConstants.CHANNEL_SCHEMASTORE_CHANGE,
            1,
            "{\"ns\":\"namespace\",\"type\":\"theType\",\"version\":1}");
    PGNotification invalidNotification =
        new Notification(
            PgConstants.CHANNEL_SCHEMASTORE_CHANGE,
            1,
            "{\"ns\":\"namespace\",\"type\":\"theType\"}");
    PGNotification otherChannelNotification =
        new Notification(
            PgConstants.CHANNEL_FACT_INSERT, 1, "{\"ns\":\"namespace\",\"type\":\"theType\"}");
    PGNotification anotherValidNotification =
        new Notification(
            PgConstants.CHANNEL_SCHEMASTORE_CHANGE,
            1,
            "{\"ns\":\"namespace\",\"type\":\"theType\",\"version\":2}");

    when(pgConnectionSupplier.get()).thenReturn(conn);
    when(conn.prepareStatement(anyString())).thenReturn(ps);
    when(conn.prepareCall(anyString()).execute()).thenReturn(true);
    when(conn.getNotifications(anyInt()))
        .thenReturn(
            new PGNotification[] {
              validNotification,
              invalidNotification,
              otherChannelNotification,
              anotherValidNotification
            })
        .thenAnswer(
            i -> {
              latch.countDown();
              return null;
            });

    PgListener pgListener = spy(new PgListener(pgConnectionSupplier, eventBus, props, registry));
    pgListener.listen();
    latch.await(1, TimeUnit.MINUTES);
    pgListener.destroy();

    verify(pgListener, times(2)).postSchemaStoreChangeSignal(any());
    verify(pgListener, times(1))
        .postSchemaStoreChangeSignal(
            new PgListener.SchemaStoreChangeSignal("namespace", "theType", 1));
    verify(pgListener, times(1))
        .postSchemaStoreChangeSignal(
            new PgListener.SchemaStoreChangeSignal("namespace", "theType", 2));

    verify(eventBus, times(2)).post(any(PgListener.SchemaStoreChangeSignal.class));
  }

  @Test
  void testConnectionIsStopped() throws Exception {
    when(pgConnectionSupplier.get()).thenReturn(conn);
    when(conn.prepareStatement(anyString())).thenReturn(mock(PreparedStatement.class));

    PgListener pgListener = new PgListener(pgConnectionSupplier, eventBus, props, registry);
    pgListener.afterPropertiesSet();
    pgListener.destroy();
    sleep(150); // TODO flaky
    verify(conn).close();
  }

  @Test
  void testStopWithoutStarting() {
    PgListener pgListener = new PgListener(pgConnectionSupplier, eventBus, props, registry);
    pgListener.destroy();
    verifyNoMoreInteractions(conn);
  }

  private void sleep(int i) {
    try {
      Thread.sleep(i);
    } catch (InterruptedException ignored) {
    }
  }
}
