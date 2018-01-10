package org.factcast.server.rest.resources;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.MockitoAnnotations.initMocks;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.factcast.core.TestHelper;
import org.glassfish.jersey.media.sse.EventOutput;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import com.mercateo.common.rest.schemagen.link.LinkFactory;
import com.mercateo.common.rest.schemagen.types.HyperSchemaCreator;

import lombok.SneakyThrows;

public class FactsObserverFactory0Test {

    @Mock
    private LinkFactory<FactsResource> factsResourceLinkFactory;

    @Mock
    private HyperSchemaCreator hyperSchemaCreator;

    @Mock
    private FactTransformer factTransformer;

    @Mock
    private ScheduledExecutorService executorService;

    private int waitSecondsForCleanUpCheck = 1;

    FactsObserverFactory uut;

    @Before
    public void prepare() {
        initMocks(this);
        uut = new FactsObserverFactory(factsResourceLinkFactory, hyperSchemaCreator,
                factTransformer, executorService, waitSecondsForCleanUpCheck);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testNullContracts() throws Exception {

        TestHelper.expectNPE(() -> new FactsObserverFactory(null, hyperSchemaCreator,
                factTransformer, executorService, 0));

        TestHelper.expectNPE(() -> new FactsObserverFactory(factsResourceLinkFactory, null,
                factTransformer, executorService, 0));

        TestHelper.expectNPE(() -> new FactsObserverFactory(factsResourceLinkFactory,
                hyperSchemaCreator, null, executorService, 0));

        TestHelper.expectNPE(() -> new FactsObserverFactory(factsResourceLinkFactory,
                hyperSchemaCreator, factTransformer, null, 0));

        FactsObserverFactory uut = new FactsObserverFactory(factsResourceLinkFactory,
                hyperSchemaCreator, factTransformer, executorService, 0);

        TestHelper.expectNPE(() -> uut.createFor(null, new URI("http://ibm.com"),
                new AtomicReference<>(), false));
        TestHelper.expectNPE(() -> uut.createFor(mock(EventOutput.class), null,
                new AtomicReference<>(), false));
        TestHelper.expectNPE(() -> uut.createFor(mock(EventOutput.class), new URI("http://ibm.com"),
                null, false));

    }

    @SuppressWarnings({ "unchecked", "boxing" })
    @SneakyThrows
    @Test
    public void test_scheduleCleanUp() {

        // given
        CompletableFuture<Void> future = mock(CompletableFuture.class);
        FactsObserver factsObserver = mock(FactsObserver.class);

        // when
        uut.scheduleCleanUp(future, factsObserver);

        // then
        verify(future).thenRun(any(Runnable.class));
        verify(executorService).scheduleWithFixedDelay(any(ConnectionCleanupRunnable.class), eq(Long
                .valueOf(1)), eq(Long.valueOf(1)), eq(TimeUnit.SECONDS));

        verifyNoMoreInteractions(future);
        verifyNoMoreInteractions(executorService);

    }
}
