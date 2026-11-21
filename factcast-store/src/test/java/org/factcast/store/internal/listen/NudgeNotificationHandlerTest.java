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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.common.eventbus.EventBus;
import io.micrometer.core.instrument.Timer;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.*;
import org.factcast.store.internal.notification.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
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
    lenient().when(props.getFactNotificationPollLatencyInMillis()).thenReturn(25L);
    // Individual safety-poll tests enable it after isolating the background timer.
    lenient().when(props.getFactNotificationSafetyPollLatencyInMillis()).thenReturn(Long.MAX_VALUE);
    lenient().when(metrics.timer(any())).thenReturn(timer);
    lenient().when(metrics.startSample()).thenReturn(sample);
    lenient().doNothing().when(jdbc).execute(anyString());
    lenient()
        .when(jdbc.queryForObject(NudgeNotificationHandler.MAX_NOTIFICATION_SERIAL, Long.class))
        .thenReturn(200L);
    handler = spy(new NudgeNotificationHandler(bus, jdbc, props, metrics, false));
  }

  @AfterEach
  void tearDown() throws Exception {
    handler.destroy();
  }

  @Test
  void readOnlyModeDoesNotScheduleCleanup() throws Exception {
    handler.destroy();
    when(props.isReadOnlyModeEnabled()).thenReturn(true);

    handler = new NudgeNotificationHandler(bus, jdbc, props, metrics);
    awaitInitialTimerTasks();

    verifyNoInteractions(jdbc);
  }

  @Test
  void writableModeRunsCleanup() throws Exception {
    handler.destroy();

    handler = new NudgeNotificationHandler(bus, jdbc, props, metrics);
    awaitInitialTimerTasks();

    verify(jdbc).execute("CALL notificationCleanup()");
  }

  private void awaitInitialTimerTasks() throws InterruptedException {
    var completed = new CountDownLatch(1);
    // The timer runs tasks serially, so this runs after any immediate cleanup scheduled at startup.
    handler.timer.schedule(
        new TimerTask() {
          @Override
          public void run() {
            completed.countDown();
          }
        },
        0);
    assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
  }

  private ControlledTimer replaceTimerWithControlledTimer() throws Exception {
    handler.destroy();
    clearInvocations(bus, jdbc, metrics, timer, sample);

    var controlledTimer = new ControlledTimer();
    handler =
        spy(new NudgeNotificationHandler(bus, jdbc, props, metrics, false, controlledTimer.timer));
    return controlledTimer;
  }

  private static class ControlledTimer {
    final java.util.Timer timer = mock(java.util.Timer.class);
    final List<TimerTask> tasks = new ArrayList<>();
    final List<Long> delays = new ArrayList<>();

    ControlledTimer() {
      doAnswer(
              invocation -> {
                tasks.add(invocation.getArgument(0));
                delays.add(invocation.getArgument(1));
                return null;
              })
          .when(timer)
          .schedule(any(TimerTask.class), anyLong());
    }
  }

  @Test
  void testDestroyUnregistersBus() throws Exception {
    handler.destroy();
    verify(bus).unregister(handler);
  }

  @Test
  void testNudgeInitialCallReadsMaximumAndPostsInternalNotification() {
    // Given

    // When
    handler.nudge(new NudgeNotification(12));

    // Then
    verify(jdbc).queryForObject(NudgeNotificationHandler.MAX_NOTIFICATION_SERIAL, Long.class);
    verify(bus).post(any(FactInsertionNotification.class));
  }

  @Test
  void schedulesSubsequentPolls() throws Exception {
    // Given
    var controlledTimer = replaceTimerWithControlledTimer();
    when(props.getFactNotificationPollLatencyInMillis()).thenReturn(25L);
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
    assertThat(controlledTimer.delays).containsExactly(25L, 50L, 75L, 100L);
    controlledTimer.tasks.forEach(TimerTask::run);
    verify(handler, times(5)).fetchPairsAndDispatch();
  }

  @Test
  void foldsDuplicatePolls() throws Exception {
    // Given
    var controlledTimer = replaceTimerWithControlledTimer();
    when(props.getFactNotificationPollLatencyInMillis()).thenReturn(25L);
    // Access notificationSer to set it > 0
    handler.notificationSer.set(101L);

    // Stub BASE_EXISTS_SQL to return true
    lenient()
        .when(jdbc.queryForObject(NudgeNotificationHandler.BASE_EXISTS_SQL, Boolean.class, 101L))
        .thenReturn(true);
    // When
    handler.nudge(new NudgeNotification(13));
    var firstNudgeTasks = List.copyOf(controlledTimer.tasks);
    firstNudgeTasks.get(0).run();
    handler.nudge(new NudgeNotification(13));

    assertThat(controlledTimer.delays).containsExactly(25L, 50L, 75L, 100L, 25L, 50L, 75L, 100L);
    firstNudgeTasks.subList(1, firstNudgeTasks.size()).forEach(TimerTask::run);
    controlledTimer.tasks.subList(4, controlledTimer.tasks.size()).forEach(TimerTask::run);

    // The first nudge's 25ms poll, both immediate polls, and the four current-version polls run.
    verify(handler, times(7)).fetchPairsAndDispatch();
  }

  @Test
  void notifesForEachPair() throws Exception {
    // Given
    replaceTimerWithControlledTimer();
    when(props.getFactNotificationPollLatencyInMillis()).thenReturn(25L);
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
    assertThat(cdl.await(5, TimeUnit.SECONDS)).isTrue();

    verify(bus).post(eq(FactInsertionNotification.internal("ns", "t1")));
    verify(bus).post(eq(FactInsertionNotification.internal("ns", "t2")));
    verify(bus).post(eq(FactInsertionNotification.internal("ns", "t3")));
    verify(bus).register(any());
    verifyNoMoreInteractions(bus);
    assertThat(handler.notificationSer).hasValue(200);

    ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc)
        .query(
            contains("notification.ser <= ?"), any(DataClassRowMapper.class), parameters.capture());
    assertThat(parameters.getValue()).containsExactly(102L, 200L);
  }

  @Test
  void notifiesQuicklyAfterScheduledPoll() throws Exception {
    // Given
    var controlledTimer = replaceTimerWithControlledTimer();
    when(props.getFactNotificationPollLatencyInMillis()).thenReturn(50L);
    // Access notificationSer to set it > 0
    handler.notificationSer.set(100L);
    when(jdbc.queryForObject(NudgeNotificationHandler.MAX_NOTIFICATION_SERIAL, Long.class))
        .thenReturn(200L, 201L);

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
    assertThat(cdl.await(5, TimeUnit.SECONDS)).isTrue();

    verify(bus).register(any());
    verify(bus).post(eq(FactInsertionNotification.internal("ns", "t1")));
    verify(bus).post(eq(FactInsertionNotification.internal("ns", "t2")));
    verify(bus).post(eq(FactInsertionNotification.internal("ns", "t3")));
    verifyNoMoreInteractions(bus);

    // Running the first scheduled poll should emit another notification.
    controlledTimer.tasks.get(0).run();
    assertThat(cdl4.getCount()).isZero();
    verify(bus, times(2)).post(eq(FactInsertionNotification.internal("ns", "t1")));
    verifyNoMoreInteractions(bus);
  }

  @Test
  void ignoresOlderVersions() throws Exception {
    // Given
    var controlledTimer = replaceTimerWithControlledTimer();
    when(props.getFactNotificationPollLatencyInMillis()).thenReturn(25L);
    // Access notificationSer to set it > 0
    handler.notificationSer.set(110L);

    // Stub BASE_EXISTS_SQL to return true
    lenient()
        .when(
            jdbc.queryForObject(
                eq(NudgeNotificationHandler.BASE_EXISTS_SQL), eq(Boolean.class), same(110)))
        .thenReturn(true);
    // When
    handler.nudge(new NudgeNotification(13));
    assertThat(controlledTimer.delays).containsExactly(25L, 50L, 75L, 100L);
    handler.timerVersion.incrementAndGet();
    controlledTimer.tasks.forEach(TimerTask::run);
    verify(handler, times(1)).fetchPairsAndDispatch();
  }

  @Test
  void scheduledPollHandlesDatabaseFailure() {
    // Given
    long version = handler.timerVersion.get();
    doThrow(new DataAccessResourceFailureException("database unavailable"))
        .when(handler)
        .fetchPairsAndDispatch();

    // When / Then
    assertThatCode(() -> handler.new ScheduledPoll(version).run()).doesNotThrowAnyException();
  }

  @Test
  void scheduledCleanupHandlesDatabaseFailure() {
    // Given
    doThrow(new DataAccessResourceFailureException("database unavailable"))
        .when(jdbc)
        .execute(anyString());

    // When / Then
    assertThatCode(() -> handler.new ScheduledCleanup().run()).doesNotThrowAnyException();
  }

  @Test
  void safetyPollDispatchesWithoutANudgeAndSkipsImmediateRepeat() throws Exception {
    stopBackgroundTimer();
    when(props.getFactNotificationSafetyPollLatencyInMillis()).thenReturn(60_000L);
    var task = handler.new CatchAllTask();

    task.run();
    task.run();

    verify(jdbc).queryForObject(NudgeNotificationHandler.MAX_NOTIFICATION_SERIAL, Long.class);
    verify(bus).post(FactInsertionNotification.internal());
    assertThat(handler.notificationSer).hasValue(200);
  }

  @Test
  void safetyPollSkipsAfterAnOrdinaryRefresh() throws Exception {
    stopBackgroundTimer();
    when(props.getFactNotificationSafetyPollLatencyInMillis()).thenReturn(60_000L);
    handler.fetchPairsAndDispatch();
    clearInvocations(jdbc, bus);

    handler.new CatchAllTask().run();

    verifyNoInteractions(jdbc, bus);
  }

  @Test
  void safetyPollResumesAfterConfiguredIdleInterval() throws Exception {
    stopBackgroundTimer();
    when(props.getFactNotificationSafetyPollLatencyInMillis()).thenReturn(500L);
    handler.fetchPairsAndDispatch();
    when(jdbc.queryForObject(NudgeNotificationHandler.MAX_NOTIFICATION_SERIAL, Long.class))
        .thenReturn(201L);
    when(jdbc.queryForObject(NudgeNotificationHandler.BASE_EXISTS_SQL, Boolean.class, 200L))
        .thenReturn(true);
    when(jdbc.query(anyString(), any(DataClassRowMapper.class), eq(200L), eq(201L)))
        .thenReturn(List.of(new NudgeNotificationHandler.FetchNotificationTuple(201L, "ns", "t")));
    var task = handler.new CatchAllTask();

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              task.run();
              verify(bus).post(FactInsertionNotification.internal("ns", "t"));
            });

    assertThat(handler.notificationSer).hasValue(201);
  }

  @Test
  void safetyPollHandlesDatabaseFailureAndRetriesLater() throws Exception {
    stopBackgroundTimer();
    when(props.getFactNotificationSafetyPollLatencyInMillis()).thenReturn(500L);
    when(jdbc.queryForObject(NudgeNotificationHandler.MAX_NOTIFICATION_SERIAL, Long.class))
        .thenThrow(new DataAccessResourceFailureException("database unavailable"))
        .thenReturn(200L);
    var task = handler.new CatchAllTask();

    assertThatCode(task::run).doesNotThrowAnyException();
    assertThat(handler.notificationSer).hasValue(0);
    verify(bus, never()).post(any());

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              task.run();
              verify(bus).post(FactInsertionNotification.internal());
            });
    assertThat(handler.notificationSer).hasValue(200);
  }

  @Test
  void safetyPollRunsPeriodicallyInReadOnlyModeAfterDatabaseFailure() throws Exception {
    handler.destroy();
    when(props.isReadOnlyModeEnabled()).thenReturn(true);
    when(props.getFactNotificationSafetyPollLatencyInMillis()).thenReturn(500L);
    when(jdbc.queryForObject(NudgeNotificationHandler.MAX_NOTIFICATION_SERIAL, Long.class))
        .thenThrow(new DataAccessResourceFailureException("database unavailable"))
        .thenReturn(200L, 201L);
    when(jdbc.queryForObject(NudgeNotificationHandler.BASE_EXISTS_SQL, Boolean.class, 200L))
        .thenReturn(false);
    CountDownLatch dispatched = new CountDownLatch(2);
    doAnswer(
            invocation -> {
              dispatched.countDown();
              return null;
            })
        .when(bus)
        .post(any(FactInsertionNotification.class));

    handler = new NudgeNotificationHandler(bus, jdbc, props, metrics);

    assertThat(dispatched.await(5, TimeUnit.SECONDS)).isTrue();
    verify(jdbc, never()).execute(anyString());
  }

  private void stopBackgroundTimer() throws InterruptedException {
    awaitInitialTimerTasks();
    handler.timer.cancel();
  }

  @Test
  void fetchPairsAndDispatchCoalescesRequestsArrivingDuringRefresh() throws Exception {
    // Given
    handler.notificationSer.set(100);
    when(jdbc.queryForObject(NudgeNotificationHandler.MAX_NOTIFICATION_SERIAL, Long.class))
        .thenReturn(200L, 201L);
    when(jdbc.queryForObject(
            eq(NudgeNotificationHandler.BASE_EXISTS_SQL), eq(Boolean.class), anyLong()))
        .thenReturn(true);
    CountDownLatch letGo = new CountDownLatch(1);
    CountDownLatch hasLock = new CountDownLatch(1);
    // Mock queryForList to hold execution
    when(jdbc.query(anyString(), any(DataClassRowMapper.class), any(Object[].class)))
        .thenAnswer(
            inv -> {
              hasLock.countDown();
              letGo.await(10, TimeUnit.SECONDS);
              return java.util.Collections.emptyList();
            });

    verify(jdbc, never()).query(anyString(), any(DataClassRowMapper.class), any(Object[].class));

    // When
    Thread t1 = new Thread(() -> handler.fetchPairsAndDispatch());
    t1.start();

    assertThat(hasLock.await(5, TimeUnit.SECONDS)).isTrue();

    // Several requests arriving during the same pass only need one additional refresh.
    for (int i = 0; i < 3; i++) {
      handler.fetchPairsAndDispatch();
    }

    // Release t1
    letGo.countDown();
    t1.join();

    // Then
    verify(jdbc, times(2)).query(anyString(), any(DataClassRowMapper.class), any(Object[].class));
  }

  @Test
  void refreshRequestedDuringDispatchWaitsUntilCursorIsUpdated() {
    when(jdbc.queryForObject(NudgeNotificationHandler.MAX_NOTIFICATION_SERIAL, Long.class))
        .thenReturn(200L, 201L);
    when(jdbc.queryForObject(NudgeNotificationHandler.BASE_EXISTS_SQL, Boolean.class, 200L))
        .thenReturn(true);
    when(jdbc.query(anyString(), any(DataClassRowMapper.class), any(Object[].class)))
        .thenReturn(Collections.emptyList());
    doAnswer(
            invocation -> {
              handler.fetchPairsAndDispatch();
              return null;
            })
        .doNothing()
        .when(bus)
        .post(any());

    handler.fetchPairsAndDispatch();

    verify(jdbc, times(2))
        .queryForObject(NudgeNotificationHandler.MAX_NOTIFICATION_SERIAL, Long.class);
    verify(bus).post(FactInsertionNotification.internal());
    verify(jdbc).query(anyString(), any(DataClassRowMapper.class), eq(200L), eq(201L));
    assertThat(handler.notificationSer).hasValue(201);
  }

  @Test
  void emitsMetricsWhenFetching() {
    handler.notificationSer.set(100);
    when(jdbc.queryForObject(NudgeNotificationHandler.BASE_EXISTS_SQL, Boolean.class, 100L))
        .thenReturn(true);

    // When
    handler.fetchPairsAndDispatch();

    // Then
    verify(metrics).startSample();
    verify(sample).stop(timer);
  }

  @Test
  void emptyBoundedFetchStillAdvancesNotificationCursor() {
    handler.notificationSer.set(100);
    when(jdbc.queryForObject(NudgeNotificationHandler.BASE_EXISTS_SQL, Boolean.class, 100L))
        .thenReturn(true);

    handler.fetchPairsAndDispatch();

    assertThat(handler.notificationSer).hasValue(200);
    verify(bus, never()).post(any());
  }

  @Test
  void missingCursorWakesSubscribersWhenMaximumAdvances() {
    handler.notificationSer.set(200);
    when(jdbc.queryForObject(NudgeNotificationHandler.MAX_NOTIFICATION_SERIAL, Long.class))
        .thenReturn(201L);
    when(jdbc.queryForObject(NudgeNotificationHandler.BASE_EXISTS_SQL, Boolean.class, 200L))
        .thenReturn(false);

    handler.fetchPairsAndDispatch();

    verify(bus).post(FactInsertionNotification.internal());
    verify(jdbc, never()).query(anyString(), any(DataClassRowMapper.class), any(Object[].class));
    assertThat(handler.notificationSer).hasValue(201);
  }

  @Test
  void missingCursorWakesSubscribersWhenMaximumIsUnchanged() {
    handler.notificationSer.set(200);
    when(jdbc.queryForObject(NudgeNotificationHandler.BASE_EXISTS_SQL, Boolean.class, 200L))
        .thenReturn(false);

    handler.fetchPairsAndDispatch();

    verify(bus).post(FactInsertionNotification.internal());
    verify(jdbc, never()).query(anyString(), any(DataClassRowMapper.class), any(Object[].class));
    assertThat(handler.notificationSer).hasValue(200);
  }

  @Test
  void rebasesCursorAndWakesSubscribersWhenNotificationSequenceResets() {
    handler.notificationSer.set(200);
    when(jdbc.queryForObject(NudgeNotificationHandler.MAX_NOTIFICATION_SERIAL, Long.class))
        .thenReturn(1L);

    handler.fetchPairsAndDispatch();

    verify(bus).post(FactInsertionNotification.internal());
    verify(jdbc, never()).queryForObject(anyString(), eq(Boolean.class), anyLong());
    assertThat(handler.notificationSer).hasValue(1);
  }

  @Test
  void failedRefreshStillDrainsPendingRequestBeforeRethrowing() {
    handler.notificationSer.set(100);
    when(jdbc.queryForObject(NudgeNotificationHandler.BASE_EXISTS_SQL, Boolean.class, 100L))
        .thenReturn(true);
    var failure = new DataAccessResourceFailureException("database unavailable");
    when(jdbc.query(anyString(), any(DataClassRowMapper.class), any(Object[].class)))
        .thenAnswer(
            invocation -> {
              handler.fetchPairsAndDispatch();
              throw failure;
            })
        .thenReturn(Collections.emptyList());

    assertThatCode(handler::fetchPairsAndDispatch).isSameAs(failure);

    verify(jdbc, times(2))
        .queryForObject(NudgeNotificationHandler.MAX_NOTIFICATION_SERIAL, Long.class);
    verify(jdbc, times(2)).query(anyString(), any(DataClassRowMapper.class), any(Object[].class));
    assertThat(handler.notificationSer).hasValue(200);
  }

  @Test
  void failedRefreshDrainsRequestArrivingDuringUnlock() throws Exception {
    handler.destroy();
    var failure = new DataAccessResourceFailureException("database unavailable");
    when(jdbc.queryForObject(NudgeNotificationHandler.MAX_NOTIFICATION_SERIAL, Long.class))
        .thenThrow(failure)
        .thenReturn(200L);

    try (var ignored = requestRefreshDuringFirstUnlock()) {
      handler = new NudgeNotificationHandler(bus, jdbc, props, metrics, false);

      assertThatCode(handler::fetchPairsAndDispatch).isSameAs(failure);

      verify(jdbc, times(2))
          .queryForObject(NudgeNotificationHandler.MAX_NOTIFICATION_SERIAL, Long.class);
      assertThat(handler.notificationSer).hasValue(200);
    }
  }

  @Test
  void failuresAcrossUnlockKeepFirstExceptionAndSuppressTheNext() throws Exception {
    handler.destroy();
    var firstFailure = new DataAccessResourceFailureException("first failure");
    var secondFailure = new DataAccessResourceFailureException("second failure");
    when(jdbc.queryForObject(NudgeNotificationHandler.MAX_NOTIFICATION_SERIAL, Long.class))
        .thenThrow(firstFailure)
        .thenThrow(secondFailure);

    try (var ignored = requestRefreshDuringFirstUnlock()) {
      handler = new NudgeNotificationHandler(bus, jdbc, props, metrics, false);

      assertThatCode(handler::fetchPairsAndDispatch).isSameAs(firstFailure);

      assertThat(firstFailure.getSuppressed()).containsExactly(secondFailure);
      verify(jdbc, times(2))
          .queryForObject(NudgeNotificationHandler.MAX_NOTIFICATION_SERIAL, Long.class);
    }
  }

  private MockedConstruction<ReentrantLock> requestRefreshDuringFirstUnlock() {
    var actualLock = new ReentrantLock();
    var requestOnUnlock = new AtomicBoolean(true);
    return mockConstruction(
        ReentrantLock.class,
        (lock, context) -> {
          when(lock.tryLock()).thenAnswer(invocation -> actualLock.tryLock());
          when(lock.isHeldByCurrentThread())
              .thenAnswer(invocation -> actualLock.isHeldByCurrentThread());
          doAnswer(
                  invocation -> {
                    // Request after the inner loop finishes, while the owner still holds the lock.
                    // Delegate to a real lock to preserve ownership and reentry behavior.
                    if (requestOnUnlock.getAndSet(false)) handler.fetchPairsAndDispatch();
                    actualLock.unlock();
                    return null;
                  })
              .when(lock)
              .unlock();
        });
  }

  @Test
  void repeatedExceptionInstanceIsNotSuppressedOntoItself() {
    var failure = new DataAccessResourceFailureException("database unavailable");
    when(jdbc.queryForObject(NudgeNotificationHandler.MAX_NOTIFICATION_SERIAL, Long.class))
        .thenAnswer(
            invocation -> {
              handler.fetchPairsAndDispatch();
              throw failure;
            })
        .thenThrow(failure);

    assertThatCode(handler::fetchPairsAndDispatch).isSameAs(failure);

    assertThat(failure.getSuppressed()).isEmpty();
    verify(jdbc, times(2))
        .queryForObject(NudgeNotificationHandler.MAX_NOTIFICATION_SERIAL, Long.class);
  }

  @Test
  void failedBoundedFetchPreservesCursorAndAllowsRetry() {
    handler.notificationSer.set(100);
    when(jdbc.queryForObject(NudgeNotificationHandler.BASE_EXISTS_SQL, Boolean.class, 100L))
        .thenReturn(true);
    when(jdbc.query(anyString(), any(DataClassRowMapper.class), any(Object[].class)))
        .thenThrow(new DataAccessResourceFailureException("database unavailable"))
        .thenReturn(Collections.emptyList());

    assertThatCode(handler::fetchPairsAndDispatch)
        .isInstanceOf(DataAccessResourceFailureException.class);

    assertThat(handler.notificationSer).hasValue(100);

    handler.fetchPairsAndDispatch();

    assertThat(handler.notificationSer).hasValue(200);
  }
}
