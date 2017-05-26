package org.factcast.server.rest.resources;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.factcast.server.rest.TestFacts;
import org.factcast.server.rest.resources.FactJson.Header;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import com.mercateo.common.rest.schemagen.JsonHyperSchemaCreator;
import com.mercateo.common.rest.schemagen.link.LinkFactory;
import com.mercateo.common.rest.schemagen.link.relation.RelationContainer;
import com.mercateo.common.rest.schemagen.types.HyperSchemaCreator;
import com.mercateo.common.rest.schemagen.types.ObjectWithSchemaCreator;

@RunWith(MockitoJUnitRunner.class)
public class FactsSchemaCreator0Test {
    @Mock
    private LinkFactory<FactsResource> eventsResourceLinkFactory;

    @Spy
    private HyperSchemaCreator hyperSchemaCreator = new HyperSchemaCreator(
            new ObjectWithSchemaCreator(), new JsonHyperSchemaCreator());

    @InjectMocks
    private FactsSchemaCreator uut;

    @Test
    public void testForFactWithId() throws Exception {
        FactJson returnValue = mock(FactJson.class);
        Header header = new Header(TestFacts.one.id(), TestFacts.one.ns(), TestFacts.one.type(),
                TestFacts.one.aggIds());
        when(returnValue.header()).thenReturn(header);
        when(eventsResourceLinkFactory.forCall(any(RelationContainer.class), any())).thenReturn(
                Optional.empty());
        uut.forFactWithId(returnValue);
        verify(eventsResourceLinkFactory).forCall(any(RelationContainer.class), any());
    }

}
