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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.common.eventbus.EventBus;
import io.micrometer.core.instrument.Timer;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.*;
import org.assertj.core.api.Assertions;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.*;
import org.factcast.store.internal.notification.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.*;

@SuppressWarnings({"java:S6068", "unchecked"})
@ExtendWith(MockitoExtension.class)
class NudgeNotificationHandlerTest {

  @Mock private EventBus bus;

  @Mock private StoreConfigurationProperties props;

  @Mock private JdbcTemplate jdbc;

  @Mock private PgMetrics metrics;

  @Mock ResultSet rs;
  private NudgeNotificationHandler handler;
  private @Mock Timer timer;
  private @Mock Timer.Sample sample;

  @BeforeEach
  void setUp() {
    lenient().when(props.getMaxNotificationPollLatencyInMillis()).thenReturn(25L);
    lenient().when(metrics.timer(any())).thenReturn(timer);
    lenient().when(metrics.startSample()).thenReturn(sample);
    lenient().doNothing().when(jdbc).execute(anyString());
    handler = spy(new NudgeNotificationHandler(bus, jdbc, props, metrics));
  }

  @AfterEach
  void tearDown() throws Exception {
    handler.destroy();
  }

  @Test
  void testDestroyUnregistersBus() throws Exception {
    handler.destroy();
    verify(bus).unregister(handler);
  }

  @Test
  void testNudgeInitialCallFetchesMaxSerAndPostsInternalNotification() {
    // Given

    // When
    handler.nudge(new NudgeNotification(12));

    // Then
    verify(jdbc).queryForObject(contains("SELECT max(ser) FROM notification"), eq(Long.class));
    verify(bus).post(any(FactInsertionNotification.class));
  }

  @Test
  void schedulesSubsequentPolls() throws Exception {
    // Given
    when(props.getMaxNotificationPollLatencyInMillis()).thenReturn(25L);
    // Access notificationSer to set it > 0
    handler.notificationSer.set(100L);

    // Stub BASE_EXISTS_SQL to return true
    lenient()
        .when(
            jdbc.queryForObject(
                eq(NudgeNotificationHandler.BASE_EXISTS_SQL), eq(Boolean.class), same(100)))
        .thenReturn(true);

    // When
    handler.nudge(new NudgeNotification(13));

    // Then
    // Should call fetchPairsAndDispatch, which queries for pairs.
    verify(handler).fetchPairsAndDispatch();
    Thread.sleep(110);
    verify(handler, times(5)).fetchPairsAndDispatch();
  }

  @Test
  void foldsDuplicatePolls() throws Exception {
    // Given
    when(props.getMaxNotificationPollLatencyInMillis()).thenReturn(25L);
    // Access notificationSer to set it > 0
    handler.notificationSer.set(101L);

    // Stub BASE_EXISTS_SQL to return true
    lenient()
        .when(jdbc.queryForObject(NudgeNotificationHandler.BASE_EXISTS_SQL, Boolean.class, 101L))
        .thenReturn(true);
    // When
    // schedules 25 50 75 100
    handler.nudge(new NudgeNotification(13));
    Thread.sleep(30);
    // schedules 55 80 105 130
    handler.nudge(new NudgeNotification(13));

    Thread.sleep(150);
    // one for initial, one for 25,30 (initial for 2nd nudge), 55, 80, 105, 130
    verify(handler, times(7)).fetchPairsAndDispatch();
  }

  @Test
  void notifesForEachPair() throws Exception {
    // Given
    when(props.getMaxNotificationPollLatencyInMillis()).thenReturn(25L);
    // Access notificationSer to set it > 0
    handler.notificationSer.set(102L);

    // Stub BASE_EXISTS_SQL to return true
    lenient()
        .when(
            jdbc.queryForObject(
                eq(NudgeNotificationHandler.BASE_EXISTS_SQL), eq(Boolean.class), same(102L)))
        .thenReturn(true);

    when(jdbc.query(
            startsWith("SELECT max(ser) as max,ns,type FROM notification WHERE"),
            any(DataClassRowMapper.class),
            any(Object[].class)))
        .thenReturn(
            List.of(
                new NudgeNotificationHandler.FetchNotificationTuple(7L, "ns", "t1"),
                new NudgeNotificationHandler.FetchNotificationTuple(8L, "ns", "t2"),
                new NudgeNotificationHandler.FetchNotificationTuple(9L, "ns", "t3")),
            Collections.emptyList());

    final CountDownLatch cdl = new CountDownLatch(3);
    doAnswer(
            invocation -> {
              cdl.countDown();
              return null;
            })
        .when(bus)
        .post(any());

    handler.nudge(new NudgeNotification(13));
    Assertions.assertThat(cdl.await(5, TimeUnit.SECONDS)).isTrue();

    verify(bus).post(eq(FactInsertionNotification.internal("ns", "t1")));
    verify(bus).post(eq(FactInsertionNotification.internal("ns", "t2")));
    verify(bus).post(eq(FactInsertionNotification.internal("ns", "t3")));
    verify(bus).register(any());
    verifyNoMoreInteractions(bus);
  }

