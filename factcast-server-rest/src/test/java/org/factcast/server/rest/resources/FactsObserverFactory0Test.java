package org.factcast.server.rest.resources;

import static org.mockito.Mockito.*;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import org.factcast.core.TestHelper;
import org.glassfish.jersey.media.sse.EventOutput;
import org.junit.Test;

import com.mercateo.common.rest.schemagen.link.LinkFactory;
import com.mercateo.common.rest.schemagen.types.HyperSchemaCreator;

public class FactsObserverFactory0Test {

    @Test
    public void testNullContracts() throws Exception {

        TestHelper.expectNPE(() -> new FactsObserverFactory(null, mock(HyperSchemaCreator.class),
                mock(FactTransformer.class)));

        TestHelper.expectNPE(() -> new FactsObserverFactory(mock(LinkFactory.class), null, mock(
                FactTransformer.class)));

        TestHelper.expectNPE(() -> new FactsObserverFactory(mock(LinkFactory.class), mock(
                HyperSchemaCreator.class), null));

        FactsObserverFactory uut = new FactsObserverFactory(mock(LinkFactory.class), mock(
                HyperSchemaCreator.class), mock(FactTransformer.class));

        TestHelper.expectNPE(() -> uut.createFor(null, new URI("http://ibm.com"),
                new AtomicReference<>(), false));
        TestHelper.expectNPE(() -> uut.createFor(mock(EventOutput.class), null,
                new AtomicReference<>(), false));
        TestHelper.expectNPE(() -> uut.createFor(mock(EventOutput.class), new URI("http://ibm.com"),
                null, false));

    }
}
