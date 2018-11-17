package org.factcast.store.pgsql.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.Timer;
import java.util.TimerTask;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CondensedQueryExecutor0Test {

    @Mock
    Timer mockTimer;

    @Mock
    PGSynchronizedQuery callback;

    @Captor
    ArgumentCaptor<TimerTask> task;

    @BeforeEach
    public void setUp() {
        doNothing().when(mockTimer).schedule(task.capture(), anyLong());
    }

    @Test
    public void testDelayedExecution() {
        CondensedQueryExecutor uut = new CondensedQueryExecutor(1, callback, () -> true, mockTimer);
        uut.trigger();
        verify(mockTimer).schedule(any(), eq(1L));
        task.getValue().run();
        verify(callback).run(anyBoolean());
    }

    @Test
    public void testDelayedMultipleExecution() {
        CondensedQueryExecutor uut = new CondensedQueryExecutor(22, callback, () -> true,
                mockTimer);
        verify(mockTimer, never()).schedule(any(), anyLong());
        uut.trigger();
        task.getAllValues().get(0).run();
        uut.trigger();
        task.getAllValues().get(1).run();
        verify(callback, times(2)).run(anyBoolean());
    }

    @Test
    public void testDelayedCondensedExecution() {
        CondensedQueryExecutor uut = new CondensedQueryExecutor(104, callback, () -> true,
                mockTimer);
        // not yet scheduled anything
        verify(mockTimer, never()).schedule(any(), anyLong());
        uut.trigger();
        // scheduled once
        verify(mockTimer).schedule(any(), eq(104L));
        uut.trigger();
        uut.trigger();
        uut.trigger();
        uut.trigger();
        // still scheduled only once
        verify(mockTimer).schedule(any(), eq(104L));
        TimerTask taskArg = task.getValue();
        taskArg.run();
        // executing must noch change anything for scheduling
        verify(mockTimer).schedule(any(), eq(104L));
        verifyNoMoreInteractions(mockTimer);
        uut.trigger();
        // a second call is scheduled
        verify(mockTimer, times(2)).schedule(any(), eq(104L));
        uut.trigger();
        uut.trigger();
        uut.trigger();
        // no change: second call is scheduled
        verify(mockTimer, times(2)).schedule(any(), eq(104L));
    }
}
