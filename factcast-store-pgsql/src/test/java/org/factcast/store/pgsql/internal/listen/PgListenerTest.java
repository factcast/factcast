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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.common.eventbus.EventBus;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import org.factcast.store.pgsql.PgConfigurationProperties;
import org.factcast.store.pgsql.internal.PgConstants;
import org.factcast.store.pgsql.internal.PgMetrics;
import org.factcast.store.pgsql.internal.listen.PgListener.FactInsertionEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.postgresql.PGNotification;
import org.postgresql.core.Notification;
import org.postgresql.jdbc.PgConnection;

@ExtendWith(MockitoExtension.class)
public class PgListenerTest {

  @Mock PgConnectionSupplier pgConnectionSupplier;

  @Mock EventBus eventBus;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  PgConnection conn;

  @Mock PreparedStatement ps;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  PgMetrics registry;

  final PgConfigurationProperties props = new PgConfigurationProperties();

  @Captor ArgumentCaptor<FactInsertionEvent> factCaptor;

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

    verify(eventBus, times(1)).post(factCaptor.capture());
    assertEquals("scheduled-poll", factCaptor.getAllValues().get(0).name());
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
          new Notification("some notification", 1), new Notification("fact_insert", 1)
        };

    PgListener pgListener = new PgListener(pgConnectionSupplier, eventBus, props, registry);
    pgListener.informSubscriberOfChannelNotifications(receivedNotifications);

    verify(eventBus, times(1)).post(factCaptor.capture());
    assertEquals("fact_insert", factCaptor.getAllValues().get(0).name());
  }

  @Test
  public void otherNotificationsAreIgnored() {
    PGNotification[] receivedNotifications =
        new PGNotification[] {
          new Notification("some notification", 1), new Notification("some other notification", 1)
        };

    PgListener pgListener = new PgListener(pgConnectionSupplier, eventBus, props, registry);
    pgListener.informSubscriberOfChannelNotifications(receivedNotifications);

    verify(eventBus, never()).post(any(FactInsertionEvent.class));
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

  // tests the whole thread
  @Test
  void testNotify() throws Exception {
    when(pgConnectionSupplier.get()).thenReturn(conn);
    when(conn.prepareStatement(anyString())).thenReturn(ps);
    when(conn.prepareCall(anyString()).execute()).thenReturn(true);
    when(conn.getNotifications(anyInt()))
        .thenReturn(
            new PGNotification[] { //
              new Notification(PgConstants.CHANNEL_NAME, 1), //
              new Notification(PgConstants.CHANNEL_NAME, 1), //
              new Notification(PgConstants.CHANNEL_NAME, 1)
            },
            new PGNotification[] {new Notification(PgConstants.CHANNEL_NAME, 2)}, //
            new PGNotification[] {new Notification(PgConstants.CHANNEL_NAME, 3)},
            new PGNotification[] {},
            new PGNotification[] {},
            new PGNotification[] {});

    PgListener pgListener = new PgListener(pgConnectionSupplier, eventBus, props, registry);
    pgListener.listen();
    sleep(500);
    pgListener.destroy();

    verify(eventBus, atLeastOnce()).post(factCaptor.capture());
    List<FactInsertionEvent> allEvents = factCaptor.getAllValues();

    // first event is the general wakeup to the subscribers after startup
    assertEquals("scheduled-poll", allEvents.get(0).name());
    // events 2 - incl. 4 are notifies
    assertTrue(
        allEvents.subList(1, 4).stream().allMatch(event -> event.name().equals("fact_insert")));

    // in total there are only 3 notifies
    long totalNotifyCount = allEvents.stream().filter(f -> f.name().equals("fact_insert")).count();
    assertEquals(3, totalNotifyCount);
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
