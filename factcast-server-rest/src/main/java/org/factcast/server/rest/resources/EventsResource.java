package org.factcast.server.rest.resources;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;

import org.factcast.core.Fact;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.core.util.FactCastJson;
import org.factcast.server.rest.resources.cache.Cacheable;
import org.factcast.server.rest.resources.cache.NoCache;
import org.glassfish.jersey.media.sse.EventOutput;
import org.glassfish.jersey.media.sse.SseFeature;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercateo.common.rest.schemagen.JerseyResource;
import com.mercateo.common.rest.schemagen.link.LinkFactoryContext;
import com.mercateo.common.rest.schemagen.types.ObjectWithSchema;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Path("events")

@Slf4j

@Component
@AllArgsConstructor
public class EventsResource implements JerseyResource {

    private final FactStore factStore;

    private final ObjectMapper objectMapper;

    private final EventsSchemaCreator schemaCreator;

    private final EventObserverFactory eventObserverFactory;

    private final LinkFactoryContext linkFactoryContext;

    @GET
    @Produces(SseFeature.SERVER_SENT_EVENTS)
    @NoCache
    public EventOutput getServerSentEvents(
            @NotNull @Valid @BeanParam SubscriptionRequestParams subscriptionRequestParams) {
        final EventOutput eventOutput = new EventOutput();
        SubscriptionRequestTO req = subscriptionRequestParams.toRequest();
        AtomicReference<Subscription> subscription = new AtomicReference<Subscription>(null);
        FactObserver observer = eventObserverFactory.createFor(eventOutput, linkFactoryContext
                .getBaseUri(), subscription);

        Subscription sub = factStore.subscribe(req, observer);
        subscription.set(sub);

        return eventOutput;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{id}")
    @Cacheable
    public ObjectWithSchema<FactJson> getForId(@NotNull @PathParam("id") String id) {
        Optional<Fact> fact;
        try {
            fact = factStore.fetchById(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            throw new NotFoundException();
        }
        FactJson returnValue = fact.map(f -> {
            try {
                JsonNode payLoad = objectMapper.readTree(f.jsonPayload());
                return new FactJson(FactCastJson.readValue(
                        org.factcast.server.rest.resources.FactJson.Header.class, f.jsonHeader()),
                        payLoad);
            } catch (Exception e) {
                log.error("error", e);
                throw new WebApplicationException(500);
            }
        }).orElseThrow(NotFoundException::new);
        return schemaCreator.forFactWithId(returnValue);
    }
}
