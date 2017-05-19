package org.factcast.server.rest.resources;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import org.factcast.core.subscription.Subscription;
import org.glassfish.jersey.media.sse.EventOutput;
import org.springframework.stereotype.Component;

import com.mercateo.common.rest.schemagen.link.LinkFactory;
import com.mercateo.common.rest.schemagen.types.HyperSchemaCreator;

import lombok.AllArgsConstructor;
import lombok.NonNull;

@Component
@AllArgsConstructor
class FactsObserverFactory {
    @NonNull
    private final LinkFactory<FactsResource> factsResourceLinkFactory;

    @NonNull
    private final HyperSchemaCreator hyperSchemaCreator;

    @NonNull
    private final FactTransformer factTransformer;

    FactsObserver createFor(EventOutput eventOutput, URI baseURI,
            AtomicReference<Subscription> subscription, boolean fullOutputMode) {
        return new FactsObserver(eventOutput, factsResourceLinkFactory, hyperSchemaCreator, baseURI,
                subscription, factTransformer, fullOutputMode);
    }
}
