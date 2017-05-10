package org.factcast.server.rest.resources;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import org.factcast.core.subscription.Subscription;
import org.glassfish.jersey.media.sse.EventOutput;
import org.springframework.stereotype.Component;

import com.mercateo.common.rest.schemagen.link.LinkFactory;
import com.mercateo.common.rest.schemagen.types.HyperSchemaCreator;

import lombok.AllArgsConstructor;

@Component
@AllArgsConstructor
class FactsObserverFactory {

    private final LinkFactory<FactsResource> factsResourceLinkFactory;

    private final HyperSchemaCreator hyperSchemaCreator;

    FactsObserver createFor(EventOutput eventOutput, URI baseURI,
            AtomicReference<Subscription> subscription) {
        return new FactsObserver(eventOutput, factsResourceLinkFactory, hyperSchemaCreator, baseURI,
                subscription);
    }
}
