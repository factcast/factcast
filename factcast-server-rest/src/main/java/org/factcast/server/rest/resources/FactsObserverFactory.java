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

/**
 * this class creates a FactObserver in the first place, when the request comes
 * in
 * 
 * @author joerg_adler
 *
 */
@Component
@AllArgsConstructor
class FactsObserverFactory {
    @NonNull
    private final LinkFactory<FactsResource> factsResourceLinkFactory;

    @NonNull
    private final HyperSchemaCreator hyperSchemaCreator;

    @NonNull
    private final FactTransformer factTransformer;

    FactsObserver createFor(@NonNull EventOutput eventOutput, @NonNull URI baseURI,
            @NonNull AtomicReference<Subscription> subscription, boolean fullOutputMode) {
        FactsObserver factsObserver = new FactsObserver(eventOutput, factsResourceLinkFactory,
                hyperSchemaCreator, baseURI, subscription, factTransformer, fullOutputMode);
        new ConnectionCleanupTimer(factsObserver).start();
        return factsObserver;
    }
}
