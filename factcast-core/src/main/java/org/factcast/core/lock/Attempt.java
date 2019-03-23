package org.factcast.core.lock;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.factcast.core.Fact;

import lombok.NonNull;

@FunctionalInterface
public interface Attempt {
    IntermediatePublishResult run() throws AttemptAbortedException;

    /**
     * this is only a convenience method. You can choose to throw
     * AttemptAbortedException from your lambda yourself or use custom
     * subclasses in order to pass additional info out of your lamdba.
     * 
     * @param msg
     * @throws AttemptAbortedException
     */
    static IntermediatePublishResult abort(@NonNull String msg) throws AttemptAbortedException {
        throw new AttemptAbortedException(msg);
    }

    static IntermediatePublishResult publish(@NonNull List<Fact> factsToPublish) {
        return new IntermediatePublishResult(factsToPublish);
    }

    // convenience
    static IntermediatePublishResult publish(@NonNull Fact f, Fact... other) {
        List<Fact> l = new LinkedList<>();
        l.add(f);
        if (other != null)
            l.addAll(Arrays.asList(other));
        return publish(l);
    }

}