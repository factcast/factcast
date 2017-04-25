package org.factcast.store.pgsql.internal;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.Timer;
import java.util.TimerTask;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CondensedExecutorTest {

    @Mock
    Timer mockTimer;

    @Mock
    Runnable callback;

    @Captor
    ArgumentCaptor<TimerTask> task;

    @Before
    public void setUp() {
        doNothing().when(mockTimer).schedule(task.capture(), anyLong());
    }

    @Test
    public void testDelayedExecution() throws Exception {
        CondensedExecutor uut = new CondensedExecutor(1, callback, () -> true, mockTimer);

        uut.trigger();
        verify(mockTimer).schedule(anyObject(), eq(1L));

        task.getValue().run();
        verify(callback).run();
    }

    @Test
    public void testDelayedMultipleExecution() throws Exception {
        CondensedExecutor uut = new CondensedExecutor(22, callback, () -> true, mockTimer);

        verify(mockTimer, never()).schedule(anyObject(), anyLong());

        uut.trigger();
        task.getAllValues().get(0).run();
        uut.trigger();
        task.getAllValues().get(1).run();

        verify(callback, times(2)).run();

    }

    @Test
    public void testDelayedCondensedExecution() throws Exception {
        CondensedExecutor uut = new CondensedExecutor(104, callback, () -> true, mockTimer);

        // not yet scheduled anything
        verify(mockTimer, never()).schedule(anyObject(), anyLong());

        uut.trigger();

        // scheduled once
        verify(mockTimer).schedule(anyObject(), eq(104L));

        uut.trigger();
        uut.trigger();
        uut.trigger();
        uut.trigger();
        // still scheduled only once
        verify(mockTimer).schedule(anyObject(), eq(104L));

        TimerTask taskArg = task.getValue();
        taskArg.run();

        // executing must noch change anything for scheduling
        verify(mockTimer).schedule(anyObject(), eq(104L));
        verifyNoMoreInteractions(mockTimer);

        uut.trigger();
        // a second call is scheduled
        verify(mockTimer, times(2)).schedule(anyObject(), eq(104L));

        uut.trigger();
        uut.trigger();
        uut.trigger();
        // no change: second call is scheduled
        verify(mockTimer, times(2)).schedule(anyObject(), eq(104L));

    }

}
