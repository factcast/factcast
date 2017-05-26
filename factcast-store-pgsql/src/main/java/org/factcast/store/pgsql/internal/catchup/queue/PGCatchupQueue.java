package org.factcast.store.pgsql.internal.catchup.queue;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.factcast.core.Fact;

public class PGCatchupQueue {
    public PGCatchupQueue(int capacity) {
        queue = new LinkedBlockingQueue<>(capacity);
    }

    @lombok.experimental.Delegate
    final BlockingQueue<Fact> queue;

    final AtomicBoolean done = new AtomicBoolean(false);

    public boolean isDone() {
        return done.get();
    }

    public void notifyDone() {
        done.set(true);
    }
}