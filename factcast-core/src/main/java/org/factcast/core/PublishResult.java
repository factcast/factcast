package org.factcast.core;

import java.util.UUID;

public interface PublishResult {
    // TODO should contain last factId
    public static SUCCESS ok(UUID lastFactId) {
        return new SUCCESS();
    }

    // TODO should contain fail info
    public static FAILURE fail(RuntimeException e) {
        return new FAILURE();
    }

    public static class SUCCESS implements PublishResult {
    }

    public static class FAILURE implements PublishResult {
    }

}
