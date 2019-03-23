package org.factcast.core;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.factcast.core.store.FactStore;
import org.factcast.core.store.StateToken;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;

@RequiredArgsConstructor
@Accessors(fluent = true, chain = true)
public class WithOptimisticLock {
    final FactStore store;

    final StateToken token;

    @Setter
    private int retry = 1;

    @Setter
    private long interval = 10;

    private int count = 0;

    public PublishResult publish(Supplier<IntermediatePublishResult> operation) {
        try {
            while (++count <= retry) {
                IntermediatePublishResult r = operation.get();

                if (store.publishIfUnchanged(token, r.factsToPublish())) {
                    // TODO might throw RTE
                    return r.andThen().apply(lastFactId(r.factsToPublish()));
                } else {
                    sleep();
                }
            }
            return PublishResult.fail(new IllegalStateException("TODO failed " + count
                    + " times to acquire lock"));

        } catch (RuntimeException e) {
            return PublishResult.fail(e);
        }
    }

    @SneakyThrows
    private void sleep() {
        Thread.sleep(count * interval);
    }

    // TODO unsafe
    private UUID lastFactId(@NonNull List<Fact> factsToPublish) {
        return factsToPublish.get(factsToPublish.size() - 1).id();
    }

}
