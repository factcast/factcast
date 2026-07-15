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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.PgConstants;
import org.factcast.store.internal.PgMetrics;
import org.factcast.store.internal.notification.BlacklistChangeNotification;
import org.factcast.store.internal.notification.CacheClearNotification;
import org.factcast.store.internal.notification.FactInsertionNotification;
import org.factcast.store.internal.notification.NudgeNotification;
import org.factcast.store.internal.notification.SchemaStoreChangeNotification;
import org.factcast.store.internal.notification.StoreNotification;
import org.factcast.store.internal.notification.TransformationStoreChangeNotification;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.postgresql.PGNotification;
import org.postgresql.core.Notification;
import org.postgresql.jdbc.PgConnection;

@SuppressWarnings("UnstableApiUsage")
@ExtendWith(MockitoExtension.class)
class PgListenerTest {
  static {
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "trace");
  }

  private static final Predicate<Object> IS_FACT_INSERT =
      FactInsertionNotification.class::isInstance;
  private static final Predicate<Object> NOT_SCHEDULED_POLL =
      f -> !f.equals(FactInsertionNotification.internal());

  @Mock PgConnectionSupplier pgConnectionSupplier;

  @Mock EventBus eventBus;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  PgConnection conn;

  @Mock PreparedStatement ps;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  PgMetrics registry;

  final StoreConfigurationProperties props = new StoreConfigurationProperties();

  @Captor ArgumentCaptor<StoreNotification> notificationCaptor;

  @Test
  public void postgresListenersAreSetup() throws SQLException {
    when(conn.prepareStatement(anyString()).execute()).thenReturn(true);

    PgListener pgListener = new PgListener(pgConnectionSupplier, eventBus, props, registry);
    pgListener.setupPostgresListeners(conn);

    verify(conn.prepareStatement(anyString()), times(8)).execute();
  }

  @Test
  public void subscribersAreInformedViaInternalEvent() {
    PgListener pgListener = new PgListener(pgConnectionSupplier, eventBus, props, registry);
    pgListener.informSubscribersAboutFreshConnection();

    verify(eventBus, atLeastOnce()).post(notificationCaptor.capture());
    assertThat(
            notificationCaptor.getAllValues().stream()
                .anyMatch(e -> e instanceof NudgeNotification))
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
    when(conn.prepareCall(PgConstants.NOTIFY_ROUNDTRIP_SQL).execute()).thenReturn(true);
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
    when(conn.prepareCall(PgConstants.NOTIFY_ROUNDTRIP_SQL).execute()).thenReturn(true);
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
  void otherNotificationsAreIgnored() {
    PGNotification[] receivedNotifications =
        new PGNotification[] {
          new Notification("some notification", 1, "{}"),
          new Notification("some other notification", 1, "{}")
        };

    PgListener pgListener = new PgListener(pgConnectionSupplier, eventBus, props, registry);
    pgListener.processNotifications(receivedNotifications);

    verify(eventBus, never()).post(any(FactInsertionNotification.class));
  }

  @Test
  public void notificationLoopHandlesSqlException() throws SQLException {
    when(pgConnectionSupplier.getUnpooledConnection(any()))
        .thenThrow(SQLException.class, RuntimeException.class, Error.class);

    PgListener pgListener = new PgListener(pgConnectionSupplier, eventBus, props, registry);
    PgListener.NotificationReceiverLoop notificationReceiverLoop =
        pgListener.new NotificationReceiverLoop();

    Assertions.assertThrows(Error.class, notificationReceiverLoop::run);
    verify(pgConnectionSupplier, times(3)).getUnpooledConnection("notification-receiver-loop");
  }

  @Test
  void testProcessing() throws Exception {

    when(conn.prepareStatement(anyString())).thenReturn(ps);
    PgListener pgListener = new PgListener(pgConnectionSupplier, eventBus, props, registry);
    doNothing().when(eventBus).post(notificationCaptor.capture());

    pgListener.processNotifications(
        new PGNotification[] {
          new Notification(PgConstants.CHANNEL_NUDGE, 1, "{\"txId\":101}"),
          new Notification(PgConstants.CHANNEL_BLACKLIST_CHANGE, 2, "{\"txId\":1}"),
          new Notification(PgConstants.CHANNEL_BLACKLIST_CHANGE, 3, "{\"txId\":2}"),
          new Notification(PgConstants.CHANNEL_CACHE_CLEAR, 3, "{\"txId\":1}"),
        });

    pgListener.processNotifications(
        new PGNotification[] {
          new Notification(PgConstants.CHANNEL_NUDGE, 4, "{\"txId\":112}"),
          new Notification(PgConstants.CHANNEL_NUDGE, 5, "{\"txId\":113}"),
          new Notification(PgConstants.CHANNEL_NUDGE, 6, "{\"txId\":114}"),
          new Notification(
              PgConstants.CHANNEL_SCHEMASTORE_CHANGE,
              5,
              "{\"txId\":1,\"ns\":\"x\",\"type\":\"y\",\"version\":1}"),
          new Notification(
              PgConstants.CHANNEL_TRANSFORMATIONSTORE_CHANGE,
              6,
              "{\"txId\":1,\"ns\":\"x\",\"type\":\"y\"}"),
        });

    List<StoreNotification> allNotifications = notificationCaptor.getAllValues();

    assertThat(allNotifications)
        .hasAtLeastOneElementOfType(NudgeNotification.class)
        .hasAtLeastOneElementOfType(BlacklistChangeNotification.class)
        .hasAtLeastOneElementOfType(CacheClearNotification.class)
        .hasAtLeastOneElementOfType(SchemaStoreChangeNotification.class)
        .hasAtLeastOneElementOfType(TransformationStoreChangeNotification.class);

    assertThat(allNotifications).filteredOn(n -> n instanceof NudgeNotification).hasSize(2);
    assertThat(allNotifications).filteredOn(n -> n instanceof CacheClearNotification).hasSize(1);
    assertThat(allNotifications)
        .filteredOn(n -> n instanceof BlacklistChangeNotification)
        .hasSize(1);
  }

  @Test
  void testNotifySchemaStoreChange() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    PGNotification validNotification =
        new Notification(
            PgConstants.CHANNEL_SCHEMASTORE_CHANGE,
            1,
            "{\"ns\":\"namespace\",\"type\":\"theType\",\"version\":1,\"txId\":123}");
    PGNotification invalidNotification =
        new Notification(PgConstants.CHANNEL_SCHEMASTORE_CHANGE, 1, "{\"ns\":\"namespace\"}");
    PGNotification otherChannelNotification =
        new Notification(
            PgConstants.CHANNEL_FACT_INSERT,
            1,
            "{\"ns\":\"namespace\",\"type\":\"theType\",\"version\":2,\"txId\":123}");
    PGNotification anotherValidNotification =
        new Notification(
            PgConstants.CHANNEL_SCHEMASTORE_CHANGE,
            1,
            "{\"ns\":\"namespace\",\"type\":\"theType\",\"version\":3,\"txId\":123}");

    when(pgConnectionSupplier.getUnpooledConnection(any())).thenReturn(conn);
    when(conn.prepareStatement(anyString())).thenReturn(ps);
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

    verify(eventBus, times(1))
        .post(
            ArgumentMatchers.argThat(
                n ->
                    SchemaStoreChangeNotification.class.isInstance(n)
                        && "namespace".equals(((SchemaStoreChangeNotification) n).ns())
                        && "theType".equals(((SchemaStoreChangeNotification) n).type())
                        && ((SchemaStoreChangeNotification) n).version() == 1));
    verify(eventBus, times(1))
        .post(
            ArgumentMatchers.argThat(
                n ->
                    SchemaStoreChangeNotification.class.isInstance(n)
                        && "namespace".equals(((SchemaStoreChangeNotification) n).ns())
                        && "theType".equals(((SchemaStoreChangeNotification) n).type())
                        && ((SchemaStoreChangeNotification) n).version() == 3));

    // the initial one
    // v1
    // v3
    verify(eventBus, times(3)).post(any(SchemaStoreChangeNotification.class));
  }

  @Test
  void testNotifyTransformationStoreChange() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    PGNotification validNotification =
        new Notification(
            PgConstants.CHANNEL_TRANSFORMATIONSTORE_CHANGE,
            1,
            "{\"ns\":\"namespace\",\"type\":\"theType\",\"txId\":123}");
    PGNotification invalidNotification =
        new Notification(
            PgConstants.CHANNEL_TRANSFORMATIONSTORE_CHANGE,
            1,
            "{\"ns\":\"namespace\",\"invalidTypeKey\":\"theType\",\"txId\":123}");
    PGNotification otherChannelNotification =
        new Notification(
            PgConstants.CHANNEL_FACT_INSERT,
            1,
            "{\"ns\":\"namespace\",\"type\":\"theType\",\"txId\":123}");
    PGNotification anotherValidNotification =
        new Notification(
            PgConstants.CHANNEL_TRANSFORMATIONSTORE_CHANGE,
            1,
            "{\"ns\":\"namespace\",\"type\":\"theOtherType\",\"txId\":123}");

    when(pgConnectionSupplier.getUnpooledConnection(any())).thenReturn(conn);
    when(conn.prepareStatement(anyString())).thenReturn(ps);
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

    verify(eventBus, times(2)).post(any(TransformationStoreChangeNotification.class));
    verify(pgListener, times(1))
        .post(
            ArgumentMatchers.argThat(
                n ->
                    TransformationStoreChangeNotification.class.isInstance(n)
                        && "namespace".equals(((TransformationStoreChangeNotification) n).ns())
                        && "theType".equals(((TransformationStoreChangeNotification) n).type())));
    verify(pgListener, times(1))
        .post(
            ArgumentMatchers.argThat(
                n ->
                    TransformationStoreChangeNotification.class.isInstance(n)
                        && "namespace".equals(((TransformationStoreChangeNotification) n).ns())
                        && "theOtherType"
                            .equals(((TransformationStoreChangeNotification) n).type())));
  }

  @Test
  void testConnectionIsStopped() throws Exception {
    when(pgConnectionSupplier.getUnpooledConnection(any())).thenReturn(conn);
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

  @Nested
  class WhenCompactingFactInserts {

    PgListener uut;

    @BeforeEach
    void setup() {
      uut = new PgListener(pgConnectionSupplier, eventBus, props, registry);
    }

    @Test
    void empty() {
      org.assertj.core.api.Assertions.assertThat(uut.compact(Stream.empty())).isEmpty();
    }

    @Test
    void unrelated() {
      List<FactInsertionNotification> notifications =
          List.of(
              new FactInsertionNotification("ns", "type", 1L),
              new FactInsertionNotification("ns", "type2", 2L),
              new FactInsertionNotification("ns", "type3", 3L));
      org.assertj.core.api.Assertions.assertThat(uut.compact(notifications.stream()).toList())
          .isEqualTo(notifications);
    }

    @Test
    void one() {
      FactInsertionNotification i1 = new FactInsertionNotification("ns", "type", 1L);
      FactInsertionNotification i2 = new FactInsertionNotification("ns", "type2", 2L);
      FactInsertionNotification i3 = new FactInsertionNotification("ns", "type", 3L);
      List<FactInsertionNotification> notifications = List.of(i1, i2, i3);
      org.assertj.core.api.Assertions.assertThat(uut.compact(notifications.stream()).toList())
          .hasSize(2)
          .contains(i1, i2);
    }

    @Test
    void many() {
      FactInsertionNotification i1 = new FactInsertionNotification("ns", "type", 1L);
      FactInsertionNotification i2 = new FactInsertionNotification("ns", "type2", 2L);
      FactInsertionNotification i3 = new FactInsertionNotification("ns", "type", 3L);
      FactInsertionNotification i4 = new FactInsertionNotification("ns", "type", 4L);
      FactInsertionNotification i5 = new FactInsertionNotification("ns", "type2", 5L);
      List<FactInsertionNotification> notifications = List.of(i1, i2, i3, i4, i5);
      org.assertj.core.api.Assertions.assertThat(uut.compact(notifications.stream()).toList())
          .hasSize(2)
          .containsExactly(i1, i2);
    }
  }
}
