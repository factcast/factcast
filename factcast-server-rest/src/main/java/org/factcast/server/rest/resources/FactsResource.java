/**
 * Copyright © 2018 Mercateo AG (http://www.mercateo.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import javax.ws.rs.core.MediaType;

import org.factcast.core.Fact;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.server.rest.resources.cache.Cacheable;
import org.factcast.server.rest.resources.cache.NoCache;
import org.glassfish.jersey.media.sse.EventOutput;
import org.glassfish.jersey.media.sse.SseFeature;
import org.springframework.stereotype.Component;

import com.mercateo.common.rest.schemagen.JerseyResource;
import com.mercateo.common.rest.schemagen.link.LinkFactoryContext;
import com.mercateo.common.rest.schemagen.types.ObjectWithSchema;

import lombok.AllArgsConstructor;

/**
 * The read resource for facts. Providing two SSE streams (one for only ids, one
 * for full facts) and a cacheable endpoint for single facts
 * 
 * @author joerg_adler
 *
 */
@Path("facts")
@Component
@AllArgsConstructor
public class FactsResource implements JerseyResource {

    private final FactStore factStore;

    private final FactsSchemaCreator schemaCreator;

    private final FactsObserverFactory factsObserverFactory;

    private final LinkFactoryContext linkFactoryContext;

    private final FactTransformer factTransformer;

    @GET
    @Produces(SseFeature.SERVER_SENT_EVENTS)
    @NoCache
    @Path("id-only")
    public EventOutput getServerSentEvents(
            @NotNull @Valid @BeanParam SubscriptionRequestParams subscriptionRequestParams) {
        return createEventOutput(subscriptionRequestParams, false);
    }

    @GET
    @Produces(SseFeature.SERVER_SENT_EVENTS)
    @NoCache
    public EventOutput getServerSentEventsFull(
            @NotNull @Valid @BeanParam SubscriptionRequestParams subscriptionRequestParams) {
        return createEventOutput(subscriptionRequestParams, true);
    }

    private EventOutput createEventOutput(SubscriptionRequestParams subscriptionRequestParams,
            boolean fullOutput) {
        final EventOutput eventOutput = new EventOutput();
        SubscriptionRequestTO req = subscriptionRequestParams.toRequest(!fullOutput);
        AtomicReference<Subscription> subscription = new AtomicReference<Subscription>(null);
        FactObserver observer = factsObserverFactory.createFor(eventOutput, linkFactoryContext
                .getBaseUri(), subscription, fullOutput);

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
        FactJson returnValue = fact.map(factTransformer::toJson).orElseThrow(
                NotFoundException::new);
        return schemaCreator.forFactWithId(returnValue);
    }
}
