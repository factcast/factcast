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

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.inject.Inject;

import org.factcast.core.subscription.Subscription;
import org.glassfish.jersey.media.sse.EventOutput;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.MoreExecutors;
import com.mercateo.common.rest.schemagen.link.LinkFactory;
import com.mercateo.common.rest.schemagen.types.HyperSchemaCreator;

import lombok.NonNull;

/**
 * this class creates a FactObserver in the first place, when the request comes
 * in
 * 
 * @author joerg_adler
 *
 */
@Component
class FactsObserverFactory {
    @NonNull
    private final LinkFactory<FactsResource> factsResourceLinkFactory;

    @NonNull
    private final HyperSchemaCreator hyperSchemaCreator;

    @NonNull
    private final FactTransformer factTransformer;

    @NonNull
    private final ScheduledExecutorService executorService;

    private final int waitSecondsForCleanUpCheck;

    @VisibleForTesting
    FactsObserverFactory(@NonNull LinkFactory<FactsResource> factsResourceLinkFactory,
            @NonNull HyperSchemaCreator hyperSchemaCreator,
            @NonNull FactTransformer factTransformer,
            @NonNull ScheduledExecutorService executorService, int waitSecondsForCleanUpCheck) {
        this.factsResourceLinkFactory = factsResourceLinkFactory;
        this.hyperSchemaCreator = hyperSchemaCreator;
        this.factTransformer = factTransformer;
        this.executorService = executorService;
        this.waitSecondsForCleanUpCheck = waitSecondsForCleanUpCheck;
    }

    @Inject
    public FactsObserverFactory(@NonNull LinkFactory<FactsResource> factsResourceLinkFactory,
            @NonNull HyperSchemaCreator hyperSchemaCreator,
            @NonNull FactTransformer factTransformer,
            @Value("${rest.cleanup-conn.threads-nr:10}") int nrCleanUpThreads,
            @Value("${rest.cleanup-conn.interval-sec:10}") int waitSecondsForCleanUpCheck) {
        this(factsResourceLinkFactory, hyperSchemaCreator, factTransformer, MoreExecutors
                .getExitingScheduledExecutorService(new ScheduledThreadPoolExecutor(
                        nrCleanUpThreads), 100, TimeUnit.MILLISECONDS), waitSecondsForCleanUpCheck);
    }

    FactsObserver createFor(@NonNull EventOutput eventOutput, @NonNull URI baseURI,
            @NonNull AtomicReference<Subscription> subscription, boolean fullOutputMode) {
        FactsObserver factsObserver = new FactsObserver(eventOutput, factsResourceLinkFactory,
                hyperSchemaCreator, baseURI, subscription, factTransformer, fullOutputMode);

        scheduleCleanUp(new CompletableFuture<>(), factsObserver);

        return factsObserver;
    }

    @VisibleForTesting
    void scheduleCleanUp(CompletableFuture<Void> future, FactsObserver factsObserver) {

        ScheduledFuture<?> scheduleWithFixedDelay = executorService.scheduleWithFixedDelay(
                new ConnectionCleanupRunnable(factsObserver, future), waitSecondsForCleanUpCheck,
                waitSecondsForCleanUpCheck, TimeUnit.SECONDS);
        future.thenRun(() -> scheduleWithFixedDelay.cancel(true));
    }
}
