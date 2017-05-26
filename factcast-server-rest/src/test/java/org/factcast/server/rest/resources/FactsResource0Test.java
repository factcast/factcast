package org.factcast.server.rest.resources;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Optional;

import javax.ws.rs.NotFoundException;

import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.server.rest.TestFacts;
import org.glassfish.jersey.media.sse.EventOutput;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.mercateo.common.rest.schemagen.JsonHyperSchema;
import com.mercateo.common.rest.schemagen.link.LinkFactoryContext;
import com.mercateo.common.rest.schemagen.types.ObjectWithSchema;

@RunWith(MockitoJUnitRunner.class)
public class FactsResource0Test {
    @Mock
    private FactsObserverFactory eventObserverFactory;

    @Mock
    private FactStore factStore;

    @Mock
    private LinkFactoryContext linkFactoryContext;

    @Spy
    private FactTransformer factTransformer = new FactTransformer(new ObjectMapper());

    @Mock
    private FactsSchemaCreator schemaCreator;

    @InjectMocks
    private FactsResource uut;

    @Test
    public void testGetServerSentEvents() throws Exception {
        SubscriptionRequestParams subscriptionRequestParams = mock(SubscriptionRequestParams.class);
        SubscriptionRequestTO subTo = mock(SubscriptionRequestTO.class);
        when(subscriptionRequestParams.toRequest(anyBoolean())).thenReturn(subTo);
        FactsObserver value = mock(FactsObserver.class);
        when(eventObserverFactory.createFor(any(), any(), any(), anyBoolean())).thenReturn(value);
        URI baseUri = new URI("http://localhost:8080");
        when(linkFactoryContext.getBaseUri()).thenReturn(baseUri);
        EventOutput returnValue = uut.getServerSentEvents(subscriptionRequestParams);

        verify(eventObserverFactory).createFor(eq(returnValue), eq(baseUri), any(), eq(false));
        verify(factStore).subscribe(subTo, value);
    }

    @Test
    public void testGetFullServerSentEvents() throws Exception {
        SubscriptionRequestParams subscriptionRequestParams = mock(SubscriptionRequestParams.class);
        SubscriptionRequestTO subTo = mock(SubscriptionRequestTO.class);
        when(subscriptionRequestParams.toRequest(anyBoolean())).thenReturn(subTo);
        FactsObserver value = mock(FactsObserver.class);
        when(eventObserverFactory.createFor(any(), any(), any(), anyBoolean())).thenReturn(value);
        URI baseUri = new URI("http://localhost:8080");
        when(linkFactoryContext.getBaseUri()).thenReturn(baseUri);
        EventOutput returnValue = uut.getServerSentEventsFull(subscriptionRequestParams);

        verify(eventObserverFactory).createFor(eq(returnValue), eq(baseUri), any(), eq(true));
        verify(factStore).subscribe(subTo, value);
    }

    @Test
    public void testGetForId() throws Exception {
        when(factStore.fetchById(TestFacts.one.id())).thenReturn(Optional.of(TestFacts.one));
        ObjectWithSchema<FactJson> value = ObjectWithSchema.create(null, JsonHyperSchema.from(Lists
                .newArrayList()));
        when(schemaCreator.forFactWithId(any())).thenReturn(value);
        ObjectWithSchema<FactJson> result = uut.getForId(TestFacts.one.id().toString());
        assertThat(result, is(value));
    }

    @SuppressWarnings("unchecked")
    @Test(expected = NotFoundException.class)
    public void testGetForIdNotFound() throws Exception {
        when(factStore.fetchById(TestFacts.one.id())).thenThrow(IllegalArgumentException.class);
        ObjectWithSchema<FactJson> value = ObjectWithSchema.create(null, JsonHyperSchema.from(Lists
                .newArrayList()));
        when(schemaCreator.forFactWithId(any())).thenReturn(value);
        uut.getForId(TestFacts.one.id().toString());

    }

}