  @Test
  void notifiesQuicklyAfterScheduledPoll() throws Exception {
    // Given
    when(props.getMaxNotificationPollLatencyInMillis()).thenReturn(50L);
    // Access notificationSer to set it > 0
    handler.notificationSer.set(100L);

    // Stub BASE_EXISTS_SQL to return true
    lenient()
        .when(
            jdbc.queryForObject(
                eq(NudgeNotificationHandler.BASE_EXISTS_SQL), eq(Boolean.class), anyLong()))
        .thenReturn(true);

    when(jdbc.query(
            startsWith("SELECT max(ser) as max,ns,type FROM notification WHERE"),
            any(DataClassRowMapper.class),
            any(Object[].class)))
        .thenReturn(
            List.of(
                new NudgeNotificationHandler.FetchNotificationTuple(7L, "ns", "t1"),
                new NudgeNotificationHandler.FetchNotificationTuple(8L, "ns", "t2"),
                new NudgeNotificationHandler.FetchNotificationTuple(9L, "ns", "t3")),
            List.of(new NudgeNotificationHandler.FetchNotificationTuple(10L, "ns", "t1")));

    final CountDownLatch cdl = new CountDownLatch(3);
    final CountDownLatch cdl4 = new CountDownLatch(4);
    doAnswer(
            invocation -> {
              cdl.countDown();
              cdl4.countDown();
              return null;
            })
        .when(bus)
        .post(any());

    handler.nudge(new NudgeNotification(13));
    Assertions.assertThat(cdl.await(5, TimeUnit.SECONDS)).isTrue();

    verify(bus).register(any());
    verify(bus).post(eq(FactInsertionNotification.internal("ns", "t1")));
    verify(bus).post(eq(FactInsertionNotification.internal("ns", "t2")));
    verify(bus).post(eq(FactInsertionNotification.internal("ns", "t3")));
    verifyNoMoreInteractions(bus);

    // now after 50ms, a scheduled poll should emit another
    Assertions.assertThat(cdl4.await(75, TimeUnit.MILLISECONDS)).isTrue();
    verify(bus, times(2)).post(eq(FactInsertionNotification.internal("ns", "t1")));
    verifyNoMoreInteractions(bus);
  }

  @Test
  void ignoresOlderVersions() throws Exception {
    // Given
    when(props.getMaxNotificationPollLatencyInMillis()).thenReturn(25L);
    // Access notificationSer to set it > 0
    handler.notificationSer.set(110L);

    // Stub BASE_EXISTS_SQL to return true
    lenient()
        .when(
            jdbc.queryForObject(
                eq(NudgeNotificationHandler.BASE_EXISTS_SQL), eq(Boolean.class), same(110)))
        .thenReturn(true);
    // When
    // schedules 25 50 75 100
    handler.nudge(new NudgeNotification(13));
    Thread.sleep(5);
    handler.timerVersion.incrementAndGet();
    Thread.sleep(100);
    // one for initial, one for 25,30 (initial for 2nd nudge), 55, 80, 105, 130
    verify(handler, times(1)).fetchPairsAndDispatch();
  }

  @Test
  void fetchPairsAndDispatchOnlyExecutesOnceWhenCalledConcurrently() throws Exception {
    // Given
    CountDownLatch latch = new CountDownLatch(1);
    // Mock queryForList to hold execution
    when(jdbc.query(anyString(), any(DataClassRowMapper.class), any(Object[].class)))
        .thenAnswer(
            inv -> {
              latch.await(2, TimeUnit.SECONDS);
              return java.util.Collections.emptyList();
            });

    verify(jdbc, never()).query(anyString(), any(DataClassRowMapper.class), any(Object[].class));

    // When
    Thread t1 = new Thread(() -> handler.fetchPairsAndDispatch());
    t1.start();

    // Wait for t1 to start and acquire lock (give it some time)
    Thread.sleep(100);

    // Call from main thread while t1 holds lock
    handler.fetchPairsAndDispatch();

    // Release t1
    latch.countDown();
    t1.join();

    // Then
    verify(jdbc, times(1)).query(anyString(), any(DataClassRowMapper.class), any(Object[].class));
  }

  @Test
  void emitsMetricsWhenFetching() {
    // When
    handler.fetchPairsAndDispatch();

    // Then
    verify(metrics).startSample();
    verify(sample).stop(timer);
  }
}
