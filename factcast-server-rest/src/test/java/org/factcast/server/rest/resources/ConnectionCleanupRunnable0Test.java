package org.factcast.server.rest.resources;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.concurrent.CompletableFuture;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class ConnectionCleanupRunnable0Test {

    ConnectionCleanupRunnable uut;

    @Mock
    private FactsObserver observer;

    @Mock
    private CompletableFuture<Void> future;

    @Before
    public void prepare() {

        initMocks(this);
        uut = new ConnectionCleanupRunnable(observer, future);

    }

    @SuppressWarnings("boxing")
    @Test
    public void test_connection_stillAlive() {

        when(observer.isConnectionAlive()).thenReturn(Boolean.TRUE);

        uut.run();

        verify(observer, never()).unsubscribe();
        verifyZeroInteractions(future);
    }

    @SuppressWarnings("boxing")
    @Test
    public void test_connection_dead() {

        when(observer.isConnectionAlive()).thenReturn(Boolean.FALSE);

        uut.run();

        verify(observer).unsubscribe();
        verify(future).complete(eq(null));

    }

}
