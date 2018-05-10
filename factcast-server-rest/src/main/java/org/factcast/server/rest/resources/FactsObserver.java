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

import com.google.common.annotations.VisibleForTesting;
import com.mercateo.common.rest.schemagen.link.LinkFactory;
import com.mercateo.common.rest.schemagen.link.LinkFactoryContext;
import com.mercateo.common.rest.schemagen.link.relation.Rel;
import com.mercateo.common.rest.schemagen.plugin.FieldCheckerForSchema;
import com.mercateo.common.rest.schemagen.plugin.MethodCheckerForLink;
import com.mercateo.common.rest.schemagen.types.HyperSchemaCreator;
import com.mercateo.common.rest.schemagen.types.ObjectWithSchema;

import lombok.NonNull;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

/**
 * This is the worker, where the content of the SSE-Streams is created. It has
 * two output modes. Depending on the flag "fullOutputMode" there will be only
 * ids in the stream or full facts.
 * 
 * @author joerg_adler
 *
 */
@Slf4j
public class FactsObserver implements FactObserver {
    private final EventOutput eventOutput;

    private final LinkFactory<FactsResource> linkFatory;

    private final HyperSchemaCreator hyperSchemaCreator;

    private final LinkFactoryContext linkFactoryContext;

    private AtomicReference<Subscription> subcription;

    private FactTransformer factTransformer;

    private boolean fullOutputMode;

    public FactsObserver(@NonNull EventOutput eventOutput,
            @NonNull LinkFactory<FactsResource> linkFatory,
            @NonNull HyperSchemaCreator hyperSchemaCreator, @NonNull URI baseURI,
            @NonNull AtomicReference<Subscription> subcription,
            @NonNull FactTransformer factTransformer, boolean fullOutputMode) {
        super();

        this.fullOutputMode = fullOutputMode;
        this.eventOutput = eventOutput;
        this.linkFatory = linkFatory;
        this.hyperSchemaCreator = hyperSchemaCreator;
        this.subcription = subcription;
        this.factTransformer = factTransformer;
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
        final OutboundEvent.Builder eventBuilder = new OutboundEvent.Builder();
        eventBuilder.name("new-fact");
        ObjectWithSchema<?> withSchema = createPayload(f, fullOutputMode);

        eventBuilder.data(withSchema);
        eventBuilder.mediaType(MediaType.APPLICATION_JSON_TYPE);
        eventBuilder.id(f.id().toString());
        final OutboundEvent event = eventBuilder.build();
        try {
            eventOutput.write(event);
        } catch (IOException e) {
            unsubscribe();
            log.debug("Error while writing into the pipe", e);
        }
    }

    @VisibleForTesting
    ObjectWithSchema<?> createPayload(Fact f, boolean fullOutputMode) {
        UUID t = f.id();
        String toReturn = t.toString();

        ObjectWithSchema<?> withSchema;
        if (fullOutputMode) {
            val linkToEvent = linkFatory.forCall(Rel.SELF, r -> r.getForId(toReturn),
                    linkFactoryContext);
            withSchema = hyperSchemaCreator.create(factTransformer.toJson(f), linkToEvent);
        } else {

            val linkToEvent = linkFatory.forCall(Rel.CANONICAL, r -> r.getForId(toReturn),
                    linkFactoryContext);
            withSchema = hyperSchemaCreator.create(new FactIdJson(toReturn), linkToEvent);
        }
        return withSchema;
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
            unsubscribe();
            log.debug("Error while writing into the pipe", e);
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
            unsubscribe();
            log.debug("Error while writing into the pipe", e);
        }

    }

    synchronized void unsubscribe() {
        try {
            subcription.get().close();
        } catch (Exception e) {
            log.error("unable to close event output", e);
        }
        try {
            eventOutput.close();
        } catch (IOException e) {
            log.error("unable to close event output", e);
        }
    }

    @Override
    public void onError(Throwable exception) {
        try {
            subcription.get().close();
            eventOutput.close();
        } catch (Exception e1) {

        }

        log.error("Error while reading", exception);
    }

    boolean isConnectionAlive() {

        final OutboundEvent.Builder eventBuilder = new OutboundEvent.Builder();
        eventBuilder.name("keep-alive");
        eventBuilder.comment("Signal event for keep-alive");
        final OutboundEvent event = eventBuilder.build();
        try {
            eventOutput.write(event);
            return true;
        } catch (Exception e) {
            return false;
        }

    }
}