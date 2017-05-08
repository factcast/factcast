package org.factcast.server.rest.resources;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicReference;

import org.factcast.core.subscription.Subscription;
import org.factcast.server.rest.TestFacts;
import org.glassfish.jersey.media.sse.EventOutput;
import org.glassfish.jersey.media.sse.OutboundEvent;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import com.mercateo.common.rest.schemagen.JsonHyperSchema;
import com.mercateo.common.rest.schemagen.JsonHyperSchemaCreator;
import com.mercateo.common.rest.schemagen.link.LinkFactory;
import com.mercateo.common.rest.schemagen.link.LinkMetaFactory;
import com.mercateo.common.rest.schemagen.link.relation.Rel;
import com.mercateo.common.rest.schemagen.types.HyperSchemaCreator;
import com.mercateo.common.rest.schemagen.types.ObjectWithSchema;
import com.mercateo.common.rest.schemagen.types.ObjectWithSchemaCreator;

public class EventObserverTest {

    @Mock
    private EventOutput eventOutput;

    @SuppressWarnings("deprecation")
    @Spy
    private LinkFactory<EventsResource> linkFatory = LinkMetaFactory.createInsecureFactoryForTest()
            .createFactoryFor(EventsResource.class);

    private EventObserver uut;

    @Mock
    private Subscription subscription;

    @Before
    public void setup() throws URISyntaxException {
        MockitoAnnotations.initMocks(this);

        subscription = mock(Subscription.class);
        AtomicReference<Subscription> subsup = new AtomicReference<Subscription>(subscription);
        URI baseURI = new URI("http://localhost:8080");
        HyperSchemaCreator hyperSchemaCreator = new HyperSchemaCreator(
                new ObjectWithSchemaCreator(), new JsonHyperSchemaCreator());
        uut = new EventObserver(eventOutput, linkFatory, hyperSchemaCreator, baseURI, subsup);
    }

    @Test
    public void testOnNext() throws Exception {
        uut.onNext(TestFacts.one);
        ArgumentCaptor<OutboundEvent> cap = ArgumentCaptor.forClass(OutboundEvent.class);
        verify(eventOutput).write(cap.capture());
        OutboundEvent ev = cap.getValue();
        @SuppressWarnings("unchecked")
        JsonHyperSchema jsonHyperSchema = ((ObjectWithSchema<Void>) ev.getData()).schema;
        assertTrue(jsonHyperSchema.getByRel(Rel.CANONICAL).isPresent());
        assertThat(ev.getId(), is(TestFacts.one.id().toString()));
    }

    @Test
    public void testOnCatchup() throws Exception {
        uut.onCatchup();
        ArgumentCaptor<OutboundEvent> cap = ArgumentCaptor.forClass(OutboundEvent.class);
        verify(eventOutput).write(cap.capture());
        OutboundEvent event = cap.getValue();
        assertThat(event, is(notNullValue()));
        assertThat(event.getName(), is("catchup"));
        verifyNoMoreInteractions(subscription, linkFatory, eventOutput);

    }

    @Test
    public void testOnCatchupError() throws Exception {
        doThrow(IOException.class).when(eventOutput).write(any());
        try {
            uut.onCatchup();
        } catch (RuntimeException e) {
            verify(subscription).close();
            verify(eventOutput).close();
            verify(eventOutput, times(1)).write(any());
            verifyNoMoreInteractions(subscription, linkFatory, eventOutput);
            return;
        }
        fail();

    }

    @Test
    public void testOnComplete() throws Exception {

        uut.onComplete();
        ArgumentCaptor<OutboundEvent> cap = ArgumentCaptor.forClass(OutboundEvent.class);
        verify(eventOutput).write(cap.capture());
        OutboundEvent event = cap.getValue();
        assertThat(event, is(notNullValue()));
        assertThat(event.getName(), is("complete"));
        verify(subscription).close();
        verify(eventOutput).close();
        verifyNoMoreInteractions(subscription, linkFatory, eventOutput);
    }

}
