package org.factcast.server.rest.resources;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import javax.ws.rs.core.MediaType;

import org.factcast.core.Fact;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.observer.FactObserver;
import org.glassfish.jersey.media.sse.EventOutput;
import org.glassfish.jersey.media.sse.OutboundEvent;

import com.mercateo.common.rest.schemagen.link.LinkFactory;
import com.mercateo.common.rest.schemagen.link.LinkFactoryContext;
import com.mercateo.common.rest.schemagen.link.relation.Rel;
import com.mercateo.common.rest.schemagen.plugin.FieldCheckerForSchema;
import com.mercateo.common.rest.schemagen.plugin.MethodCheckerForLink;
import com.mercateo.common.rest.schemagen.types.HyperSchemaCreator;

import lombok.val;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventObserver implements FactObserver {
    private final EventOutput eventOutput;

    private final LinkFactory<EventsResource> linkFatory;

    private final HyperSchemaCreator hyperSchemaCreator;

    private final LinkFactoryContext linkFactoryContext;

    private AtomicReference<Subscription> subcription;

    public EventObserver(EventOutput eventOutput, LinkFactory<EventsResource> linkFatory,
            HyperSchemaCreator hyperSchemaCreator, URI baseURI,
            AtomicReference<Subscription> subcription) {
        super();
        this.eventOutput = eventOutput;
        this.linkFatory = linkFatory;
        this.hyperSchemaCreator = hyperSchemaCreator;
        this.subcription = subcription;
        // this is need, because we are nor in requestscope anymore
        this.linkFactoryContext = new LinkFactoryContext() {

            @Override
            public MethodCheckerForLink getMethodCheckerForLink() {
                return m -> true;
            }

            @Override
            public FieldCheckerForSchema getFieldCheckerForSchema() {
                return (f, c) -> true;
            }

            @Override
            public URI getBaseUri() {
                return baseURI;
            }
        };
    }

    @Override
    public void onNext(Fact f) {
        UUID t = f.id();

        final OutboundEvent.Builder eventBuilder = new OutboundEvent.Builder();
        eventBuilder.name("new-event");
        String toReturn = t.toString();
        val linkToEvent = linkFatory.forCall(Rel.CANONICAL, r -> r.getForId(toReturn),
                linkFactoryContext);
        val withSchema = hyperSchemaCreator.create(new EventIdJson(toReturn), linkToEvent);
        eventBuilder.data(withSchema);
        eventBuilder.mediaType(MediaType.APPLICATION_JSON_TYPE);
        eventBuilder.id(t.toString());
        final OutboundEvent event = eventBuilder.build();
        try {
            eventOutput.write(event);
        } catch (IOException e) {
            unsubscribeAndThrow(e);
        }
    }

    @Override
    public void onCatchup() {
        final OutboundEvent.Builder eventBuilder = new OutboundEvent.Builder();
        eventBuilder.name("catchup");
        eventBuilder.comment("Signal event for catching up");
        eventBuilder.data("{\"catchup\":true}");
        final OutboundEvent event = eventBuilder.build();

        try {
            eventOutput.write(event);
        } catch (IOException e) {
            unsubscribeAndThrow(e);
        }
    }

    @Override
    public void onComplete() {

        final OutboundEvent.Builder eventBuilder = new OutboundEvent.Builder();
        eventBuilder.name("complete");
        eventBuilder.comment("Signal event for completion");
        eventBuilder.data("{\"complete\":true}");
        final OutboundEvent event = eventBuilder.build();
        try {
            eventOutput.write(event);
            eventOutput.close();
            subcription.get().close();
        } catch (Exception e) {
            unsubscribeAndThrow(e);
        }

    }

    private void unsubscribeAndThrow(Throwable e) {
        try {
            subcription.get().close();
            eventOutput.close();
        } catch (Exception e1) {

        }
        // debug level, because error occurs always, if client disappears
        log.debug("Error while writing into the pipe", e);
        throw new RuntimeException("Error when writing the event.", e);
    }

}