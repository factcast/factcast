package org.factcast.server.grpc;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import io.grpc.stub.ServerCallStreamObserver;

@RunWith(MockitoJUnitRunner.class)
public class BlockingStreamObserverTest {
    @Mock
    private ServerCallStreamObserver<?> delegate;

    private BlockingStreamObserver<?> uut;

    @Before
    public void setUp() throws Exception {
        uut = new BlockingStreamObserver<>("foo", delegate);
    }

    @Test
    public void testOnCompleted() throws Exception {
        verify(delegate, never()).onCompleted();
        uut.onCompleted();
        verify(delegate).onCompleted();
    }

    @Test
    public void testOnError() throws Exception {
        verify(delegate, never()).onError(any());
        uut.onError(new Exception());
        verify(delegate).onError(any());
    }

}
