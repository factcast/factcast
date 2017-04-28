package org.factcast.store.inmem;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.factcast.core.Fact;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.FactSpecMatcher;
import org.factcast.core.subscription.FactStoreObserver;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.Subscriptions;
import org.springframework.beans.factory.DisposableBean;

import com.google.common.annotations.VisibleForTesting;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Eternally-growing InMem Implementation of a FactStore. USE FOR TESTING
 * PURPOSES ONLY
 * 
 * @author uwe.schaefer@mercateo.com, joerg.adler@mercateo.com
 *
 */
@Deprecated
@Slf4j
public class InMemFactStore implements FactStore, DisposableBean {

    @VisibleForTesting
    InMemFactStore(@NonNull ExecutorService es) {
        this.executorService = es;

        if (Package.getPackage("org.junit") == null) {

            log.warn("");
            log.warn(
                    "**********************************************************************************************************");
            log.warn(
                    "* You are using an inmem-impl of a FactStore. This imlementation is for quick testing ONLY and will fail *");
            log.warn(
                    "*   with OOM if you load it with a significant amount of Facts.                                          *");
            log.warn(
                    "**********************************************************************************************************");
            log.warn("");
        }
    }

    public InMemFactStore() {
        this(Executors.newCachedThreadPool());
    }

    private final AtomicInteger highwaterMark = new AtomicInteger(0);

    private final LinkedHashMap<Integer, Fact> store = new LinkedHashMap<>();

    private final Set<UUID> ids = new HashSet<>();

    private final CopyOnWriteArrayList<InMemSubscription> activeSubscriptions = new CopyOnWriteArrayList<>();

    private final ExecutorService executorService;

    private class InMemSubscription implements Consumer<Fact> {
        private final Predicate<Fact> matcher;

        final Consumer<Fact> consumer;

        InMemSubscription(SubscriptionRequestTO request, Consumer<Fact> consumer) {
            this.consumer = consumer;
            matcher = FactSpecMatcher.matchesAnyOf(request.specs());
        }

        public void close() {
            synchronized (InMemFactStore.this) {
                activeSubscriptions.remove(this);
            }
        }

        public boolean matches(Fact f) {
            return matcher.test(f);
        }

        @Override
        public void accept(Fact t) {
            if (matches(t)) {
                consumer.accept(t);
            }
        }

    }

    @Override
    public synchronized Optional<Fact> fetchById(@NonNull UUID id) {
        Stream<Entry<Integer, Fact>> stream = store.entrySet().stream();
        return stream.filter(e -> e.getValue().id().equals(id)).findFirst().map(e -> e.getValue());
    }

    @Override
    public synchronized void publish(@NonNull List<? extends Fact> factsToPublish) {

        if (factsToPublish.stream().anyMatch(f -> ids.contains(f.id()))) {
            throw new IllegalArgumentException("duplicate ids - ids must be unique!");
        }

        factsToPublish.forEach(f -> {
            int ser = highwaterMark.incrementAndGet();
            store.put(ser, f);
            ids.add(f.id());

            activeSubscriptions.stream().forEachOrdered(s -> s.accept(f));
        });
    }

    @Override
    public synchronized Subscription subscribe(SubscriptionRequestTO request,
            FactStoreObserver observer) {

        // FIXME
        InMemSubscription s = new InMemSubscription(request, c -> observer.onNext(c));
        SubscriptionImpl<Fact> subscription = Subscriptions.on(observer);

        executorService.submit(() -> {
            if (!request.ephemeral()) {
                store.values().stream().forEach(f -> {
                    if (s.matches(f)) {
                        subscription.notifyElement(f);
                    }
                });
            }
            subscription.notifyCatchup();
            if (request.continous()) {
                activeSubscriptions.add(s);
            } else {
                subscription.notifyComplete();
            }
        });

        return subscription.onClose(s::close);
    }

    @Override
    public synchronized void destroy() throws Exception {
        executorService.shutdown();
    }

}
